package com.boggyb.androidmirror.media;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;

import java.nio.ByteBuffer;

public class Encoder extends MediaCodec.Callback {
  private static String TAG = Encoder.class.getCanonicalName();

  private long pts = 0;
  private Surface surface = null;
  private boolean started = false;
  private int bitrateKbps;

  interface Callback{
    int OnInputBuffer(ByteBuffer b);
    void OnOutputBuffer(ByteBuffer b, int size, int flags);
  }

  private Callback callback;
  private final MediaCodec mediaCodec;

  Encoder(MediaFormat format, int bitrateKbps) throws Exception {
    setBitrate(bitrateKbps);
    format.setInteger(MediaFormat.KEY_BIT_RATE, this.bitrateKbps * 1024);
    this.mediaCodec = MediaCodec.createEncoderByType(format.getString(MediaFormat.KEY_MIME));
    mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
  }

  Surface getSurface(){
    if(started) return surface;
    if(surface == null) surface = mediaCodec.createInputSurface();
    return surface;
  }

  public void start(Callback callback, Handler handler){
    if(started) return;
    this.callback = callback;
    if(this.callback != null) mediaCodec.setCallback(this, handler);
    mediaCodec.start();
    started = true;
  }

  private void stop(){
    if(!started) return;
    mediaCodec.stop();
    started = false;
  }

  void destroy(){
    stop();
    if(surface != null) surface.release();
    mediaCodec.release();
  }

  void requestKeyFrame(){
    if(!started) return;
    final Bundle b = new Bundle();
    b.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
    mediaCodec.setParameters(b);
  }

  void setBitrate(int bitrateKbps) {
    this.bitrateKbps = Math.max(Math.max(bitrateKbps, 128), Math.min(bitrateKbps, 10240));
    if(!started) return;
    Bundle params = new Bundle();
    Log.d(TAG, "set bitrate: " + this.bitrateKbps);
    params.putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, this.bitrateKbps * 1024);
    mediaCodec.setParameters(params);
  }

  int getBitrate() {
    return bitrateKbps;
  }

  @Override
  public void onInputBufferAvailable(MediaCodec codec, int index){
    ByteBuffer b = codec.getInputBuffer(index); b.clear();
    int size = callback.OnInputBuffer(b);
    if(size >= 0) codec.queueInputBuffer(index, 0, size, System.nanoTime()/1000, 0);
  }

  @Override
  public void onOutputBufferAvailable(MediaCodec codec, int index, MediaCodec.BufferInfo info) {
    try{
//    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0)
//      Log.d(TAG, "config frame generated. offset: " + info.offset + ". size: " + info.size + ", index: " + index);
//    else if((info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0)
//      Log.d(TAG, "keyframe frame generated: outputBufferIndex = " + index);
//    else if((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0)
//      Log.d(TAG, "end of stream");
    if (index >= 0) {
      if(pts <= info.presentationTimeUs) {
        pts = info.presentationTimeUs;
        ByteBuffer outputBuffer = codec.getOutputBuffer(index);
        if(outputBuffer != null) {
          outputBuffer.position(info.offset);
          outputBuffer.limit(info.offset + info.size);
          callback.OnOutputBuffer(outputBuffer, info.size, info.flags);
        }
      }
      codec.releaseOutputBuffer(index, false);
    }
    } catch(Exception e){Log.e(TAG, "onOutputBufferAvailable", e);}
  }

  @Override
  public void onError(MediaCodec codec, MediaCodec.CodecException e) {
    Log.e(TAG, "onError", e);
  }

  @Override
  public void onOutputFormatChanged(MediaCodec codec, MediaFormat format){
    pts = 0;
    Log.i(TAG, "videoEncoder onOutputFormatChanged: " + format);
  }
}
