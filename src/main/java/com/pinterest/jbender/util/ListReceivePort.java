package com.pinterest.jbender.util;

import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.strands.Timeout;
import co.paralleluniverse.strands.channels.ReceivePort;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class ListReceivePort<T> implements ReceivePort<T> {
  private final List<T> list;
  private final int total;
  private int cur;

  public ListReceivePort(List<T> list) {
    this.list = list;
    total = list.size();
    cur = 0;
  }

  public ListReceivePort(List<T> list, int total) {
    this.list = list;
    this.total = total;
    cur = 0;
  }

  @Override
  public T receive() throws SuspendExecution, InterruptedException {
    if (cur >= total) {
      return null;
    }

    return list.get(cur++ % list.size());
  }

  @Override
  public T receive(long timeout, TimeUnit unit) throws SuspendExecution, InterruptedException {
    return receive();
  }

  @Override
  public T receive(Timeout timeout) throws SuspendExecution, InterruptedException {
    return receive();
  }

  @Override
  public T tryReceive() {
    if (cur >= total) {
      return null;
    }

    return list.get(cur++ % list.size());
  }

  @Override
  public void close() {}

  @Override
  public boolean isClosed() {
    return false;
  }
}
