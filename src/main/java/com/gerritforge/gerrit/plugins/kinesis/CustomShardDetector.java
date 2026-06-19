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

import java.util.List;
import software.amazon.awssdk.services.kinesis.model.Shard;
import software.amazon.awssdk.services.kinesis.model.ShardFilter;
import software.amazon.kinesis.common.StreamIdentifier;
import software.amazon.kinesis.leases.ShardDetector;

/**
 * Shard detector wrapper that exposes a single configured shard to KCL.
 *
 * <p>The delegate still performs shard discovery, while this wrapper filters discovery results so a
 * scheduler owns only the shard mapped from a logical partition.
 */
class CustomShardDetector implements ShardDetector {
  private final ShardDetector delegate;
  private final String shardId;

  CustomShardDetector(ShardDetector delegate, String shardId) {
    this.delegate = delegate;
    this.shardId = shardId;
  }

  @Override
  public Shard shard(String shardId) {
    if (!this.shardId.equals(shardId)) {
      throw new IllegalArgumentException(
          String.format(
              "Shard %s cannot be handled by a detector configured for shard %s",
              shardId, this.shardId));
    }
    return delegate.shard(shardId);
  }

  @Override
  public List<Shard> listShards() {
    return delegate.listShards().stream().filter(shard -> shardId.equals(shard.shardId())).toList();
  }

  @Override
  public List<Shard> listShardsWithFilter(ShardFilter shardFilter) {
    return delegate.listShardsWithFilter(shardFilter).stream()
        .filter(shard -> shardId.equals(shard.shardId()))
        .toList();
  }

  @Override
  public StreamIdentifier streamIdentifier() {
    return delegate.streamIdentifier();
  }
}
