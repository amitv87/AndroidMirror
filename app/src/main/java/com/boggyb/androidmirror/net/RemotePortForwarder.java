package com.boggyb.androidmirror.net;

import android.os.Handler;
import android.util.Log;

import com.boggyb.androidmirror.BuildConfig;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

public class RemotePortForwarder{
  private static final String TAG = RemotePortForwarder.class.getCanonicalName();

  public interface Callback{
    void onSessionOpen();
    void onSessionClose();
    void onForwardedPortHost(int lport, String host);
  }

  private Session session = null;
  private final JSch jsch;
  private final Handler handler;
  private final Callback callback;

  public RemotePortForwarder(Handler handler, Callback callback){
    this.handler = handler;
    this.callback = callback;
    this.jsch = new JSch();
  }

  public void connect(String user, String pass, String remoteHost, int remotePort, String localHost, List<int[]> portMappingList){
    if(session != null) return;
    new Thread(()->{
      try {
        session = jsch.getSession(user, remoteHost, remotePort);
        session.setPassword(pass);
        session.setConfig("TCPKeepAlive", "yes");
        session.setConfig("ServerAliveInterval", "10");
        session.setConfig("StrictHostKeyChecking", "no");
        session.setConfig("UserKnownHostsFile", "/dev/null");
        session.connect();

        Channel channel = session.openChannel("shell");
        channel.setOutputStream(new OutputStream() {
          int count = 0;
          StringBuilder builder = new StringBuilder();

          @Override
          public void write(int b) {
            if (!BuildConfig.DEBUG && count >= portMappingList.size()) return;
            if (b == '\n' && builder.length() > 0) {
              String line = builder.toString().replaceAll("\u001B\\[[;\\d]*m", "").trim();
              if(count <= portMappingList.size()) {
                String[] arr = line.split("Forwarding HTTP traffic from");
                if (arr.length == 2) {
                  int idx = count;
                  count += 1;
                  handler.post(() -> callback.onForwardedPortHost(portMappingList.get(idx)[1], arr[1].trim()));
                } else Log.d(TAG, "remote host: " + Arrays.toString(arr));
              }
              else Log.d(TAG, line);
              builder.setLength(0);
            } else builder.append((char) b);
          }
        });

        channel.connect();
        handler.post(callback::onSessionOpen);
        for(int[] ports : portMappingList) session.setPortForwardingR(ports[0], localHost, ports[1]);
      }
      catch (Exception e1){
        Log.e(TAG, "connect err", e1);
        if(disconnect()) handler.post(callback::onSessionClose);
      }
    }).start();
  }

  public boolean disconnect(){
    boolean rc = true;
    try {
      rc = session != null;
      if (session != null && session.isConnected()) session.disconnect();
      session = null;
    }
    catch (Exception e2){
      Log.e(TAG, "disconnect err", e2);
    }
    return rc;
  }
}
