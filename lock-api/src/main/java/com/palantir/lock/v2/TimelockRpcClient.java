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

import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import com.palantir.lock.client.IdentifiedLockRequest;
import com.palantir.logsafe.Safe;
import com.palantir.processors.AutoDelegate;
import com.palantir.timestamp.TimestampRange;

/**
 * Interface describing timelock endpoints to be used by feign client factories to create raw clients.
 *
 * If you are adding a replacement for an endpoint, please version by number, e.g. a new version of
 * fresh-timestamp might be fresh-timestamp-2.
 */

@Path("/{namespace}/timelock")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@AutoDelegate
public interface TimelockRpcClient {

    @POST
    @Path("fresh-timestamp")
    long getFreshTimestamp(@PathParam("namespace") String namespace);

    @POST
    @Path("fresh-timestamps")
    TimestampRange getFreshTimestamps(
            @PathParam("namespace") String namespace, @Safe @QueryParam("number") int numTimestampsRequested);

    @POST
    @Path("immutable-timestamp")
    long getImmutableTimestamp(@PathParam("namespace") String namespace);

    @POST
    @Path("lock-v2")
    LockResponseV2 lock(@PathParam("namespace") String namespace, IdentifiedLockRequest request);

    @POST
    @Path("await-locks")
    WaitForLocksResponse waitForLocks(@PathParam("namespace") String namespace, WaitForLocksRequest request);

    @POST
    @Path("refresh-locks-v2")
    RefreshLockResponseV2 refreshLockLeases(@PathParam("namespace") String namespace, Set<LockToken> tokens);

    @POST
    @Path("unlock")
    Set<LockToken> unlock(@PathParam("namespace") String namespace, Set<LockToken> tokens);

    @POST
    @Path("current-time-millis")
    long currentTimeMillis(@PathParam("namespace") String namespace);

}
