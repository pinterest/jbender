package com.pinterest.jbender.executors;

import co.paralleluniverse.fibers.SuspendExecution;

/**
 * Request executor interface.
 *
 * @param <Q> The request class.
 * @param <S> The response class.
 */
public interface RequestExecutor<Q, S> {
  /**
   * The suspendable request execution logic.
   *
   * @param nanoTime The request execution start time.
   * @param request  The request to be executed.
   *
   * @return The response value.
   */
  S execute(long nanoTime, Q request) throws SuspendExecution, InterruptedException;
}
