package android.content;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;

public interface IOnPrimaryClipChangedListener extends IInterface {
    void dispatchPrimaryClipChanged();
    abstract class Stub extends Binder implements IOnPrimaryClipChangedListener {
        public IBinder asBinder() { return null; }
    }
}