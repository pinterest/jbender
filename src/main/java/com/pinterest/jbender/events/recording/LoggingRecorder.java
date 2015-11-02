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
