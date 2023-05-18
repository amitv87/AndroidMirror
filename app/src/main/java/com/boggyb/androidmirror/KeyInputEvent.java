package com.boggyb.androidmirror;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;

import org.json.JSONArray;

import java.util.HashMap;

class KeyInputEvent {
  public static final int deviceId = KeyCharacterMap.VIRTUAL_KEYBOARD;

  public static final HashMap<String, Integer> buttonMap = new HashMap<String, Integer>(){{
    put("home", KeyEvent.KEYCODE_HOME);
    put("menu", KeyEvent.KEYCODE_MENU);
    put("back", KeyEvent.KEYCODE_BACK);
    put("apps", KeyEvent.KEYCODE_APP_SWITCH);
    put("power", KeyEvent.KEYCODE_POWER);
    put("vup", KeyEvent.KEYCODE_VOLUME_UP);
    put("vdown", KeyEvent.KEYCODE_VOLUME_DOWN);
    put("vmute", KeyEvent.KEYCODE_VOLUME_MUTE);
    put("play", KeyEvent.KEYCODE_MEDIA_PLAY);
    put("pause", KeyEvent.KEYCODE_MEDIA_PAUSE);
    put("next", KeyEvent.KEYCODE_MEDIA_NEXT);
    put("prev", KeyEvent.KEYCODE_MEDIA_PREVIOUS);
    put("stop", KeyEvent.KEYCODE_MEDIA_STOP);
    put("web", KeyEvent.KEYCODE_EXPLORER);
    put("search", KeyEvent.KEYCODE_SEARCH);
  }};

  public static final HashMap<Integer, Integer> keyMap = new HashMap<Integer, Integer>(){{
    put(8, KeyEvent.KEYCODE_DEL);
    put(9, KeyEvent.KEYCODE_TAB);
    put(13, KeyEvent.KEYCODE_ENTER);
    put(16, KeyEvent.KEYCODE_SHIFT_LEFT);
    put(17, KeyEvent.KEYCODE_CTRL_LEFT);
    put(18, KeyEvent.KEYCODE_ALT_LEFT);
    put(20, KeyEvent.KEYCODE_CAPS_LOCK);
    put(27, KeyEvent.KEYCODE_ESCAPE);
    put(32, KeyEvent.KEYCODE_SPACE);

    put(33, KeyEvent.KEYCODE_PAGE_UP);
    put(34, KeyEvent.KEYCODE_PAGE_DOWN);
    put(35, KeyEvent.KEYCODE_MOVE_END);
    put(36, KeyEvent.KEYCODE_MOVE_HOME);

    put(37, KeyEvent.KEYCODE_DPAD_LEFT);
    put(38, KeyEvent.KEYCODE_DPAD_UP);
    put(39, KeyEvent.KEYCODE_DPAD_RIGHT);
    put(40, KeyEvent.KEYCODE_DPAD_DOWN);
    put(45, KeyEvent.KEYCODE_INSERT);
    put(46, KeyEvent.KEYCODE_FORWARD_DEL);

    put(91, KeyEvent.KEYCODE_CTRL_RIGHT);
    put(93, KeyEvent.KEYCODE_MENU);

    put(173, KeyEvent.KEYCODE_VOLUME_MUTE);
    put(174, KeyEvent.KEYCODE_VOLUME_DOWN);
    put(175, KeyEvent.KEYCODE_VOLUME_UP);

    put(186, KeyEvent.KEYCODE_SEMICOLON);
    put(187, KeyEvent.KEYCODE_EQUALS);
    put(188, KeyEvent.KEYCODE_COMMA);
    put(189, KeyEvent.KEYCODE_MINUS);
    put(190, KeyEvent.KEYCODE_PERIOD);
    put(191, KeyEvent.KEYCODE_SLASH);
    put(192, KeyEvent.KEYCODE_GRAVE);
    put(219, KeyEvent.KEYCODE_LEFT_BRACKET);
    put(220, KeyEvent.KEYCODE_BACKSLASH);
    put(221, KeyEvent.KEYCODE_RIGHT_BRACKET);
    put(222, KeyEvent.KEYCODE_APOSTROPHE);
  }};

  public static final int kAsciiNumStart = 48;
  public static final int kAsciiNumEnd = 57;

  public static final int kAsciiCharStart = 65;
  public static final int kAsciiCharEnd = 90;

  static void Dispatch(final JSONArray arr) throws Exception{
    Integer keyCode = arr.getInt(1);
    if(keyCode >= kAsciiNumStart && keyCode <= kAsciiNumEnd) keyCode += (KeyEvent.KEYCODE_0 - kAsciiNumStart);
    else if(keyCode >= kAsciiCharStart && keyCode <= kAsciiCharEnd) keyCode += (KeyEvent.KEYCODE_A - kAsciiCharStart);
    else if((keyCode = keyMap.get(keyCode)) == null) return;

    sendKey(keyCode, arr.getInt(0),
      (arr.getInt(2) == 1 ? KeyEvent.META_ALT_ON : 0)
        | (arr.getInt(3) == 1 ? KeyEvent.META_SHIFT_ON : 0)
        | (arr.getInt(4) == 1 ? KeyEvent.META_CTRL_ON : 0)
    );
  }

  static void SendButton(String action) throws Exception {
    Integer keyCode = buttonMap.get(action);
    if(keyCode == null) return;
    sendKey(keyCode, KeyEvent.ACTION_DOWN, 0);
    sendKey(keyCode, KeyEvent.ACTION_UP, 0);
  }

  private static void sendKey(int keyCode, int action, int meta) throws Exception {
    long now = SystemClock.uptimeMillis();
    InputManager.DispatchEvent(new KeyEvent(
      now, now, action, keyCode, 0, meta, deviceId, 0,
      KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD
    ));
  }
}
