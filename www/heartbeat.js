var exec = require('cordova/exec');

var heartbeat = {};

heartbeat.take = function (successCallback, errorCallback) {
	exec(successCallback, errorCallback, "HeartBeat", "take", []);
};

module.exports = heartbeat;

if(typeof angular !== 'undefined'){

	angular.module('ngCordova.plugins.heartbeat', [])
		.factory('$cordovaHeartBeat', ['$q', '$window', function ($q, $window) {

	    return {
		
			take: function () {
		        var q = $q.defer();

		        $window.cordova.plugins.heartbeat.take(
			        function (bpm) {
			        	q.resolve(bpm);
			        }, function (error) {
			        	q.reject(err);
			        }
			    );

		        return q.promise;
      		}

    	};

	}]);

}