package com.boggyb.androidmirror;

import android.content.Context;
import android.content.IClipboard;
import android.content.IOnPrimaryClipChangedListener;
import android.hardware.input.IInputManager;
import android.os.Build;
import android.os.ServiceManager;
import android.util.Log;
import android.view.IWindowManager;
import android.view.InputEvent;

import org.json.JSONArray;
import org.json.JSONTokener;

import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class InputManager {
  private static final String TAG = InputManager.class.getCanonicalName();

  private static final int kUserId = 0;
  private static final String kPackageName = "com.android.shell";

  private static IClipboard icp;
  private static IInputManager iim;
  private static IWindowManager iwm;

  private static final IOnPrimaryClipChangedListener clipboardListener = new IOnPrimaryClipChangedListener.Stub() {
    @Override
    public void dispatchPrimaryClipChanged() {
      Log.d(TAG, "dispatchPrimaryClipChanged");
    }
  };

  static void DispatchEvent(InputEvent inputEvent) throws Exception{
    iim.injectInputEvent(inputEvent, 0);
  }

  private static void Process(JSONArray arr) throws Exception {
    if (arr.length() == 3) MotionInputEvent.Dispatch(arr);
    else if (arr.length() == 2) {
      Object action = arr.opt(0);
      if(action instanceof Integer) MotionInputEvent.Dispatch((Integer)action, arr.getJSONArray(1));
      else if(action instanceof String) {
        if ("rotate".equals(action)) iwm.freezeRotation(arr.getInt(1));
        else if ("button".equals(action)) KeyInputEvent.SendButton(arr.getString(1));
      }
    }
    else if (arr.length() == 5) KeyInputEvent.Dispatch(arr);
    else if (arr.length() == 4) MotionInputEvent.DispatchScroll(arr);
  }

  public static void main(String[] argv) {
//    android.os.Process.setArgV0(TAG);
    icp = IClipboard.Stub.asInterface(ServiceManager.getService(Context.CLIPBOARD_SERVICE));
    iim = IInputManager.Stub.asInterface(ServiceManager.getService(Context.INPUT_SERVICE));
    iwm = IWindowManager.Stub.asInterface(ServiceManager.getService(Context.WINDOW_SERVICE));

    if(Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) icp.addPrimaryClipChangedListener(clipboardListener, kPackageName);
    else icp.addPrimaryClipChangedListener(clipboardListener, kPackageName, kUserId);

    System.out.println("ready");
    Scanner in = new Scanner(System.in, StandardCharsets.US_ASCII.name());
    while (true) try {
      String str = in.nextLine().trim();
//      Log.d(TAG, "in: " + str);
      if (str.length() == 0) continue;
      Object object = new JSONTokener(str).nextValue();
      if (object instanceof JSONArray) Process((JSONArray)object);
    } catch (Exception e) {
      Log.e(TAG, "in.nextLine err", e);
    }
  }
}
