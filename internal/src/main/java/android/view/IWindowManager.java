package android.view;

import android.os.IBinder;

public interface IWindowManager {
    public static class Stub {
        public static IWindowManager asInterface( IBinder binder ) {
            return null;
        }
    }

    // These can only be called when injecting events to your own window,
    // or by holding the INJECT_EVENTS permission.  These methods may block
    // until pending input events are finished being dispatched even when 'sync' is false.
    // Avoid calling these methods on your UI thread or use the 'NoWait' version instead.
    boolean injectKeyEvent(KeyEvent ev, boolean sync);
    boolean injectPointerEvent(MotionEvent ev, boolean sync);
    boolean injectTrackballEvent(MotionEvent ev, boolean sync);
    boolean injectInputEventNoWait(InputEvent ev);
    void updateRotation(boolean alwaysSendConfiguration, boolean forceRelayout);
    int getRotation();
    int getDefaultDisplayRotation();
    int watchRotation(IRotationWatcher watcher);
    int watchRotation(IRotationWatcher watcher, int displayId);
    void removeRotationWatcher(IRotationWatcher watcher);
    int getPreferredOptionsPanelGravity();
    void freezeRotation(int rotation);
    void thawRotation();
    boolean isRotationFrozen();
}