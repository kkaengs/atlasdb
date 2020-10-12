/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.atlasdb.keyvalue.api.watch;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import com.palantir.atlasdb.transaction.api.TransactionLockWatchFailedException;
import com.palantir.lock.watch.LockWatchCreatedEvent;
import com.palantir.lock.watch.LockWatchEvent;
import com.palantir.lock.watch.LockWatchStateUpdate;
import com.palantir.lock.watch.LockWatchVersion;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;

final class LockWatchEventLog {
    private final ClientLockWatchSnapshot snapshot;
    private final VersionedEventStore eventStore;
    private Optional<LockWatchVersion> latestVersion = Optional.empty();

    static LockWatchEventLog create(int maxEvents) {
        return new LockWatchEventLog(ClientLockWatchSnapshot.create(), maxEvents);
    }

    private LockWatchEventLog(ClientLockWatchSnapshot snapshot, int maxEvents) {
        this.snapshot = snapshot;
        this.eventStore = new VersionedEventStore(maxEvents);
    }

    CacheUpdate processUpdate(LockWatchStateUpdate update) {
        if (!latestVersion.isPresent() || !update.logId().equals(latestVersion.get().id())) {
            return update.accept(new NewLeaderVisitor());
        } else {
            return update.accept(new ProcessingVisitor());
        }
    }

    /**
     * @param versionBounds contains:
     *                     - the latest version the client knows about, which should be before or equal to the
     *                       timestamps in the mapping;
     *                     - the end version, which is the upper bound of events that should be returned;
     *                     - the earliest version to which the events may be condensed to in the case of a snapshot.
     * @return lock watch events that occurred from (exclusive) the provided version, up to the end version (inclusive);
     *         this may begin with a snapshot if the latest version is too far behind.
     */
    public ClientLogEvents getEventsBetweenVersions(VersionBounds versionBounds) {
        Optional<LockWatchVersion> startVersion = versionBounds.startVersion().map(this::createStartVersion);
        LockWatchVersion currentVersion = getLatestVersionAndVerify(versionBounds.endVersion());

        if (!startVersion.isPresent() || differentLeaderOrTooFarBehind(currentVersion, startVersion.get())) {
            long snapshotVersion = versionBounds.snapshotVersion();
            Collection<LockWatchEvent> afterSnapshotEvents = eventStore.getEventsBetweenVersionsInclusive(
                    Optional.of(snapshotVersion + 1), versionBounds.endVersion().version());

            return new ClientLogEvents.Builder()
                    .clearCache(true)
                    .events(new LockWatchEvents.Builder()
                            .addEvents(getCompressedSnapshot(versionBounds))
                            .addAllEvents(afterSnapshotEvents)
                            .build())
                    .build();
        } else {
            return new ClientLogEvents.Builder()
                    .clearCache(false)
                    .events(new LockWatchEvents.Builder()
                            .addAllEvents(eventStore.getEventsBetweenVersionsInclusive(
                                    Optional.of(startVersion.get().version()), versionBounds.endVersion().version()))
                            .build())
                    .build();
        }
    }

    void retentionEvents() {
        getLatestKnownVersion().ifPresent(version -> {
            LockWatchEvents eventsToBeRemoved = eventStore.retentionEvents();
            snapshot.processEvents(eventsToBeRemoved, version.id());
        });
    }

    Optional<LockWatchVersion> getLatestKnownVersion() {
        return latestVersion;
    }

    @VisibleForTesting
    LockWatchEventLogState getStateForTesting() {
        return ImmutableLockWatchEventLogState.builder()
                .latestVersion(latestVersion)
                .eventStoreState(eventStore.getStateForTesting())
                .snapshotState(snapshot.getStateForTesting())
                .build();
    }

    private LockWatchEvent getCompressedSnapshot(VersionBounds versionBounds) {
        long snapshotVersion = versionBounds.snapshotVersion();
        Collection<LockWatchEvent> collapsibleEvents =
                eventStore.getEventsBetweenVersionsInclusive(Optional.empty(), snapshotVersion);
        LockWatchEvents events = new LockWatchEvents.Builder().addAllEvents(collapsibleEvents).build();

        return LockWatchCreatedEvent.fromSnapshot(
                snapshot.getSnapshotWithEvents(events, versionBounds.leader()));
    }

    private boolean differentLeaderOrTooFarBehind(LockWatchVersion currentVersion,
            LockWatchVersion startVersion) {
        return !startVersion.id().equals(currentVersion.id())
                || !eventStore.containsEntryLessThanOrEqualTo(startVersion.version());
    }

    private LockWatchVersion createStartVersion(LockWatchVersion startVersion) {
        return LockWatchVersion.of(startVersion.id(), startVersion.version() + 1);
    }

    private LockWatchVersion getLatestVersionAndVerify(LockWatchVersion endVersion) {
        Preconditions.checkState(latestVersion.isPresent(), "Cannot get events when log does not know its version");
        LockWatchVersion currentVersion = latestVersion.get();
        Preconditions.checkArgument(endVersion.version() <= currentVersion.version(),
                "Transactions' view of the world is more up-to-date than the log");
        return currentVersion;
    }

    private void processSuccess(LockWatchStateUpdate.Success success) {
        Preconditions.checkState(latestVersion.isPresent(), "Must have a known version to process successful updates");
        Optional<LockWatchVersion> snapshotVersion = snapshot.getSnapshotVersion();
        Preconditions.checkState(snapshotVersion.isPresent(),
                "Must have a snapshot before processing successful updates");

        if (success.lastKnownVersion() < snapshotVersion.get().version()) {
            throw new TransactionLockWatchFailedException(
                    "Cannot process events before the oldest event. The transaction should be retried, although this"
                            + " should only happen very rarely.");
        }

        if (success.lastKnownVersion() > latestVersion.get().version()) {
            LockWatchEvents events = new LockWatchEvents.Builder()
                    .addAllEvents(success.events())
                    .build();
            assertNoEventsAreMissing(events);
            latestVersion = Optional.of(LockWatchVersion.of(success.logId(), eventStore.putAll(events)));
        }
    }

    private void assertNoEventsAreMissing(LockWatchEvents events) {
        if (events.events().isEmpty()) {
            return;
        }

        if (latestVersion.isPresent()) {
            Preconditions.checkArgument(events.versionRange().isPresent(),
                    "First element not preset in list of events");
            long firstVersion = events.versionRange().get().lowerEndpoint();
            Preconditions.checkArgument(firstVersion <= latestVersion.get().version()
                            || latestVersion.get().version() + 1 == firstVersion,
                    "Events missing between last snapshot and this batch of events",
                    SafeArg.of("latestVersionSequence", latestVersion.get().version()),
                    SafeArg.of("firstNewVersionSequence", firstVersion));
        }
    }

    private void processSnapshot(LockWatchStateUpdate.Snapshot snapshotUpdate) {
        eventStore.clear();
        snapshot.resetWithSnapshot(snapshotUpdate);
        latestVersion = Optional.of(LockWatchVersion.of(snapshotUpdate.logId(), snapshotUpdate.lastKnownVersion()));
    }

    private void processFailed() {
        eventStore.clear();
        snapshot.reset();
        latestVersion = Optional.empty();
    }

    private class ProcessingVisitor implements LockWatchStateUpdate.Visitor<CacheUpdate> {

        @Override
        public CacheUpdate visit(LockWatchStateUpdate.Success success) {
            processSuccess(success);
            return new CacheUpdate(false,
                    Optional.of(LockWatchVersion.of(success.logId(), success.lastKnownVersion())));
        }

        @Override
        public CacheUpdate visit(LockWatchStateUpdate.Snapshot snapshotUpdate) {
            processSnapshot(snapshotUpdate);
            return new CacheUpdate(true, latestVersion);
        }
    }

    private class NewLeaderVisitor implements LockWatchStateUpdate.Visitor<CacheUpdate> {

        @Override
        public CacheUpdate visit(LockWatchStateUpdate.Success success) {
            processFailed();
            return CacheUpdate.FAILED;
        }

        @Override
        public CacheUpdate visit(LockWatchStateUpdate.Snapshot snapshotUpdate) {
            processSnapshot(snapshotUpdate);
            return new CacheUpdate(true, latestVersion);
        }
    }
}
