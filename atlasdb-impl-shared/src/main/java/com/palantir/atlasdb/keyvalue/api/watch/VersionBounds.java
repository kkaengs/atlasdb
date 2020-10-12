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

import java.util.Optional;
import java.util.UUID;

import org.immutables.value.Value;

import com.palantir.lock.watch.LockWatchVersion;
import com.palantir.logsafe.Preconditions;

@Value.Immutable
interface VersionBounds {
    Optional<LockWatchVersion> startVersion();

    LockWatchVersion endVersion();

    /**
     * This field encodes the fact that, if startVersion is too far behind, we need to know how far we can fast-forward
     * the snapshot.
     */
    Optional<Long> earliestSnapshotVersion();

    @Value.Derived
    default long snapshotVersion() {
        return earliestSnapshotVersion().orElseGet(() -> endVersion().version());
    }

    @Value.Derived
    default UUID leader() {
        return endVersion().id();
    }

    @Value.Check
    default void checkTimestampOrdering() {
        if (startVersion().isPresent()) {
            Preconditions.checkArgument(startVersion().get().version() <= endVersion().version(),
                    "The start version version cannot exceed the end version");

            Preconditions.checkArgument(startVersion().get().version() <= snapshotVersion(),
                    "The start version cannot exceed the snapshot version");
        }

        Preconditions.checkArgument(snapshotVersion() <= endVersion().version(),
                "The snapshot version cannot exceed the end version");
    }

    class Builder extends ImmutableVersionBounds.Builder {}
}
