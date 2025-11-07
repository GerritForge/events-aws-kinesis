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

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gerrit.server.logging.LoggingContextAwareExecutorService;
import com.google.inject.Provider;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class ProducerCallbackExecutorProvider implements Provider<ExecutorService> {

  @Override
  public ExecutorService get() {
    // Currently, we use 1 thread only when publishing, so it makes sense to
    // have the same when processing the related callbacks.
    return new LoggingContextAwareExecutorService(
        Executors.newFixedThreadPool(
            1,
            new ThreadFactoryBuilder()
                .setNameFormat("kinesis-producer-callback-executor-%d")
                .build()));
  }
}
