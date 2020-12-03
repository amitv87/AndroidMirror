package com.boggyb.androidmirror.rpc;

import java.util.Timer;
import java.util.TimerTask;

import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.ServiceManager;

/**
 * Created by amitverma on 11/11/17.
 */

public class SystemSerivceClient extends TimerTask {

  private final String serviceName;
  private final ServiceClientImpl client;
  private final Callbacks.ServiceClient cb;

  final Messenger messenger = new Messenger(new Handler(){
    @Override
    public void handleMessage(Message msg) {
      cb.onMessage(msg.getData());
    }
  });

  public SystemSerivceClient(String serviceName, String who, Callbacks.ServiceClient cb){
    this.cb = cb;
    this.serviceName = serviceName;
    client = new ServiceClientImpl(who, cb);
    connect();
    new Timer().scheduleAtFixedRate(this, 5000, 5000);
  }

  private void connect() {
    IBinder serviceConnection = ServiceManager.checkService(serviceName);
    if(serviceConnection != null) client.connect(serviceConnection);
  }

  public boolean send(Bundle what){
    return client.send(what);
  }

  @Override
  public void run() {
    if(!client.isAlive()){
      cb.onDisconnect();
      connect();
    }
  }
}