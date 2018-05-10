/*******************************************************************************
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package org.eclipse.microprofile.reactive.messaging;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * A message envelope.
 *
 * @param <T> The type of the message payload.
 */
public interface Message<T> {

  /**
   * The payload for this message.
   */
  T getPayload();

  /**
   * Acknowledge this message.
   */
  default CompletionStage<Void> ack() {
    return CompletableFuture.completedFuture(null);
  }

  /**
   * Attach a new payload to this envelope.
   *
   * This acking the returned envelope will ack this envelope. This is useful for transformations from one ackable
   * source to an acking destination.
   */
  default <R> Message<R> withPayload(R payload) {
    Message<T> thisMessage = this;

    return new Message<R>() {
      @Override
      public R getPayload() {
        return payload;
      }

      @Override
      public CompletionStage<Void> ack() {
        return thisMessage.ack();
      }
    };
  }

  static <T> Message<T> ackableMessage(T payload, Supplier<CompletionStage<Void>> ack) {
    return new Message<T>() {
      @Override
      public T getPayload() {
        return payload;
      }

      @Override
      public CompletionStage<Void> ack() {
        return ack.get();
      }
    };
  }
}
