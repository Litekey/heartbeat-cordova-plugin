var exec = require('cordova/exec');

var heartbeat = {};

heartbeat.take = function (options, successCallback, errorCallback) {
    var args = new Array();
    args.push(options.seconds ? options.seconds : 10);
    args.push(options.fps ? options.fps : 30);
	exec(successCallback, errorCallback, "HeartBeat", "take", args);
};


module.exports = heartbeat;


(function(){
try{
	if(typeof angular !== 'undefined'){
		angular.module('ngCordova.plugins.heartbeat', [])
			.factory('$cordovaHeartBeat', ['$q', '$window', function ($q, $window) {

		    return {
			
				take: function (options) {
			        var q = $q.defer();			        
			        heartbeat.take(options,
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
		angular.module('ngCordova.plugins').requires.push('ngCordova.plugins.heartbeat');
		console.log("[HeartBeat]: ngCordova plugin loaded");
	}
} finally {
}
})();