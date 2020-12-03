package com.boggyb.androidmirror;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;

import org.json.JSONArray;

import java.util.Arrays;

class MotionInputEvent {
  private static final int kMaxTouchCount = 10;

  private static int touchIdMask = 0;
  private static long lastTouchDown = System.currentTimeMillis();
  private static MotionEvent.PointerProperties[] pps = new MotionEvent.PointerProperties[kMaxTouchCount];
  private static MotionEvent.PointerCoords[] pcs = new MotionEvent.PointerCoords[kMaxTouchCount];
  private static MotionEvent.PointerProperties[] tpps = new MotionEvent.PointerProperties[kMaxTouchCount];
  private static MotionEvent.PointerCoords[] tpcs = new MotionEvent.PointerCoords[kMaxTouchCount];

  static{
    for(int i = 0; i < kMaxTouchCount; i++){
      MotionEvent.PointerProperties pp = new MotionEvent.PointerProperties();
      pp.clear();
      pp.id = i;
      pp.toolType = MotionEvent.TOOL_TYPE_FINGER;
      pps[i] = pp;

      MotionEvent.PointerCoords pc = new MotionEvent.PointerCoords();
      pc.clear();
      pcs[i] = pc;
    }
  }

  private static int getPointerAction(int motionEnvent, int index) {
    return motionEnvent + (index << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
  }

  private static boolean isPowerOfTwo(int x){
    return x!=0 && ((x&(x-1)) == 0);
  }

  static void Dispatch(JSONArray t) throws Exception{
    int len = 1;
    int action = t.getInt(0);
    pcs[0].x = t.getInt(1);
    pcs[0].y = t.getInt(2);

    long now = SystemClock.uptimeMillis();
    if(action == MotionEvent.ACTION_DOWN) lastTouchDown = now;

    InputManager.DispatchEvent(MotionEvent.obtain(
      lastTouchDown, now, action, len,
      Arrays.copyOf(pps, len),
      Arrays.copyOf(pcs, len),
      0, 0, 1,
      1, 5, 0,
      InputDevice.SOURCE_TOUCHSCREEN, 0
    ));
  }

  static void Dispatch(int action, JSONArray tArr) throws Exception{
    int lid = -1;
    for(int i = 0; i < tArr.length(); i ++){
      JSONArray t = tArr.getJSONArray(i);
      int id = t.getInt(0);
      if(id >= kMaxTouchCount || id < 0) continue;
      pcs[id].x = t.getInt(1);
      pcs[id].y = t.getInt(2);
      lid = id;
      touchIdMask |= (1 << lid);
    }

    if(lid == -1) return;

    int len = 0, idx = 0;

    if(isPowerOfTwo(touchIdMask)){
      tpps[len] = pps[lid];
      tpcs[len] = pcs[lid];
      idx = len;
      len += 1;
    }
    else for (int i = 0; i < kMaxTouchCount; i++) {
      if((touchIdMask & (1 << i)) > 0){
        tpps[len] = pps[i];
        tpcs[len] = pcs[i];
        if(i == lid) idx = len;
        len += 1;
      }
    }

    // clear mask on action_up
    if(action == MotionEvent.ACTION_UP) touchIdMask &= ~(1 << lid);

    action = action == MotionEvent.ACTION_MOVE || len == 1 ? action
      : getPointerAction(action + MotionEvent.ACTION_POINTER_DOWN, idx);

    long now = SystemClock.uptimeMillis();
    if(action == MotionEvent.ACTION_DOWN) lastTouchDown = now;

    InputManager.DispatchEvent(MotionEvent.obtain(
      lastTouchDown, now, action, len,
      Arrays.copyOf(tpps, len),
      Arrays.copyOf(tpcs, len),
      0, 0, 1,
      1, 4, 0,
      InputDevice.SOURCE_TOUCHSCREEN, 0
    ));
//    Log.d(TAG, "idx: " + idx + ", action: " + MotionEvent.actionToString(action) + ", len: " + len + ", inLen: " + tArr.length());
  }

  static void DispatchScroll(JSONArray arr) throws Exception{
    int len = 1;
    MotionEvent.PointerCoords pc = pcs[0];
    pc.x = arr.getInt(0);
    pc.y = arr.getInt(1);
    pc.setAxisValue(MotionEvent.AXIS_HSCROLL, (float) arr.getDouble(2));
    pc.setAxisValue(MotionEvent.AXIS_VSCROLL, (float) arr.getDouble(3));

    long now = SystemClock.uptimeMillis();
    InputManager.DispatchEvent(MotionEvent.obtain(
      lastTouchDown, now, MotionEvent.ACTION_SCROLL, len,
      Arrays.copyOf(pps, len), Arrays.copyOf(pcs, len),
      0, 0, 1,
      1, 4, 0,
      InputDevice.SOURCE_MOUSE, 0
    ));
  }
}
