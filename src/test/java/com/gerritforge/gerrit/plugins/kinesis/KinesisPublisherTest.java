// Copyright (C) 2026 GerritForge, Inc.
//
// Licensed under the BSL 1.1 (the "License");
// you may not use this file except in compliance with the License.
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.gerritforge.gerrit.plugins.kinesis;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.services.kinesis.producer.KinesisProducer;
import com.amazonaws.services.kinesis.producer.UserRecordResult;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.EventGsonProvider;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class KinesisPublisherTest {
  private static final String EXPLICIT_HASH_KEY = "49";
  private static final String LOGICAL_PARTITION = "change-index";
  private static final String STREAM_EVENTS = "stream-events";

  @Mock KinesisProducer kinesisProducer;
  @Mock Configuration configuration;
  @Mock KinesisShardResolver shardResolver;
  @Mock UserRecordResult userRecordResult;

  private ExecutorService callbackExecutor;
  private KinesisPublisher publisher;

  @Before
  public void setUp() {
    callbackExecutor = MoreExecutors.newDirectExecutorService();
    publisher =
        new KinesisPublisher(
            new EventGsonProvider().get(),
            kinesisProducer,
            configuration,
            callbackExecutor,
            shardResolver);
  }

  @Test
  public void shouldPublishPartitionAwareEventWithExplicitHashKey() {
    Event event = event();
    when(configuration.isSendAsync()).thenReturn(true);
    when(kinesisProducer.addUserRecord(
            eq(STREAM_EVENTS), eq(LOGICAL_PARTITION), eq(EXPLICIT_HASH_KEY), any(ByteBuffer.class)))
        .thenReturn(Futures.immediateFuture(userRecordResult));

    publisher.publish(
        STREAM_EVENTS,
        event,
        Optional.of(
            new KinesisShardResolver.ShardRouting(
                LOGICAL_PARTITION, "shardId-000000000000", EXPLICIT_HASH_KEY)));

    verify(kinesisProducer)
        .addUserRecord(
            eq(STREAM_EVENTS), eq(LOGICAL_PARTITION), eq(EXPLICIT_HASH_KEY), any(ByteBuffer.class));
    verify(kinesisProducer, never())
        .addUserRecord(eq(STREAM_EVENTS), eq(LOGICAL_PARTITION), any(ByteBuffer.class));
  }

  @Test
  public void shouldPublishPartitionAwareEventWithExplicitHashKeySync() throws Exception {
    Event event = event();
    when(configuration.isSendAsync()).thenReturn(false);
    when(configuration.getPublishTimeoutMs()).thenReturn(1000L);
    when(userRecordResult.isSuccessful()).thenReturn(true);
    when(kinesisProducer.addUserRecord(
            eq(STREAM_EVENTS), eq(LOGICAL_PARTITION), eq(EXPLICIT_HASH_KEY), any(ByteBuffer.class)))
        .thenReturn(Futures.immediateFuture(userRecordResult));

    assertThat(
            publisher
                .publish(
                    STREAM_EVENTS,
                    event,
                    Optional.of(
                        new KinesisShardResolver.ShardRouting(
                            LOGICAL_PARTITION, "shardId-000000000000", EXPLICIT_HASH_KEY)))
                .get())
        .isTrue();

    verify(kinesisProducer)
        .addUserRecord(
            eq(STREAM_EVENTS), eq(LOGICAL_PARTITION), eq(EXPLICIT_HASH_KEY), any(ByteBuffer.class));
  }

  @Test
  public void shouldPublishNonPartitionAwareEventWithoutExplicitHashKey() {
    Event event = event();
    when(configuration.isSendAsync()).thenReturn(true);
    when(kinesisProducer.addUserRecord(
            eq(STREAM_EVENTS), eq(LOGICAL_PARTITION), any(ByteBuffer.class)))
        .thenReturn(Futures.immediateFuture(userRecordResult));

    publisher.publish(STREAM_EVENTS, event, Optional.empty());

    verify(kinesisProducer)
        .addUserRecord(eq(STREAM_EVENTS), eq(LOGICAL_PARTITION), any(ByteBuffer.class));
    verify(kinesisProducer, never())
        .addUserRecord(
            eq(STREAM_EVENTS), eq(LOGICAL_PARTITION), any(String.class), any(ByteBuffer.class));
  }

  @Test
  public void shouldFailWhenEventPartitionValueIsNotConfigured() {
    Event event = event();
    when(configuration.getStreamEventsTopic()).thenReturn(STREAM_EVENTS);
    when(shardResolver.resolveFromEvent(STREAM_EVENTS, event))
        .thenThrow(new IllegalArgumentException("partition is not configured"));

    assertThrows(IllegalArgumentException.class, () -> publisher.onEvent(event));

    verify(kinesisProducer, never())
        .addUserRecord(any(String.class), any(String.class), any(ByteBuffer.class));
    verify(kinesisProducer, never())
        .addUserRecord(
            any(String.class), any(String.class), any(String.class), any(ByteBuffer.class));
  }

  private Event event() {
    return new Event(LOGICAL_PARTITION) {};
  }
}
