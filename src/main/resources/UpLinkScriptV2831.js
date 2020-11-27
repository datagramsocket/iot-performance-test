// Decode an uplink message from a buffer
// payload - array of bytes
// metadata - key/value object

/** Decoder **/

    // decode payload to string
    // var payloadStr = decodeToString(payload);

    // decode payload to JSON
var data = decodeToJson(payload);

var topicPattern = ["data/(.+)/environment", "status/(.+)/lock", "data/(.+)/power", "alarm/(.+)/power", "alarm/(.+)/screen"];
var sn;
var telemetry;
for (var i = 0; i < topicPattern.length; i++) {
    try {
        sn = metadata.topicName.match(topicPattern[i])[1];
    }
    catch(ex){

    }
    if (sn && i === 0) {
        telemetry = parseEnv(data.data.value);
        break;
    }
    if (sn && i === 1) {
        telemetry = parseLock(data.locks);
        break;
    }
    if (sn && i === 2) {
        telemetry = parsePower(data.data.powerValue);
        break;
    }
    if (sn && i === 3) {
        telemetry = parsePowerAlarm(data.alarmType);
        break;
    }
    if (sn && i === 4) {
        telemetry = parseScreenAlarm(data.data.value);
        break;
    }
}

var deviceName = '英飞拓智能网关';
var deviceType = 'V2831';

// Result object with device attributes/telemetry data
var result = {
    deviceName: deviceName,
    deviceType: deviceType,
    attributes: {
        sn: sn,
    },
    telemetry: telemetry
};

/** Helper functions **/

function decodeToString(payload) {
    return String.fromCharCode.apply(String, payload);
}

function decodeToJson(payload) {
    // covert payload to string.
    var str = decodeToString(payload);

    // parse string to JSON
    var data = JSON.parse(str);
    return data;
}

function parseEnv(str) {
    var key = { "PM2.5": "PM2.5", PM10: "PM10", Temp: "温度", Noise: "噪音", Humid: "湿度", illumination: "亮度", "barometric pressure": "气压", windspeed: "风速" };
    var strArr = str.split(",");
    var keyArr = [];
    var obj = {};
    for (var s = 0; s < strArr.length; s++) {
        keyArr.push(strArr[s].split("="));
    }
    for (var i = 0; i < keyArr.length; i++) {
        var item = keyArr[i];
        obj[key[item[0]]] = item[1];
    }
    return obj;
}

function parseLock(locks) {
    var obj = {};
    var value = { lock: "关", unlock: "开", exception: "异常" };
    for (var i = 0; i < locks.length; i++) {
        var key = "门锁" + (i + 1) + "状态";
        obj[key] = value[locks[i].value];
    }
    return obj;
}

function parsePower(str) {
    var key = { week: "周用电量", month: "月用电量", year: "年用电量" };
    var strArr = str.split(",");
    var keyArr = [];
    var obj = {};
    for (var s = 0; s < strArr.length; s++) {
        keyArr.push(strArr[s].split("="));
    }
    for (var i = 0; i < keyArr.length; i++) {
        var item = keyArr[i];
        obj[key[item[0]]] = item[1];
    }
    return obj;
}

function parsePowerAlarm(str) {
    var strArr = str.split(",");
    var keyArr = [];
    var obj = {};
    var value = { Poweroff: "断电", belowCurrent: "欠电流", overCurrent: "过电流", belowVoltage: "欠电压", overVoltage: "过电压" };
    obj["报警类型"] = value[str];
    return obj;
}

function parseScreenAlarm(str) {
    var strArr = str.split(",");
    var keyArr = [];
    var obj = {};
    var value = { Poweroff: "断电", belowCurrent: "欠电流", overCurrent: "过电流", belowVoltage: "欠电压", overVoltage: "过电压" };
    obj["报警类型"] = value[str];
    return obj;
}

return result;
