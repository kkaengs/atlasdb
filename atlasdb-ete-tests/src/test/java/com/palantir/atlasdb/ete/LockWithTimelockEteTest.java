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

package com.palantir.atlasdb.ete;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.Test;

import com.palantir.atlasdb.lock.EteLockResource;

public class LockWithTimelockEteTest {
    private EteLockResource lockResource = EteSetup.createClientToSingleNode(EteLockResource.class);

    @Test
    public void smallV1LockSucceeds() throws InterruptedException {
        assertThat(lockResource.lockUsingLegacyLockApi(1, 100)).isTrue();
    }

    @Test
    public void smallV2LockSucceeds() {
        assertThat(lockResource.lockUsingTimelockApi(1, 100)).isTrue();
    }

    @Test
    public void largeV1LockSucceeds() throws InterruptedException {
        assertThat(lockResource.lockUsingLegacyLockApi(50, 100_000)).isTrue();

    }

    @Test
    public void largeV2LockSucceeds() {
        assertThat(lockResource.lockUsingTimelockApi(50, 100_000)).isTrue();
    }

    @Test
    public void hugeV1LockThrowsOnClientSide() throws InterruptedException {
        assertThatThrownBy(() -> lockResource.lockUsingLegacyLockApi(100, 500_000))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("INVALID_ARGUMENT");
    }

    @Test
    public void hugeV2ThrowsOnClientSide() {
        assertThatThrownBy(() -> lockResource.lockUsingTimelockApi(100, 500_000))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("INVALID_ARGUMENT");
    }
}