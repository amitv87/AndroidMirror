package com.boggyb.androidmirror.media;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.Surface;
import android.view.WindowManager;

import com.boggyb.androidmirror.gles.EglCore;
import com.boggyb.androidmirror.gles.FullFrameRect;
import com.boggyb.androidmirror.gles.Texture2dProgram;
import com.boggyb.androidmirror.gles.WindowSurface;

import java.nio.ByteBuffer;

public class ScreenCapture extends MediaCapture implements Encoder.Callback {
  private static final String TAG = ScreenCapture.class.getCanonicalName();

  public static final int kScaleFactor = 2;
  private static final int kFrameRate = 60;
  private static final String kVideoMimeType = MediaFormat.MIMETYPE_VIDEO_AVC;
  private static final int kAVCProfile = MediaCodecInfo.CodecProfileLevel.AVCProfileHigh;
  private static final int kAVCProfileLevel = MediaCodecInfo.CodecProfileLevel.AVCLevel4;
  private final VirtualDisplay mVirtualDisplay;

//  private final EGLContext mEglContext;
//  private final EglCore mEglCore;
//  private final WindowSurface mInputWindowSurface;
//  private final SurfaceTexture mSurfaceTexture;
//  private final Surface mSurface;
//  private final FullFrameRect ffrect;
//  private final int mTextureId;

  public static ScreenCapture createInstance(MediaProjection mediaProjection, Context context, int bitrateKbps) throws Exception{
    DisplayMetrics metrics = new DisplayMetrics();
    WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    wm.getDefaultDisplay().getRealMetrics(metrics);

    int width = metrics.widthPixels / kScaleFactor;
    int height = metrics.heightPixels / kScaleFactor;

    MediaFormat format = MediaFormat.createVideoFormat(kVideoMimeType, width, height);
    format.setInteger(MediaFormat.KEY_FRAME_RATE, kFrameRate);
    format.setInteger(MediaFormat.KEY_CAPTURE_RATE, kFrameRate);
    format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 999999);
    format.setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1000000 / kFrameRate);
    format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
    format.setInteger(MediaFormat.KEY_PROFILE, kAVCProfile);
    format.setInteger(MediaFormat.KEY_LEVEL, kAVCProfileLevel);
    format.setFloat(MediaFormat.KEY_MAX_FPS_TO_ENCODER, kFrameRate);

    format.setInteger(MediaFormat.KEY_PRIORITY, 0);
    format.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);

    return new ScreenCapture(mediaProjection, format, width, height, metrics.densityDpi, bitrateKbps);
  }

  private ScreenCapture(MediaProjection mediaProjection, MediaFormat format, int width, int height, int densityDpi, int bitrateKbps) throws Exception {
    super(format, bitrateKbps);

//    mEglContext = EGL14.eglGetCurrentContext();
//    mEglCore = new EglCore(mEglContext, EglCore.FLAG_RECORDABLE);
//    mInputWindowSurface = new WindowSurface(mEglCore, encoder.getSurface(), true);
//    mInputWindowSurface.makeCurrent();
//    ffrect = new FullFrameRect(new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));
//    mTextureId = ffrect.createTextureObject();
//    mSurfaceTexture = new SurfaceTexture(mTextureId);
//    mSurfaceTexture.setDefaultBufferSize(width, height);
//    mSurface = new Surface(mSurfaceTexture);

    mVirtualDisplay = mediaProjection.createVirtualDisplay("android_mirror",
      width, height, densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
      encoder.getSurface(),
//      null,
      null, null
    );
  }

  @Override
  public boolean startCapture(Callback callback, final Handler handler){
    if(this.state != State.Initial) return false;;
    super.startCapture(callback, handler);
//    mSurfaceTexture.setOnFrameAvailableListener(stListener, handler);
//    mVirtualDisplay.setSurface(mSurface);
    this.state = State.Running;
    return true;
  }

  @Override
  public boolean stopCapture(){
    if(this.state != State.Running) return false;
    mVirtualDisplay.release();
//    mSurfaceTexture.setOnFrameAvailableListener(null);
//    mSurface.release();
//    mSurfaceTexture.release();
//    mInputWindowSurface.release();
//    mEglCore.release();
    super.stopCapture();
    this.state = State.Stopped;
    return true;
  }

  @Override
  public int OnInputBuffer(ByteBuffer b){
    return 0;
  }

  @Override
  public void OnOutputBuffer(ByteBuffer b, int size, int flags){
    byte[] outData = new byte[size];
    b.get(outData, 0, size);
    callback.onEncodedFrame(outData, flags);
  }

//  private SurfaceTexture.OnFrameAvailableListener stListener = new SurfaceTexture.OnFrameAvailableListener() {
//    private int mFrameNum;
//    private final float[] mTransform = new float[16];
//    private void drawBox() {
//      final int width = mInputWindowSurface.getWidth();
//      int xpos = ((mFrameNum++) * 4) % (width - 50);
//      GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
//      GLES20.glScissor(xpos, 0, 100, 100);
//      GLES20.glClearColor(1.0f, 0.0f, 1.0f, 1.0f);
//      GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
//      GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
//    }
//
//    @Override
//    public void onFrameAvailable(SurfaceTexture surfaceTexture){
//      if(ScreenCapture.this.state != State.Running) return;
//      surfaceTexture.updateTexImage();
//      surfaceTexture.getTransformMatrix(mTransform);
//      ffrect.drawFrame(mTextureId, mTransform);
//      drawBox();
//      mInputWindowSurface.setPresentationTime(surfaceTexture.getTimestamp());
//      mInputWindowSurface.swapBuffers();
//    }
//  };
}
