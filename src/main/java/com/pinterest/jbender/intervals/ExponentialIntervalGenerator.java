package com.pinterest.jbender.intervals;

import java.util.Random;

/**
 * Poisson distribution request interval generator.
 */
public class ExponentialIntervalGenerator implements IntervalGenerator {
  public ExponentialIntervalGenerator(int queriesPerSecond) {
    nanosPerQuery = 1000000000.0 / queriesPerSecond;
    this.rand = new Random();
  }

  @Override
  public long nextInterval(long nanoTimeSinceStart) {
    return (long) (-Math.log(rand.nextDouble()) * this.nanosPerQuery);
  }

  private final double nanosPerQuery;
  private final Random rand;
}
