HeartBeat
======

The `heartbeat` object provide functions to know what's your heart rate (beats per minute) with device's camera.

How does it work?
-------
The plugin opens device's camera and turn on the lightflash next  you must to put your finger on the cam. The image of the cam only is red, your heart rate will become to variations of red hue. The plugin will analyze this variations and will get your heart rate in bmp (beats per minute).


Install
-------

	cordova plugin add https://github.com/Litekey/heartbeat-cordova-plugin

Methods
-------

- take

take (Method)
==============

Get the current bpm. Turn light on and take a few seconds for detect the heart rate.

Use it
----------------

	function successCallback(bpm){
        alert("Your heart beat per minute is:" + bpm);
    }

    function errorCallback(){
        alert("Has not posible measure your heart beat");
    }

    var props = {
        seconds: 10,
        fps: 30
    };

    cordova.plugins.heartbeat.take(props, successCallback, errorCallback);

Use it with ngCordova
------------------

	app.controller('HeartBeatController', function ($cordovaHeartBeat) {
        
        var props = {
            seconds: 10,
            fps: 30
        };

        $cordovaHeartBeat.take(props).then(
            function(bpm){
                console.log("Your heart beat per minute is:" + bpm);
            }, function(error){
                console.log("Has not posible measure your heart beat");
            }
        );
        
    });

Properties
-------------------
- seconds: It indicates how many seconds the heart rate will monitor. For more fps more precision in heart rate measure. If the value is not setted the default is 10.
- fps: It indicates how many frames per seconds the heart rate will monitor. For more fps more precision in heart rate measure. If the value is not setted the default is 30.

Supported Platforms
-------------------

- iOS
- Android

About
==============

This plugin was created by LiteKey (http://lite-key.com)