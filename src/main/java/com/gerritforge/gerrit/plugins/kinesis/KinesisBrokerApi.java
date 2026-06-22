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

import com.gerritforge.gerrit.eventbroker.AckAwareConsumer;
import com.gerritforge.gerrit.eventbroker.BrokerApi;
import com.gerritforge.gerrit.eventbroker.TopicSubscriber;
import com.gerritforge.gerrit.eventbroker.TopicSubscriberWithGroupId;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.events.Event;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

class KinesisBrokerApi implements BrokerApi {
  private final KinesisConsumer.Factory consumerFactory;
  private final Configuration configuration;
  private final KinesisShardResolver shardResolver;

  private final KinesisPublisher kinesisPublisher;
  private final Set<KinesisConsumer> consumers;

  @Inject
  public KinesisBrokerApi(
      KinesisPublisher kinesisPublisher,
      KinesisConsumer.Factory consumerFactory,
      Configuration configuration,
      KinesisShardResolver shardResolver) {
    this.kinesisPublisher = kinesisPublisher;
    this.consumerFactory = consumerFactory;
    this.configuration = configuration;
    this.shardResolver = shardResolver;
    this.consumers = Collections.newSetFromMap(new ConcurrentHashMap<>());
  }

  @Override
  public ListenableFuture<Boolean> send(String streamName, Event event) {
    return kinesisPublisher.publish(
        streamName, event, shardResolver.resolveFromEvent(streamName, event));
  }

  @Override
  public void receiveAsync(String streamName, AckAwareConsumer<Event> eventConsumer) {
    receive(streamName, eventConsumer, null);
  }

  @Override
  public void receiveAsync(String streamName, String groupId, AckAwareConsumer<Event> consumer) {
    receive(streamName, consumer, groupId);
  }

  @Override
  public void receiveAsyncWithPartition(
      String streamName, String partition, String groupId, AckAwareConsumer<Event> consumer) {
    KinesisShardResolver.ShardRouting routing =
        shardResolver.resolveFromLogicalPartition(streamName, partition);
    receive(streamName, consumer, groupId, Optional.of(routing));
  }

  @Override
  public Set<TopicSubscriber> topicSubscribers() {
    return consumers.stream()
        .map(s -> TopicSubscriber.topicSubscriber(s.getStreamName(), s.getMessageProcessor()))
        .collect(Collectors.toSet());
  }

  @Override
  public void disconnect() {
    consumers.parallelStream().forEach(KinesisConsumer::shutdown);
    consumers.clear();
  }

  @Override
  public void disconnect(String topic, String groupId) {
    Set<KinesisConsumer> consumersOfTopic =
        consumers.parallelStream()
            .filter(c -> topic.equals(c.getStreamName()) && groupId.equals(c.getGroupId()))
            .collect(Collectors.toSet());

    consumersOfTopic.forEach(KinesisConsumer::shutdown);
    consumers.removeAll(consumersOfTopic);
  }

  @Override
  public void replayAllEvents(String topic) {
    consumers.stream()
        .filter(subscriber -> topic.equals(subscriber.getStreamName()))
        .forEach(KinesisConsumer::resetOffset);
  }

  @Override
  public Set<TopicSubscriberWithGroupId> topicSubscribersWithGroupId() {
    return consumers.stream()
        .map(
            s ->
                TopicSubscriberWithGroupId.topicSubscriberWithGroupId(
                    s.getGroupId(),
                    TopicSubscriber.topicSubscriber(s.getStreamName(), s.getMessageProcessor()),
                    s.getLogicalPartition()))
        .collect(Collectors.toSet());
  }

  @Override
  public boolean isAutoAck() {
    return configuration.isAutoCommitEnabled();
  }

  private void receive(
      String streamName, AckAwareConsumer<Event> eventConsumer, @Nullable String maybeGroupId) {
    receive(streamName, eventConsumer, maybeGroupId, Optional.empty());
  }

  private void receive(
      String streamName,
      AckAwareConsumer<Event> eventConsumer,
      @Nullable String maybeGroupId,
      Optional<KinesisShardResolver.ShardRouting> routing) {
    String groupId = Optional.ofNullable(maybeGroupId).orElse(configuration.getApplicationName());
    KinesisConsumer consumer = consumerFactory.create(streamName, groupId, routing, eventConsumer);
    consumers.add(consumer);
    consumer.subscribe();
  }
}
