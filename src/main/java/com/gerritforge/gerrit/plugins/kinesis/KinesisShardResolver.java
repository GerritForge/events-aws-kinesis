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

import com.gerritforge.gerrit.eventbroker.EventsBrokerConfiguration;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.EventGson;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient;
import software.amazon.awssdk.services.kinesis.model.ListShardsRequest;
import software.amazon.awssdk.services.kinesis.model.ListShardsResponse;
import software.amazon.awssdk.services.kinesis.model.Shard;

@Singleton
class KinesisShardResolver {
  /*
   * Kinesis producers cannot publish directly to a shardId because the publishing API only accepts
   * a partition key and, optionally, an explicitHashKey. Keep both values: explicitHashKey routes
   * published records into the selected shard, while shardId is still needed for consumer and offset
   * operations.
   */
  record ShardRouting(String logicalPartition, String shardId, String explicitHashKey) {}

  private final LoadingCache<String, List<Shard>> openShardsCache =
      CacheBuilder.newBuilder()
          .expireAfterWrite(10, TimeUnit.MINUTES)
          .build(CacheLoader.from(this::listOpenShards));

  private final EventsBrokerConfiguration eventsBrokerConfiguration;
  private final KinesisAsyncClient kinesisClient;
  private final Gson gson;

  @Inject
  KinesisShardResolver(
      EventsBrokerConfiguration eventsBrokerConfiguration,
      KinesisAsyncClient kinesisClient,
      @EventGson Gson gson) {
    this.eventsBrokerConfiguration = eventsBrokerConfiguration;
    this.kinesisClient = kinesisClient;
    this.gson = gson;
  }

  ShardRouting resolveFromLogicalPartition(String streamName, String logicalPartition) {
    List<String> partitions = eventsBrokerConfiguration.getPartitionsForTopic(streamName);
    int partitionIndex = partitions.indexOf(logicalPartition);
    if (partitionIndex < 0) {
      throw new IllegalArgumentException(
          String.format(
              "Logical partition value %s is not configured for topic %s",
              logicalPartition, streamName));
    }

    List<Shard> openShards = getOpenShards(streamName);
    if (partitionIndex >= openShards.size()) {
      throw new IllegalArgumentException(
          String.format(
              "Logical partition %s has index %d for topic %s, but stream has only %d open shards",
              logicalPartition, partitionIndex, streamName, openShards.size()));
    }

    Shard shard = openShards.get(partitionIndex);
    return new ShardRouting(logicalPartition, shard.shardId(), getExplicitHashKey(shard));
  }

  private List<Shard> getOpenShards(String streamName) {
    try {
      return openShardsCache.get(streamName);
    } catch (ExecutionException e) {
      throw new RuntimeException(
          String.format("Cannot retrieve shards for stream %s", streamName), e);
    }
  }

  private List<Shard> listOpenShards(String streamName) {
    List<Shard> shards = new ArrayList<>();
    String nextToken = null;
    do {
      ListShardsRequest request =
          nextToken == null
              ? ListShardsRequest.builder().streamName(streamName).build()
              : ListShardsRequest.builder().nextToken(nextToken).build();
      ListShardsResponse response = kinesisClient.listShards(request).join();
      shards.addAll(response.shards().stream().filter(this::isOpen).toList());
      nextToken = response.nextToken();
    } while (nextToken != null);

    return shards.stream().sorted(Comparator.comparing(Shard::shardId)).toList();
  }

  private boolean isOpen(Shard shard) {
    return shard.sequenceNumberRange().endingSequenceNumber() == null;
  }

  Optional<ShardRouting> resolveFromEvent(String streamName, Event event) {
    List<String> partitions = eventsBrokerConfiguration.getPartitionsForTopic(streamName);
    if (partitions.isEmpty()) {
      return Optional.empty();
    }

    String eventProperty =
        eventsBrokerConfiguration
            .getEventPropertyForTopic(streamName)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        String.format(
                            "Topic %s has configured partitions but no partition event property",
                            streamName)));
    JsonObject eventJson = gson.toJsonTree(event).getAsJsonObject();
    JsonElement partitionValue = eventJson.get(eventProperty);
    if (partitionValue == null) {
      throw new IllegalArgumentException(
          String.format("Event does not contain partition property %s", eventProperty));
    }
    if (partitionValue.isJsonNull() || !partitionValue.isJsonPrimitive()) {
      throw new IllegalArgumentException(
          String.format(
              "Event partition property %s for topic %s must be a non-null primitive",
              eventProperty, streamName));
    }

    String partition = partitionValue.getAsString();
    return Optional.of(resolveFromLogicalPartition(streamName, partition));
  }

  String getExplicitHashKey(Shard shard) {
    // Use BigInteger because Kinesis hash keys span the full MD5 unsigned integer range.
    BigInteger start = new BigInteger(shard.hashKeyRange().startingHashKey());
    BigInteger end = new BigInteger(shard.hashKeyRange().endingHashKey());
    // Use the midpoint so the explicit hash key deterministically falls inside the target shard.
    return start.add(end).divide(BigInteger.valueOf(2)).toString();
  }
}
