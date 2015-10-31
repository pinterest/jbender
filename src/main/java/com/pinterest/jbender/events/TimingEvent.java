package com.pinterest.jbender.events;

import com.google.common.base.MoreObjects;

/**
 * TimingEvents collect timing information and result data for a single request from the load
 * tester.
 *
 * @param <T> the response type from the service being tested.
 */
public class TimingEvent<T> {
  public final long waitNanos;
  public final long durationNanos;
  public final long overageNanos;
  public final boolean isSuccess;
  public final Exception exception;
  public final T response;

  private TimingEvent(final long waitNanos,
                      final long durationNanos,
                      long overageNanos,
                      final T response,
                      final Exception exc)
  {
    this.response = response;
    this.exception = exc;
    this.waitNanos = waitNanos;
    this.durationNanos = durationNanos;
    this.overageNanos = overageNanos;
    this.isSuccess = exc == null;
  }

  public TimingEvent(
      final long waitNanos, final long durationNanos, long overageNanos, final T response) {
    this(waitNanos, durationNanos, overageNanos, response, null);
  }

  public TimingEvent(
      final long waitNanos, final long durationNanos, long overageNanos, final Exception exc) {
    this(waitNanos, durationNanos, overageNanos, null, exc);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("waitNanos", waitNanos)
        .add("durationNanos", durationNanos)
        .add("overageNanos", overageNanos)
        .add("isSuccess", isSuccess)
        .add("exception", exception)
        .add("response", response)
        .toString();
  }
}
