package com.boggyb.androidmirror.rpc;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by amitverma on 14/11/17.
 */

public class AppServiceClient {

  private Timer timer;
  private final Context mContext;
  private final ServiceClientImpl client;
  private final Callbacks.ServiceClient cb;
  private final ComponentName mComponentName;

  // componentName = <pakage>/.<service class>
  public AppServiceClient(String who, Context context, ComponentName componentName, Callbacks.ServiceClient cb){
    this.cb = cb;
    mContext = context;
    mComponentName = componentName;
    client = new ServiceClientImpl(who, cb);
    connect();
  }

  private void _connect(){
    try {
      Intent intent = new Intent();
      intent.setComponent(mComponentName);
      Log.d("APS", "_connect intent: " + intent);
      mContext.startService(intent);
      mContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }
    catch (Exception ignored){}
  }

  public boolean send(Bundle what){
    return client.send(what);
  }

  private ServiceConnection mConnection = new ServiceConnection() {
    @Override
    public void onServiceConnected(ComponentName name, IBinder service){
      client.connect(service);
    }

    @Override // noop
    public void onServiceDisconnected(ComponentName name) {}
  };

  public void disconnect(){
    if(timer != null) timer.cancel();
    timer = null;
    try {
      mContext.unbindService(mConnection);
    }
    catch(Exception ignored){}
  }

  public void connect(){
    if(timer != null) return;
    timer = new Timer();
    timer.scheduleAtFixedRate(new TimerTask() {
      @Override
      public void run() {
        if(client.isAlive()) return;
        cb.onDisconnect();
        _connect();
      }
    }, 0, 1000);
  }
}
