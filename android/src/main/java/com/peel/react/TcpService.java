package com.peel.react;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.Nullable;
import android.util.Base64;

import com.facebook.common.logging.FLog;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.GuardedAsyncTask;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;

public class TcpService extends Service implements TcpSocketListener {
    private static String TAG = "TcpService";

    private boolean mShuttingDown = false;
    private TcpSocketManager socketManager;

    private static ReactContext ctx;

    public static void setCtx(ReactContext context){
        ctx = context;
    }

    public TcpService(){
        super();
    }

    @Override
    public IBinder onBind(Intent intent) {
        try {
            socketManager = new TcpSocketManager(this);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return binder;
    }

    public class ServiceBinder extends Binder{
        public TcpService getService(){
            return TcpService.this;
        }
    }

    private ServiceBinder binder = new ServiceBinder();



    public void onCatalystInstanceDestroy() {
        mShuttingDown = true;

        try {
            new GuardedAsyncTask<Void, Void>(ctx) {
                @Override
                protected void doInBackgroundGuarded(Void... params) {
                    socketManager.closeAllSockets();
                }
            }.execute().get();
        } catch (InterruptedException ioe) {
            FLog.e(TAG, "onCatalystInstanceDestroy", ioe);
        } catch (ExecutionException ee) {
            FLog.e(TAG, "onCatalystInstanceDestroy", ee);
        }
    }

    private void sendEvent(String eventName, WritableMap params) {
        ctx
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }

    public void listen(final Integer cId, final String host, final Integer port) {
        new GuardedAsyncTask<Void, Void>(ctx) {
            @Override
            protected void doInBackgroundGuarded(Void... params) {
                try {
                    socketManager.listen(cId, host, port);
                } catch (UnknownHostException uhe) {
                    FLog.e(TAG, "listen", uhe);
                    onError(cId, uhe.getMessage());
                } catch (IOException ioe) {
                    FLog.e(TAG, "listen", ioe);
                    onError(cId, ioe.getMessage());
                }
            }
        }.execute();
    }

    public void connect(final Integer cId, final @Nullable String host, final Integer port, final ReadableMap options) {
        new GuardedAsyncTask<Void, Void>(ctx) {
            @Override
            protected void doInBackgroundGuarded(Void... params) {
                // NOTE : ignoring options for now, just use the available interface.
                try {
                    socketManager.connect(cId, host, port);
                } catch (UnknownHostException uhe) {
                    FLog.e(TAG, "connect", uhe);
                    onError(cId, uhe.getMessage());
                } catch (IOException ioe) {
                    FLog.e(TAG, "connect", ioe);
                    onError(cId, ioe.getMessage());
                }
            }
        }.execute();
    }

    public void write(final Integer cId, final String base64String, final Callback callback) {
        new GuardedAsyncTask<Void, Void>(ctx) {
            @Override
            protected void doInBackgroundGuarded(Void... params) {
                socketManager.write(cId, Base64.decode(base64String, Base64.NO_WRAP));
                if (callback != null) {
                    callback.invoke();
                }
            }
        }.execute();
    }

    private Timer timer;

    public void invokeInterval(final Integer cId,final Integer period){
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                Message msg = new Message();
                Bundle bundle = new Bundle();
                bundle.putInt("cId",cId);

                msg.what = 0;
                msg.setData(bundle);
                handler.sendMessage(msg);
            }
        },0,period);
    }

    private Handler handler = new Handler(){
        public void handleMessage(Message msg){
            switch (msg.what){
                case 0:
                    Bundle bundle = msg.getData();
                    onInterval(bundle.getInt("cId"));
                    break;
            }
        }
    };

    public void end(final Integer cId) {
        new GuardedAsyncTask<Void, Void>(ctx) {
            @Override
            protected void doInBackgroundGuarded(Void... params) {
                socketManager.close(cId);
            }
        }.execute();
    }

    public void destroy(final Integer cId) {
        end(cId);
    }

    /** TcpSocketListener */

    @Override
    public void onConnection(Integer serverId, Integer clientId, InetSocketAddress socketAddress) {
        if (mShuttingDown) {
            return;
        }
        WritableMap eventParams = Arguments.createMap();
        eventParams.putInt("id", serverId);

        WritableMap infoParams = Arguments.createMap();
        infoParams.putInt("id", clientId);

        final InetAddress address = socketAddress.getAddress();

        WritableMap addressParams = Arguments.createMap();
        addressParams.putString("address", address.getHostAddress());
        addressParams.putInt("port", socketAddress.getPort());
        addressParams.putString("family", address instanceof Inet6Address ? "IPv6" : "IPv4");

        infoParams.putMap("address", addressParams);
        eventParams.putMap("info", infoParams);

        sendEvent("connection", eventParams);
    }

    @Override
    public void onConnect(Integer id, InetSocketAddress socketAddress) {
        if (mShuttingDown) {
            return;
        }
        WritableMap eventParams = Arguments.createMap();
        eventParams.putInt("id", id);

        final InetAddress address = socketAddress.getAddress();

        WritableMap addressParams = Arguments.createMap();
        addressParams.putString("address", address.getHostAddress());
        addressParams.putInt("port", socketAddress.getPort());
        addressParams.putString("family", address instanceof Inet6Address ? "IPv6" : "IPv4");

        eventParams.putMap("address", addressParams);

        sendEvent("connect", eventParams);
    }

    @Override
    public void onData(Integer id, byte[] data) {
        if (mShuttingDown) {
            return;
        }
        WritableMap eventParams = Arguments.createMap();
        eventParams.putInt("id", id);
        eventParams.putString("data", Base64.encodeToString(data, Base64.NO_WRAP));

        sendEvent("data", eventParams);
    }

    public void onInterval(Integer id){
        WritableMap eventParams = Arguments.createMap();
        eventParams.putInt("id", id);
        sendEvent("interval",eventParams);
    }

    @Override
    public void onClose(Integer id, String error) {
        if (mShuttingDown) {
            return;
        }
        if (error != null) {
            onError(id, error);
        }

        WritableMap eventParams = Arguments.createMap();
        eventParams.putInt("id", id);
        eventParams.putBoolean("hadError", error != null);
        timer.cancel();
        sendEvent("close", eventParams);
    }

    @Override
    public void onError(Integer id, String error) {
        if (mShuttingDown) {
            return;
        }

        WritableMap eventParams = Arguments.createMap();
        eventParams.putInt("id", id);
        eventParams.putString("error", error);
        timer.cancel();
        sendEvent("error", eventParams);
    }
}
