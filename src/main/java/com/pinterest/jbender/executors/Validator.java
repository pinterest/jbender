package com.pinterest.jbender.executors;

/**
 * Validator of {@code T} values.
 *
 * @param <T> The value class.
 */
@FunctionalInterface
public interface Validator<T> {
  /**
   * Will throw an unchecked exception if validation failes
   */
  void validate(final T value);
}
