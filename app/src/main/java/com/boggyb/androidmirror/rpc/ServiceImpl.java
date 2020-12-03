package com.boggyb.androidmirror.rpc;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by amitverma on 11/11/17.
 */

public class ServiceImpl extends ISystemServiceWrapper.Stub {

  static final String WHO_KEY = "who";
  static final String MESSENGER_KEY = "mess";
  private static final String TAG = ServiceImpl.class.getCanonicalName();

  public enum ACTION {
    CONNECT(101),
    DISCONNECT(102);

    public final int value;
    private static Map<Integer, ACTION> map = new HashMap<>();

    static {
      for (ACTION action : ACTION.values()) {
        map.put(action.value, action);
      }
    }

    ACTION(int _value) { value = _value; }

    public int value(){
      return value;
    }

    public static ACTION valueOf(int value) {
      return map.get(value);
    }
  };

  private final Messenger messenger = new Messenger(new Handler(){
    @Override
    public void handleMessage(Message msg) {
      if(msg.replyTo == null) return;

      String who = messengerMap.get(msg.replyTo);
      if(who == null) return;

      cb.onMessage(who, msg.getData());
    }
  });

  private final String serviceName;
  private final Callbacks.Service cb;
  private final Set<String> whos;
  private final HashMap<String, Messenger> whoMap = new HashMap<>();
  private final HashMap<Messenger, String> messengerMap = new HashMap<>();

  ServiceImpl(String serviceName, Callbacks.Service cb, final Set<String> whos){
    this.cb = cb;
    this.whos = whos;
    this.serviceName = serviceName;

    new Timer().scheduleAtFixedRate(new TimerTask() {
      @Override
      public void run() {
        synchronized (whoMap){
          if(whos != null) {
            for (String who : whos) {
              Messenger m = whoMap.get(who);
              if (m == null || !m.getBinder().pingBinder()) {
                whoMap.remove(who);
                ServiceImpl.this.cb.onDisconnect(who);
              }
            }
          }
          for(Messenger m: whoMap.values()) {
            if (m == null || !m.getBinder().pingBinder()){
              String who = messengerMap.get(m);
              whoMap.remove(who);
              ServiceImpl.this.cb.onDisconnect(who);
            }
          }
        }
      }
    }, 5000, 5000);
  }

  @Override
  public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
    if(data == null) return false;

    Bundle req = data.readBundle();
    if(req == null) return false;

    String who = req.getString(WHO_KEY);
    Messenger mess = req.getParcelable(MESSENGER_KEY);


    if(who == null && mess == null) return false;

    ACTION action = ACTION.valueOf(code);

    if(action == null) return false;

    if(reply != null){
      Bundle res = new Bundle();
      res.putString(WHO_KEY, serviceName);
      res.putParcelable(MESSENGER_KEY, messenger);
      reply.writeBundle(res);
    }

    synchronized (whoMap) {
      switch (action) {
        case CONNECT:
          whoMap.put(who, mess);
          messengerMap.put(mess, who);
          cb.onConnect(who);
          break;
        case DISCONNECT:
          whoMap.remove(who);
          messengerMap.remove(mess);
          cb.onDisconnect(who);
          break;
        default:
          break;
      }
    }

    return true;
  }

  boolean send(String who, Bundle what){
    boolean rc = false;
    synchronized (whoMap) {
      Messenger mess = whoMap.get(who);
      if (mess == null) return rc;

      Message msg = Message.obtain();
      msg.setData(what);
      try {
        mess.send(msg);
        rc = true;
      } catch (RemoteException e) {
        Log.e(TAG, serviceName +" send to " + who +" error", e);
      }
    }
    return rc;
  }
}
