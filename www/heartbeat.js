var exec = require('cordova/exec');

var heartbeat = {};

heartbeat.take = function (successCallback, errorCallback) {
	exec(successCallback, errorCallback, "HeartBeat", "take", []);
};

module.exports = heartbeat;