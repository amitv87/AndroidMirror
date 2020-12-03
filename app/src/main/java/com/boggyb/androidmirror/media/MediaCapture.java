package com.boggyb.androidmirror.media;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Handler;
import android.util.Log;

import java.util.Arrays;

public abstract class MediaCapture implements Encoder.Callback{
  private static final String TAG = MediaCapture.class.getCanonicalName();

  enum State{
    Initial,
    Running,
    Stopped,
  }

  public interface Callback{
    void onEncodedFrame(byte[] bytes, int flags);
  }

  State state;
  Encoder encoder;
  Callback callback = null;

  MediaCapture(MediaFormat format, int bitrateKbps) throws Exception {
    this.encoder = new Encoder(format, bitrateKbps);
    this.state = State.Initial;
  }

  public void requestKeyFrame(){
    encoder.requestKeyFrame();
  }

  public void setBitrate(int bitrateKbps){
    encoder.setBitrate(bitrateKbps);
  }

  public int getBitrate(){
    return encoder.getBitrate();
  }

  public boolean startCapture(Callback callback, Handler handler){
    this.callback = callback;
    encoder.start(this, handler);
    return true;
  }

  public boolean stopCapture(){
    encoder.destroy();
    this.callback = null;
    return true;
  }

  private static void findCodec(){
    MediaCodecList list = new MediaCodecList(MediaCodecList.ALL_CODECS);
    for(MediaCodecInfo info : list.getCodecInfos())
      Log.d(TAG, Arrays.toString(info.getSupportedTypes()) +  " " + (info.isHardwareAccelerated() ? "h/w": "s/w" ) + " " + info.getCanonicalName());
  }
}
