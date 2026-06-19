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
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import software.amazon.awssdk.services.kinesis.model.Shard;
import software.amazon.kinesis.leases.ShardDetector;

@RunWith(MockitoJUnitRunner.class)
public class CustomShardDetectorTest {
  @Mock ShardDetector delegate;

  @Test
  public void shouldExposeOnlyTheConfiguredShard() {
    Shard first = Shard.builder().shardId("shardId-000000000000").build();
    Shard second = Shard.builder().shardId("shardId-000000000001").build();
    when(delegate.listShards()).thenReturn(List.of(first, second));

    CustomShardDetector detector = new CustomShardDetector(delegate, second.shardId());

    assertThat(detector.listShards()).containsExactly(second);
  }

  @Test
  public void shouldRejectShardLookupsOutsideTheConfiguredShard() {
    Shard shard = Shard.builder().shardId("shardId-000000000001").build();
    CustomShardDetector detector = new CustomShardDetector(delegate, shard.shardId());

    assertThrows(IllegalArgumentException.class, () -> detector.shard("shardId-000000000000"));
  }
}
