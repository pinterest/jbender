/*
Copyright 2014 Pinterest.com
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
    http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
package com.pinterest.jbender;

import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.FiberScheduler;
import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.strands.*;
import co.paralleluniverse.strands.channels.ReceivePort;
import co.paralleluniverse.strands.channels.SendPort;
import co.paralleluniverse.strands.concurrent.Semaphore;
import com.pinterest.jbender.events.TimingEvent;
import com.pinterest.jbender.executors.RequestExecutor;
import com.pinterest.jbender.intervals.IntervalGenerator;
import com.pinterest.jbender.util.WaitGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * JBender has static methods for running load tests by throughput or concurrency.
 */
public final class JBender {
  private static final Logger LOG = LoggerFactory.getLogger(JBender.class);

  private JBender() {}

  /**
   * Run a load test with the given throughput, using as many fibers as necessary.
   *
   * This method can be run in any strand; thread-fiber synchronization is more expensive than
   * fiber-fiber synchronization though, so if requests are being performed by fibers its best
   * to call this method inside a fiber.
   *
   * @param intervalGen provides the interval between subsequent requests (in nanoseconds). This
   *                    controls the throughput of the load test.
   * @param warmupRequests the number of requests to use as "warmup" for the load tester and the
   *                       service. These requests will not have TimingEvents generated in the
   *                       eventChannel, but will be sent to the remote service at the requested
   *                       rate.
   * @param requests provides requests for the load test, must be closed by the caller to stop the
   *                 load test (the load test will continue for as long as this channel is open,
   *                 even if there are no requests arriving).
   * @param executor executes the requests provided by the requests channel, returning a response
   *                 object.
   * @param eventChannel a TimingEvent is sent on this channel for every request executed during
   *                     the load test (whether the request succeeds or not).
   * @param <Req> the request type.
   * @param <Res> the response type.
   * @throws SuspendExecution
   * @throws InterruptedException
   */
  public static <Req, Res> void loadTestThroughput(final IntervalGenerator intervalGen,
                                                   final int warmupRequests,
                                                   final ReceivePort<Req> requests,
                                                   final RequestExecutor<Req, Res> executor,
                                                   final SendPort<TimingEvent<Res>> eventChannel)
    throws InterruptedException, SuspendExecution
  {
    loadTestThroughput(intervalGen, warmupRequests, requests, executor, eventChannel, null, null);
  }

  /**
   * Run a load test with a given throughput, using as many fibers as necessary.
   *
   * This method can be run in any strand; thread-fiber synchronization is more expensive than
   * fiber-fiber synchronization though, so if requests are being performed by fibers its best
   * to call this method inside a fiber.
   *
   * @param intervalGen provides the interval between subsequent requests (in nanoseconds). This
   *                    controls the throughput of the load test.
   * @param warmupRequests the number of requests to use as "warmup" for the load tester and the
   *                       service. These requests will not have TimingEvents generated in the
   *                       eventChannel, but will be sent to the remote service at the requested
   *                       rate.
   * @param requests provides requests for the load test, must be closed by the caller to stop the
   *                 load test (the load test will continue for as long as this channel is open,
   *                 even if there are no requests arriving).
   * @param executor executes the requests provided by the requests channel, returning a response
   *                 object.
   * @param eventChannel a TimingEvent is sent on this channel for every request executed during
   *                     the load test (whether the request succeeds or not).
   * @param fiberScheduler an optional scheduler for fibers that will perform the requests (the
   *                       default one will be used if {@code null}).
   * @param <Req> the request type.
   * @param <Res> the response type.
   * @throws SuspendExecution
   * @throws InterruptedException
   */
  public static <Req, Res> void loadTestThroughput(final IntervalGenerator intervalGen,
                                                   final int warmupRequests,
                                                   final ReceivePort<Req> requests,
                                                   final RequestExecutor<Req, Res> executor,
                                                   final SendPort<TimingEvent<Res>> eventChannel,
                                                   final FiberScheduler fiberScheduler)
          throws InterruptedException, SuspendExecution
  {
    loadTestThroughput(intervalGen, warmupRequests, requests, executor, eventChannel, fiberScheduler, null);
  }

  /**
   * Run a load test with a given throughput, using as many fibers as necessary.
   *
   * This method can be run in any strand; thread-fiber synchronization is more expensive than
   * fiber-fiber synchronization though, so if requests are being performed by fibers its best
   * to call this method inside a fiber.
   *
   * @param intervalGen provides the interval between subsequent requests (in nanoseconds). This
   *                    controls the throughput of the load test.
   * @param warmupRequests the number of requests to use as "warmup" for the load tester and the
   *                       service. These requests will not have TimingEvents generated in the
   *                       eventChannel, but will be sent to the remote service at the requested
   *                       rate.
   * @param requests provides requests for the load test, must be closed by the caller to stop the
   *                 load test (the load test will continue for as long as this channel is open,
   *                 even if there are no requests arriving).
   * @param executor executes the requests provided by the requests channel, returning a response
   *                 object.
   * @param eventChannel a TimingEvent is sent on this channel for every request executed during
   *                     the load test (whether the request succeeds or not).
   * @param strandFactory an optional factory for strands that will perform the requests (the
   *                      default one will be used if {@code null}).
   * @param <Req> the request type.
   * @param <Res> the response type.
   * @throws SuspendExecution
   * @throws InterruptedException
   */
  public static <Req, Res> void loadTestThroughput(final IntervalGenerator intervalGen,
                                                   final int warmupRequests,
                                                   final ReceivePort<Req> requests,
                                                   final RequestExecutor<Req, Res> executor,
                                                   final SendPort<TimingEvent<Res>> eventChannel,
                                                   final StrandFactory strandFactory)
          throws InterruptedException, SuspendExecution
  {
    loadTestThroughput(intervalGen, warmupRequests, requests, executor, eventChannel, null, strandFactory);
  }

  /**
   * Run a load test with a given number of fibers, making as many requests as possible.
   *
   * This method can be run in any strand; thread-fiber synchronization is more expensive than
   * fiber-fiber synchronization though, so if requests are being performed by fibers its best
   * to call this method inside a fiber.
   *
   * @param concurrency the number of Fibers to run. Each Fiber will execute requests serially with
   *                    as little overhead as possible.
   * @param warmupRequests the number of requests to use when warming up the load tester and the
   *                       remote service. These requests will not not have TimingEvents generated
   *                       in the eventChannel, but will be sent to the remote service.
   * @param requests provides requests for the load test and must be closed by the caller to stop
   *                 the load test (the load test will continue for as long as this channel is
   *                 open, even if there are no requests arriving).
   * @param executor executes the requets provided by the requests channel, returning a response
   *                 object.
   * @param eventChannel a TimingEvent is sent on this channel for every request executed during
   *                     the load test (whether the request succeeds or not).
   * @param <Req> the request type.
   * @param <Res> the response type.
   * @throws SuspendExecution
   * @throws InterruptedException
   */
  public static <Req, Res> void loadTestConcurrency(final int concurrency,
                                                    final int warmupRequests,
                                                    final ReceivePort<Req> requests,
                                                    final RequestExecutor<Req, Res> executor,
                                                    final SendPort<TimingEvent<Res>> eventChannel)
    throws SuspendExecution, InterruptedException
  {
    loadTestConcurrency(concurrency, warmupRequests, requests, executor, eventChannel, null, null);
  }

  /**
   * Run a load test with a given number of fibers, making as many requests as possible.
   *
   * This method can be run in any strand; thread-fiber synchronization is more expensive than
   * fiber-fiber synchronization though, so if requests are being performed by fibers its best
   * to call this method inside a fiber.
   *
   * @param concurrency the number of Fibers to run. Each Fiber will execute requests serially with
   *                    as little overhead as possible.
   * @param warmupRequests the number of requests to use when warming up the load tester and the
   *                       remote service. These requests will not not have TimingEvents generated
   *                       in the eventChannel, but will be sent to the remote service.
   * @param requests provides requests for the load test and must be closed by the caller to stop
   *                 the load test (the load test will continue for as long as this channel is
   *                 open, even if there are no requests arriving).
   * @param executor executes the requets provided by the requests channel, returning a response
   *                 object.
   * @param eventChannel a TimingEvent is sent on this channel for every request executed during
   *                     the load test (whether the request succeeds or not).
   * @param fiberScheduler an optional scheduler for fibers that will perform the requests (the
   *                       default one will be used if {@code null}).
   * @param <Req> the request type.
   * @param <Res> the response type.
   * @throws SuspendExecution
   * @throws InterruptedException
   */
  public static <Req, Res> void loadTestConcurrency(final int concurrency,
                                                    final int warmupRequests,
                                                    final ReceivePort<Req> requests,
                                                    final RequestExecutor<Req, Res> executor,
                                                    final SendPort<TimingEvent<Res>> eventChannel,
                                                    final FiberScheduler fiberScheduler)
          throws SuspendExecution, InterruptedException
  {
    loadTestConcurrency(concurrency, warmupRequests, requests, executor, eventChannel, fiberScheduler, null);
  }

  /**
   * Run a load test with a given number of fibers, making as many requests as possible.
   *
   * This method can be run in any strand; thread-fiber synchronization is more expensive than
   * fiber-fiber synchronization though, so if requests are being performed by fibers its best
   * to call this method inside a fiber.
   *
   * @param concurrency the number of Fibers to run. Each Fiber will execute requests serially with
   *                    as little overhead as possible.
   * @param warmupRequests the number of requests to use when warming up the load tester and the
   *                       remote service. These requests will not not have TimingEvents generated
   *                       in the eventChannel, but will be sent to the remote service.
   * @param requests provides requests for the load test and must be closed by the caller to stop
   *                 the load test (the load test will continue for as long as this channel is
   *                 open, even if there are no requests arriving).
   * @param executor executes the requets provided by the requests channel, returning a response
   *                 object.
   * @param eventChannel a TimingEvent is sent on this channel for every request executed during
   *                     the load test (whether the request succeeds or not).
   * @param strandFactory an optional factory for strands that will perform the requests (the
   *                      default one will be used if {@code null}).
   * @param <Req> the request type.
   * @param <Res> the response type.
   * @throws SuspendExecution
   * @throws InterruptedException
   */
  public static <Req, Res> void loadTestConcurrency(final int concurrency,
                                                    final int warmupRequests,
                                                    final ReceivePort<Req> requests,
                                                    final RequestExecutor<Req, Res> executor,
                                                    final SendPort<TimingEvent<Res>> eventChannel,
                                                    final StrandFactory strandFactory)
          throws SuspendExecution, InterruptedException
  {
    loadTestConcurrency(concurrency, warmupRequests, requests, executor, eventChannel, null, strandFactory);
  }

  private static class RequestExecOutcome<Res> {
    final long execTime;
    final Res response;
    final Exception exception;

    public RequestExecOutcome(final long execTime, final Res response, final Exception exception) {
      this.execTime = execTime;
      this.response = response;
      this.exception = exception;
    }
  }

  private static <Req, Res> void loadTestThroughput(final IntervalGenerator intervalGen,
                                                    int warmupRequests,
                                                    final ReceivePort<Req> requests,
                                                    final RequestExecutor<Req, Res> executor,
                                                    final SendPort<TimingEvent<Res>> eventChannel,
                                                    final FiberScheduler fiberScheduler,
                                                    final StrandFactory strandFactory)
          throws SuspendExecution, InterruptedException
  {
    final long startNanos = System.nanoTime();

    try {
      long overageNanos = 0;
      long overageStart = System.nanoTime();

      final WaitGroup waitGroup = new WaitGroup();
      while (true) {
        final long receiveNanosStart = System.nanoTime();
        final Req request = requests.receive();
        LOG.trace("Receive request time: {}", System.nanoTime() - receiveNanosStart);
        if (request == null) {
          break;
        }

        // Wait before dispatching request as much as generated, minus the remaining dispatching overhead
        // to be compensated for (up to having 0 waiting time of course, not negative)
        long waitNanos = intervalGen.nextInterval(System.nanoTime() - startNanos);
        final long adjust = Math.min(waitNanos, overageNanos);
        waitNanos -= adjust;
        overageNanos -= adjust;

        // Sleep in the accepting fiber
        long sleepNanosStart = System.nanoTime();
        Strand.sleep(waitNanos, TimeUnit.NANOSECONDS);
        LOG.trace("Sleep time: {}", System.nanoTime() - sleepNanosStart);

        // Increment wait group count for new request handler
        waitGroup.add();
        final long curWaitNanos = waitNanos;
        final long curWarmupRequests = warmupRequests;
        final long curOverageNanos = overageNanos;

        final SuspendableCallable<Void> sc = () -> {
          try {
            final RequestExecOutcome<Res> outcome = executeRequest(request, executor);
            if (curWarmupRequests <= 0) {
              report(curWaitNanos, curOverageNanos, outcome, eventChannel);
            }
          } finally {
            // Complete, decrementing wait group count
            waitGroup.done();
          }
          return null;
        };
        if (fiberScheduler != null) {
          new Fiber<>(fiberScheduler, sc).start();
        } else if (strandFactory != null) {
          strandFactory.newStrand(sc).start();
        } else {
          new Fiber<>(sc).start();
        }

        final long nowNanos = System.nanoTime();
        overageNanos += nowNanos - overageStart - waitNanos;
        overageStart = nowNanos;
        warmupRequests = Math.max(warmupRequests - 1, 0);
      }

      // Wait for all outstanding requests
      waitGroup.await();
    } finally {
      eventChannel.close();
    }
  }

  private static <Req, Res> void loadTestConcurrency(final int concurrency,
                                                     int warmupRequests,
                                                     final ReceivePort<Req> requests,
                                                     final RequestExecutor<Req, Res> executor,
                                                     final SendPort<TimingEvent<Res>> eventChannel,
                                                     final FiberScheduler fiberScheduler,
                                                     final StrandFactory strandFactory)
          throws SuspendExecution, InterruptedException
  {
    try {
      final WaitGroup waitGroup = new WaitGroup();
      final Semaphore running = new Semaphore(concurrency);

      while (true) {
        final Req request = requests.receive();
        if (request == null) {
          break;
        }

        running.acquire();
        waitGroup.add();
        final long curWarmupRequests = warmupRequests;
        final SuspendableCallable<Void> sc = () -> {
          try {
            final RequestExecOutcome<Res> outcome = executeRequest(request, executor);
            if (curWarmupRequests <= 0) {
              report(0, 0, outcome, eventChannel);
            }
          } finally {
            running.release();
            waitGroup.done();
          }
          return null;
        };
        if (fiberScheduler != null) {
          new Fiber<>(fiberScheduler, sc).start();
        } else if (strandFactory != null) {
          strandFactory.newStrand(sc).start();
        } else {
          new Fiber<>(sc).start();
        }

        warmupRequests = Math.max(warmupRequests - 1, 0);
      }

      waitGroup.await();
    } finally {
      eventChannel.close();
    }
  }

  private static <Req, Res> RequestExecOutcome<Res> executeRequest(final Req request, final RequestExecutor<Req, Res> executor)
          throws SuspendExecution, InterruptedException
  {
    Res response = null;
    Exception exc = null;
    final long startNanos = System.nanoTime();
    try {
      response = executor.execute(startNanos, request);
    } catch (final Exception ex) {
      LOG.error("Exception while executing request {}", request, ex);
      exc = ex;
    }
    return new RequestExecOutcome<>(System.nanoTime() - startNanos, response, exc);
  }

  private static <Res> void report(final long curWaitNanos,
                                   long curOverageNanos,
                                   final RequestExecOutcome<Res> outcome,
                                   final SendPort<TimingEvent<Res>> eventChannel)
      throws SuspendExecution, InterruptedException
  {
    if (outcome.exception == null) {
      eventChannel.send(
          new TimingEvent<>(curWaitNanos, outcome.execTime, curOverageNanos, outcome.response));
    } else {
      eventChannel.send(
          new TimingEvent<>(curWaitNanos, outcome.execTime, curOverageNanos, outcome.exception));
    }
  }
}
