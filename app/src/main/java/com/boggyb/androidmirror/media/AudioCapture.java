package com.boggyb.androidmirror.media;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.os.Handler;
import android.os.HandlerThread;

import java.nio.ByteBuffer;

public class AudioCapture extends MediaCapture{
  private static final int kSampleRate = 44100;
  private static final int kEncoding = AudioFormat.ENCODING_PCM_16BIT;
  private static final int kChannelMask = AudioFormat.CHANNEL_IN_STEREO;
  private static final String kAudioMimeType = MediaFormat.MIMETYPE_AUDIO_AAC;
  private static final int kAACProfileLevel = MediaCodecInfo.CodecProfileLevel.AACObjectLC;
  private final int freqIdx;
  private final HandlerThread ht;
  private final Handler hth;
  private Handler cbHandler;

  public static AudioCapture createInstance(MediaProjection mediaProjection, int bitrateKbps) throws Exception {
    AudioFormat inFormat = new AudioFormat.Builder()
      .setEncoding(kEncoding)
      .setSampleRate(kSampleRate)
      .setChannelMask(kChannelMask)
      .build();

    int minBufferSize = AudioRecord.getMinBufferSize(inFormat.getSampleRate(), inFormat.getChannelMask(), inFormat.getEncoding()) * 2;

    MediaFormat outFormat = new MediaFormat();
    outFormat.setString(MediaFormat.KEY_MIME, kAudioMimeType);
    outFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, inFormat.getChannelCount());
    outFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, inFormat.getSampleRate());
    outFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, kAACProfileLevel);
    outFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, minBufferSize);
    outFormat.setInteger(MediaFormat.KEY_PRIORITY, 0);
    outFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);

    return new AudioCapture(mediaProjection, outFormat, inFormat, minBufferSize, bitrateKbps);
  }

  private final AudioRecord mAudioRecord;

  private static final int USAGE_VIRTUAL_SOURCE = 15;
  private AudioCapture(MediaProjection mediaProjection, MediaFormat outFormat, AudioFormat inFormat, int minBufferSize, int bitrateKbps) throws Exception {
    super(outFormat, bitrateKbps);
    ht = new HandlerThread("audio_thread", android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
    ht.setPriority(Thread.MAX_PRIORITY);
    ht.start();
    hth = new Handler(ht.getLooper());

    if (true)
//    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
      mAudioRecord = new AudioRecord.Builder()
        .setAudioFormat(inFormat)
        .setBufferSizeInBytes(minBufferSize)
        .setAudioPlaybackCaptureConfig(new AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
          .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
          .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
          .addMatchingUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
          .addMatchingUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING)
          .addMatchingUsage(AudioAttributes.USAGE_ALARM)
          .addMatchingUsage(AudioAttributes.USAGE_NOTIFICATION)
          .addMatchingUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
          .addMatchingUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_REQUEST)
          .addMatchingUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
          .addMatchingUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_DELAYED)
          .addMatchingUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
          .addMatchingUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
          .addMatchingUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
          .addMatchingUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
          .addMatchingUsage(AudioAttributes.USAGE_GAME)
          .addMatchingUsage(USAGE_VIRTUAL_SOURCE)
          .addMatchingUsage(AudioAttributes.USAGE_ASSISTANT)
          .build())
        .build();
    else
      mAudioRecord = new AudioRecord(
        MediaRecorder.AudioSource.CAMCORDER,
        inFormat.getSampleRate(),
        inFormat.getChannelMask(),
        inFormat.getEncoding(),
        minBufferSize);

    freqIdx = determineSamplingRateKey(mAudioRecord.getSampleRate());
  }

  @Override
  public boolean startCapture(final Callback callback, Handler handler){
    if(this.state != State.Initial) return false;
    cbHandler = handler;
    hth.post(new Runnable() {
      @Override
      public void run() {
        mAudioRecord.startRecording();
        AudioCapture.super.startCapture(callback, hth);
      }
    });
    this.state = State.Running;
    return true;
  }

  @Override
  public boolean stopCapture(){
    if(this.state != State.Running) return false;
    hth.post(new Runnable() {
      @Override
      public void run() {
        AudioCapture.super.stopCapture();
        mAudioRecord.stop();
        mAudioRecord.release();
        ht.quit();
      }
    });
    this.state = State.Stopped;
    return true;
  }

  private static final int kBuffLen = 3760;

  private static boolean isEmpty(ByteBuffer byteBuffer, int offset, int length) {
    for (int i = offset; i < offset + length; i++) if(byteBuffer.get(i) != 0) return false;
    return true;
  }

  @Override
  public int OnInputBuffer(ByteBuffer b){
    int rc = mAudioRecord.read(b, kBuffLen, AudioRecord.READ_BLOCKING);
    if(rc > 0 && isEmpty(b, 0 , rc)) rc = 0; else// check for empty bytes and ignore the buffer
    if(rc < 0) stopCapture();
    return rc;
  }

  @Override
  public void OnOutputBuffer(ByteBuffer b, int size, final int flags){
    int outPacketSize = size + 7;
    final byte[] outData = new byte[outPacketSize];
    addADTStoPacket(outData, outPacketSize, mAudioRecord.getChannelCount(), freqIdx);
    b.get(outData, 7, size);
    if(cbHandler != null) cbHandler.post(new Runnable() {
      @Override
      public void run(){
        callback.onEncodedFrame(outData, flags);
      }
    });
    else callback.onEncodedFrame(outData, flags);
  }

  private static void addADTStoPacket(byte[] packet, int packetLen, int chanCfg, int freqIdx){
    packet[0] = (byte) 0xFF;
    packet[1] = (byte) 0xF9;
    packet[2] = (byte) (((kAACProfileLevel - 1) << 6) + (freqIdx << 2) + (chanCfg >> 2));
    packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
    packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
    packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
    packet[6] = (byte) 0xFC;
  }

  private static int determineSamplingRateKey(int samplingRate){
    switch (samplingRate){
      case 96000:
        return 0;
      case 88200:
        return 1;
      case 64000:
        return 2;
      case 48000:
        return 3;
      case 32000:
        return 5;
      case 24000:
        return 6;
      case 22050:
        return 7;
      case 16000:
        return 8;
      case 12000:
        return 9;
      case 11025:
        return 10;
      case 8000:
        return 11;
      case 7350:
        return 12;
      default: // 44100
        return 4;
    }
  }
}
