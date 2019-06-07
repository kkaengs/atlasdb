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

import java.util.Collection;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.palantir.common.annotation.Inclusive;

@Path("/learner")
public interface PaxosLearner {

    /**
     * Learn given value for the seq-th round.
     *
     * @param seq round in question
     * @param val value learned for that round
     */
    @POST
    @Path("learn/{seq:.+}")
    @Consumes(MediaType.APPLICATION_JSON)
    void learn(@PathParam("seq") long seq, PaxosValue val);

    /**
     * Returns learned value or null if non-exists.
     *
     * @deprecated use {@link #safeGetLearnedValue(long)} instead.
     *
     */
    @Nullable
    @Deprecated
    default PaxosValue getLearnedValue(long seq) {
        return safeGetLearnedValue(seq).orElse(null);
    }

    @GET
    @Path("learned-value/{seq:.+}")
    @Produces(MediaType.APPLICATION_JSON)
    Optional<PaxosValue> safeGetLearnedValue(@PathParam("seq") long seq);

    /**
     * Returns the learned value for the greatest known round or null if nothing has been learned.
     *
     * @deprecated use {@link #safeGetGreatestLearnedValue()} instead.
     *
     */
    @Nullable
    @Deprecated
    default PaxosValue getGreatestLearnedValue() {
        return safeGetGreatestLearnedValue().orElse(null);
    }

    @GET
    @Path("greatest-learned-value")
    @Produces(MediaType.APPLICATION_JSON)
    Optional<PaxosValue> safeGetGreatestLearnedValue();

    /**
     * Returns some collection of learned values since the seq-th round (inclusive).
     *
     * @param seq lower round cutoff for returned values
     * @return some set of learned values for rounds since the seq-th round
     */
    @GET
    @Path("learned-values-since/{seq:.+}")
    @Produces(MediaType.APPLICATION_JSON)
    Collection<PaxosValue> getLearnedValuesSince(@PathParam("seq") @Inclusive long seq);

}