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
package org.thingsboard.tools.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.thingsboard.tools.service.cassandra.CassandraTest;

import javax.annotation.PostConstruct;

@Service
@Slf4j
@ConditionalOnProperty(prefix = "test", value = "api", havingValue = "cassandra")
public class CassandraTestExecutor {

    @Value("${test.enabled:true}")
    protected boolean testEnabled;

    @Autowired
    private CassandraTest cassandraTest;

    @PostConstruct
    public void init() throws Exception {
        initEntities();

        if (testEnabled) {
            runApiTests();
        }

        cleanUpEntities();
    }

    protected void initEntities() throws Exception {
    }

    protected void runApiTests() throws InterruptedException {
        cassandraTest.start();
    }

    protected void cleanUpEntities() throws Exception {
    }
}
