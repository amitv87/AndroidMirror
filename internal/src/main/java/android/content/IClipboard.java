package android.content;
import android.os.IBinder;

public interface IClipboard {
    public static class Stub {
        public static IClipboard asInterface( IBinder binder ) {
            return null;
        }
    }
    void setPrimaryClip(ClipData clip, String callingPackage);
    ClipData getPrimaryClip(String pkg);
    ClipDescription getPrimaryClipDescription(String callingPackage);
    boolean hasPrimaryClip(String callingPackage);
    void addPrimaryClipChangedListener(IOnPrimaryClipChangedListener listener, String callingPackage);
    void removePrimaryClipChangedListener(IOnPrimaryClipChangedListener listener);
    boolean hasClipboardText(String callingPackage);

    void setPrimaryClip(ClipData clip, String callingPackage, int userId);
    ClipData getPrimaryClip(String pkg, int userId);
    ClipDescription getPrimaryClipDescription(String callingPackage, int userId);
    boolean hasPrimaryClip(String callingPackage, int userId);
    void addPrimaryClipChangedListener(IOnPrimaryClipChangedListener listener, String callingPackage, int userId);
    void removePrimaryClipChangedListener(IOnPrimaryClipChangedListener listener, String callingPackage, int userId);
    boolean hasClipboardText(String callingPackage, int userId);
}
