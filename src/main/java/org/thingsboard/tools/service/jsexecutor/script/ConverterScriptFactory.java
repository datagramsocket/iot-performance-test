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

public class ConverterScriptFactory {

    public static final String MSG = "msg";
    public static final String METADATA = "metadata";
    public static final String MSG_TYPE = "msgType";
    public static final String PAYLOAD = "payload";
    public static final String INTEGRATIONMETADATASTR = "integrationMetadataStr";

    private static final String JS_WRAPPER_DOWN_LINK_PREFIX_TEMPLATE = "function %s(msgStr, metadataStr, msgType,integrationMetadataStr) { " +
            "    var msg = JSON.parse(msgStr); " +
            "    var metadata = JSON.parse(metadataStr); " +
            "    var integrationMetadata= JSON.parse(integrationMetadataStr); " +
            "    return JSON.stringify(Encode(msg, metadata, msgType,integrationMetadata));" +
            "    function Encode(%s, %s, %s,%s) {";

    private static final String JS_WRAPPER_UP_LINK_PREFIX_TEMPLATE = "function %s(payloadStr, metadataStr) { " +
            "    var metadata = JSON.parse(metadataStr); " +
            "    var payload = new Array();\n" +
            "    for ( var i = 0; i <payloadStr.length; i++){\n" +
            "       payload.push(payloadStr.charCodeAt(i));\n" +
            "    } " +
            "    return JSON.stringify(Decode(payload, metadata));" +
            "    function Decode(%s, %s) {";


    private static final String JS_WRAPPER_SUFFIX = "\n}" +
            "\n}";


    public static String generaterDownLinkScript(String functionName, String scriptBody, String... argNames) {
        String msgArg;
        String metadataArg;
        String msgTypeArg;
        String integrationMetadataStr;
        if (argNames != null && argNames.length == 4) {
            msgArg = argNames[0];
            metadataArg = argNames[1];
            msgTypeArg = argNames[2];
            integrationMetadataStr = argNames[3];
        } else {
            msgArg = MSG;
            metadataArg = METADATA;
            msgTypeArg = MSG_TYPE;
            integrationMetadataStr=INTEGRATIONMETADATASTR;
        }
        String jsWrapperPrefix = String.format(JS_WRAPPER_DOWN_LINK_PREFIX_TEMPLATE, functionName,
                 msgArg, metadataArg, msgTypeArg,integrationMetadataStr);
        return jsWrapperPrefix + scriptBody + JS_WRAPPER_SUFFIX;
    }


    public static String generaterUpLinkScript(String functionName, String scriptBody, String... argNames) {
        String payload;
        String metadataArg;
        if (argNames != null && argNames.length == 2) {
            payload = argNames[0];
            metadataArg = argNames[1];
        } else {
            payload = PAYLOAD;
            metadataArg = METADATA;
        }
        String jsWrapperPrefix = String.format(JS_WRAPPER_UP_LINK_PREFIX_TEMPLATE, functionName,
                payload, metadataArg);
        return jsWrapperPrefix + scriptBody + JS_WRAPPER_SUFFIX;
    }
}
