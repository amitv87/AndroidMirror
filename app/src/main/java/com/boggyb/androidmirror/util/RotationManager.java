package com.boggyb.androidmirror.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import java.util.HashMap;

public class RotationManager {
  private static final String TAG = RotationManager.class.getCanonicalName();
  private static final String BCAST_CONFIGCHANGED = "android.intent.action.CONFIGURATION_CHANGED";
  private static Display display = null;
  private static Context mContext = null;

  public interface Callback{
    void onRotation(int orientation);
  }

  private static class OrientationRunnable implements Runnable {
    private final Callback cb;
    private final int rotation;

    OrientationRunnable(Callback cb, int rotation){
      this.cb = cb;
      this.rotation = rotation;
    }

    @Override
    public void run() {
      cb.onRotation(rotation);
    }
  }

  private static HashMap<Callback, Handler> cbmap = new HashMap<>();

  public static void Init(Context context){
    if(display != null) return;
    mContext = context;
    IntentFilter filter = new IntentFilter();
    filter.addAction(BCAST_CONFIGCHANGED);
    mContext.registerReceiver(mBroadcastReceiver, filter);
    display = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
  }

  public static void DeInit(){
    if(display == null) return;
    mContext.unregisterReceiver(mBroadcastReceiver);
    mContext = null;
    display = null;
  }

  public static int getRotation(){
    return display.getRotation();
  }

  public static int getOrientation(int rotation){
    int angle = 0;
    switch (rotation) {
      case Surface.ROTATION_0:
        angle = 0;
        break;
      case Surface.ROTATION_90:
        angle = 90;
        break;
      case Surface.ROTATION_180:
        angle = 180;
        break;
      case Surface.ROTATION_270:
        angle = 270;
        break;
    }
    return  angle;
  }

  public static void addRotationWatcher(Callback cb, Handler handler){
    synchronized (cbmap){
      cbmap.put(cb, handler);
    }
  }

  public static void removeRotationWatcher(Callback cb){
    synchronized (cbmap){
      cbmap.remove(cb);
    }
  }

  private static void postRotation(Callback cb, int rotation){
    Handler h = cbmap.get(cb);
    if(h != null) h.post(new OrientationRunnable(cb, rotation));
    else cb.onRotation(rotation);
  }

  private static BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent myIntent){
      synchronized (cbmap){
        int rotation = getRotation();
        Log.d(TAG, "rotation: " + rotation);
        for(Callback cb : cbmap.keySet()) postRotation(cb, rotation);
      }
    }
  };
}
