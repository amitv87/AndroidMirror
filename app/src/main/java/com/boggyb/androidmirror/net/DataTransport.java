package com.boggyb.androidmirror.net;

import android.os.Handler;

public abstract class DataTransport {
  public abstract static class Channel{
    public int id = 0;
    Receiver receiver = null;
    public abstract void close();
    public abstract void send(String data);
    public abstract void send(byte[] data);
    public void setReceiver(Receiver receiver){
      this.receiver = receiver;
    }
  }

  public interface Receiver{
    void onOpen(Channel channel);
    void onClose(Channel channel);
    void onMessage(Channel channel, String data);
    void onMessage(Channel channel, byte[] data);
  }

  public interface Callback{
    void onStart(DataTransport transport, boolean success);
    void onChannel(DataTransport transport, Channel channel, String label);
  }

  public final Handler handler;
  public final Callback callback;

  DataTransport(Handler handler, Callback callback){
    this.handler = handler;
    this.callback = callback;
  }

  public abstract void start();
  public abstract void stop();
}
