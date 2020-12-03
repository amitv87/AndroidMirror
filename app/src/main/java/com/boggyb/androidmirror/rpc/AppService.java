package com.boggyb.androidmirror.rpc;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

/**
 * Created by amitverma on 14/11/17.
 */

public abstract class AppService extends Service implements Callbacks.Service {
  ServiceImpl internalService = null;

  @Override
  public IBinder onBind(Intent intent) {
    if(internalService == null) internalService = new ServiceImpl(getName(), this, null);
    return internalService;
  }

  public abstract String getName();

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    Log.d(getName(), "onStartCommand");
    return START_STICKY;
  }

  protected boolean send(String who, Bundle what){
    boolean rc = false;
    if(internalService != null) rc = internalService.send(who, what);
    return rc;
  }
}
