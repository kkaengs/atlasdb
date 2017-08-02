/*
 * Copyright 2017 Palantir Technologies, Inc. All rights reserved.
 *
 * Licensed under the BSD-3 License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.timelock.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.palantir.timelock.partition.TimeLockPartitioner;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", visible = false)
@JsonSubTypes({
        @JsonSubTypes.Type(value = GreedyPartitionerConfiguration.class, name = "greedy"),
        @JsonSubTypes.Type(value = NopPartitionerConfiguration.class, name = "nop"),
        @JsonSubTypes.Type(value = LptPartitionerConfiguration.class, name = "lpt")})
public interface PartitionerConfiguration {
    // Each client is given a cluster of this size.
    int miniclusterSize();

    String type();

    TimeLockPartitioner createPartitioner();

    /**
     * Default configuration does not engage the time limiter at all.
     */
    static PartitionerConfiguration getDefaultConfiguration() {
        return ImmutableNopPartitionerConfiguration.builder()
                .miniclusterSize(1) // not used in Nop Partitioner, in any case
                .build();
    }
}
