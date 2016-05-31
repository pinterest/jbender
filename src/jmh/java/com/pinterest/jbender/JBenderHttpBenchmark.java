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
import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.strands.channels.Channel;
import co.paralleluniverse.strands.channels.Channels;
import com.pinterest.jbender.events.TimingEvent;
import com.pinterest.jbender.events.recording.HdrHistogramRecorder;
import com.pinterest.jbender.executors.http.FiberApacheHttpClientRequestExecutor;
import com.pinterest.jbender.intervals.ConstantIntervalGenerator;
import com.pinterest.jbender.intervals.IntervalGenerator;
import org.HdrHistogram.Histogram;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.openjdk.jmh.annotations.Benchmark;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static com.pinterest.jbender.events.recording.Recorder.record;

/**
 * Sample HTTP benchmark against {@url https://github.com/puniverse/comsat-gradle-template}
 */
public class JBenderHttpBenchmark {
  private static final Logger LOG = LoggerFactory.getLogger(JBenderHttpBenchmark.class);

  @Benchmark
  public Histogram loadtestHttpThroughput() throws SuspendExecution, InterruptedException, ExecutionException, IOException {
    final IntervalGenerator intervalGenerator = new ConstantIntervalGenerator(10000000);
    try (final FiberApacheHttpClientRequestExecutor requestExecutor =
      new FiberApacheHttpClientRequestExecutor<>((res) -> {
        if (res == null) {
          throw new AssertionError("Response is null");
        }
        final int status = res.getStatusLine().getStatusCode();
        if (status != 200) {
          throw new AssertionError("Status is " + status);
        }
      }, 1000000)) {

      final Channel<HttpGet> requestCh = Channels.newChannel(1000);
      final Channel<TimingEvent<CloseableHttpResponse>> eventCh = Channels.newChannel(1000);

      // Requests generator
      new Fiber<Void>("req-gen", () -> {
        // Bench handling 1k reqs
        for (int i = 0; i < 1000; ++i) {
          requestCh.send(new HttpGet("http://localhost:8080/hello-world"));
        }

        requestCh.close();
      }).start();

      final Histogram histogram = new Histogram(3600000000L, 3);

      // Event recording, both HistHDR and logging
      record(eventCh, new HdrHistogramRecorder(histogram, 1000000));

      // Main
      new Fiber<Void>("jbender", () -> {
        JBender.loadTestThroughput(intervalGenerator, 0, requestCh, requestExecutor, eventCh);
        eventCh.close();
      }).start().join();

      // Avoid code elimination
      return histogram;
    }
  }
}
