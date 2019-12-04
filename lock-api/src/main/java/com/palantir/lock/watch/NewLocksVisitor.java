/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.lock.watch;

import java.util.Set;

import com.google.common.collect.ImmutableSet;
import com.palantir.lock.LockDescriptor;

public final class NewLocksVisitor implements LockWatchEvent.Visitor<Set<LockDescriptor>> {
    public static final NewLocksVisitor INSTANCE = new NewLocksVisitor();

    @Override
    public Set<LockDescriptor> visit(LockEvent lockEvent) {
        return lockEvent.lockDescriptors();
    }

    @Override
    public Set<LockDescriptor> visit(UnlockEvent unlockEvent) {
        return ImmutableSet.of();
    }

    @Override
    public Set<LockDescriptor> visit(LockWatchOpenLocksEvent openLocksEvent) {
        return openLocksEvent.lockDescriptors();
    }

    @Override
    public Set<LockDescriptor> visit(LockWatchCreatedEvent lockWatchCreatedEvent) {
        return ImmutableSet.of();
    }
}
