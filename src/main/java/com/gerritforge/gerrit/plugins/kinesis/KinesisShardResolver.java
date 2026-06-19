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
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient;
import software.amazon.awssdk.services.kinesis.model.ListShardsRequest;
import software.amazon.awssdk.services.kinesis.model.ListShardsResponse;
import software.amazon.awssdk.services.kinesis.model.Shard;

@Singleton
class KinesisShardResolver {
  record ShardRouting(String logicalPartition, String shardId, String explicitHashKey) {}

  private final LoadingCache<String, List<Shard>> openShardsCache =
      CacheBuilder.newBuilder()
          .expireAfterWrite(10, TimeUnit.MINUTES)
          .build(CacheLoader.from(this::listOpenShards));

  private final EventsBrokerConfiguration eventsBrokerConfiguration;
  private final KinesisAsyncClient kinesisClient;

  @Inject
  KinesisShardResolver(
      EventsBrokerConfiguration eventsBrokerConfiguration, KinesisAsyncClient kinesisClient) {
    this.eventsBrokerConfiguration = eventsBrokerConfiguration;
    this.kinesisClient = kinesisClient;
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

    List<Shard> shards = getOpenShards(streamName);
    if (partitionIndex >= shards.size()) {
      throw new IllegalArgumentException(
          String.format(
              "Topic %s has %d configured partitions but only %d Kinesis shards",
              streamName, partitions.size(), shards.size()));
    }

    Shard shard = shards.get(partitionIndex);
    String explicitHashKey = getExplicitHashKey(shard);
    return new ShardRouting(logicalPartition, shard.shardId(), explicitHashKey);
  }

  public static String getExplicitHashKey(Shard shard) {
    BigInteger start = new BigInteger(shard.hashKeyRange().startingHashKey());
    BigInteger end = new BigInteger(shard.hashKeyRange().endingHashKey());
    // Take a hash key in the middle of the partition to avoid problems with merges, split, etc.
    return start.add(end).divide(BigInteger.valueOf(2)).toString();
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

    return shards.stream()
        .sorted(
            Comparator.comparing(shard -> new BigInteger(shard.hashKeyRange().startingHashKey())))
        .toList();
  }

  private boolean isOpen(Shard shard) {
    return shard.sequenceNumberRange().endingSequenceNumber() == null;
  }
}
