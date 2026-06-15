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
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.EventGson;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient;
import software.amazon.awssdk.services.kinesis.model.ListShardsRequest;
import software.amazon.awssdk.services.kinesis.model.ListShardsResponse;
import software.amazon.awssdk.services.kinesis.model.Shard;

@Singleton
class KinesisShardResolver {
  record ShardRouting(String logicalPartition, String shardId, String explicitHashKey) {}

  private final EventsBrokerConfiguration eventsBrokerConfiguration;
  private final Gson gson;
  private final KinesisAsyncClient kinesisClient;

  @Inject
  KinesisShardResolver(
      EventsBrokerConfiguration eventsBrokerConfiguration,
      @EventGson Gson gson,
      KinesisAsyncClient kinesisClient) {
    this.eventsBrokerConfiguration = eventsBrokerConfiguration;
    this.gson = gson;
    this.kinesisClient = kinesisClient;
  }

  Optional<ShardRouting> resolveFromEvent(String streamName, Event event) {
    List<String> partitions = eventsBrokerConfiguration.getPartitionsForTopic(streamName);
    if (partitions.isEmpty()) {
      return Optional.empty();
    }

    Optional<String> eventProperty = eventsBrokerConfiguration.getEventPropertyForTopic(streamName);
    JsonObject eventJson = gson.toJsonTree(event).getAsJsonObject();
    if (!eventJson.has(eventProperty.get())) {
      throw new IllegalArgumentException(
          String.format("Event does not contain partition property %s", eventProperty));
    }

    String partition = eventJson.get(eventProperty.get()).getAsString();
    return Optional.of(resolveFromLogicalPartition(streamName, partition));
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

    List<Shard> shards = listShards(streamName);
    if (partitionIndex >= shards.size()) {
      throw new IllegalArgumentException(
          String.format(
              "Topic %s has %d configured partitions but only %d Kinesis shards",
              streamName, partitions.size(), shards.size()));
    }

    Shard shard = shards.get(partitionIndex);
    return new ShardRouting(
        logicalPartition, shard.shardId(), shard.hashKeyRange().startingHashKey());
  }

  private List<Shard> listShards(String streamName) {
    List<Shard> shards = new ArrayList<>();
    String nextToken = null;
    do {
      ListShardsRequest request =
          nextToken == null
              ? ListShardsRequest.builder().streamName(streamName).build()
              : ListShardsRequest.builder().nextToken(nextToken).build();
      ListShardsResponse response = kinesisClient.listShards(request).join();
      shards.addAll(response.shards());
      nextToken = response.nextToken();
    } while (nextToken != null);

    shards.sort(Comparator.comparing(Shard::shardId));
    return shards;
  }
}
