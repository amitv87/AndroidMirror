package com.boggyb.androidmirror.net;

import android.content.Context;
import android.util.Log;

import com.cgutman.adblib.AdbBase64;
import com.cgutman.adblib.AdbConnection;
import com.cgutman.adblib.AdbCrypto;
import com.cgutman.adblib.AdbStream;

import java.io.File;
import java.net.Socket;

public class ADBShellSession {
  private static final String TAG = ADBShellSession.class.getCanonicalName();
  private final String ip;
  private final int port;
  private final Callback cb;
  private final Context context;

  private Thread th;
  private Socket sock;
  private State state;
  private AdbCrypto crypto;
  private AdbStream stream;
  private AdbConnection adb;
  private boolean isClosing = false;

  public enum State{
    ADB_NEW,
    ADB_READY,
    ADB_CRYPTO_FAIL,
    ADB_SOCKET_FAIL,
    ADB_SETUP_FAIL,
    ADB_STREAM_FAIL,
    ADB_CLOSE,
  }

  public interface Callback{
    void onAdbOpen();
    void onAdbClose();
    void onAdbData(byte[] bytes);
  }

  public ADBShellSession(Context context, String ip, int port, Callback cb){
    this.cb = cb;
    this.ip = ip;
    this.port = port;
    this.context = context;
    this.state = State.ADB_NEW;
  }

  public void connect(final String cmd){
    synchronized (this) {
      if (this.crypto != null) return;
      isClosing = false;

      Log.d(TAG, "setupCrypto");
      try {
        String dataDir = context.getApplicationInfo().dataDir;
        crypto = setupCrypto(dataDir + "/pub.key", dataDir + "/priv.key");
      } catch (Exception e) {
        Log.e(TAG, "setupCrypto error", e);
        clean();
        state = State.ADB_CRYPTO_FAIL;
        cb.onAdbClose();
        return;
      }

      Log.d(TAG, "Thread");
      th = new Thread(new Runnable() {
        @Override
        public void run() {

          Log.d(TAG, "Socket");
          try {
            sock = new Socket(ip, port);
          } catch (Exception e) {
//            Log.e(TAG, "sock create error", e);
            clean();
            state = State.ADB_SOCKET_FAIL;
            cb.onAdbClose();
            return;
          }

          Log.d(TAG, "AdbConnection.create");
          try {
            adb = AdbConnection.create(sock, crypto);
            Log.d(TAG, "adb.connect()");
            adb.connect();
          } catch (Exception e) {
            Log.e(TAG, "adb create error", e);
            try {
              sock.close();
              sock = null;
            } catch (Exception ex) {
              Log.e(TAG, "sock.close error", ex);
            }
            if(e instanceof InterruptedException) return;
            clean();
            state = State.ADB_SETUP_FAIL;
            cb.onAdbClose();
            return;
          }

          Log.d(TAG, "Aadb.open");
          try {
            stream = adb.open("shell:" + cmd);
            state = State.ADB_READY;
            cb.onAdbOpen();
          } catch (Exception e) {
//            Log.e(TAG, "adb open", e);
            stream = null;
            isClosing = true;
            state = State.ADB_STREAM_FAIL;
            cb.onAdbClose();
          }

          Log.d(TAG, "while !stream.isClosed()");
          while (stream != null && !stream.isClosed()) {
            try {
              byte[] data = stream.read();
              if (data.length > 0) cb.onAdbData(data);
            } catch (Exception e){}
          }

          Log.d(TAG, "adb.close()");
          try {
            adb.close();
          } catch (Exception e) {
            Log.d(TAG, "adb close error", e);
          }


          if(isClosing) th = null;
          state = State.ADB_CLOSE;
          clean();
          cb.onAdbClose();
          state = State.ADB_NEW;
        }
      });
      th.start();
    }
  }

  private void clean(){
    th = null;
    adb = null;
    sock = null;
    stream = null;
    crypto = null;
  }

  public void close(){
    synchronized (this) {
      if (this.crypto == null) return;
      isClosing = true;

      Log.d(TAG, "stream.close()");
      try {
        if(stream != null) stream.close();
      } catch (Exception e) {
        Log.d(TAG, "stream close error", e);
      }
      Log.d(TAG, " th.isAlive()) th.join()");
      try {
        if(th != null && th.isAlive()){
          if(stream != null) th.join();
          else th.interrupt();
        }
      } catch (Exception e) {
        Log.d(TAG, "thread join error ", e);
      }

      Log.d(TAG, " adb.close()");
      try {
        if(adb != null) adb.close();
      } catch (Exception e) {
        Log.d(TAG, "adb close error", e);
      }

      clean();
      state = State.ADB_NEW;
    }
  }

  public void write(String msg){
    synchronized (this) {
      if (stream == null) return;
      try {
        stream.write(msg);
      } catch (Exception e) {
        Log.e(TAG, "write error", e);
      }
    }
  }

  public State getState(){
    return state;
  }

  private static AdbBase64 adbBase64  = new AdbBase64() {
    @Override
    public String encodeToString(byte[] arg0) {
      return android.util.Base64.encodeToString(arg0, android.util.Base64.NO_WRAP);
    }
  };

  private static AdbCrypto setupCrypto(String pubKeyFile, String privKeyFile) throws Exception {
    File pub = new File(pubKeyFile);
    File priv = new File(privKeyFile);
    AdbCrypto crypto = null;

    if (pub.exists() && priv.exists()) {
      try {
        crypto = AdbCrypto.loadAdbKeyPair(adbBase64, priv, pub);
      } catch (Exception ignored) {}
    }

    if (crypto == null) {
      crypto = AdbCrypto.generateAdbKeyPair(adbBase64);
      crypto.saveAdbKeyPair(priv, pub);
      Log.d(TAG, "Generated new keypair");
    }
    else {
      Log.d(TAG, "Loaded existing keypair");
    }

    return crypto;
  }
}
