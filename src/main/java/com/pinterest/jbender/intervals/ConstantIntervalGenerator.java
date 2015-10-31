package com.pinterest.jbender.intervals;

public class ConstantIntervalGenerator implements IntervalGenerator {
  public ConstantIntervalGenerator(long interval) {
    this.interval = interval;
  }

  @Override
  public long nextInterval(long nanoTimeSinceStart) {
    return interval;
  }

  private final long interval;
}
