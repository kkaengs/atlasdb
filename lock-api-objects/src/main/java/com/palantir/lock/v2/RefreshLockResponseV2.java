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

package com.palantir.lock.v2;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.Set;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableRefreshLockResponseV2.class)
@JsonDeserialize(as = ImmutableRefreshLockResponseV2.class)
public interface RefreshLockResponseV2 {
    @Value.Parameter
    Set<LockToken> refreshedTokens();

    @Value.Parameter
    Lease getLease();

    static RefreshLockResponseV2 of(Set<LockToken> tokens, Lease lease) {
        return ImmutableRefreshLockResponseV2.of(tokens, lease);
    }
}
