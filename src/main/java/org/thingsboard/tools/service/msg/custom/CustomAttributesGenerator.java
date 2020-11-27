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
package org.thingsboard.tools.service.msg.custom;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.thingsboard.tools.service.msg.BaseMessageGenerator;
import org.thingsboard.tools.service.msg.MessageGenerator;
import org.thingsboard.tools.service.msg.Msg;

import java.text.DecimalFormat;

@Slf4j
@Service(value = "randomAttributesGenerator")
@ConditionalOnProperty(prefix = "test", value = "payloadType", havingValue = "CUSTOM")
public class CustomAttributesGenerator extends BaseMessageGenerator implements MessageGenerator {
    @Value("${test.customPayload:}")
    protected String customPayload;

    @Override
    public Msg getNextMessage(String deviceName, boolean shouldTriggerAlarm) {
        byte[] payload;
        try {
            ObjectNode data = mapper.createObjectNode();
            ObjectNode tsNode;
            if (isGateway()) {
                ArrayNode array = data.putArray(deviceName);
                tsNode = array.addObject();
            } else {
                tsNode = data;
            }

            tsNode.put("deviceName", deviceName);
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
                    tsNode.put(split2[0], random.nextInt(100));
                }
                else if(split2[1].equalsIgnoreCase("double")){
                    tsNode.put(split2[0], new DecimalFormat("#.##").format(random.nextDouble() * 100));
                }
                else if(split2[1].equalsIgnoreCase("float")){
                    tsNode.put(split2[0], new DecimalFormat("#.###").format(random.nextFloat()));
                }
                else if(split2[1].equalsIgnoreCase("boolean")){
                    tsNode.put(split2[0], random.nextBoolean());
                }
                else if(split2[1].equalsIgnoreCase("string")){
                    tsNode.put(split2[0], split2[0] + random.nextInt(100));
                }
                else if(split2[1].equalsIgnoreCase("time")){
                    tsNode.put(split2[0], System.currentTimeMillis());
                }
            }
            payload = mapper.writeValueAsBytes(data);
        } catch (Exception e) {
            log.warn("Failed to generate message", e);
            throw new RuntimeException(e);
        }

        return new Msg(payload);
    }
}
