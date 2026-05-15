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
import com.google.gerrit.server.events.Event;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import software.amazon.kinesis.processor.ShardRecordProcessor;
import software.amazon.kinesis.processor.ShardRecordProcessorFactory;

class KinesisRecordProcessorFactory implements ShardRecordProcessorFactory {
  interface Factory {
    KinesisRecordProcessorFactory create(AckAwareConsumer<Event> recordProcessor);
  }

  private final AckAwareConsumer<Event> recordProcessor;
  private final KinesisRecordProcessor.Factory processorFactory;

  @Inject
  KinesisRecordProcessorFactory(
      @Assisted AckAwareConsumer<Event> recordProcessor,
      KinesisRecordProcessor.Factory processorFactory) {
    this.recordProcessor = recordProcessor;
    this.processorFactory = processorFactory;
  }

  public ShardRecordProcessor shardRecordProcessor() {
    return processorFactory.create(recordProcessor);
  }
}
