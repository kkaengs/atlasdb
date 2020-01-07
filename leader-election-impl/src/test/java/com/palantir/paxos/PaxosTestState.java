/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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
package com.palantir.paxos;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import org.assertj.core.api.Assertions;

import com.palantir.leader.LeaderElectionService;
import com.palantir.leader.LeaderElectionService.LeadershipToken;
import com.palantir.leader.LeaderElectionService.StillLeadingStatus;

public class PaxosTestState {
    private final List<LeaderElectionService> leaders;
    private final List<PaxosLearner> learners;
    private final List<AtomicBoolean> failureToggles;
    private final ExecutorService executor;

    PaxosTestState(
            List<LeaderElectionService> leaders,
            List<PaxosLearner> learners,
            List<AtomicBoolean> failureToggles,
            ExecutorService executor) {
        this.leaders = leaders;
        this.learners = learners;
        this.failureToggles = failureToggles;
        this.executor = executor;
    }

    void goDown(int idx) {
        failureToggles.get(idx).set(true);
    }

    void comeUp(int idx) {
        failureToggles.get(idx).set(false);
    }

    LeadershipToken gainLeadership(int leaderNum) {
        return gainLeadership(leaderNum, true /* check leadership afterwards */);
    }

    private LeadershipToken gainLeadership(int leaderNum, boolean checkAfterwards) {
        LeaderElectionService.LeadershipToken token = null;
        try {
            token = leader(leaderNum).blockOnBecomingLeader();
        } catch (InterruptedException e) {
            Assertions.fail(e.getMessage(), e);
        }
        if (checkAfterwards) {
            assertEquals(
                    "leader should still be leading right after becoming leader",
                    StillLeadingStatus.LEADING,
                    leader(leaderNum).isStillLeading(token));
        }
        return token;
    }

    LeadershipToken gainLeadershipWithoutCheckingAfter(int leaderNum) {
        return gainLeadership(leaderNum, false /* check leadership afterwards */);
    }

    public LeaderElectionService leader(int idx) {
        return leaders.get(idx);
    }

    PaxosLearner learner(int idx) {
        return learners.get(idx);
    }

    public ExecutorService getExecutor() {
        return executor;
    }
}
