package com.boggyb.androidmirror.net;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.enums.ReadyState;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

public class P2PTransport extends DataTransport {
  private final static String TAG = P2PTransport.class.getCanonicalName();

  private static final String TRUE_STRING = "true";
  private static final String FALSE_STRING = "false";

  private final MediaConstraints sdpMediaConstraints = new MediaConstraints(){{
    mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", FALSE_STRING));
    mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", FALSE_STRING));
  }};

  private final ArrayList<PeerConnection.IceServer> iceServers = new ArrayList<PeerConnection.IceServer>(){{
    add(new PeerConnection.IceServer("stun:23.21.150.121"));
    add(new PeerConnection.IceServer("stun:stun.l.google.com:19302"));
    add(new PeerConnection.IceServer("turn:95.211.193.33:3478?transport=udp", "username1","key1"));
    add(new PeerConnection.IceServer("turn:95.211.193.33:3478?transport=tcp", "username1","key1"));

    add(PeerConnection.IceServer.builder(new ArrayList<String>(){{
        add("turn:64.233.189.127:19305?transport=udp");
        add("turn:[2404:6800:4008:c07::7f]:19305?transport=udp");
        add("turn:64.233.189.127:19305?transport=tcp");
        add("turn:[2404:6800:4008:c07::7f]:19305?transport=tcp");
      }})
      .setUsername("CL/3ivYFEgbBW+O06pwYqvGggqMKIICjBQ")
      .setPassword("6VEObpBp+99v6iqxbqOZii+JKmA=")
      .createIceServer()
    );
  }};

  private final PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers){{
    tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED;
    bundlePolicy = PeerConnection.BundlePolicy.MAXCOMPAT;
    rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.NEGOTIATE;
    iceTransportsType = PeerConnection.IceTransportsType.ALL;
    continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
    keyType = PeerConnection.KeyType.ECDSA;
    sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
    enableDtlsSrtp = true;
//    enableRtpDataChannel = true;
  }};

  private static class Actions{
    private static final String READY = "ready";
    private static final String OFFER = "offer";
    private static final String ANSWER = "answer";
    private static final String CANDIDATE = "candidate";
    private static final String OFFERER_READY = "offererReady";
    private static final String ANSWERER_READY = "answererReady";
  }

  private interface ICommand {
    void execute(int peerId, JSONObject payload) throws Exception;
  }

  public interface SignalingChannel{
    void send(int peerId, String action, JSONObject payload);
  }

  private class WSSignalingChannel implements SignalingChannel{
    private final URI uri;
    private WebSocketClient ws;
    private boolean reconnect = true;

    private WSSignalingChannel(String url) throws URISyntaxException {
      this.uri = new URI(url);
    }

    private void start(){
      if(timer != null || !reconnect) return;
      ws = new WebSocketClient(uri) {
        @Override
        public void onOpen(ServerHandshake handshakedata) {
          Log.d(TAG, "WSSignalingChannel onOpen");
          onSignalingReady();
        }

        @Override
        public void onMessage(String message) {
          handler.post(() -> {
            try {
              JSONObject jsonObj = new JSONObject(message);
              int peerId = jsonObj.optInt("id", -1);
              JSONObject data = new JSONObject(jsonObj.optString("d", "{}"));
              if(peerId <= 0) return;
              onSignalingMessage(peerId, data.optString("a", ""), data.optJSONObject("p"));
            } catch (Exception e) {
              Log.e(TAG, "WSSignalingChannel onMessage err", e);
            }
          });
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
          Log.d(TAG, "WSSignalingChannel onClose, code: " + code + ", reason: " + reason + ", remote: " + remote);
          tryAgain();
        }

        @Override
        public void onError(Exception e) {
//          Log.e(TAG, "WSSignalingChannel onError: " + e.getMessage());
        }
      };
      ws.connect();
    }

    private void stop(){
      if(timer != null) timer.cancel();
      reconnect = false;
      ws.close();
    }

    private Timer timer = null;
    private void tryAgain(){
      if(timer != null || !reconnect) return;
      timer = new Timer();
      timer.schedule(new TimerTask() {
        @Override
        public void run() {
          timer = null;
          handler.post(WSSignalingChannel.this::start);
        }
      }, 1000);
    }

    @Override
    public void send(int peerId, String action, JSONObject payload) {
      if(ws.getReadyState() != ReadyState.OPEN) return;

      JSONObject obj = new JSONObject(){{
        try{
          put("id", peerId);
          put("d", new JSONObject(){{
            put("a", action);
            put("p", payload);
          }}.toString());
        }
        catch (Exception ignored){}
      }};

      ws.send(obj.toString());
    }
  }

  private static boolean isInitialized = false;
  public static void Init(Context context){
    if(isInitialized) return;

    try {
      System.load(context.getApplicationInfo().dataDir + "/libjingle_peerconnection_so.so");
      try {
        Class<?> clazz = Class.forName("org.webrtc.NativeLibrary");
        Field f = clazz.getDeclaredField("libraryLoaded");
        f.setAccessible(true);
        f.setBoolean(f, true);
      } catch (Throwable e) {}
    }
    catch (Throwable e){}

    PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context)
      .setEnableInternalTracer(false)
      .setInjectableLogger((s, severity, s1) -> {}, Logging.Severity.LS_NONE)
      .createInitializationOptions());
//    Logging.enableLogToDebugOutput(Logging.Severity.LS_INFO);
    isInitialized = true;
  }

  private PeerConnectionFactory factory;
  private final SignalingChannel signalingChannel;
  private final HashMap<Integer, Peer> peers = new HashMap<>();

  private final HashMap<String, ICommand> commandMap = new HashMap<String, ICommand>(){{
    put(Actions.READY, new CreateOfferer());
    put(Actions.OFFERER_READY, new CreateAnswerer());
    put(Actions.ANSWERER_READY, new CreateOffer());
    put(Actions.OFFER, new AcceptOffer());
    put(Actions.ANSWER, new AcceptAnswer());
    put(Actions.CANDIDATE, new AddIceCandidate());
  }};

  public P2PTransport(Handler handler, Callback callback, String wsUrl) throws URISyntaxException {
    super(handler, callback);
    this.signalingChannel = new WSSignalingChannel(wsUrl);
  }

  public P2PTransport(Handler handler, Callback callback, SignalingChannel signalingChannel) {
    super(handler, callback);
    this.signalingChannel = signalingChannel;
  }

  @Override
  public void start() {
    if(factory != null) return;
    factory = PeerConnectionFactory.builder()
      .setOptions(new PeerConnectionFactory.Options())
      .setAudioDeviceModule(null)
      .setVideoEncoderFactory(null)
      .setVideoDecoderFactory(null)
      .createPeerConnectionFactory();
    if(signalingChannel instanceof WSSignalingChannel) ((WSSignalingChannel) signalingChannel).start();
  }

  @Override
  public void stop() {
    if(factory == null) return;
    if(signalingChannel instanceof WSSignalingChannel) ((WSSignalingChannel) signalingChannel).stop();
    for(Peer peer : peers.values()) peer.dispose();
    peers.clear();
    factory.dispose();
    factory = null;
  }

  public void onSignalingReady(){
    sendMessage(0, Actions.READY, new JSONObject());
  }

  public void onSignalingMessage(int peerId, String action, JSONObject payload){
//    Log.d(TAG, ">>>>> id " + peerId + " " + action + " " + payload);

    ICommand command = commandMap.get(action);
    if(command == null) return;
    Log.d(TAG,command.getClass().getSimpleName());
    try {
      command.execute(peerId, payload);
    } catch (Exception e) {
      Log.e(TAG,command.getClass().getSimpleName(), e);
    }
  }

  private void sendMessage(int peerId, String action, JSONObject payload){
    handler.post(() -> {
//      Log.d(TAG, "<<<<< " + peerId + " " + action + " " + payload);
      signalingChannel.send(peerId, action, payload);
    });
  }

  private void sendOverDC(final DataChannel dc, final ByteBuffer byteBuffer, final boolean binary){
    if(dc != null && dc.state() == DataChannel.State.OPEN) dc.send(new DataChannel.Buffer(byteBuffer, binary));
  }

  private Peer addPeer(int id) {
    Log.d(TAG, "adding peer " + id);
    removePeer(id);
    Peer peer = new Peer(id);
    peers.put(id, peer);
    Log.d(TAG, "added peer " + id);
    return peer;
  }

  private void removePeer(int id) {
    Peer peer = peers.get(id);
    if(peer == null) return;
    Log.d(TAG, "removing peer " + id);
    peer.dispose();
    peers.remove(id);
    Log.d(TAG, "removed peer " + id);
  }

  private class Peer implements SdpObserver, PeerConnection.Observer{
    boolean isOfferer = false;
    private final int id;
    private PeerConnection pc;
    final ArrayList<DC> dcs = new ArrayList<>();

    private class DC extends DataTransport.Channel implements DataChannel.Observer {
      private final String TAG = DC.class.getCanonicalName();

      final DataChannel dc;

      DC(DataChannel dc){
        this.dc = dc;
        dc.registerObserver(this);
        handler.post(() -> callback.onChannel(P2PTransport.this, this, dc.label()));
      }

      void dispose(){
        Log.d(TAG, "disposing channel: " + dc.label());
        dc.unregisterObserver();
        dc.dispose();
        handler.post(() -> {
          if (receiver != null) receiver.onClose(this);
        });
      }

      @Override
      public void close() {
        synchronized (dcs) {
          dispose();
          dcs.remove(this);
        }
      }

      @Override
      public void send(String data) {
        sendOverDC(dc, ByteBuffer.wrap(data.getBytes()), false);
      }

      @Override
      public void send(byte[] data) {
        sendOverDC(dc, ByteBuffer.wrap(data), true);
      }

      @Override
      public void onBufferedAmountChange(long l) {
//        Log.d(TAG, "channel " + dc.label() + " onBufferedAmountChange: " + l);
      }

      @Override
      public void onStateChange(){
        Log.d(TAG, "channel " + dc.label() + " onStateChange: " + dc.state().name());
        if (dc.state() == DataChannel.State.OPEN) handler.post(() -> {
          if (receiver != null) receiver.onOpen(this);
        });
        else if (dc.state() == DataChannel.State.CLOSED) close();
      }

      @Override
      public void onMessage(DataChannel.Buffer buffer) {
        final boolean isBinary = buffer.binary;
        final byte[] bytes = new byte[buffer.data.capacity()];
        buffer.data.get(bytes);

        handler.post(() -> {
          if(receiver == null) return;
          if(isBinary) receiver.onMessage(this, bytes);
          else receiver.onMessage(this, new String(bytes, StandardCharsets.UTF_8));
        });
      }
    }

    Peer(int _id) {
      this.id = _id;
      Log.d(TAG, "new Peer: " + id);
      this.pc = factory.createPeerConnection(rtcConfig, this);
    }

    void dispose(){
      if(pc != null){
        synchronized (dcs) {
          for (DC dc : dcs) dc.dispose();
          dcs.clear();
        }
        pc.dispose();
        Log.d(TAG, "peer " + id + " disposed peerconnection");
        pc = null;
      }
    }

    void createOffer(){
      synchronized (dcs) {
        for (DC dc : dcs) dc.dispose();
        dcs.clear();
      }
      DataChannel.Init dcinit = new DataChannel.Init();
      dcinit.ordered = true;
      dcinit.maxRetransmits = -1;
      dcinit.maxRetransmitTimeMs = -1;
      dcinit.negotiated = false;
      dcinit.id = -1;
      dcs.add(new DC(pc.createDataChannel("main", dcinit)));
      pc.createOffer(this, sdpMediaConstraints);
    }

    @Override
    public void onCreateSuccess(final SessionDescription sdp) {
      try {
        pc.setLocalDescription(Peer.this, sdp);
        sendMessage(id, sdp.type.canonicalForm(), new JSONObject(){{
          put("type", sdp.type.canonicalForm());
          put("sdp", sdp.description);
        }});
      } catch (Exception e) {
        Log.e(TAG, "onCreateSuccess", e);
      }
    }

    @Override
    public void onSetSuccess() {
      Log.d(TAG, "onSetSuccess");
    }

    @Override
    public void onCreateFailure(String s) {
      Log.e(TAG, "onCreateFailure: " + s);
    }

    @Override
    public void onSetFailure(String s) {
      Log.e(TAG, "onSetFailure: " + s);
    }

    @Override
    public void onSignalingChange(PeerConnection.SignalingState signalingState) {
      Log.d(TAG, "onSignalingChange: " + signalingState.name());
    }

    @Override
    public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
      Log.d(TAG, "peerId: " + id + ", IceConnectionState: " + iceConnectionState.name());
      if(iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED || iceConnectionState == PeerConnection.IceConnectionState.FAILED) handler.post(()-> removePeer(this.id));
    }

    @Override
    public void onIceConnectionReceivingChange(boolean receiving) {
      Log.d(TAG, "onIceConnectionReceivingChange: " + receiving);
    }

    @Override
    public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
      Log.d(TAG, "onIceGatheringChange: " + iceGatheringState.name());
    }

    @Override
    public void onIceCandidate(IceCandidate candidate) {
      try {
        sendMessage(id, Actions.CANDIDATE, new JSONObject(){{
          put("sdpMLineIndex", candidate.sdpMLineIndex);
          put("sdpMid", candidate.sdpMid);
          put("candidate", candidate.sdp);
        }});
      } catch (Exception e) {
        Log.e(TAG, "onIceCandidate", e);
      }
    }

    @Override
    public void onDataChannel(DataChannel dataChannel) {
      Log.d(TAG, "peerId: " + id + ", onDataChannel: " + dataChannel.label());
      synchronized(dcs){
        dcs.add(new DC(dataChannel));
      }
    }

    @Override
    public void onRenegotiationNeeded() {
      Log.d(TAG, "onRenegotiationNeeded");
    }

    public void onAddStream(MediaStream ms){}
    public void onRemoveStream(MediaStream ms){}
    public void onIceCandidatesRemoved(IceCandidate[] iceCandidates){}
    public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams){}
  }

  private class CreateOfferer implements ICommand{
    public void execute(int peerId, JSONObject payload){
      addPeer(peerId);
      sendMessage(peerId, Actions.OFFERER_READY, new JSONObject());
    }
  }

  private class CreateAnswerer implements ICommand {
    public void execute(int peerId, JSONObject payload) {
      addPeer(peerId);
      sendMessage(peerId, Actions.ANSWERER_READY, new JSONObject());
    }
  }

  private class CreateOffer implements ICommand {
    public void execute(int peerId, JSONObject payload) {
      Peer peer = peers.get(peerId);
      if(peer == null) peer = addPeer(peerId);
      peer.createOffer();
      peer.isOfferer = true;
    }
  }

  private class AcceptOffer implements ICommand {
    public void execute(int peerId, JSONObject payload) throws Exception {
      Peer peer = peers.get(peerId);
      if(peer == null) return;
      SessionDescription sdp = new SessionDescription(
        SessionDescription.Type.fromCanonicalForm(payload.getString("type")),
        payload.getString("sdp")
      );
      peer.pc.setRemoteDescription(peer, sdp);
      peer.pc.createAnswer(peer, sdpMediaConstraints);
      peer.isOfferer = false;
    }
  }

  private class AcceptAnswer implements ICommand {
    public void execute(int peerId, JSONObject payload) throws Exception {
      Peer peer = peers.get(peerId);
      if(peer == null) return;
      SessionDescription sdp = new SessionDescription(
        SessionDescription.Type.fromCanonicalForm(payload.getString("type")),
        payload.getString("sdp")
      );
      peer.pc.setRemoteDescription(peer, sdp);
    }
  }

  private class AddIceCandidate implements ICommand {
    public void execute(int peerId, JSONObject payload) throws Exception {
      Peer peer = peers.get(peerId);
      if(peer == null) return;
      if (peer.pc.getRemoteDescription() != null) {
        IceCandidate candidate = new IceCandidate(
          payload.getString("sdpMid"),
          payload.getInt("sdpMLineIndex"),
          payload.getString("candidate")
        );
        peer.pc.addIceCandidate(candidate);
      }
    }
  }
}
