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

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.rest.client.RestClient;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class DefaultRestClientService implements RestClientService {

    public static final int LOG_PAUSE = 1;

    private final ExecutorService httpExecutor = Executors.newFixedThreadPool(100);
    private final ScheduledExecutorService logScheduler = Executors.newScheduledThreadPool(10);
    private ScheduledExecutorService scheduler = null;
    private ExecutorService workers = null;

    @Value("${rest.url}")
    private String restUrl;
    @Value("${rest.username}")
    private String username;
    @Value("${rest.password}")
    private String password;
    @Value("${test.scheduler_thread_pool_size:10}")
    protected int schedulerThreadPoolSize;
    @Value("${test.worker_thread_pool_size:10}")
    protected int workerThreadPoolSize;

    static {
        disableSslVerification();
    }

    private static void disableSslVerification() {
        try
        {
            // Create a trust manager that does not validate certificate chains
            TrustManager[] trustAllCerts = new TrustManager[] {new X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }
                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }
            }
            };

            // Install the all-trusting trust manager
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

            // Create all-trusting host name verifier
            HostnameVerifier allHostsValid = new HostnameVerifier() {
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            };

            // Install the all-trusting host verifier
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }
    }

    @Getter
    private RestClient restClient;
    @Getter
    private EventLoopGroup eventLoopGroup;

    @Override
    public ExecutorService getWorkers() {
        return workers;
    }

    @Override
    public ExecutorService getHttpExecutor() {
        return httpExecutor;
    }

    @Override
    public ScheduledExecutorService getScheduler() {
        return scheduler;
    }

    @Override
    public ScheduledExecutorService getLogScheduler() {
        return logScheduler;
    }

    @PostConstruct
    public void init() {
        scheduler = Executors.newScheduledThreadPool(schedulerThreadPoolSize);
        workers = Executors.newFixedThreadPool(workerThreadPoolSize);
        restClient = new RestClient(restUrl);
        restClient.login(username, password);
        eventLoopGroup = new NioEventLoopGroup();
    }


    @PreDestroy
    public void destroy() {
        if (!this.httpExecutor.isShutdown()) {
            this.httpExecutor.shutdownNow();
        }
        if (!this.scheduler.isShutdown()) {
            this.scheduler.shutdownNow();
        }
        if (!this.workers.isShutdown()) {
            this.workers.shutdownNow();
        }
        if (!eventLoopGroup.isShutdown()) {
            eventLoopGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS);
        }

    }

}
