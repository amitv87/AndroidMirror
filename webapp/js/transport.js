class ChannelBase {
  constructor(channel){
    this.init(channel);
  }

  init(channel){
    this.channel = channel;
    channel.binaryType = "arraybuffer";
    channel.onopen = (e)=> this.receiver && this.receiver.onOpen(this);
    channel.onmessage = (e)=> this.receiver && this.receiver.onMessage(this, e.data);
    channel.onclose = (e)=> {
      this.receiver && this.receiver.onClose(this)
      if(this.retry) this.retry();
    };
  }

  send(data){
    this.channel && this.channel.send(data);
  }

  close(){
    if(!this.channel) return;
    this.channel.onopen = null;
    this.channel.onmessage = null;
    this.channel.onclose = null;
    try{this.channel.close();}catch(e){}
    if(this.channel instanceof WebSocket) this.receiver.onClose(this);
    this.channel = null;
  }
}

class DataTransport{
  constructor(onChannel, basePath, secToken){
    this.onChannel = onChannel;
    this.basePath = basePath;
    this.secToken = secToken;
  }

  start(defChannelType){
    this.defChannelType = defChannelType;
  }
}

const candidateTransportTypes = ['tcp', 'udp',];
const candidateTypes = ['host', 'relay', 'srflx',];

window.iceconf = {};

function filter(candidate){
  var rc = true;
  rc = rc && (!iceconf.type || candidate.indexOf(iceconf.type) >= 0);
  rc = rc && (!iceconf.proto || candidate.indexOf(iceconf.proto) >= 0);
  // if(rc) console.log('filter success', candidate);
  return rc;
}

const ACTIONS = {
  READY: "ready",
  OFFER: "offer",
  ANSWER: "answer",
  CANDIDATE: "candidate",
  OFFERER_READY: "offererReady",
  ANSWERER_READY: "answererReady",
}

const peerConf = {
  iceServers: {
    iceServers: [
      {url:'stun:stun.l.google.com:19302', urls: 'stun:stun.l.google.com:19302'},
      {"urls": ["stun:172.217.163.158:19302", "stun:[2404:6800:4007:80e::201e]:19302"]},
      {"urls": [
          "turn:64.233.189.127:19305?transport=udp",
          "turn:[2404:6800:4008:c07::7f]:19305?transport=udp",
          "turn:64.233.189.127:19305?transport=tcp",
          "turn:[2404:6800:4008:c07::7f]:19305?transport=tcp"
        ],
        "username": "CL/3ivYFEgbBW+O06pwYqvGggqMKIICjBQ",
        "credential": "6VEObpBp+99v6iqxbqOZii+JKmA=",
        "maxRateKbps": "8000"
      },
    ],
    // rtcpMuxPolicy: 'require',
    // rtcpMuxPolicy: 'negotiate',
    iceTransportPolicy: 'all',
    // iceTransportPolicy: 'relay',
  },
  options: {
    'optional': [
      {'DtlsSrtpKeyAgreement': true},
      {'internalSctpDataChannels': true}
    ]
  },
  constraints: {
    "offerToReceiveAudio":false,
    "offerToReceiveVideo":false
  },
}

class DataCahnnel extends ChannelBase{
  constructor(dc){
    super(dc);
  }
}

class Peer{
  constructor(onConnection, transport, onSignalingMessage){
    this.transport = transport;
    this.onSignalingMessage = onSignalingMessage;
    this.errorHandler = this.errorHandler.bind(this);

    this.pc = new RTCPeerConnection(peerConf.iceServers, peerConf.options);

    var pc = this.pc;
    pc.oniceconnectionstatechange = pc.onicechange = function(e){
      var state = pc.iceConnectionState || e;
      console.log('oniceconnectionstatechange', state);
      if(state == 'connected' || state == 'completed') onConnection(this, true);
      else if(state == 'disconnected' || state == 'failed') onConnection(this, false);
    };
    pc.ondatachannel = (e) => this.transport.onChannel(this.transport, new DataCahnnel(e.channel), e.channel.label);
    pc.onicecandidate = (e) => e.candidate && filter(e.candidate.candidate) && this.onSignalingMessage(ACTIONS.CANDIDATE, e.candidate);
    // pc.onnegotiationneeded = (e) =>console.log('onnegotiationneeded', e);
    // pc.onsignalingstatechange = (e) => console.log('onsignalingstatechange', pc.signalingState);
  }

  createOffer(label){
    console.log('createOffer called');
    this.createChannel(label);
    var pc = this.pc;
    pc.createOffer((offer)=>{
      pc.setLocalDescription(offer, ()=>{
        pc.isOfferer = true;
        this.onSignalingMessage(ACTIONS.OFFER, pc.localDescription);
      }, this.errorHandler);
    }, this.errorHandler, peerConf.constraints);
  }

  createChannel(label){
    var dc = this.pc.createDataChannel(label);
    this.transport.onChannel(this.transport, new DataCahnnel(dc), dc.label);
  }

  addCandidate(data){
    if(filter(data.candidate)) this.pc.addIceCandidate(new RTCIceCandidate(data));
  }

  acceptOffer(data) {
    var pc = this.pc;
    var off = new RTCSessionDescription(data);
    pc.setRemoteDescription(off, ()=>{
      pc.createAnswer((answer)=>{
        pc.setLocalDescription(answer, ()=>{
          console.log('success setLocalDescription');
          this.onSignalingMessage(ACTIONS.ANSWER, pc.localDescription);
        }, this.errorHandler);
      }, this.errorHandler, {});
    }, this.errorHandler);
  }

  acceptAnswer(data) {
    this.pc.setRemoteDescription(new RTCSessionDescription(data), (e)=>{
      console.log('success setRemoteDescription');
    }, this.errorHandler);
  }

  errorHandler(err){
    console.error(err);
  }

  close(){
    this.pc.close();
  }
}

export class P2PTransport extends DataTransport{
  constructor(onChannel, basePath, secToken){
    super(onChannel, basePath, secToken);
    this.send = this.send.bind(this);

    this.actionHandler = {
      [ACTIONS.READY]: (p)=>this.send(ACTIONS.OFFERER_READY),
      [ACTIONS.OFFER]: (p)=>this.peer.acceptOffer(p),
      [ACTIONS.ANSWER]: (p)=>this.peer.acceptAnswer(p),
      [ACTIONS.CANDIDATE]: (p)=>this.peer.addCandidate(p),
      [ACTIONS.OFFERER_READY]: (p)=>this.send(ACTIONS.OFFERER_READY),
      [ACTIONS.ANSWERER_READY]: (p)=>this.startPeer(),
    }
  }

  startPeer(){
    this.stopPeer();
    this.peer = new Peer((transport, status) => console.log('peer', (status ? 'success' : 'fail')), this, this.send);
    this.peer.createOffer(this.defChannelType);
  }

  stopPeer(){
    if(this.peer) this.peer.close();
    delete this.peer;
  }

  start(defChannelType){
    super.start(defChannelType);
    this.stop();

    this.ssChannel = new WSChannel(this.basePath, CHANNEL_TYPE.SIGNALING, this.secToken);
    this.ssChannel.receiver = {
      onOpen: (channel)=>this.send(ACTIONS.OFFERER_READY),
      onMessage: (channel, data)=>{
        try{
          var obj = JSON.parse(data);
          var handler = this.actionHandler[obj.a];
          if(handler) handler(obj.p);
        }
        catch(e){
          console.log('ws onmsg', e);
        }
      },
      onClose: (channel)=>{},
    }
  }

  stop(){
    this.stopPeer();
    if(this.ssChannel) this.ssChannel.close();
    delete this.ssChannel;
  }

  send(a, p){
    if(this.ssChannel) this.ssChannel.send(JSON.stringify({a,p}));
  }

  createChannel(type){
    if(!this.peer) return;
    this.peer.createChannel(type);
  }
}

export class WSChannel extends ChannelBase{
  constructor(basePath, type, secToken){
    var wsUrl = basePath + type + (secToken ? ('?token=' + secToken) : '');
    super(new WebSocket(wsUrl));
    this.wsUrl = wsUrl;
  }

  retry(){
    clearTimeout(this.timer);
    this.timer = setTimeout(()=>{
      this.init(new WebSocket(this.wsUrl));
    }, 1000);
  }

  close(){
    clearTimeout(this.timer);
    super.close();
  }
}

export class WSTransport extends DataTransport{
  constructor(onChannel, basePath, secToken){
    super(onChannel, basePath, secToken);
    this.conns = [];
  }

  start(defChannelType){
    super.start(defChannelType);
    this.stop();
    this.createChannel(defChannelType);
  }

  stop(){
    this.conns.forEach((conn) => conn.close());
    this.conns = [];
  }

  createChannel(type){
    var channel = new WSChannel(this.basePath, type, this.secToken);
    this.conns.push(channel);
    this.onChannel(this, channel, type);
  }
}
