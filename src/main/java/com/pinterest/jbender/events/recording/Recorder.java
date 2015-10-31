package com.pinterest.jbender.events.recording;

import co.paralleluniverse.fibers.DefaultFiberScheduler;
import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.FiberScheduler;
import co.paralleluniverse.strands.channels.ReceivePort;
import com.pinterest.jbender.events.TimingEvent;

/**
 * Event recorder interface and main recording facility.
 */
@FunctionalInterface
public interface Recorder<T> {
  void record(TimingEvent<T> e);

  /**
   * Record events in a separate Fiber using one or more Recorders.
   *
   * This method returns immediately after starting the Fiber to record events.
   *
   * @param rp a channel on which to receive TimingEvents, which are passed to the given recorders
   *           in the order specified.
   * @param rs a list of one or more recorders to process each TimingEvent.
   * @param <T> the type of the Response object in each TimingEvent.
   */
  @SafeVarargs
  static <T> Fiber<Void> record(final ReceivePort<TimingEvent<T>> rp, final Recorder<T>... rs) {
    return record("jbender-recorder", null, rp, rs);
  }

  /**
   * Record events in a separate Fiber using one or more Recorders.
   *
   * This method returns immediately after starting the Fiber for recording events.
   *
   * @param fiberName the name of the fiber that records events.
   * @param fe an optional scheduler for the spawned recording fiber, null to use the default.
   * @param rp a channel on which to receive TimingEvents, which are passed to the underlying
   *           Recorders.
   * @param rs zero or more Recorders, each of which receives every event (after the delay period)
   *           in the order they are passed to this method.
   * @param <T> the type of the Response object in each TimingEvent.
   */
  @SafeVarargs
  static <T> Fiber<Void> record(final String fiberName,
                                final FiberScheduler fe,
                                final ReceivePort<TimingEvent<T>> rp,
                                final Recorder<T>... rs)
  {
    return new Fiber<Void>(fiberName, fe != null ? fe : DefaultFiberScheduler.getInstance(), () -> {
      while (true) {
        final TimingEvent<T> event = rp.receive();

        if (event == null) {
          break;
        }

        for (final Recorder<T> r : rs) {
          r.record(event);
        }
      }
      return null;
    }).start();
  }
}
