package com.boggyb.androidmirror;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.boggyb.androidmirror.media.AudioCapture;
import com.boggyb.androidmirror.media.MediaCapture;
import com.boggyb.androidmirror.media.ScreenCapture;
import com.boggyb.androidmirror.net.ADBShellSession;
import com.boggyb.androidmirror.net.DataTransport;
import com.boggyb.androidmirror.net.P2PTransport;
import com.boggyb.androidmirror.net.RemotePortForwarder;
import com.boggyb.androidmirror.net.WSTransport;
import com.boggyb.androidmirror.rpc.AppService;
import com.boggyb.androidmirror.util.Conf;
import com.boggyb.androidmirror.util.Misc;
import com.boggyb.androidmirror.util.RotationManager;

import org.json.JSONObject;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AMService extends AppService implements RotationManager.Callback, DataTransport.Callback {
  private static final String TAG = AMService.class.getCanonicalName();

  private static final String kActionClip = "clip";
  private static final String kActionConfig = "config";
  private static final String kActionImStatus = "im_status";
  private static final String kActionRotation = "rotation";
  private static final String kActionVideoBitrate = "vid_bitrate";

  static final String DATA_KEY = "data";
  static final String ACTION_KEY = "action";
  static final String ACTION_STOP_CAPTURE = "stop_capture";
  static final String ACTION_START_CAPTURE = "start_capture";
  static final String ACTION_GET_CAPTURE_STATE = "get_capture";
  static final String ACTION_GET_CAPTURE_STATE_RES = "get_capture_res";
  static final String ACTION_CONN_STATUS = "conn_status";

  static final String MP_CONF = "mpconf";
  static final String MP_INTENT = "mpint";
  static final String MP_RESULT_CODE = "mprc";
  static final String MP_WEB_HOST = "web_host";
  static final String MP_SEC_TOKEN = "sec_token";
  static final String MP_CONN = "mpconns";

  static final String kSecTokenKeyName = "token";

  static final int kAdbPort = 5555;
  static final int kHTTPServerPort = 5050;
  static final String kLoopbackAddr = "127.0.0.1";

  private Conf conf = new Conf();
  private boolean isIMReady = false;
  private int prevRotation = 0;
  private Bundle mBundle = null;
  private Handler hth = null;
  private HandlerThread ht = null;
  private MediaProjection mMediaProjection = null;

  private String webHost = null;
  private String secToken = null;
  private ClipboardManager clipboard;

  private int audioBitrateKbps = 128;
  private int videoBitrateKbps = 4 * 1024;

  private Display display = null;

  private final ADBShellSession adb = new ADBShellSession(this, kLoopbackAddr, kAdbPort, new ADBShellSession.Callback() {
    @Override
    public void onAdbOpen() {
      isIMReady = true;
      broadcast(kActionImStatus, true, null);
      Log.d(TAG, "onAdbOpen");
    }

    @Override
    public void onAdbClose() {
      isIMReady = false;
      broadcast(kActionImStatus, false, null);
      Log.d(TAG, "onAdbClose: " + adb.getState());

      hth.postDelayed(() -> {
        if(mMediaProjection != null) adb.connect(getIMCmd());
      }, 2000);
    }

    @Override
    public void onAdbData(byte[] bytes) {
      try {
        Log.d(TAG, "onAdbData: " + new String( bytes, StandardCharsets.US_ASCII));
      } catch (Exception e) {
        Log.d(TAG, "onAdbData error", e);
      }
    }
  });

  @Override
  public void onCreate(){
    super.onCreate();
    Log.d(TAG, "onCreate");
    if(ht == null) {
      ht = new HandlerThread("capture thread", android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
      ht.setPriority(Thread.MAX_PRIORITY);
      ht.start();
      hth = new Handler(ht.getLooper());
    }
    /*
    RotationManager.Init(this);
    RotationManager.addRotationWatcher(this, hth);
    */
    getIMCmd();

    display = ((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay();
    clipboard = (ClipboardManager) this.getSystemService(Context.CLIPBOARD_SERVICE);
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig){
    super.onConfigurationChanged(newConfig);
    onRotation(getRotation());
  }

  @Override
  public void onDestroy(){
    Log.d(TAG, "onDestroy");
    /*
    RotationManager.DeInit();
    */
    stopCapture();
    adb.close();
    super.onDestroy();
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
     Log.d(TAG, "AMService onStartCommand " + intent);
     String action;
     if(intent != null && (action = intent.getAction()) != null){
       if(action.equals(ACTION_STOP_CAPTURE)){
         hth.post(() -> {
           stopCapture();
           sendCaptureState(null);
         });
       }
     }
    return Service.START_NOT_STICKY;
  }

  @Override
  public String getName() {
    return "AMService";
  }

  @Override
  public void onConnect(String who) {
    Log.d(TAG, "onConnect: " + who);
  }

  @Override
  public void onDisconnect(String who) {
    Log.d(TAG, "onDisconnect: " + who);
  }

  @Override
  public void onMessage(final String who, final Bundle what){
    if(what == null) return;

    String action = what.getString(ACTION_KEY);

    Log.d(TAG, "onMessage who: " + who + ", action: " + action);

    if(action == null) return;

    hth.post(() -> {
      switch (action) {
        case ACTION_GET_CAPTURE_STATE:
          break;
        case ACTION_START_CAPTURE:
          mBundle = what.getBundle(DATA_KEY);
          startCapture();
          break;
        case ACTION_STOP_CAPTURE:
          stopCapture();
          break;
      }
      sendCaptureState(who);
    });
  }

  private String getIMCmd(){
    String classPath = getApplicationInfo().sourceDir;

    String cmd = " CLASSPATH=" + classPath +
      " app_process /system/bin " + InputManager.class.getCanonicalName() + "\n";

    Log.d(TAG, "cmd: " + cmd);
    return cmd;
  }

  private MediaProjection.Callback mediaProjectionCB = new MediaProjection.Callback() {
    @Override
    public void onStop(){
      Log.d(TAG, "mediaProjectionCB onStop");
      stopCapture();
      sendCaptureState(null);
    }
  };

  MediaCapture.Callback acb = new MediaCapture.Callback(){
    @Override
    public void onEncodedFrame(byte[] bytes, int flags){
      if(flags != 2) audioChannels.broadcast(bytes);
      if(flags > 0)
      Log.d(TAG, "on audio bytes: " + bytes.length + ", flags: " + flags);
    }
  };

  MediaCapture.Callback vcb = new MediaCapture.Callback(){
    @Override
    public void onEncodedFrame(byte[] bytes, int flags){
      if(flags == 2) videoChannels.storeConfFrames(bytes);
      videoChannels.broadcast(bytes);
      if(flags > 0)
      Log.d(TAG, "on video bytes: " + bytes.length + ", flags: " + flags);
    }
  };

  MediaCapture ac = null, vc = null;

  private void stopCapture(){
    Log.d(TAG, "stopCapture beg");

    hth.removeCallbacksAndMessages(null);

    if(ac != null) ac.stopCapture(); ac = null;
    if(vc != null) vc.stopCapture(); vc = null;

    if(mMediaProjection != null) mMediaProjection.unregisterCallback(mediaProjectionCB);
    if(mMediaProjection != null) mMediaProjection.stop();
    mMediaProjection = null;

    stopTransports();
    adb.close();
    isIMReady = false;

    Log.d(TAG, "stopCapture end");
    stopForeground(true);
  }

  private int getRotation(){
    return
      display.getRotation()
      // RotationManager.getRotation()
    ;
  }

  private void startCapture(){
    stopCapture();
    if (mBundle == null) return;

    mBundle.setClassLoader(Conf.class.getClassLoader());

    conf = mBundle.getParcelable(MP_CONF);
    Intent data = mBundle.getParcelable(MP_INTENT);
    secToken = mBundle.getString(MP_SEC_TOKEN, null);
    int rc = mBundle.getInt(MP_RESULT_CODE, Activity.RESULT_CANCELED);
    Log.d(TAG, "startCaptrue rc: " + rc + ", intent: " + data + ", conf: " + conf);

    if (data == null || rc == Activity.RESULT_CANCELED) return;

    display = ((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay();

    startForeground();

    MediaProjectionManager mMediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
    assert mMediaProjectionManager != null;

    mMediaProjection = mMediaProjectionManager.getMediaProjection(rc, data);

    if (mMediaProjection == null) {
      stopCapture();
      return;
    }

    mMediaProjection.registerCallback(mediaProjectionCB, hth);

    prevRotation = getRotation();

    if(conf.isAudioEnabled){
      try {
        ac = AudioCapture.createInstance(mMediaProjection, audioBitrateKbps);
        ac.startCapture(acb, hth);
      } catch (Throwable e) {
        Log.e(TAG, "AudioCapture error", e);
      }
    }

    try {
      vc = ScreenCapture.createInstance(mMediaProjection, this, videoBitrateKbps);
      vc.startCapture(vcb, hth);
    } catch (Exception e) {
      Log.e(TAG, "ScreenCapture error", e);
    }

    if(conf.isInputEnabled){
      if(conf.useADB) adb.connect(getIMCmd());
      else isIMReady = true;
    }

    startTransports();
  }

  private void sendCaptureState(String who){
    Bundle bundle = new Bundle();
    bundle.putString(ACTION_KEY, ACTION_GET_CAPTURE_STATE_RES);
    bundle.putBoolean(DATA_KEY, mMediaProjection != null);
    if(conf != null) bundle.putParcelable(MP_CONF, conf);
    if(webHost != null) bundle.putString(MP_WEB_HOST, webHost);
    bundle.putInt(MP_CONN, videoChannels.getConnCount());
    send(who != null ? who : MainActivity.WHO, bundle);
  }

  private void startForeground(){
    Notification.Builder builder = new Notification.Builder(this);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      String channel_id = "am_service";
      NotificationChannel chan = new NotificationChannel(channel_id, channel_id, NotificationManager.IMPORTANCE_NONE);
      chan.setShowBadge(false);
      chan.setSound(null, null);
      chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
      NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
      assert manager != null;
      manager.createNotificationChannel(chan);
      builder.setChannelId(channel_id);
    }

    builder
      .setOngoing(true)
      .setSound(null)
      .setGroupSummary(true)
      .setContentTitle("Casting...")
      .setPriority(Notification.PRIORITY_LOW)
      .setCategory(Notification.CATEGORY_SERVICE)
      .setVisibility(Notification.VISIBILITY_PUBLIC)
      .setSmallIcon(R.drawable.ic_phonelink_white_24dp)
      .setContentIntent(PendingIntent.getActivity(this, (int) System.currentTimeMillis(),
        new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE))
    ;

    startForeground(1000, builder.build());
  }

  public void sendConf(DataTransport.Channel channel){
    try{
      DisplayMetrics metrics = new DisplayMetrics();
      WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
      wm.getDefaultDisplay().getRealMetrics(metrics);

      broadcast(kActionConfig, new JSONObject(){{
        put("platform", "android");
        put("api_level", android.os.Build.VERSION.SDK_INT);
        put("width", metrics.widthPixels / ScreenCapture.kScaleFactor);
        put("height", metrics.heightPixels / ScreenCapture.kScaleFactor);
        put("d_width", metrics.widthPixels);
        put("d_height", metrics.heightPixels);
        put(kActionRotation, getRotation());
        put(kActionImStatus, isIMReady);
        put(kActionVideoBitrate, videoBitrateKbps);
        put("caps", new JSONObject(){{
          put("audio", conf.isAudioEnabled);
          put("input", conf.isInputEnabled);
          put("web", conf.isWebAccessEnabled);
          put("secure", conf.isSecure);
          put("p2p", Misc.isP2PEanbled());
        }});
      }}, channel);
    } catch (Exception e){
      Log.e(TAG, "sendConf", e);
    }
  }

  public void rotate(int rotation){
    ContentResolver cr = getContentResolver();
    Settings.System.putInt(cr, "accelerometer_rotation", 0);
    Settings.System.putInt(cr, "user_rotation", rotation);
  }

  @Override
  public void onRotation(int rotation){
    int diff = Math.abs(prevRotation - rotation);
    prevRotation = rotation;
    if(diff % 2 == 0){
      broadcast(kActionRotation, rotation, null);
      return;
    }
    sendConf(null);
    if(mMediaProjection != null){
      if(vc != null) vc.stopCapture(); vc = null;
      try {
        vc = ScreenCapture.createInstance(mMediaProjection, this, videoBitrateKbps);
        vc.startCapture(vcb, hth);
      } catch (Exception e) {
        Log.e(TAG, "onOrientation", e);
      }
    }
  }

  void getClipPostPie(DataTransport.Channel channel){
    try {
      WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
      WindowManager.LayoutParams params = new WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        PixelFormat.TRANSLUCENT);
      FrameLayout fl = new FrameLayout(AMService.this);
      windowManager.addView(fl, params);
      try {
        getClip(channel, false);
      }
      catch (Exception e){
        Log.e(TAG, "getClipPostPie err1", e);
      }
      windowManager.removeView(fl);
    }
    catch (Exception e){
      Log.e(TAG, "getClipPostPie err2", e);
    }
  }

  void getClip(final DataTransport.Channel channel, boolean checkAndroidVersion) throws Exception{
    if (checkAndroidVersion && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
      hth.post(() -> getClipPostPie(channel));
      return;
    }

    CharSequence seq = clipboard.getText();
    Log.d(TAG, "getClip: " + seq);
    broadcast(kActionClip, seq != null ? seq.toString() : "", channel);
  }

  void setClip(String txt){
    Log.d(TAG, "setClip: " + txt);
    clipboard.setText(txt);
  }

  private static final String kVideoChannelName = "/video";
  private static final String kAudioChannelName = "/audio";
  private static final String kInputChannelName = "/input";
  private static final String kSignalingChannelName = "/signaling";

  private RemotePortForwarder rp;
  private WSTransport wsTransport;
  private P2PTransport p2pTransport;

  private void sendConnStatus(String who){
    Bundle bundle = new Bundle();
    bundle.putString(ACTION_KEY, ACTION_CONN_STATUS);
    bundle.putInt(DATA_KEY, videoChannels.getConnCount());
    send(who != null ? who : MainActivity.WHO, bundle);
  }

  void stopTransports(){
    if(wsTransport != null){
      wsTransport.stop();
      wsTransport = null;
    }

    if(p2pTransport != null){
      p2pTransport.stop();
      p2pTransport = null;
    }

    inputChannels.clean();
    audioChannels.clean();
    videoChannels.clean();
    signalingChannels.clean();

    webHost = null;
    if(rp != null) rp.disconnect();
  }

  void startTransports(){
//    System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "TRACE");

    stopTransports();

    wsTransport = new WSTransport(hth, this, null, kHTTPServerPort, getAssets());
    wsTransport.start();

    if(Misc.isP2PEanbled()) {
      P2PTransport.Init(this);
      p2pTransport = new P2PTransport(hth, this, (peerId, action, payload) -> {
        String data = new JSONObject() {{
          try {
            put("a", action);
            put("p", payload);
            ;
          } catch (Exception ignored) {
          }
        }}.toString();

//      Log.d(TAG, "p2pTransport on signaling msg" + peerId + " " + data);

        if (peerId > 0) signalingChannels.sendTo(peerId, data);
        else signalingChannels.broadcast(data);
      });
      p2pTransport.start();
    }

    if(conf.isWebAccessEnabled){
      if(rp == null) rp = new RemotePortForwarder(hth, rpfcb);
      rp.connect("boggy", "pass", "serveo.net", 22, "localhost", new ArrayList<int[]>(){{
        add(new int[]{80, 5050});
      }});
    }
  }

  @Override
  public void onStart(DataTransport transport, boolean success) {
    Log.d(TAG, transport.getClass().getSimpleName() + " onStart: " + success);
  }

  @Override
  public void onChannel(DataTransport transport, DataTransport.Channel channel, String label){
    Log.d(TAG, transport.getClass().getSimpleName() + " onChannel: " + label);
    if(transport instanceof WSTransport){
      try {
        URL url = new URL("http://test.com" + label);
        if(conf.isSecure && secToken != null && !secToken.isEmpty()){
          Map<String, List<String>> qsMap = Misc.getQueryMap(url);
          String token = null;
          if(qsMap.containsKey(kSecTokenKeyName)) token = qsMap.get(kSecTokenKeyName).get(0);
          if(token == null || !token.equals(secToken)) throw new Exception("invalid token");
        }
        label = url.getPath();
      } catch (Exception e) {
        Log.i(TAG, "onChannel err: " + e.getMessage());
        channel.close();
        return;
      }
    }
    if(label.equals(videoChannels.name)) videoChannels.add(channel);
    else if(label.equals(inputChannels.name)) inputChannels.add(channel);
    else if(label.equals(audioChannels.name)) audioChannels.add(channel);
    else if(label.equals(signalingChannels.name) && transport instanceof WSTransport) signalingChannels.add(channel);
    else channel.close();
  }

  private abstract static class Channels implements DataTransport.Receiver {
    final String name;
    private byte[] conf = null;
    private ArrayList<DataTransport.Channel> channels = new ArrayList<>();

    Channels(String name){
      this.name = name;
    }

    void storeConfFrames(byte[] conf){
      this.conf = conf;
    }

    void clean(){
      conf = null;
      for(DataTransport.Channel channel : channels) channel.setReceiver(null);
      channels.clear();
    }

    void add(DataTransport.Channel channel){
      channels.add(channel);
      channel.setReceiver(this);
    }

    void remove(DataTransport.Channel channel){
      channel.setReceiver(null);
      channels.remove(channel);
    }

    void broadcast(String message){
      for(DataTransport.Channel channel : channels) channel.send(message);
    }

    void broadcast(byte[] message){
      for(DataTransport.Channel channel : channels) channel.send(message);
    }

    int getConnCount(){
      return channels.size();
    }

    @Override
    public void onOpen(DataTransport.Channel channel){
      if(this.conf != null) channel.send(conf);
    }

    @Override
    public void onClose(DataTransport.Channel channel){
      remove(channel);
    }

    @Override
    public void onMessage(DataTransport.Channel channel, byte[] data){
      Log.d(TAG, name + " onMessage: " + data);
    }
  }

  private Channels inputChannels = new Channels(kInputChannelName){
    @Override
    public void onMessage(DataTransport.Channel channel, String message) {
//      Log.d(TAG, name + " onMessage: " + message + ", isIMReady: " + isIMReady);
      if(!isIMReady) return;
      if(conf.useADB) adb.write(message + "\n");
      else AccessibilityService.Process(AMService.this, message);
    }
  };

  private Channels audioChannels = new Channels(kAudioChannelName){
    @Override
    public void onMessage(DataTransport.Channel channel, String message) {
      Log.d(TAG, name + " onMessage: " + message);
    }
  };

  private Channels videoChannels = new Channels(kVideoChannelName){
    @Override
    public void onOpen(DataTransport.Channel channel){
      sendConf(channel);
      if(vc != null) vc.requestKeyFrame();
      super.onOpen(channel);
      sendConnStatus(null);
    }

    @Override
    public void onMessage(DataTransport.Channel channel, String message) {
      Log.d(TAG, name + " onMessage: " + message);
      try{
        JSONObject obj = new JSONObject(message);
        String action = obj.optString("a", "");
        if(!action.isEmpty()) {
          switch (action) {
            case "req_key_frame":
              if(vc != null) vc.requestKeyFrame();
              break;
            case "get_clip":
              if(conf.isInputEnabled) getClip(channel, true);
              break;
            case "set_clip":
              if(conf.isInputEnabled) setClip(obj.optString("d", ""));
              break;
            case "vid_bitrate":
              if (vc != null){
                int bitRate = obj.optInt("d", 0);
                if(bitRate > 0){
                  vc.setBitrate(obj.optInt("d", 512));
                  videoBitrateKbps = vc.getBitrate();
                }
              }
              AMService.this.broadcast(kActionVideoBitrate, videoBitrateKbps, null);
              break;
          }
        }
      }
      catch (Exception e){
        Log.e(TAG, "vwscb onMessage err", e);
      }
    }

    @Override
    public void onClose(DataTransport.Channel channel){
      super.onClose(channel);
      sendConnStatus(null);
    }
  };

  private abstract static class SignalingChannels implements DataTransport.Receiver {
    final String name;
    int id = 0;
    HashMap<Integer, DataTransport.Channel> channelMap = new HashMap<>();
    SignalingChannels(String name) {
      this.name = name;
    }

    void clean(){
      for(DataTransport.Channel channel : channelMap.values()) channel.setReceiver(null);
      channelMap.clear();
    }

    void add(DataTransport.Channel channel){
      channel.id = ++id;
      channelMap.put(channel.id, channel);
      channel.setReceiver(this);
    }

    void sendTo(int id, String message){
      DataTransport.Channel channel = channelMap.get(id);
      if(channel != null) channel.send(message);
    }

    void broadcast(String message){
      for(DataTransport.Channel channel : channelMap.values()) channel.send(message);
    }

    @Override
    public void onMessage(DataTransport.Channel channel, byte[] data) {}

    @Override
    public void onOpen(DataTransport.Channel channel){
      Log.d(TAG, "signalingChannel onOpen");
    }

    @Override
    public void onClose(DataTransport.Channel channel){
      Log.d(TAG, "signalingChannel onOpen");
      channel.setReceiver(null);
      channelMap.remove(channel.id);
    }
  }

  private SignalingChannels signalingChannels = new SignalingChannels(kSignalingChannelName){
    @Override
    public void onMessage(DataTransport.Channel channel, String message) {
      if(p2pTransport == null) return;
//      Log.d(TAG, this.name + " onMessage: " + message);
      try {
        JSONObject jsonObj = new JSONObject(message);
        p2pTransport.onSignalingMessage(channel.id, jsonObj.optString("a", ""), jsonObj.optJSONObject("p"));
      } catch (Exception e) {
        Log.e(TAG, "signalingChannel onMessage err", e);
      }
    }
  };

  private RemotePortForwarder.Callback rpfcb = new RemotePortForwarder.Callback() {
    @Override
    public void onSessionOpen() {
      Log.d(TAG, "onSessionOpen");
    }

    @Override
    public void onSessionClose() {
      webHost = null;
      sendCaptureState(null);
      Log.d(TAG, "onSessionClose");
    }

    @Override
    public void onForwardedPortHost(int lport, String host) {
      webHost = host;
      sendCaptureState(null);
      Log.d(TAG, "onForwardedPortHost " + lport + " --> " + host);
    }
  };

  private void broadcast(String action, Object data, DataTransport.Channel channel){
    try {
      JSONObject json = new JSONObject();
      json.put("a", action);
      json.put("d", data);
      if(channel != null) channel.send(json.toString());
      else videoChannels.broadcast(json.toString());
    } catch (Exception e){
      Log.e(TAG, "broadcast", e);
    }
  }
}
