/**
 * Copyright © 2016-2018 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.tools.service.jsexecutor.script;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public interface JsInvokeService {

    ListenableFuture<UUID> eval(AtomicInteger totalSuccessPublishedCount,
                                AtomicInteger totalFailedPublishedCount,
                                AtomicInteger iterationSuccessPublishedCount,
                                AtomicInteger iterationFailedPublishedCount,
                                CountDownLatch iterationLatch,
                                JsScriptType scriptType, String scriptBody, String... argNames);

    ListenableFuture<Object> invokeFunction(AtomicInteger totalSuccessPublishedCount,
                                            AtomicInteger totalFailedPublishedCount,
                                            AtomicInteger iterationSuccessPublishedCount,
                                            AtomicInteger iterationFailedPublishedCount,
                                            CountDownLatch iterationLatch,
                                            UUID scriptId, Object... args);

    ListenableFuture<Void> release(UUID scriptId);

}
