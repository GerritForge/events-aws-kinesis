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

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gerritforge.gerrit.eventbroker.AckAwareConsumer;
import com.google.gerrit.server.events.Event;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class KinesisBrokerApiTest {
  private static final String GROUP_ID = "group-id";
  private static final String PARTITION = "change-index";
  private static final String STREAM_EVENTS = "stream-events";

  @Mock KinesisPublisher publisher;
  @Mock KinesisConsumer.Factory consumerFactory;
  @Mock Configuration configuration;
  @Mock KinesisShardResolver shardResolver;

  private KinesisBrokerApi brokerApi;

  @Before
  public void setUp() {
    brokerApi = new KinesisBrokerApi(publisher, consumerFactory, configuration, shardResolver);
  }

  @Test
  public void shouldPublishUsingTheConfiguredLogicalPartition() {
    Event event = changeIndexEvent();
    KinesisShardResolver.ShardRouting routing =
        new KinesisShardResolver.ShardRouting(PARTITION, "shardId-000000000000", "100");
    when(shardResolver.resolveFromEvent(STREAM_EVENTS, event)).thenReturn(Optional.of(routing));

    brokerApi.send(STREAM_EVENTS, event);

    verify(publisher).publish(STREAM_EVENTS, event, Optional.of(routing));
  }

  @Test
  public void shouldPublishWithoutLogicalPartitionWhenTopicIsNotPartitionAware() {
    Event event = changeIndexEvent();
    when(shardResolver.resolveFromEvent(STREAM_EVENTS, event)).thenReturn(Optional.empty());

    brokerApi.send(STREAM_EVENTS, event);

    verify(publisher).publish(STREAM_EVENTS, event, Optional.empty());
  }

  @Test
  public void shouldRejectAnUnknownLogicalPartition() {
    AckAwareConsumer<Event> consumer = mock(AckAwareConsumer.class);
    when(shardResolver.resolveFromLogicalPartition(STREAM_EVENTS, "unknown"))
        .thenThrow(IllegalArgumentException.class);

    assertThrows(
        IllegalArgumentException.class,
        () -> brokerApi.receiveAsyncWithPartition(STREAM_EVENTS, "unknown", GROUP_ID, consumer));
  }

  private Event changeIndexEvent() {
    return new Event(PARTITION) {};
  }
}
