package com.pinterest.jbender;

import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.strands.channels.Channel;
import co.paralleluniverse.strands.channels.Channels;
import com.pinterest.jbender.events.TimingEvent;
import com.pinterest.jbender.events.recording.HdrHistogramRecorder;
import com.pinterest.jbender.executors.NoopRequestExecutor;
import com.pinterest.jbender.executors.RequestExecutor;
import com.pinterest.jbender.intervals.ConstantIntervalGenerator;
import com.pinterest.jbender.intervals.IntervalGenerator;
import org.HdrHistogram.Histogram;
import org.openjdk.jmh.annotations.Benchmark;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;

import static com.pinterest.jbender.events.recording.Recorder.record;

public class JBenderBenchmark {
  // @Benchmark
  public Histogram loadtestThroughput() throws SuspendExecution, InterruptedException, ExecutionException {
    final IntervalGenerator intervalGenerator = new ConstantIntervalGenerator(1000);
    final RequestExecutor<String, Void> requestExecutor = new NoopRequestExecutor<>();

    final Channel<String> requestCh = Channels.newChannel(10000);
    final Channel<TimingEvent<Void>> eventCh = Channels.newChannel(10000);

    // Requests generator
    new Fiber<Void>("req-gen", () -> {
      // Bench handling 10k reqs
      for (int i = 0; i < 10000; ++i) {
        requestCh.send("message");
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

  private static final Logger LOG = LoggerFactory.getLogger(JBenderBenchmark.class);
}
