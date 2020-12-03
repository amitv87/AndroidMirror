package com.boggyb.androidmirror.net;

import android.util.Log;

import org.apache.http.conn.util.InetAddressUtils;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WifiUtil {
  private static final String TAG = WifiUtil.class.getCanonicalName();

  public static List<String> GetIPAddresses() {
    ArrayList<String> list = new ArrayList<>();
    try {
      List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
      for (NetworkInterface intf : interfaces) {
        List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
        for (InetAddress addr : addrs) {
          String ip = addr.getHostAddress();
          if(InetAddressUtils.isIPv4Address(ip)){
            list.add(ip);
            Log.d(TAG, "if: " + intf.getName() + ", ip: " + ip);
          }
        }
      }
    } catch (Exception e) {
      Log.e(TAG, "scanIPAddresses", e);
    }
    return list;
  }
}
