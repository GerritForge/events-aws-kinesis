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

import com.gerritforge.gerrit.eventbroker.BrokerApi;
import com.gerritforge.gerrit.eventbroker.TopicSubscriber;
import com.gerritforge.gerrit.eventbroker.TopicSubscriberWithGroupId;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Set;

@Singleton
class KinesisBrokerLifeCycleManager implements LifecycleListener {
  private final Set<TopicSubscriber> consumers;
  private final Set<TopicSubscriberWithGroupId> consumersWithGroupId;
  private final BrokerApi brokerApi;

  @Inject
  public KinesisBrokerLifeCycleManager(
      Set<TopicSubscriber> consumers,
      Set<TopicSubscriberWithGroupId> consumersWithGroupId,
      BrokerApi brokerApi) {
    this.consumers = consumers;
    this.consumersWithGroupId = consumersWithGroupId;
    this.brokerApi = brokerApi;
  }

  @Override
  public void start() {
    consumers.forEach(
        topicSubscriber ->
            brokerApi.receiveAsync(topicSubscriber.topic(), topicSubscriber.consumer()));
    consumersWithGroupId.forEach(
        consumer -> {
          TopicSubscriber topicSubscriber = consumer.topicSubscriber();
          if (consumer.partition().isPresent()) {
            brokerApi.receiveAsyncWithPartition(
                topicSubscriber.topic(),
                consumer.partition().get(),
                consumer.groupId(),
                topicSubscriber.consumer());
          } else {
            brokerApi.receiveAsync(
                topicSubscriber.topic(), consumer.groupId(), topicSubscriber.consumer());
          }
        });
  }

  @Override
  public void stop() {
    brokerApi.disconnect();
  }
}
