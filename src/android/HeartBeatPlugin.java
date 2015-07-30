package com.litekey.cordova.plugins.heartbeat;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

public class HeartBeatPlugin extends CordovaPlugin {

    private static final String TAG = HeartBeatPlugin.class.getSimpleName();
    public static final int REQUEST_CODE = 0x03A070A73;
    public static final String SECONDS_KEY = "seconds";
    public static final String FPS_KEY = "fps";
    public static final String BPM_KEY = "bpm";

    private CallbackContext callback;

    @Override
    public boolean execute(String action, JSONArray data,
            CallbackContext callbackContext) throws JSONException {
        this.callback = callbackContext;
        if (action.equals("take")) {
            take(data.getInt(0), data.getInt(1));
            PluginResult r = new PluginResult(PluginResult.Status.NO_RESULT);
            r.setKeepCallback(true);
            callbackContext.sendPluginResult(r);
        } else {
            return false;
        }
        return true;
    }

    private void take(final int seconds, final int fps) {
        Intent intent = new Intent(cordova.getActivity()
                .getApplicationContext(), CameraActivity.class);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setPackage(cordova.getActivity().getApplicationContext()
                .getPackageName());
        intent.putExtra(SECONDS_KEY, seconds);
        intent.putExtra(FPS_KEY, fps);
        cordova.startActivityForResult((CordovaPlugin) HeartBeatPlugin.this,
                intent, REQUEST_CODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                int bpm = intent.getIntExtra(BPM_KEY, 0);
                Log.i(TAG, "Result: " + bpm);
                this.callback.sendPluginResult(new PluginResult(
                        PluginResult.Status.OK, bpm));
            } else {
                this.callback.sendPluginResult(new PluginResult(
                        PluginResult.Status.ERROR));
            }
        }
    }

}
