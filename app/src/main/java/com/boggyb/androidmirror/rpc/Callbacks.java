package com.boggyb.androidmirror.rpc;

import android.os.Bundle;

/**
 * Created by amitverma on 14/11/17.
 */

public class Callbacks {
  public interface Service {
    void onConnect(String who);
    void onDisconnect(String who);
    void onMessage(String who, Bundle what);
  }

  public interface ServiceClient{
    void onConnect();
    void onDisconnect();
    void onMessage(Bundle what);
  }
}
