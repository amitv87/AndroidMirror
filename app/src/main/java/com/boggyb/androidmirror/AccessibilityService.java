package com.boggyb.androidmirror;

import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.graphics.Path;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityEvent;

import java.util.HashMap;

import org.json.JSONArray;
import org.json.JSONTokener;

public class AccessibilityService extends android.accessibilityservice.AccessibilityService {
  private static final String TAG = AccessibilityService.class.getCanonicalName();

  private static final int kMaxTouchCount = 10;
  private static AccessibilityService instance = null;

  private static class Pointer{
    int prevX = 0, prevY = 0;
    GestureDescription.StrokeDescription stroke = null;
  }

  private static Pointer pointers[] = new Pointer[kMaxTouchCount];

  static{
    for(int i = 0; i < kMaxTouchCount; i++) pointers[i] = new Pointer();
  }

  private static final HashMap<String, Integer> buttonMap = new HashMap<String, Integer>(){{
    put("home", AccessibilityService.GLOBAL_ACTION_HOME);
    // put("menu", AccessibilityService.GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS);
    put("back", AccessibilityService.GLOBAL_ACTION_BACK);
    put("apps", AccessibilityService.GLOBAL_ACTION_RECENTS);
    put("power", AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
    // put("vup", KeyEvent.KEYCODE_VOLUME_UP);
    // put("vdown", KeyEvent.KEYCODE_VOLUME_DOWN);
    // put("vmute", KeyEvent.KEYCODE_VOLUME_MUTE);
    // put("play", KeyEvent.KEYCODE_MEDIA_PLAY);
    // put("pause", KeyEvent.KEYCODE_MEDIA_PAUSE);
    // put("next", KeyEvent.KEYCODE_MEDIA_NEXT);
    // put("prev", KeyEvent.KEYCODE_MEDIA_PREVIOUS);
    // put("stop", KeyEvent.KEYCODE_MEDIA_STOP);
    // put("web", KeyEvent.KEYCODE_EXPLORER);
    // put("search", KeyEvent.KEYCODE_SEARCH);
  }};

  @Override
  public void onCreate() {
    Log.d(TAG, "onCreate");
    super.onCreate();
    instance = this;
  }

  @Override
  public void onDestroy(){
    Log.d(TAG, "onDestroy");
    instance = null;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event){
  }

  @Override
  public void onInterrupt() {
  }

  public static void Process(AMService service, String str){
    if (str.length() == 0 || instance == null) return;

    try{
      Object object = new JSONTokener(str).nextValue();
      if(!(object instanceof JSONArray)) return;

      JSONArray arr = (JSONArray)object;

      if(arr.length() == 3){
        int action = arr.getInt(0);
        dispatch(new JSONArray(){{put(0, arr.put(0, 0));}}, action != MotionEvent.ACTION_DOWN, action != MotionEvent.ACTION_UP);
      }
      else if(arr.length() == 2){
        Object action = arr.opt(0);
        if(action instanceof Integer){
          dispatch(arr.getJSONArray(1), (Integer)action != MotionEvent.ACTION_DOWN, (Integer)action != MotionEvent.ACTION_UP);
        }
        else if(action instanceof String) {
          if ("rotate".equals(action)) service.rotate(arr.getInt(1));
          else if ("button".equals(action)){
            String button = arr.getString(1);
            if(button.equals("power") && isScreenOff()) wake();
            else{
              Integer global_action = buttonMap.get(button);
              if(global_action != null && global_action > 0) instance.performGlobalAction(global_action);
            }
          }
        }
      }
    }
    catch (Exception e){
      Log.e(TAG, "Process err", e);
    }
  }

  private static void dispatch(JSONArray tArr, boolean isContinuedGesture, boolean willContinue) throws Exception{
    GestureDescription.Builder builder = new GestureDescription.Builder();
    for(int i = 0; i < tArr.length(); i ++){
      JSONArray t = tArr.getJSONArray(i);
      int id = t.getInt(0);
      if(id >= kMaxTouchCount || id != 0) continue;
      Pointer p = pointers[id];
      int x = t.getInt(1), y = t.getInt(2);
      if(x < 0) x = 0; if(y < 0) y = 0;

      // Log.d(TAG, "id: " + id + ", is_c: " + isContinuedGesture + ", will_c: " + willContinue + " " + x + "," + y);

      Path path = new Path();

      if(isContinuedGesture){
        if(p.stroke == null) continue;
        path.moveTo(p.prevX, p.prevY);
        path.lineTo(x, y);
        p.stroke = p.stroke.continueStroke(path, 0, 1, willContinue);
      }
      else{
        path.moveTo(x, y);
        p.stroke = new GestureDescription.StrokeDescription(path, 0, 1, willContinue);
      }
      p.prevX = x; p.prevY = y;
      builder.addStroke(p.stroke);
      if(!willContinue) p.stroke = null;
      break;
    }

    instance.dispatchGesture(builder.build(), null, null);
  }

  private static boolean isScreenOff() {
    return !((PowerManager)instance.getSystemService(Context.POWER_SERVICE)).isInteractive();
  }


  private static void wake() {
    PowerManager.WakeLock screenLock = ((PowerManager)instance.getSystemService(Context.POWER_SERVICE))
      .newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, TAG);
    screenLock.acquire();
    screenLock.release();
  }
}
