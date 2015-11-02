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
package com.pinterest.jbender.events.recording;

import com.pinterest.jbender.events.TimingEvent;
import org.HdrHistogram.Histogram;

/**
 * Records the duration of each TimingEvent in an HdrHistogram and keeps track of the total number
 * of errors and the start and end time of the load test.
 */
public class HdrHistogramRecorder implements Recorder {
  private final long scale;
  private boolean started;

  // The histogram used to record durations
  public final Histogram histogram;

  // The number of errors seen by this recorder so far.
  public long errorCount;

  // The start time of the first TimingEvent seen by this recorder. This is an estimate of the
  // start time of the load test, and not exact.
  public long startNanos;

  // The end time of the latest TimingEvent seen by this recorder.
  public long endNanos;

  /**
   * Constructor.
   *
   * @param h the HdrHistogram object into which values are written.
   * @param scale the value by which to divide the durationNanos field of each TimingEvent before
   *              recording it in the histogram. For example, to record milliseconds you would set
   *              the scale to 1,000,000, for microseconds you would use 1,000, and so on.
   */
  public HdrHistogramRecorder(final Histogram h, long scale) {
    this.histogram = h;
    this.scale = scale;
    this.errorCount = 0;
    this.startNanos = System.nanoTime();
    this.endNanos = System.nanoTime();
    this.started = false;
  }

  @Override
  public void record(final TimingEvent e) {
    if (!started) {
      started = true;
      startNanos = System.nanoTime() - e.durationNanos;
    }

    if (!e.isSuccess) {
      errorCount++;
    }

    histogram.recordValue(e.durationNanos / scale);

    endNanos = System.nanoTime();
  }
}
