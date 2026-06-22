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

import static com.gerritforge.gerrit.plugins.kinesis.KinesisShardResolver.getExplicitHashKey;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.gerritforge.gerrit.eventbroker.EventsBrokerConfiguration;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.EventGsonProvider;
import com.google.gerrit.server.events.EventTypes;
import com.google.gerrit.server.events.ProjectCreatedEvent;
import com.google.gson.Gson;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient;
import software.amazon.awssdk.services.kinesis.model.HashKeyRange;
import software.amazon.awssdk.services.kinesis.model.ListShardsRequest;
import software.amazon.awssdk.services.kinesis.model.ListShardsResponse;
import software.amazon.awssdk.services.kinesis.model.SequenceNumberRange;
import software.amazon.awssdk.services.kinesis.model.Shard;

@RunWith(MockitoJUnitRunner.class)
public class KinesisShardResolverTest {
  private static final String STREAM_EVENTS = "stream-events";
  private static final String CHANGE_INDEX_PARTITION = "change-index";
  private static final String ACCOUNT_INDEX_PARTITION = "account-index";
  public static final String CHANGE_SHARD_ID = "shardId-000000000001";
  public static final String ACCOUNT_SHARD_ID = "shardId-000000000000";
  public static final String CHANGE_START_HASH_KEY = "100";
  public static final String ACCOUNT_START_HASH_KEY = "0";
  public static final String CHANGE_END_HASH_KEY = "199";
  public static final String ACCOUNT_END_HASH_KEY = "99";

  @Mock EventsBrokerConfiguration configuration;
  @Mock KinesisAsyncClient kinesisClient;

  private final Gson gson = new EventGsonProvider().get();
  private KinesisShardResolver resolver;
  private Shard changeIndexShard;
  private Shard accountIndexShard;

  @Before
  public void setUp() {
    EventTypes.register(ChangeIndexEvent.TYPE, ChangeIndexEvent.class);
    resolver = new KinesisShardResolver(configuration, kinesisClient, gson);
    changeIndexShard = shard(CHANGE_SHARD_ID, CHANGE_START_HASH_KEY, CHANGE_END_HASH_KEY);
    accountIndexShard = shard(ACCOUNT_SHARD_ID, ACCOUNT_START_HASH_KEY, ACCOUNT_END_HASH_KEY);
    when(configuration.getPartitionsForTopic(STREAM_EVENTS))
        .thenReturn(List.of("change-index", "account-index"));
    when(kinesisClient.listShards(any(ListShardsRequest.class)))
        .thenReturn(
            CompletableFuture.completedFuture(
                ListShardsResponse.builder().shards(changeIndexShard, accountIndexShard).build()));
  }

  @Test
  public void shouldMapPartitionsToShardsByConfiguredOrder() {
    KinesisShardResolver.ShardRouting changeRouting =
        resolver.resolveFromLogicalPartition(STREAM_EVENTS, CHANGE_INDEX_PARTITION);
    KinesisShardResolver.ShardRouting accountRouting =
        resolver.resolveFromLogicalPartition(STREAM_EVENTS, ACCOUNT_INDEX_PARTITION);

    assertThat(changeRouting.shardId()).isEqualTo(ACCOUNT_SHARD_ID);
    assertThat(changeRouting.explicitHashKey()).isEqualTo(getExplicitHashKey(accountIndexShard));
    assertThat(accountRouting.shardId()).isEqualTo(CHANGE_SHARD_ID);
    assertThat(accountRouting.explicitHashKey()).isEqualTo(getExplicitHashKey(changeIndexShard));
  }

  @Test
  public void shouldFailWhenThereAreFewerShardsThanConfiguredPartitions() {
    when(kinesisClient.listShards(any(ListShardsRequest.class)))
        .thenReturn(
            CompletableFuture.completedFuture(
                ListShardsResponse.builder().shards(accountIndexShard).build()));

    assertThrows(
        IllegalArgumentException.class,
        () -> resolver.resolveFromLogicalPartition(STREAM_EVENTS, "account-index"));
  }

  @Test
  public void shouldNotResolveRoutingForTopicWithoutPartitions() {
    Event event = new ProjectCreatedEvent();
    when(configuration.getPartitionsForTopic(STREAM_EVENTS)).thenReturn(List.of());

    assertThat(resolver.resolveFromEvent(STREAM_EVENTS, event)).isEmpty();
  }

  @Test
  public void shouldFailWhenPartitionAwareTopicDoesNotConfigureEventProperty() {
    Event event = changeIndexEvent();
    when(configuration.getPartitionsForTopic(STREAM_EVENTS))
        .thenReturn(List.of(CHANGE_INDEX_PARTITION));
    when(configuration.getEventPropertyForTopic(STREAM_EVENTS)).thenReturn(Optional.empty());

    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class, () -> resolver.resolveFromEvent(STREAM_EVENTS, event));

    assertThat(thrown)
        .hasMessageThat()
        .contains("Topic stream-events has configured partitions but no partition event property");
  }

  @Test
  public void shouldFailWhenEventDoesNotContainPartitionProperty() {
    Event event = new ProjectCreatedEvent();
    when(configuration.getPartitionsForTopic(STREAM_EVENTS)).thenReturn(List.of("project-created"));
    when(configuration.getEventPropertyForTopic(STREAM_EVENTS)).thenReturn(Optional.of("missing"));

    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class, () -> resolver.resolveFromEvent(STREAM_EVENTS, event));

    assertThat(thrown)
        .hasMessageThat()
        .contains("Event does not contain partition property missing");
  }

  @Test
  public void shouldResolveRoutingFromConfiguredEventProperty() {
    Event event = changeIndexEvent();
    when(configuration.getPartitionsForTopic(STREAM_EVENTS))
        .thenReturn(List.of(CHANGE_INDEX_PARTITION, ACCOUNT_INDEX_PARTITION));
    when(configuration.getEventPropertyForTopic(STREAM_EVENTS)).thenReturn(Optional.of("type"));

    Optional<KinesisShardResolver.ShardRouting> routing =
        resolver.resolveFromEvent(STREAM_EVENTS, event);

    assertThat(routing).isPresent();
    assertThat(routing.get().logicalPartition()).isEqualTo(CHANGE_INDEX_PARTITION);
    assertThat(routing.get().shardId()).isEqualTo(ACCOUNT_SHARD_ID);
    assertThat(routing.get().explicitHashKey()).isEqualTo(getExplicitHashKey(accountIndexShard));
  }

  private Shard shard(String shardId, String startingHashKey, String endingHashKey) {
    return Shard.builder()
        .shardId(shardId)
        .hashKeyRange(
            HashKeyRange.builder()
                .startingHashKey(startingHashKey)
                .endingHashKey(endingHashKey)
                .build())
        .sequenceNumberRange(SequenceNumberRange.builder().startingSequenceNumber("1").build())
        .build();
  }

  private Event changeIndexEvent() {
    return new ChangeIndexEvent();
  }

  static class ChangeIndexEvent extends Event {
    static final String TYPE = CHANGE_INDEX_PARTITION;

    ChangeIndexEvent() {
      super(TYPE);
    }
  }
}
