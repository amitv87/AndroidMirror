package android.hardware.input;

import android.os.IBinder;
import android.view.InputDevice;
import android.view.InputEvent;

public interface IInputManager {
    class Stub {
        public static IInputManager asInterface( IBinder binder ) {
            return null;
        }
    }
    boolean injectInputEvent(InputEvent inputevent, int i);
    InputDevice getInputDevice(int id);
}
