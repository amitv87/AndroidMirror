package com.boggyb.androidmirror.rpc;

import java.util.Set;
import android.os.Bundle;
import android.os.ServiceManager;


/**
 * Created by amitverma on 11/11/17.
 */

public class SystemService {
  final String serviceName;
  final ServiceImpl sw;

  public SystemService(String serviceName, Callbacks.Service cb, Set<String> whos){
    this.serviceName = serviceName;
    this.sw = new ServiceImpl(serviceName, cb, whos);
    ServiceManager.addService(serviceName, sw);
  }

  public boolean send(String who, Bundle what){
    return sw.send(who, what);
  }
}