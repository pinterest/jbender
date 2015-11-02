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
package com.pinterest.jbender.executors;

import co.paralleluniverse.fibers.SuspendExecution;

public class NoopRequestExecutor<Q> implements RequestExecutor<Q, Void> {
  @Override
  public Void execute(final long nanoTime, final Q request) throws SuspendExecution, InterruptedException {
    // NOP
    return null;
  }
}
