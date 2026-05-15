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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gerritforge.gerrit.eventbroker.AckAwareConsumer;
import com.gerritforge.gerrit.eventbroker.EventDeserializer;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.EventGsonProvider;
import com.google.gerrit.server.events.ProjectCreatedEvent;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gson.Gson;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kinesis.model.Record;
import software.amazon.kinesis.lifecycle.events.InitializationInput;
import software.amazon.kinesis.lifecycle.events.ProcessRecordsInput;
import software.amazon.kinesis.processor.RecordProcessorCheckpointer;
import software.amazon.kinesis.retrieval.KinesisClientRecord;
import software.amazon.kinesis.retrieval.kpl.ExtendedSequenceNumber;

@RunWith(MockitoJUnitRunner.class)
public class KinesisRecordProcessorTest {
  private KinesisRecordProcessor objectUnderTest;
  private Gson gson = new EventGsonProvider().get();
  private EventDeserializer eventDeserializer = new EventDeserializer(gson);

  @Mock AckAwareConsumer<Event> succeedingConsumer;
  @Captor ArgumentCaptor<Event> eventMessageCaptor;
  @Mock OneOffRequestContext oneOffCtx;
  @Mock ManualRequestContext requestContext;
  @Mock Configuration configuration;
  @Mock RecordProcessorCheckpointer checkpointer;

  @Before
  public void setup() {
    when(oneOffCtx.open()).thenReturn(requestContext);
    when(configuration.isAutoCommitEnabled()).thenReturn(true);
    objectUnderTest =
        new KinesisRecordProcessor(succeedingConsumer, oneOffCtx, eventDeserializer, configuration);
  }

  @Test
  public void shouldNotCheckpointBeforeIntervalIsExpired() {
    when(configuration.getCheckpointIntervalMs()).thenReturn(10000L);
    Event event = new ProjectCreatedEvent();

    initializeRecordProcessor();

    ProcessRecordsInput kinesisInput = sampleMessage(gson.toJson(event));
    ProcessRecordsInput processRecordsInputSpy = Mockito.spy(kinesisInput);
    objectUnderTest.processRecords(processRecordsInputSpy);

    verify(processRecordsInputSpy, never()).checkpointer();
  }

  @Test
  public void shouldCheckpointAfterIntervalIsExpired() throws InterruptedException {
    when(configuration.getCheckpointIntervalMs()).thenReturn(0L);
    Event event = new ProjectCreatedEvent();

    initializeRecordProcessor();

    ProcessRecordsInput kinesisInput = sampleMessage(gson.toJson(event));
    ProcessRecordsInput processRecordsInputSpy = Mockito.spy(kinesisInput);
    objectUnderTest.processRecords(processRecordsInputSpy);

    verify(processRecordsInputSpy).checkpointer();
  }

  @Test
  public void shouldSkipEventWithoutSourceInstanceId() {
    Event event = new ProjectCreatedEvent();
    event.instanceId = UUID.randomUUID().toString();

    ProcessRecordsInput kinesisInput = sampleMessage(gson.toJson(event));

    objectUnderTest.processRecords(kinesisInput);

    verify(succeedingConsumer, never()).accept(any(), any());
  }

  @Test
  public void shouldParseEventObject() {
    String instanceId = "instance-id";

    Event event = new ProjectCreatedEvent();
    event.instanceId = instanceId;

    ProcessRecordsInput kinesisInput = sampleMessage(gson.toJson(event));
    objectUnderTest.processRecords(kinesisInput);

    verify(succeedingConsumer, only()).accept(eventMessageCaptor.capture(), any());

    Event result = eventMessageCaptor.getValue();
    assertThat(result.instanceId).isEqualTo(instanceId);
  }

  @Test
  public void shouldProcessEventObjectWithoutInstanceId() {
    Event event = new ProjectCreatedEvent();
    event.instanceId = null;

    ProcessRecordsInput kinesisInput = sampleMessage(gson.toJson(event));
    objectUnderTest.processRecords(kinesisInput);

    verify(succeedingConsumer, times(1)).accept(any(), any());
  }

  @Test
  public void shouldSkipEventObjectWithUnknownType() {
    String instanceId = "instance-id";
    Event event = new Event("unknown-type") {};
    event.instanceId = instanceId;

    ProcessRecordsInput kinesisInput = sampleMessage(gson.toJson(event));
    objectUnderTest.processRecords(kinesisInput);

    verify(succeedingConsumer, never()).accept(any(), any());
  }

  @Test
  public void shouldSkipEventObjectWithoutType() {
    String instanceId = "instance-id";
    Event event = new Event(null) {};
    event.instanceId = instanceId;

    ProcessRecordsInput kinesisInput = sampleMessage(gson.toJson(event));
    objectUnderTest.processRecords(kinesisInput);

    verify(succeedingConsumer, never()).accept(any(), any());
  }

  @Test
  public void shouldSkipEmptyObjectJsonPayload() {
    String emptyJsonObject = "{}";

    ProcessRecordsInput kinesisInput = sampleMessage(emptyJsonObject);
    objectUnderTest.processRecords(kinesisInput);

    verify(succeedingConsumer, never()).accept(any(), any());
  }

  @Test
  public void shouldParseEventObjectWithHeaderAndBodyProjectName() {
    ProjectCreatedEvent event = new ProjectCreatedEvent();
    event.instanceId = "instance-id";
    event.projectName = "header_body_parser_project";
    ProcessRecordsInput kinesisInput = sampleMessage(gson.toJson(event));
    objectUnderTest.processRecords(kinesisInput);

    verify(succeedingConsumer, only()).accept(any(Event.class), any());
  }

  @Test
  public void shouldNotAutoCommitWhenManualAcknowledgementIsEnabled() throws Exception {
    when(configuration.isAutoCommitEnabled()).thenReturn(false);
    when(configuration.getCheckpointIntervalMs()).thenReturn(0L);
    Event event = new ProjectCreatedEvent();

    initializeRecordProcessor();

    objectUnderTest.processRecords(sampleMessage(gson.toJson(event)));

    verify(checkpointer, never()).checkpoint();
  }

  @Test
  public void shouldCommitCurrentRecordWhenManualAcknowledgementIsCalled() throws Exception {
    String sequenceNumber = "12345";
    when(configuration.isAutoCommitEnabled()).thenReturn(false);
    objectUnderTest =
        new KinesisRecordProcessor(
            (event, acknowledgement) -> acknowledgement.ack(event),
            oneOffCtx,
            eventDeserializer,
            configuration);

    KinesisClientRecord clientRecord =
        sampleClientRecord(sequenceNumber, gson.toJson(new ProjectCreatedEvent()));
    ProcessRecordsInput input = sampleMessage(clientRecord);
    objectUnderTest.processRecords(input);

    verify(checkpointer)
        .checkpoint(clientRecord.sequenceNumber(), clientRecord.subSequenceNumber());
  }

  @Test
  public void shouldRejectExplicitAckWhenAutomaticCommitIsEnabled() {
    AtomicReference<IllegalStateException> thrown = new AtomicReference<>();
    objectUnderTest =
        new KinesisRecordProcessor(
            (event, acknowledgement) -> {
              try {
                acknowledgement.ack(event);
              } catch (IllegalStateException e) {
                thrown.set(e);
              }
            },
            oneOffCtx,
            eventDeserializer,
            configuration);

    objectUnderTest.processRecords(sampleMessage(gson.toJson(new ProjectCreatedEvent())));

    assertThat(thrown.get()).isNotNull();
    assertThat(thrown.get()).hasMessageThat().contains("already acknowledged automatically");
  }

  private ProcessRecordsInput sampleMessage(String message) {
    return sampleMessage(sampleClientRecord("0000", message));
  }

  private ProcessRecordsInput sampleMessage(KinesisClientRecord clientRecord) {
    return ProcessRecordsInput.builder()
        .checkpointer(checkpointer)
        .records(Collections.singletonList(clientRecord))
        .build();
  }

  private KinesisClientRecord sampleClientRecord(String sequenceNumber, String message) {
    Record kinesisRecord =
        Record.builder()
            .data(SdkBytes.fromUtf8String(message))
            .sequenceNumber(sequenceNumber)
            .build();
    return KinesisClientRecord.fromRecord(kinesisRecord);
  }

  private void initializeRecordProcessor() {
    InitializationInput initializationInput =
        InitializationInput.builder()
            .shardId("shard-0000")
            .extendedSequenceNumber(new ExtendedSequenceNumber("0000"))
            .build();
    objectUnderTest.initialize(initializationInput);
  }
}
