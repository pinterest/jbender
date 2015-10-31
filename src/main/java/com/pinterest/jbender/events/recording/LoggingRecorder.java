package com.pinterest.jbender.events.recording;

import com.pinterest.jbender.events.TimingEvent;
import org.slf4j.Logger;

/**
 * A very simple Recorder that logs each TimingEvent at the INFO level, using the
 * TimingEvent#toString method.
 */
public class LoggingRecorder implements Recorder {
  private final Logger log;

  public LoggingRecorder(final Logger l) {
    this.log = l;
  }

  @Override
  public void record(final TimingEvent e) {
    log.info(e.toString());
  }
}
