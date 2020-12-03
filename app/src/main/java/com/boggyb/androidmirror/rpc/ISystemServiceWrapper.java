package com.boggyb.androidmirror.rpc;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;

/**
 * Created by amitverma on 11/11/17.
 */

interface ISystemServiceWrapper extends IInterface {
  abstract class Stub extends Binder implements ISystemServiceWrapper {
    public IBinder asBinder() {
      return this;
    }
  }
}
