// Copyright (C) 2025 GerritForge, Inc.
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

import com.amazonaws.services.kinesis.producer.KinesisProducer;
import com.amazonaws.services.kinesis.producer.UserRecordResult;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.EventGson;
import com.google.gerrit.server.events.EventListener;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Singleton
class KinesisPublisher implements EventListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final KinesisProducer kinesisProducer;
  private final Configuration configuration;
  private final ExecutorService callBackExecutor;
  private final KinesisShardResolver shardResolver;

  private final Gson gson;

  @Inject
  public KinesisPublisher(
      @EventGson Gson gson,
      KinesisProducer kinesisProducer,
      Configuration configuration,
      @ProducerCallbackExecutor ExecutorService callBackExecutor,
      KinesisShardResolver shardResolver) {
    this.gson = gson;
    this.kinesisProducer = kinesisProducer;
    this.configuration = configuration;
    this.callBackExecutor = callBackExecutor;
    this.shardResolver = shardResolver;
  }

  @Override
  public void onEvent(Event event) {
    String streamName = configuration.getStreamEventsTopic();
    publish(streamName, event, shardResolver.resolveFromEvent(streamName, event));
  }

  ListenableFuture<Boolean> publish(
      String streamName, Event event, Optional<KinesisShardResolver.ShardRouting> shardRouting) {
    String partitionKey =
        shardRouting
            .map(KinesisShardResolver.ShardRouting::logicalPartition)
            .orElse(event.getType());
    Optional<String> explicitHashKey =
        shardRouting.map(KinesisShardResolver.ShardRouting::explicitHashKey);
    if (configuration.isSendAsync()) {
      return publishAsync(streamName, gson.toJson(event), partitionKey, explicitHashKey);
    }
    return publishSync(streamName, gson.toJson(event), partitionKey, explicitHashKey);
  }

  private ListenableFuture<Boolean> publishSync(
      String streamName,
      String stringEvent,
      String partitionKey,
      Optional<String> explicitHashKey) {
    SettableFuture<Boolean> resultFuture = SettableFuture.create();
    try {
      resultFuture.set(
          publishAsync(streamName, stringEvent, partitionKey, explicitHashKey)
              .get(configuration.getPublishTimeoutMs(), TimeUnit.MILLISECONDS));
    } catch (CancellationException
        | ExecutionException
        | InterruptedException
        | TimeoutException futureException) {
      logger.atSevere().withCause(futureException).log(
          "KINESIS PRODUCER - Failed publishing event %s [PK: %s]", stringEvent, partitionKey);
      resultFuture.set(false);
    }

    return resultFuture;
  }

  private ListenableFuture<Boolean> publishAsync(
      String streamName,
      String stringEvent,
      String partitionKey,
      Optional<String> explicitHashKey) {
    try {
      ListenableFuture<UserRecordResult> publishF =
          explicitHashKey.isPresent()
              ? kinesisProducer.addUserRecord(
                  streamName,
                  partitionKey,
                  explicitHashKey.get(),
                  ByteBuffer.wrap(stringEvent.getBytes()))
              : kinesisProducer.addUserRecord(
                  streamName, partitionKey, ByteBuffer.wrap(stringEvent.getBytes()));

      Futures.addCallback(
          publishF,
          new FutureCallback<UserRecordResult>() {
            @Override
            public void onSuccess(UserRecordResult result) {
              logger.atFine().log(
                  "KINESIS PRODUCER - Successfully published event '%s' to shardId '%s' [PK: %s]"
                      + " [Sequence: %s] after %s attempt(s)",
                  stringEvent,
                  result.getShardId(),
                  partitionKey,
                  result.getSequenceNumber(),
                  result.getAttempts().size());
            }

            @Override
            public void onFailure(Throwable e) {
              logger.atSevere().withCause(e).log(
                  "KINESIS PRODUCER - Failed publishing event %s [PK: %s]",
                  stringEvent, partitionKey);
            }
          },
          callBackExecutor);

      return Futures.transform(
          publishF, res -> res != null && res.isSuccessful(), callBackExecutor);
    } catch (Exception e) {
      logger.atSevere().withCause(e).log(
          "KINESIS PRODUCER - Error when publishing event %s [PK: %s]", stringEvent, partitionKey);
      return Futures.immediateFailedFuture(e);
    }
  }
}
