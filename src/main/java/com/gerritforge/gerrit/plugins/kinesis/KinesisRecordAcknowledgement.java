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

import com.gerritforge.gerrit.eventbroker.MessageAcknowledgement;
import com.gerritforge.gerrit.eventbroker.MessageAcknowledgementException;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.events.Event;
import software.amazon.kinesis.processor.RecordProcessorCheckpointer;
import software.amazon.kinesis.retrieval.KinesisClientRecord;

class KinesisRecordAcknowledgement implements MessageAcknowledgement<Event> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final KinesisClientRecord consumerRecord;
  private final RecordProcessorCheckpointer checkpointer;

  KinesisRecordAcknowledgement(
      KinesisClientRecord consumerRecord, RecordProcessorCheckpointer checkpointer) {
    this.consumerRecord = consumerRecord;
    this.checkpointer = checkpointer;
  }

  @Override
  public void ack(Event event) {
    try {
      checkpointer.checkpoint(consumerRecord.sequenceNumber(), consumerRecord.subSequenceNumber());
    } catch (Exception e) {
      throw new MessageAcknowledgementException(
          String.format(
              "Failed to acknowledge record %s:%d",
              consumerRecord.sequenceNumber(), consumerRecord.subSequenceNumber()),
          e);
    }
    logger.atFine().log(
        "Checkpointed record %s:%d",
        consumerRecord.sequenceNumber(), consumerRecord.subSequenceNumber());
  }
}
