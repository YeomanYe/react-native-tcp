/**
 * Copyright (c) 2015-present, Peel Technologies, Inc.
 * All rights reserved.
 */

package com.peel.react;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;

/**
 * The NativeModule acting as an api layer for {@link TcpSocketManager}
 */
public final class TcpSockets extends ReactContextBaseJavaModule {
    private static final String TAG = "TcpSockets";

    private ReactContext ctx;
    private TcpService service;
    private ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            TcpService.ServiceBinder myBinder = (TcpService.ServiceBinder)binder;
            service = myBinder.getService();
            Log.i(TAG, "onServiceConnected");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i(TAG, "onServiceDisconnected");
        }
    };

    public TcpSockets(ReactApplicationContext reactContext) {
        super(reactContext);
        TcpService.setCtx(reactContext);
        Intent intent = new Intent(reactContext,TcpService.class);
        ctx = reactContext;
        ctx.bindService(intent,conn,Context.BIND_AUTO_CREATE);
    }

    @Override
    public String getName() {
        return TAG;
    }

    @Override
    public void initialize() {
//        service.initialize();
    }

    @Override
    public void onCatalystInstanceDestroy() {
        service.onCatalystInstanceDestroy();
        ctx.unbindService(conn);
    }

    @ReactMethod
    public void listen(final Integer cId, final String host, final Integer port) {
        service.listen(cId,host,port);
    }

    @ReactMethod
    public void connect(final Integer cId, final @Nullable String host, final Integer port, final ReadableMap options) {
        service.connect(cId,host,port,options);
    }

    @ReactMethod
    public void invokeInterval(Integer cId,Integer period){
        service.invokeInterval(cId,period);
    }

    @ReactMethod
    public void write(final Integer cId, final String base64String, final Callback callback) {
        service.write(cId,base64String,callback);
    }

    @ReactMethod
    public void end(final Integer cId) {
        service.end(cId);
    }

    @ReactMethod
    public void destroy(final Integer cId) {
        end(cId);
    }

}
