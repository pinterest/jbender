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
