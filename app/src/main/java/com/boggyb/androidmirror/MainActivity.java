package com.boggyb.androidmirror;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;
import android.provider.Settings;
import android.view.ContextThemeWrapper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.boggyb.androidmirror.net.DataTransport;
import com.boggyb.androidmirror.net.P2PTransport;
import com.boggyb.androidmirror.net.RemotePortForwarder;
import com.boggyb.androidmirror.net.WSTransport;
import com.boggyb.androidmirror.net.WifiUtil;
import com.boggyb.androidmirror.rpc.AppServiceClient;
import com.boggyb.androidmirror.rpc.Callbacks;
import com.boggyb.androidmirror.util.Conf;
import com.boggyb.androidmirror.util.Misc;
import com.boggyb.androidmirror.util.Secure;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;

import static com.boggyb.androidmirror.AMService.*;

public class MainActivity extends Activity implements Callbacks.ServiceClient{
  private static final String TAG = MainActivity.class.getCanonicalName();

  static final String WHO = "main";
  private static final int REQUEST_CODE_CAPTURE_PERM = 1;
  private static final int REQUEST_CODE_WRITE_SETT_PERM = 2;
  private static final int REQUEST_CODE_DRAW_OVERLAY_PERM = 3;
  private static final int REQUEST_CODE_ACCESSIBILITY_PERM = 4;

  private static Bundle dataBundle = null;

  private TextView txtConns = null;
  private AppServiceClient appServiceClient = null;
  private CompoundButton toggleButton, toggleSwitch;
  private LinearLayout hostList, optOverlay;
  private boolean prevChecked = false;

  private
  LinearLayout
  // com.google.android.flexbox.FlexboxLayout
  optList;

  private static final String kDevPrefs = "devPref";
  private static final String kSecToken = "secToken";

  private static final String kWebControlTitle = "Web";
  private static final String kADBControlTitle = "ADB";
  private static final String kAudioControlTitle = "Audio";
  private static final String kInputControlTitle = "Input";
  private static final String kSecureControlTitle = "Secure";

  private final HashMap<String, ToggleButton> ctrlMap = new HashMap<>();

  private String webHost = null;
  private Conf conf = new Conf();

  private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();
      Log.d(TAG, "mReceiver: " + action);
      hostList.removeCallbacks(null);
      hostList.post(MainActivity.this::populateHosts);
    }
  };

  @Override
  public void onRequestPermissionsResult (int requestCode, String[] permissions, int[] grantResults){
    boolean should_start = false;
    for(int  i = 0; i < permissions.length; i ++){
      String perm = permissions[i];
      int result = grantResults[i];
      Log.i(TAG, "onRequestPermissionsResult " + perm + " " + result);
      switch(perm){
        case Manifest.permission.RECORD_AUDIO:
          should_start = result == PackageManager.PERMISSION_GRANTED;
          if(!should_start && !shouldShowRequestPermissionRationale(perm)) Misc.showToast("RECORD_AUDIO permission denied", this);
          break;
      }
    }

    setToggleControl(should_start);
  }

  private void startActivityForResult(String action, int code, boolean uri){
    startActivityForResult(new Intent(action){{
      if(uri) setData(Uri.parse("package:" + getPackageName()));
    }}, code);
    setToggleControl(false);
  }

  private final CompoundButton.OnCheckedChangeListener toggleListener = (buttonView, isChecked) -> {
    if(prevChecked == isChecked) return;
    Bundle bundle = new Bundle();
    if(isChecked){

      if(conf.isInputEnabled){
        if(!conf.useADB){
          if(!Settings.System.canWrite(this)){
            startActivityForResult(Settings.ACTION_MANAGE_WRITE_SETTINGS, REQUEST_CODE_WRITE_SETT_PERM, true); return;
          }
          if(!isAccessibilityServiceEnabled()){
            startActivityForResult(Settings.ACTION_ACCESSIBILITY_SETTINGS, REQUEST_CODE_ACCESSIBILITY_PERM, false); return;
          }
        }
        if(!Settings.canDrawOverlays(this)){
          startActivityForResult(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, REQUEST_CODE_DRAW_OVERLAY_PERM, true); return;
        }
      }

      ArrayList<String> permissions = new ArrayList<String>();

      if(conf.isAudioEnabled && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED){
        permissions.add(Manifest.permission.RECORD_AUDIO);
      }

      if(permissions.size() > 0){
        requestPermissions(permissions.toArray(new String[permissions.size()]), 3);
        setToggleControl(false); return;
      }

      if(dataBundle == null){
        MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        assert mediaProjectionManager != null;
        Intent permissionIntent = mediaProjectionManager.createScreenCaptureIntent();
        startActivityForResult(permissionIntent, REQUEST_CODE_CAPTURE_PERM);
        setToggleControl(false); return;
      }

      dataBundle.putParcelable(MP_CONF, conf);
      dataBundle.putString(MP_SEC_TOKEN, conf.isSecure ? getSecToken() : null);
      bundle.putString(ACTION_KEY, ACTION_START_CAPTURE);
      bundle.putParcelable(DATA_KEY, dataBundle);
    }
    else bundle.putString(ACTION_KEY, ACTION_STOP_CAPTURE);

    prevChecked = isChecked;
    if (!appServiceClient.send(bundle)) Log.e(TAG, "send onCheckedChanged error");
//    else if(isChecked) finish();
  };

  private static void initToggleControls(CompoundButton control, CompoundButton.OnCheckedChangeListener toggleListener){
    control.setChecked(false);
    control.setVisibility(View.GONE);
    control.setOnCheckedChangeListener(toggleListener);
  }

  private static void toggleControl(CompoundButton control, boolean enable){
    control.setChecked(enable);
    control.setVisibility(View.VISIBLE);
  }

  private void setToggleControl(boolean enable){
    toggleControl(toggleSwitch, enable);
    toggleControl(toggleButton, enable);
    enableOptions(!enable);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState){
    Log.d(TAG, "onCreate");
    super.onCreate(savedInstanceState);
    StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().permitAll().build());

//    debugSSH();
//    debugWS();
//    debugP2P();
//    if(debugTouchEvents());
//    return;

    setContentView(R.layout.main_activity_layout);

    txtConns = findViewById(R.id.txtConns);

    txtConns.setVisibility(View.GONE);

    optList = findViewById(R.id.optList);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
      addOption(kAudioControlTitle, conf.isAudioEnabled, (buttonView, isChecked) -> conf.isAudioEnabled = isChecked);
    addOption(kADBControlTitle, conf.useADB, (buttonView, isChecked) -> conf.useADB = isChecked);
    addOption(kInputControlTitle, conf.isInputEnabled, (buttonView, isChecked) -> conf.isInputEnabled = isChecked);
    addOption(kWebControlTitle, conf.isWebAccessEnabled, (buttonView, isChecked) -> conf.isWebAccessEnabled = isChecked);
    addOption(kSecureControlTitle, conf.isSecure, (buttonView, isChecked) -> conf.isSecure = isChecked).setOnLongClickListener(v -> {
      if(conf.isSecure) {
        getPrefs().edit().remove(kSecToken).apply();
        Misc.showToast("new token generated", this);
      }
      return true;
    });

    optOverlay = findViewById(R.id.optOverlay);
    optOverlay.setOnTouchListener((v, event) -> true);
//    optOverlay.setLayoutParams();
    enableOptions(false);

    hostList = findViewById(R.id.hostList);
    populateHosts();

    initToggleControls(toggleSwitch = findViewById(R.id.toggleSwitch), toggleListener);
    initToggleControls(toggleButton = findViewById(R.id.toggleButton), toggleListener);

    appServiceClient = new AppServiceClient(WHO, this, new ComponentName(this, AMService.class), this);

    IntentFilter filter = new IntentFilter();
    filter.addAction("android.net.wifi.WIFI_AP_STATE_CHANGED");
    filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
    registerReceiver(mReceiver, filter);
  }

  private boolean isAccessibilityServiceEnabled() {
    Context context = getApplicationContext();
    String enabledList = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
    return enabledList != null && enabledList.contains(new ComponentName(context, AccessibilityService.class).flattenToString());
  }

  SharedPreferences getPrefs(){
    return getSharedPreferences(kDevPrefs, MODE_PRIVATE);
  }

  private String getSecToken(){
    String token = getPrefs().getString(kSecToken, null);
    if(token == null){
      token = Secure.getRandomString(64);
      getPrefs().edit().putString(kSecToken, token).apply();
    }
    return token;
  }

  private ToggleButton addOption(String title, boolean checked, CompoundButton.OnCheckedChangeListener listener){
    if(ctrlMap.containsKey(title)) return null;
    ToggleButton tb = new ToggleButton(new ContextThemeWrapper(this, R.style.tb));
    tb.setTextOn(title);
    tb.setTextOff(title);
    tb.setChecked(checked);
    tb.setOnCheckedChangeListener(listener);
    optList.addView(tb);
    ctrlMap.put(title, tb);
    return tb;
  }

  private void setOption(String title, boolean checked){
    if(!ctrlMap.containsKey(title)) return;
    ctrlMap.get(title).setChecked(checked);
  }

  private void enableOptions(boolean enabled){
//    for(ToggleButton tb : ctrlMap.values()) tb.setEnabled(enabled);

    txtConns.setVisibility(enabled ? View.GONE : View.VISIBLE);
    optOverlay.setVisibility(enabled ? View.GONE : View.VISIBLE);
    optOverlay.requestLayout();
  }

  public void onActivityResult(int requestCode, int resultCode, Intent intent) {
    Log.i(TAG, "onActivityResult requestCode: " + requestCode + ", resultCode: " + resultCode + " " + intent);
    if(REQUEST_CODE_CAPTURE_PERM == requestCode && resultCode == Activity.RESULT_OK){
      dataBundle = new Bundle();
      dataBundle.putParcelable(MP_INTENT, intent);
      dataBundle.putInt(MP_RESULT_CODE, resultCode);
    }
    setToggleControl(resultCode == Activity.RESULT_OK);
  }

  @Override
  protected void onDestroy(){
    Log.d(TAG, "onDestroy");
    unregisterReceiver(mReceiver);
    appServiceClient.disconnect();
    super.onDestroy();
  }

  @Override
  public void onConnect() {
    Log.d(TAG, "onConnect");

    Bundle bundle = new Bundle();
    bundle.putString(ACTION_KEY, ACTION_GET_CAPTURE_STATE);
    if (!appServiceClient.send(bundle)) Log.e(TAG, "send onConnect error");
  }

  @Override
  public void onDisconnect() {
    Log.d(TAG, "onDisconnect");
  }

  @Override
  public void onMessage(Bundle what) {
    if(what == null){
      Log.d(TAG, "onMessage");
      return;
    }
    what.setClassLoader(Conf.class.getClassLoader());
    String action = what.getString(ACTION_KEY);
    Log.d(TAG, "onMessage action: " + action);
    if(action == null) return;
    if (ACTION_GET_CAPTURE_STATE_RES.equals(action)) {
      prevChecked = what.getBoolean(DATA_KEY);
      webHost = what.getString(MP_WEB_HOST);
      Conf conf = what.getParcelable(MP_CONF);
      int conns = what.getInt(MP_CONN, 0);
      hostList.post(() -> {
        if(conf != null){
          this.conf = conf;
          setOption(kADBControlTitle, conf.useADB);
          setOption(kAudioControlTitle, conf.isAudioEnabled);
          setOption(kInputControlTitle, conf.isInputEnabled);
          setOption(kWebControlTitle, conf.isWebAccessEnabled);
          setOption(kSecureControlTitle, conf.isSecure);
        }
        setToggleControl(prevChecked);
        populateHosts();
        updateConnections(conns);
      });
    }
    else if(ACTION_CONN_STATUS.equals(action)){
      hostList.post(() -> updateConnections(what.getInt(DATA_KEY, 0)));
    }
  }

  private void updateConnections(int conns){
    txtConns.setText("Connections: " + conns);
  }

  private void addHost(String host){
    if(conf.isSecure) host += "/#" + kSecTokenKeyName + "=" + getSecToken();
    Log.d(TAG, "host: " + host);
    TextView view = (TextView) getLayoutInflater().inflate(R.layout.host_entry, null, true);
    view.setText(host);
    hostList.addView(view);
  }

  private void populateHosts(){
    hostList.removeAllViews();
    if(!prevChecked) return;
    if(webHost != null) addHost(webHost);
    for(String ip : WifiUtil.GetIPAddresses()) addHost("http://" + ip + ":" + kHTTPServerPort);
  }

  boolean debugTouchEvents(){
    final LinearLayout ll = new LinearLayout(this){
      @Override
      public boolean performClick() {
        // do what you want
        return true;
      }
    };
    ll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    final View view = getLayoutInflater().inflate(R.layout.main_activity_layout, null, false);
    ll.addView(view);
    ll.setOnTouchListener(new View.OnTouchListener() {
      @Override
      public boolean onTouch(View v, MotionEvent event) {
        Log.d(TAG, "action: " + MotionEvent.actionToString(event.getAction()) + ", count: " + event.getPointerCount());
        view.dispatchTouchEvent(event);
        return true;
      }
    });
    addContentView(ll, ll.getLayoutParams());
    return true;
  }

  WSTransport w1;
  P2PTransport p1, p2, p3;
  RemotePortForwarder rp;

  DataTransport.Receiver receiver = new DataTransport.Receiver() {
    @Override
    public void onOpen(DataTransport.Channel channel) {
      Log.d(TAG, channel.getClass().getSimpleName() + " onOpen");
      channel.send("hello world: " + channel);
    }

    @Override
    public void onClose(DataTransport.Channel channel) {
      Log.d(TAG, channel.getClass().getSimpleName() + " onClose");
    }

    @Override
    public void onMessage(DataTransport.Channel channel, String data) {
      Log.d(TAG, channel.getClass().getSimpleName() + " onMessage: " + data);
    }

    @Override
    public void onMessage(DataTransport.Channel channel, byte[] data) {
      Log.d(TAG, channel.getClass().getSimpleName() + " onMessage: " + data);
    }
  };

  DataTransport.Callback callback = new DataTransport.Callback() {
    @Override
    public void onStart(DataTransport transport, boolean success) {
      Log.d(TAG, transport.getClass().getSimpleName() + " onStart: " + success);
    }

    @Override
    public void onChannel(DataTransport transport, DataTransport.Channel channel, String label) {
      Log.d(TAG, transport.getClass().getSimpleName() + " onChannel: " + label);
      channel.setReceiver(receiver);
    }
  };

  void debugP2P(){
    P2PTransport.Init(this);

    p1 = new P2PTransport(new Handler(Looper.myLooper()), callback, (id, action, data) ->  p2.onSignalingMessage(id, action, data));
    p2 = new P2PTransport(new Handler(Looper.myLooper()), callback, (id, action, data) ->  p1.onSignalingMessage(id, action, data));
    p1.start();
    p2.start();
    p1.onSignalingReady();

    try {
      p3 = new P2PTransport(new Handler(Looper.myLooper()), callback, "ws://192.168.0.177:8090/ss?client=dev&&token=WolkL66SMTD9JNLEVABeYkrOGUUq2E9QvAjb6i6n");
      p3.start();
    } catch (URISyntaxException e) {
      Log.e(TAG, "p3 create error");
    }
  }

  void debugWS(){
    w1 = new WSTransport(new Handler(Looper.myLooper()), callback, null, 9090, getAssets());
    w1.start();
  }

  void debugSSH(){
    rp = new RemotePortForwarder(new Handler(Looper.myLooper()), new RemotePortForwarder.Callback() {
      @Override
      public void onSessionOpen() {
        Log.d(TAG, "onSessionOpen");
      }

      @Override
      public void onSessionClose() {
        Log.d(TAG, "onSessionClose");
      }

      @Override
      public void onForwardedPortHost(int lport, String host) {
        Log.d(TAG, "onForwardedPortHost " + lport + " --> " + host);
//        rp.disconnect();
      }
    });

    rp.connect("boggy", "pass", "serveo.net", 22, "localhost", new ArrayList<int[]>(){{
      add(new int[]{80, 5050});
      add(new int[]{443, 5051});
    }});
  }
}
