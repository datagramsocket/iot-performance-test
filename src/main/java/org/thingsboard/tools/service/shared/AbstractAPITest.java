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
package org.thingsboard.tools.service.shared;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.util.concurrent.Future;
import lombok.extern.slf4j.Slf4j;
import org.antlr.runtime.misc.IntArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.tools.service.customer.CustomerManager;
import org.thingsboard.tools.service.mqtt.DeviceClient;
import org.thingsboard.tools.service.msg.MessageGenerator;
import org.thingsboard.tools.service.msg.Msg;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public abstract class AbstractAPITest {

    private static ObjectMapper mapper = new ObjectMapper();

    protected ScheduledFuture<?> reportScheduledFuture;

    @Value("${device.startIdx}")
    protected int deviceStartIdxConfig;
    @Value("${device.endIdx}")
    protected int deviceEndIdxConfig;
    @Value("${device.count}")
    protected int deviceCount;
    @Value("${warmup.packSize:100}")
    protected int warmUpPackSize;
    @Value("${test.instanceIdx:0}")
    protected int instanceIdxConfig;
    @Value("${test.useInstanceIdx:false}")
    protected boolean useInstanceIdx;
    @Value("${test.useInstanceIdxRegex:false}")
    protected boolean useInstanceIdxRegex;
    @Value("${test.instanceIdxRegexSource:}")
    protected String instanceIdxRegexSource;
    @Value("${test.instanceIdxRegex:([0-9]+)$}")
    protected String instanceIdxRegex;

    @Value("${test.sequential:true}")
    protected boolean sequentialTest;
    @Value("${test.telemetry:true}")
    protected boolean telemetryTest;
    @Value("${test.mps:100}")
    protected int testMessagesPerSecond;
    @Value("${test.duration:100}")
    protected int testDurationInSec;
    @Value("${test.alarms.start:0}")
    protected int alarmsStartTs;
    @Value("${test.alarms.end:0}")
    protected int alarmsEndTs;
    @Value("${test.alarms.aps:0}")
    protected int alarmsPerSecond;
    @Value("${test.statSampleInterval:5}")
    protected int statSampleInterval;

    @Autowired
    @Qualifier("randomTelemetryGenerator")
    protected MessageGenerator tsMsgGenerator;
    @Autowired
    @Qualifier("randomAttributesGenerator")
    protected MessageGenerator attrMsgGenerator;
    @Autowired
    protected RestClientService restClientService;
    @Autowired
    protected CustomerManager customerManager;

    protected List<Device> devices = Collections.synchronizedList(new ArrayList<>(1024 * 1024));

    protected final Random random = new Random();
    private volatile CountDownLatch testDurationLatch;

    protected int deviceStartIdx;
    protected int deviceEndIdx;
    protected int instanceIdx;
    protected long timeSpent[] = null;

    @PostConstruct
    protected void init() {
        if (this.useInstanceIdx) {
            boolean parsed = false;
            if (this.useInstanceIdxRegex) {
                try {
                    Pattern r = Pattern.compile(this.instanceIdxRegex);
                    Matcher m = r.matcher(this.instanceIdxRegexSource);
                    if (m.find()) {
                        this.instanceIdx = Integer.parseInt(m.group(0));
                        parsed = true;
                    }
                } catch (Exception e) {
                    log.error("Failed to parse instanceIdx", e);
                }
            }
            if (!parsed) {
                this.instanceIdx = this.instanceIdxConfig;
            }
            log.info("Initialized with instanceIdx [{}]", this.instanceIdx);

            this.deviceStartIdx = this.deviceCount * this.instanceIdx;
            this.deviceEndIdx = this.deviceStartIdx + this.deviceCount;
        } else {
            this.deviceStartIdx = this.deviceStartIdxConfig;
            this.deviceEndIdx = this.deviceEndIdxConfig;
        }

        log.info("Initialized with deviceStartIdx [{}], deviceEndIdx [{}]", this.deviceStartIdx, this.deviceEndIdx);
    }

    @PreDestroy
    protected void destroy() {
        if (reportScheduledFuture != null) {
            reportScheduledFuture.cancel(true);
        }
    }

    protected void createDevices(boolean setCredentials) throws Exception {
        List<Device> entities = createEntities(deviceStartIdx, deviceEndIdx, false, setCredentials);
        devices = Collections.synchronizedList(entities);
    }

    protected void runApiTests(int deviceCount) throws InterruptedException {
        log.info("Starting performance test for {} devices...", deviceCount);
        AtomicInteger totalSuccessCount = new AtomicInteger();
        AtomicInteger totalFailedCount = new AtomicInteger();
        testDurationLatch = new CountDownLatch(testDurationInSec);
        timeSpent = new long[testDurationInSec];
        for (int i = 0; i < testDurationInSec; i++) {
            int iterationNumber = i;
            restClientService.getScheduler().schedule(() -> runApiTestIteration(iterationNumber, totalSuccessCount, totalFailedCount, testDurationLatch), 0, TimeUnit.SECONDS);
        }
//        testDurationLatch.await((long) (testDurationInSec * 1.2), TimeUnit.SECONDS);
//        log.info("Completed performance iteration. Success: {}, Failed: {}", totalSuccessCount.get(), totalFailedCount.get());
        int prevSuccessCount = 0;
        long totalSeconds = 0;
        long prevTimeStop = 0;
        List<Integer> realtime = new ArrayList<Integer>();
        long timeStart = System.currentTimeMillis();
        prevTimeStop = timeStart;
        while(true){
            testDurationLatch.await(1, TimeUnit.SECONDS);
            long timeStop = System.currentTimeMillis();
            totalSeconds = (timeStop - timeStart) / 1000;
            int totalSuccess = totalSuccessCount.get();
            int realtimeCount = totalSuccess - prevSuccessCount;
            long mean = totalSuccess / totalSeconds;
            log.info("Time spent: {} seconds, message published last {} ms: {}, mean:{}", totalSeconds, timeStop - prevTimeStop, realtimeCount, mean);
            if(totalSeconds % statSampleInterval == 0 || totalSeconds == 1){
                realtime.add((int) realtimeCount);
            }
            prevSuccessCount = totalSuccess;
            prevTimeStop = timeStop;
            if(testDurationLatch.getCount() == 0){
                log.info("Completed performance iteration. Success: {}, Failed: {}", totalSuccessCount.get(), totalFailedCount.get());
                break;
            }
            if(totalSeconds >= testDurationInSec * 1.2){
                log.info("Time spent: {} seconds,spend to much time,force to complete performance iteration. Success: {}, Failed: {}", totalSeconds, totalSuccessCount.get(), totalFailedCount.get());
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
        log.info("Message published speed,mean: {} msg/s, samples of every {} seconds is:{}", totalSuccessCount.get() / totalSeconds, statSampleInterval, realtime);
    }

    protected abstract void runApiTestIteration(int iteration, AtomicInteger totalSuccessPublishedCount, AtomicInteger totalFailedPublishedCount, CountDownLatch testDurationLatch);

    protected void removeEntities(List<DeviceId> entityIds, boolean isGateway) throws InterruptedException {
        log.info("Removing {} {}...", isGateway ? "gateways" : "devices", entityIds.size());
        CountDownLatch latch = new CountDownLatch(entityIds.size());
        AtomicInteger count = new AtomicInteger();
        for (DeviceId entityId : entityIds) {
            restClientService.getHttpExecutor().submit(() -> {
                try {
                    restClientService.getRestClient().deleteDevice(entityId);
                    count.getAndIncrement();
                } catch (Exception e) {
                    log.error("Error while deleting {}", isGateway ? "gateway" : "device", e);
                } finally {
                    latch.countDown();
                }
            });
        }

        ScheduledFuture<?> logScheduleFuture = restClientService.getLogScheduler().scheduleAtFixedRate(() -> {
            try {
                log.info("{} {} have been removed so far...", isGateway ? "gateways" : "devices", count.get());
            } catch (Exception ignored) {
            }
        }, 0, DefaultRestClientService.LOG_PAUSE, TimeUnit.SECONDS);

        latch.await();
        logScheduleFuture.cancel(true);
        Thread.sleep(1000);
        log.info("{} {} have been removed successfully! {} were failed for removal!", count.get(), isGateway ? "gateways" : "devices", entityIds.size() - count.get());
    }


    protected List<Device> createEntities(int startIdx, int endIdx, boolean isGateway, boolean setCredentials) throws InterruptedException {
        List<Device> result;
        if (isGateway) {
            result = Collections.synchronizedList(new ArrayList<>(1024));
        } else {
            result = Collections.synchronizedList(new ArrayList<>(1024 * 1024));
        }
        int entityCount = endIdx - startIdx;
        log.info("Creating {} {}...", entityCount, isGateway ? "gateways" : "devices");
        CountDownLatch latch = new CountDownLatch(entityCount);
        AtomicInteger count = new AtomicInteger();
        List<CustomerId> customerIds = customerManager.getCustomerIds();
        for (int i = startIdx; i < endIdx; i++) {
            final int tokenNumber = i;
            restClientService.getHttpExecutor().submit(() -> {
                Device entity = new Device();
                try {
                    String token = getToken(isGateway, tokenNumber);
                    if (isGateway) {
                        entity.setName(token);
                        entity.setType("gateway");
                        entity.setAdditionalInfo(mapper.createObjectNode().putObject("additionalInfo").put("gateway", true));
                    } else {
                        entity.setName(token);
                        entity.setType("device");
                    }

                    if (setCredentials) {
                        entity = restClientService.getRestClient().createDevice(entity, token);
                    } else {
                        entity = restClientService.getRestClient().createDevice(entity);
                    }

                    result.add(entity);

                    count.getAndIncrement();
                } catch (Exception e) {
                    log.error("Error while creating entity", e);
                    if (entity != null && entity.getId() != null) {
                        restClientService.getRestClient().deleteDevice(entity.getId());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        ScheduledFuture<?> logScheduleFuture = restClientService.getLogScheduler().scheduleAtFixedRate(() -> {
            try {
                log.info("{} {} have been created so far...", count.get(), isGateway ? "gateways" : "devices");
            } catch (Exception ignored) {
            }
        }, 0, DefaultRestClientService.LOG_PAUSE, TimeUnit.SECONDS);

        latch.await();
        logScheduleFuture.cancel(true);

        log.info("{} {} have been created successfully!", result.size(), isGateway ? "gateways" : "devices");

        return result;
    }

    protected String getToken(boolean isGateway, int token) {
        return (isGateway ? "GW" : "DW") + String.format("%8d", token).replace(" ", "0");
    }

    protected Msg getNextMessage(String deviceName, boolean alarmRequired) {
        return (telemetryTest ? tsMsgGenerator : attrMsgGenerator).getNextMessage(deviceName, alarmRequired);
    }
}
