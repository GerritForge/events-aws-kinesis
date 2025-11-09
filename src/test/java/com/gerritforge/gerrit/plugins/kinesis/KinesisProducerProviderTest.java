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

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.services.kinesis.producer.KinesisProducer;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.AwsRegionProviderChain;

@RunWith(MockitoJUnitRunner.class)
public class KinesisProducerProviderTest {
  private KinesisProducerProvider objectUnderTest;

  @Mock Configuration configuration;
  @Mock AwsRegionProviderChain regionProvider;

  @Before
  public void setup() {
    long aRequestTimeout = 1000L;
    when(configuration.getPublishSingleRequestTimeoutMs()).thenReturn(aRequestTimeout);
    objectUnderTest = new KinesisProducerProvider(configuration, regionProvider);
  }

  @Test
  public void shouldCallRegionProviderWhenRegionNotExplicitlyConfigured() {
    when(configuration.getRegion()).thenReturn(Optional.empty());
    when(regionProvider.getRegion()).thenReturn(Region.US_EAST_1);

    KinesisProducer kinesisProducer = objectUnderTest.get();

    verify(regionProvider).getRegion();
  }

  @Test
  public void shouldNotCallRegionProviderWhenRegionIsExplicitlyConfigured() {
    when(configuration.getRegion()).thenReturn(Optional.of(Region.US_EAST_1));

    KinesisProducer kinesisProducer = objectUnderTest.get();

    verify(regionProvider, never()).getRegion();
  }
}
