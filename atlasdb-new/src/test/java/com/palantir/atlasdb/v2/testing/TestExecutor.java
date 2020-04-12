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

package com.palantir.atlasdb.v2.testing;

import static com.palantir.logsafe.Preconditions.checkState;

import java.util.Collection;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.SetMultimap;
import com.google.common.primitives.Ints;

public final class TestExecutor {
    private static final Logger log = LoggerFactory.getLogger(TestExecutor.class);

    private final PriorityQueue<Task> tasks = new PriorityQueue<>();
    private final Random random = new Random(0);
    private final Random externalRandom = new Random(0);

    private SetMultimap<Long, Long> trace = HashMultimap.create();

    private long globalConstructionTime = 0;

    private long now = 0;
    private long executed = 0;

    private long currentTask = 0;

    public SetMultimap<Long, Long> getTrace() {
        return ImmutableSetMultimap.copyOf(trace);
    }

    private final class Task implements Comparable<Task> {
        private final long runAtTime;
        private final long constructionTime;
        private final long constructedBy;
        private final Runnable task;

        private Task(long runAtTime, long constructedBy, Runnable task) {
            this.runAtTime = runAtTime;
            this.constructionTime = globalConstructionTime++;
            trace.put(constructedBy, constructionTime);
            this.constructedBy = constructedBy;
            this.task = task;
        }

        @Override
        public int compareTo(Task other) {
            int runTimeComparison = Long.compare(runAtTime, other.runAtTime);
            return runTimeComparison != 0 ? runTimeComparison : Long.compare(constructionTime, other.constructionTime);
        }
    }

    public int randomInt(int bound) {
        return externalRandom.nextInt(bound);
    }

    private Task nextTask() {
        Task t = tasks.remove();
        now = t.runAtTime;
        return t;
    }

    public Executor nowScheduler() {
        return task -> tasks.add(new Task(now, currentTask, task));
    }

    // 5% uniformly distributed jitter
    private long jitter(long input) {
        long jitterLimit = input / 20;
        long jitter = random.nextInt(Ints.checkedCast(jitterLimit));
        long result = input + jitter - jitterLimit;
        checkState(result > 0, "negative jitter...");
        return result;
    }

    public Executor soonScheduler() {
        return task -> {
            tasks.add(new Task(now + jitter(1_000), currentTask, task));
        };
    }

    public Executor notSoonScheduler() {
        return task -> {
            tasks.add(new Task(now + jitter(1_000_000), currentTask, task));
        };
    }

    public void start() {
        long sum = 0;
        while (now < TimeUnit.DAYS.toMicros(10) && !tasks.isEmpty()) {
            try {
                executed++;
                Task task = nextTask();
                sum += task.constructedBy;
                currentTask = task.constructionTime;
                task.task.run();
            } catch (Throwable t) {
                log.info("Task threw", t);
            }
            currentTask = 0;
        }
        System.out.println(String.format("Executed %d tasks", executed));
    }

    public ScheduledExecutorService actuallyProgrammableScheduler() {
        return new ScheduledExecutorService() {
            @Override
            public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
                tasks.add(new Task(now + unit.toMicros(delay), currentTask, command));
                return null;
            }

            @Override
            public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period,
                    TimeUnit unit) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay,
                    TimeUnit unit) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void shutdown() {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<Runnable> shutdownNow() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isShutdown() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isTerminated() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> Future<T> submit(Callable<T> task) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> Future<T> submit(Runnable task, T result) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Future<?> submit(Runnable task) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> T invokeAny(Collection<? extends Callable<T>> tasks) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
                    throws InterruptedException, ExecutionException, TimeoutException {
                throw new UnsupportedOperationException();
            }

            @Override
            public void execute(Runnable command) {
                nowScheduler().execute(command);
            }
        };
    }

}
