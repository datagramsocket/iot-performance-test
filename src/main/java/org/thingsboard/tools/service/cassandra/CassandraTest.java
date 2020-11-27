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
package org.thingsboard.tools.service.cassandra;

import com.datastax.oss.driver.api.core.uuid.Uuids;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.kv.*;
import org.thingsboard.server.dao.timeseries.TimeseriesService;
import org.thingsboard.tools.service.msg.MessageGenerator;
import org.thingsboard.tools.service.shared.RestClientService;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "test", value = "api", havingValue = "cassandra")
public class CassandraTest extends Thread{
    @Value("${test.mps:1000}")
    protected int testMessagesPerSecond;
    @Value("${test.duration:60}")
    protected int testDurationInSec;
    @Value("${test.statSampleInterval:5}")
    protected int statSampleInterval;
    @Value("${test.customPayload:}")
    protected String customPayload;

    private volatile CountDownLatch testDurationLatch;
    private UUID scriptId = null;
    private long timeSpent[] = null;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
    private final ExecutorService workers = Executors.newFixedThreadPool(10);

    @Autowired
    @Qualifier("randomTelemetryGenerator")
    protected MessageGenerator tsMsgGenerator;

    @Autowired
    TimeseriesService tsService;

    @SneakyThrows
    public void run() {
        runApiTests();
    }

    public void runApiTests() throws InterruptedException{
        log.info("Starting performance test for cassandra in 10 seconds...");
        Thread.sleep(10000);
        timeSpent = new long[testDurationInSec];
        AtomicInteger totalSuccessCount = new AtomicInteger();
        AtomicInteger totalFailedCount = new AtomicInteger();
        AtomicInteger successPublishedCount = new AtomicInteger();
        AtomicInteger failedPublishedCount = new AtomicInteger();

        if(totalSuccessCount.get() > 0){
            totalSuccessCount.decrementAndGet();
        }
        if(totalFailedCount.get() > 0){
            totalFailedCount.decrementAndGet();
        }
        if(successPublishedCount.get() > 0){
            successPublishedCount.decrementAndGet();
        }
        if(failedPublishedCount.get() > 0){
            failedPublishedCount.decrementAndGet();
        }
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
            if(totalSenconds >= (testDurationInSec + 5)){
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
            log.info("[{}] Starting performance iteration for cassandra...", iteration);
            UUID tenantId = Uuids.timeBased();
            List<TsKvEntry> tsKvEntryList = new ArrayList<>();
            createTelemetry(tsKvEntryList);
            AtomicInteger successPublishedCount = new AtomicInteger();
            AtomicInteger failedPublishedCount = new AtomicInteger();
            CountDownLatch iterationLatch = new CountDownLatch(testMessagesPerSecond);
            long timeStart = System.currentTimeMillis();
            for (int i = 0; i < testMessagesPerSecond; i++) {
                workers.submit(() -> {
                    UUID entityId = Uuids.timeBased();
                    tsService.save(new TenantId(tenantId), new DeviceId(entityId), tsKvEntryList,0);
                    successPublishedCount.incrementAndGet();
                    totalSuccessPublishedCount.incrementAndGet();
                    iterationLatch.countDown();
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

    void createTelemetry(List<TsKvEntry> tsKvEntryList){
        Random random = new Random();
        byte[] payload;
        try {
            String[] split = customPayload.split(",");
            for (String node : split) {
                if(node.isEmpty()){
                    continue;
                }
                String[] split2 = node.split(":");
                if(split2.length<=1){
                    continue;
                }
                if(split2[1].equalsIgnoreCase("int")){
                    tsKvEntryList.add(new BasicTsKvEntry(System.currentTimeMillis(), new LongDataEntry(split2[0], (long) random.nextInt(100))));
                }
                else if(split2[1].equalsIgnoreCase("double")){
                    tsKvEntryList.add(new BasicTsKvEntry(System.currentTimeMillis(), new DoubleDataEntry(split2[0], random.nextDouble() * 100)));
                }
                else if(split2[1].equalsIgnoreCase("float")){
                    tsKvEntryList.add(new BasicTsKvEntry(System.currentTimeMillis(), new DoubleDataEntry(split2[0], random.nextDouble())));
                }
                else if(split2[1].equalsIgnoreCase("boolean")){
                    tsKvEntryList.add(new BasicTsKvEntry(System.currentTimeMillis(), new BooleanDataEntry(split2[0], random.nextBoolean())));
                }
                else if(split2[1].equalsIgnoreCase("string")){
                    tsKvEntryList.add(new BasicTsKvEntry(System.currentTimeMillis(), new StringDataEntry(split2[0], split2[0] + random.nextInt(100))));
                }
                else if(split2[1].equalsIgnoreCase("time")){
                    long time = System.currentTimeMillis();
                    tsKvEntryList.add(new BasicTsKvEntry(System.currentTimeMillis(), new StringDataEntry(split2[0], new Long(time).toString())));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to generate message", e);
            throw new RuntimeException(e);
        }

    }
}
