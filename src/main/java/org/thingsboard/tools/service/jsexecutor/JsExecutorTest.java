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
package org.thingsboard.tools.service.jsexecutor;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.thingsboard.tools.service.jsexecutor.script.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "test", value = "api", havingValue = "js-executor")
public class JsExecutorTest extends Thread{
    @Value("${test.mps:1000}")
    protected int testMessagesPerSecond;
    @Value("${test.duration:60}")
    protected int testDurationInSec;
    @Value("${test.statSampleInterval:5}")
    protected int statSampleInterval;
    @Value("${test.testeval:false}")
    protected boolean testEval;
    @Value("${test.testevalCount:10000}")
    protected int testEvalCount;
    @Value("${test.scheduler_thread_pool_size:10}")
    protected int schedulerThreadPoolSize;
    @Value("${test.worker_thread_pool_size:10}")
    protected int workerThreadPoolSize;
    @Value("${js.script_file:UpLinkScriptV2831.js}")
    protected String jsScriptFile;
    @Value("${js.script_type:CONVERTER_UP_LINK_SCRIPT}")
    protected String jsScriptType;
    @Value("${js.script_param_file:}")
    protected String jsScriptParamFile;

    private volatile CountDownLatch testDurationLatch;
    private UUID scriptId = null;
    private String jsCode = null;
    private String[] jsParam = null;
    private long timeSpent[] = null;

    private ScheduledExecutorService scheduler = null;
    private ExecutorService workers = null;

    @Autowired
    JsInvokeService jsInvokeService;

    @SneakyThrows
    public void run() {
        runApiTests();
    }

    public void runApiTests() throws InterruptedException{
        scheduler = Executors.newScheduledThreadPool(schedulerThreadPoolSize);
        workers = Executors.newFixedThreadPool(workerThreadPoolSize);

        log.info("Starting performance test for js-executor in 10 seconds...");
        Thread.sleep(10000);

        try {
            InputStream stream =  this.getClass().getClassLoader().getResourceAsStream(jsScriptFile);
            jsCode = IOUtils.toString(stream,String.valueOf(StandardCharsets.UTF_8));
            stream = this.getClass().getClassLoader().getResourceAsStream(jsScriptParamFile);
            List<String> param = IOUtils.readLines(stream,String.valueOf(StandardCharsets.UTF_8));
            jsParam = new String[param.size()];
            param.toArray(jsParam);
        }catch (IOException e){
            log.info("Failed to read js script: {}", e.getMessage());
        }
        timeSpent = new long[testDurationInSec];
        AtomicInteger totalSuccessCount = new AtomicInteger();
        AtomicInteger totalFailedCount = new AtomicInteger();
        AtomicInteger successPublishedCount = new AtomicInteger();
        AtomicInteger failedPublishedCount = new AtomicInteger();
        CountDownLatch iterationLatch = new CountDownLatch(testEvalCount);
        if(testEval) {
            log.info("start calling eval and release {} times...", testEvalCount);
            long time1 = System.currentTimeMillis();
            for (int i = 0; i < testEvalCount; i++) {
                workers.submit(() -> {
                    try {
                        scriptId = jsInvokeService.eval(totalSuccessCount,
                                totalFailedCount,
                                successPublishedCount,
                                failedPublishedCount,
                                iterationLatch,
                                JsScriptType.valueOf(jsScriptType), jsCode).get();
                        jsInvokeService.release(scriptId);
                    } catch (Throwable th) {
                        th.printStackTrace();

                    } finally {

                    }
                });
            }
            iterationLatch.await();
            long time2 = System.currentTimeMillis();
            log.info("finish calling eval and release {} times, spent: {} ms, mean speed:{} calls/s", testEvalCount, (time2 - time1), testEvalCount * 1000 / (time2 - time1));
        }
        try {
            scriptId = jsInvokeService.eval(totalSuccessCount,
                    totalFailedCount,
                    successPublishedCount,
                    failedPublishedCount,
                    iterationLatch,
                    JsScriptType.valueOf(jsScriptType), jsCode).get();
        } catch (Throwable th) {
            th.printStackTrace();

        } finally {

        }
        totalSuccessCount.getAndSet(0);
        totalFailedCount.getAndSet(0);
        successPublishedCount.getAndSet(0);
        failedPublishedCount.getAndSet(0);

        int invokeCount = testMessagesPerSecond * 10;
        log.info("start calling invokeFunction {} times...", invokeCount);
        CountDownLatch iterationLatch1 = new CountDownLatch(invokeCount);
        long time1 = System.currentTimeMillis();
        for (int i = 0; i < invokeCount; i++) {
                jsInvokeService.invokeFunction(totalSuccessCount,
                        totalFailedCount,
                        successPublishedCount,
                        failedPublishedCount,
                        iterationLatch1,
                        scriptId, jsParam);
        }
        iterationLatch1.await();
        long time2 = System.currentTimeMillis();
        log.info("finish calling invokeFunction {} times, spent: {} ms, mean speed:{} calls/s", invokeCount, (time2 - time1), invokeCount * 1000 / (time2 - time1));

        totalSuccessCount.getAndSet(0);
        totalFailedCount.getAndSet(0);
        successPublishedCount.getAndSet(0);
        failedPublishedCount.getAndSet(0);

        testDurationLatch = new CountDownLatch(testDurationInSec);
        for (int i = 0; i < testDurationInSec; i++) {
            int iterationNumber = i;
            scheduler.schedule(() -> runTestIteration(iterationNumber, totalSuccessCount, totalFailedCount, testDurationLatch), i, TimeUnit.SECONDS);
        }
        int prevSuccessCount = 0;
        int totalSenconds = 0;
        List<Integer> realtime = new ArrayList<Integer>();
        while(true){
            long timeStart = System.currentTimeMillis();
            testDurationLatch.await(1, TimeUnit.SECONDS);
            long timeStop = System.currentTimeMillis();
            totalSenconds += ((timeStop - timeStart) / 1000);
            int totalSuccess = totalSuccessCount.get();
            int realtimeCount = totalSuccess - prevSuccessCount;
            int mean = totalSuccess / totalSenconds;
            log.info("Time spent: {} seconds, message published last {} ms: {}, mean:{}", totalSenconds, timeStop - timeStart, realtimeCount, mean);
            if(totalSenconds % statSampleInterval == 0 || totalSenconds == 1){
                realtime.add(realtimeCount);
            }
            prevSuccessCount = totalSuccess;
            if(testDurationLatch.getCount() == 0){
                log.info("Completed performance iteration. Success: {}, Failed: {}", totalSuccessCount.get(), totalFailedCount.get());
                break;
            }
            if(totalSenconds >= testDurationInSec + 10){
                log.info("Time spent: {} seconds,spend to much time,force to complete performance iteration. Success: {}, Failed: {}", totalSenconds, totalSuccessCount.get(), totalFailedCount.get());
                break;
            }
        }
        List<Long> timeSample = new ArrayList<Long>();
        long TotalMS = 0;
        for(int i = 0; i < testDurationInSec; i ++){
            if(i % statSampleInterval == 0){
                timeSample.add(timeSpent[i]);
            }
            TotalMS += timeSpent[i];
        }
        log.info("Message published spend time,mean: {} ms/{}-msg, samples of every {} seconds is : {}", TotalMS / testDurationInSec, testMessagesPerSecond, statSampleInterval, timeSample);
        log.info("Message published speed,mean: {} msg/s, samples of every {} seconds is:{}", totalSuccessCount.get() / totalSenconds, statSampleInterval, realtime);
    }

    protected void runTestIteration(int iteration,
                                       AtomicInteger totalSuccessPublishedCount,
                                       AtomicInteger totalFailedPublishedCount,
                                       CountDownLatch testDurationLatch ) {
        try {
            log.info("[{}] Starting performance iteration for js-executor...", iteration);
            AtomicInteger successPublishedCount = new AtomicInteger();
            AtomicInteger failedPublishedCount = new AtomicInteger();
            CountDownLatch iterationLatch = new CountDownLatch(testMessagesPerSecond);
            long timeStart = System.currentTimeMillis();
            for (int i = 0; i < testMessagesPerSecond; i++) {
                workers.submit(() -> {
                    jsInvokeService.invokeFunction(totalSuccessPublishedCount,
                            totalFailedPublishedCount,
                            successPublishedCount,
                            failedPublishedCount,
                            iterationLatch,
                            scriptId, jsParam);
                });
            }
            iterationLatch.await();
            long timeStop = System.currentTimeMillis();
            timeSpent[iteration] = timeStop - timeStart;
            log.info("[{}] Time spent: {} milliseconds, completed performance iteration. Success: {}, Failed: {}", iteration, timeStop - timeStart, successPublishedCount.get(), failedPublishedCount.get());
            testDurationLatch.countDown();
        } catch (Throwable t) {
            log.warn("[{}] Failed to process iteration", iteration, t);
        }
    }

}
