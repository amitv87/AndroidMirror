package com.boggyb.androidmirror.rpc;

import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcel;
import android.os.RemoteException;

import static com.boggyb.androidmirror.rpc.ServiceImpl.*;

/**
 * Created by amitverma on 14/11/17.
 */

public class ServiceClientImpl{

  private final String who;
  private Messenger serviceMessenger;
  private final Callbacks.ServiceClient cb;

  final Messenger messenger = new Messenger(new Handler(){
    @Override
    public void handleMessage(Message msg) {
      cb.onMessage(msg.getData());
    }
  });

  ServiceClientImpl(String who, Callbacks.ServiceClient cb){
    this.cb = cb;
    this.who = who;
  }

  void connect(IBinder serviceConnection){
    Parcel preq = Parcel.obtain();
    Parcel pres = Parcel.obtain();

    Bundle req = new Bundle();
    req.putString(WHO_KEY, who);
    req.putParcelable(MESSENGER_KEY, messenger);
    preq.writeBundle(req);

    try {
      serviceConnection.transact(ServiceImpl.ACTION.CONNECT.value(), preq, pres, 0);
    } catch (RemoteException e) {
      e.printStackTrace();
    }

    if(pres == null) return;

    Bundle res = pres.readBundle();

    if(res == null) return;

    String who = res.getString(WHO_KEY);
    Messenger mess = res.getParcelable(MESSENGER_KEY);

    if(mess != null && mess.getBinder().pingBinder()){
      serviceMessenger = mess;
      cb.onConnect();
    }
  }

  public boolean send(Bundle what){
    boolean rc = false;
    if(serviceMessenger == null) return rc;

    Message msg = Message.obtain();
    msg.setData(what);
    msg.replyTo = messenger;
    try {
      serviceMessenger.send(msg);
      rc = true;
    } catch (RemoteException e) {
      e.printStackTrace();
    }
    return rc;
  }

  public boolean isAlive() {
    return serviceMessenger != null && serviceMessenger.getBinder().pingBinder();
  }
}
