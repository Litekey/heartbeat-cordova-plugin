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

- heartbeat.take

heartbeat.take
==============

Get the current bpm. Turn light on and take a few seconds for detect the heart rate.

Works on cordova
----------------

	function successCallback(bpm){
        alert("Your heart beat per minute is:" + bpm);
    }

    function errorCallback(){
        alert("Has not posible measure your heart beat");
    }

    cordova.plugins.heartbeat.take(successCallback, errorCallback);

Works on ngCordova
------------------

	app.controller('HeartBeatController', function (cordovaHeartBeat) {
                    

        $cordovaHeartBeat.take().then(
            function(bpm){
                console.log("Your heart beat per minute is:" + bpm);
            }, function(error){
                console.log("Has not posible measure your heart beat");
            }
        );
        
    });

Supported Platforms
-------------------

- iOS
- Android

About
==============

This plugin was created by LiteKey (http://lite-key.com)