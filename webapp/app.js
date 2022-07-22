import './css/main.css';
import {initInteractions} from './js/interaction.js'
import {P2PTransport, WSTransport} from './js/transport.js'
import {InitVideo, requestPiP, MseH264Player, WasmH264Player, WasmWorkerH264Player, WebCodecH264Player} from './js/videoPlayers.js'
import {AudioPlayerMSE, AudioPlayerURL, AudioPlayerContext, WebCodecAudioPlayer} from './js/audioPlayers.js'

window.CHANNEL_TYPE = {
  VIDEO: '/video',
  AUDIO: '/audio',
  INPUT: '/input',
  SIGNALING: '/signaling',
}

var videoCont = document.getElementById('vidcont'), hashParams = {};
var videoPlayer, audioPlayer, mainChannel, videoChannel, audioChannel, inputChannel, transport, onDeviceEvent;

var sendMain = e => mainChannel && mainChannel.send(JSON.stringify(e));
var sendInput = e => inputChannel && inputChannel.send(JSON.stringify(e));

function broadcastMessage(msg){
  if(typeof __floaty_broadcast == "function") __floaty_broadcast(msg);
}

const receivers = {
  [CHANNEL_TYPE.VIDEO]:{
    onOpen: channel => {
      mainChannel = videoChannel = channel;
      console.log('video channel onOpen');
    },
    onMessage: (channel, data) => {
      if(typeof(data) != 'string') videoPlayer.play(data);
      else try{
        var obj = JSON.parse(data);
        var action = obj.a, data = obj.d;
        if(action == 'config'){
          window.conf = data;
          if(videoPlayer) videoPlayer.release();

          var Player = useWasm ? WasmH264Player : (hashParams.wasm ? WasmWorkerH264Player : MseH264Player);
          if(Player == MseH264Player && window.VideoDecoder) Player = WebCodecH264Player;

          videoPlayer = new Player({
            av: hashParams.av,
            use_gl: hashParams.gl,
            offscreen: hashParams.offscreen,
            displayOnCanvas: !hashParams.video,
            requestKeyFrame: () => sendMain({a: 'req_key_frame'}),
          });

          console.log('using', videoPlayer);
          broadcastMessage(data);

          if(conf.caps.audio && !receivers[CHANNEL_TYPE.AUDIO].channel) transport.createChannel(CHANNEL_TYPE.AUDIO);
          if(conf.caps.input && !receivers[CHANNEL_TYPE.INPUT].channel) transport.createChannel(CHANNEL_TYPE.INPUT);
        }
        onDeviceEvent(action, data);
      }
      catch(e){
        console.error('video channel onMessage', data, e);
      }
    },
    onClose: channel => {
      if(mainChannel) onDeviceEvent('lost');
      mainChannel = videoChannel = null;

      [CHANNEL_TYPE.AUDIO, CHANNEL_TYPE.INPUT].forEach((chType)=>{
        if(receivers[chType].channel) receivers[chType].channel.close();
        receivers[chType].channel = null;
      });

      inputChannel = null;
      audioChannel = null;

      console.log('video channel onClose')
    },
  },
  [CHANNEL_TYPE.AUDIO]:{
    onOpen: channel => {
      audioChannel = channel;
      console.log('audio channel onOpen');
    },
    onMessage: (channel, data) => {
      if(typeof(data) != 'string') audioPlayer.play(data);
      else console.log('audio channel onMessage', data);
    },
    onClose: channel => {
      audioChannel = null;
      console.log('audio channel onClose')
    },
  },
  [CHANNEL_TYPE.INPUT]:{
    onOpen: channel => {
      inputChannel = channel;
      console.log('input channel onOpen');
    },
    onMessage: (channel, data) => {
      console.log('input channel onMessage', data);
    },
    onClose: channel => {
      inputChannel = null;
      console.log('input channel onClose');
    },
  },
};

function stopTransport(){
  if(transport) transport.stop();
  transport = null;
}

function startTransport(){
  if(transport) return;
  var hash = window.location.hash.substring(1);
  hash.split('&').map(hk => {
    var temp = hk.split('=');
    hashParams[temp[0]] = temp[1];
  });

  var Transport = (hashParams.p2p ? P2PTransport : WSTransport);
  var baseWSPath = window.location.protocol.replace('http', 'ws') + '//' + (hashParams.wsHost ? hashParams.wsHost : window.location.host);
  console.log('baseWSPath', baseWSPath);
  transport = new Transport((transport, channel, label)=>{
    var receiver = receivers[label];
    if(receiver.channel) receiver.channel.close();
    receiver.channel = channel;
    channel.receiver = receiver;
  }, baseWSPath, hashParams.token);

  transport.start(CHANNEL_TYPE.VIDEO);
}

window.start = function(button){
  if(button) button.remove();
  startTransport();

  var ap_ctr = AudioPlayerURL;
  ap_ctr = AudioPlayerMSE;
  ap_ctr = window.AudioDecoder ? WebCodecAudioPlayer : AudioPlayerContext;

  if(!audioPlayer) audioPlayer = new ap_ctr('audio/aac');

  console.log('using', audioPlayer);

  onDeviceEvent = initInteractions(videoCont, requestPiP, sendMain, sendInput, InitVideo(videoCont));
};
