// Best quality
// Starts with very little lag but builds up over time
// Using mediasource extensions (works only on chrome for now)
export function AudioPlayerMSE(mime) {
  var queue = [], buffer;
  var mediaSource = new MediaSource();

  var a_ms = new Audio();
  a_ms.src = URL.createObjectURL(mediaSource);
  a_ms.autoplay = true;
  a_ms.play();

  mediaSource.addEventListener('sourceopen', ()=>{
    buffer = mediaSource.addSourceBuffer(mime);
    mediaSource.duration = +Infinity;
    buffer.addEventListener('update', ()=>{
      if(queue.length > 0 && !buffer.updating)
        buffer.appendBuffer(queue.shift());
    });
  });

  this.play = data =>{
    if(!buffer || buffer.updating) queue.push(data);
    else buffer.appendBuffer(data);
  }
}


// Choppy but very little lag
// Using HTML5 audio tag element (hacky stuff)
export function AudioPlayerURL(mime){
  var buff = [], audios = [], src = '';
  var buffer_counter = 0, a_counter = 0, audio_s_length = 4, buffer_length = 10;
  for(var i = 0; i < audio_s_length; i++){
    var a = new Audio();
    a.autoplay = true;
    a.normalize();
    audios.push(a);
  }
  var audio = audios[0];

  this.play = data =>{
    buffer_counter++;
    buff.push(new Uint8Array(data));
    if(buffer_counter == buffer_length){
      buffer_counter = 0;
      // audio.muted = true;
      audio = audios[a_counter];
      URL.revokeObjectURL(src);
      src = URL.createObjectURL(new Blob(buff, {type:mime}))
      audio.src = src;
      audio.muted = false;
      buff = [];
      a_counter++;
      if(a_counter >= audio_s_length)
        a_counter = 0;
    }
  }
}

const AudioContext = window.AudioContext || window.webkitAudioContext || window.mozAudioContext;

function AudioContextRenderer(audioCtx){
  var duration = 0;
  this.input = ab => {
    var source = audioCtx.createBufferSource();
    source.buffer = ab;
    source.connect(audioCtx.destination);
    // console.log('diff', duration - audioCtx.currentTime, audioCtx.currentTime, duration);
    if(duration < audioCtx.currentTime) duration = audioCtx.currentTime;
    source.start(duration);
    duration += ab.duration;
  };
}

// Choppy but very little lag
// Using HTML5 audo context
export function AudioPlayerContext(mime){
  var queue = [], minQlength = 10, byte_length = 0;
  var audioCtx = new AudioContext({sampleRate: 44100});
  var renderer = new AudioContextRenderer(audioCtx);
  var play = renderer.input.bind(renderer);

  // safari hack
  play(audioCtx.createBuffer(1, 1, audioCtx.sampleRate));

  this.play = data =>{
    queue.push(data);
    byte_length += data.byteLength;
    if(queue.length >= minQlength){
      var length = 0;
      var arr = new Uint8Array(byte_length);
      byte_length = 0;

      while(queue.length){
        var abuff = queue.shift();
        arr.set(new Uint8Array(abuff), length);
        length += abuff.byteLength;
      }

      audioCtx.decodeAudioData(arr.buffer, play, e => console.error('ctx decoder err', e));
    }
  }
}

/*
// https://wiki.multimedia.cx/index.php/ADTS
// https://wiki.multimedia.cx/index.php/MPEG-4_Audio
typedef struct adts_header{
  uint16_t sync_word          :12;  // 0xfff
  uint8_t mpeg_version        :1;   // 0 -> MPEG-4, 1 -> MPEG-2
  uint8_t layer               :2;   // always 0
  uint8_t no_crc              :1;   // 1 -> no CRC, 0 -> CRC is present
  uint8_t profile             :2;   // MPEG-4 Audio Object Type minus 1
  uint8_t smpl_freq_idx       :4;   // MPEG-4 Sampling Frequency Index (15 is forbidden)
  uint8_t priv                :1;   // guaranteed never to be used by MPEG, set to 0 when encoding, ignore when decoding
  uint8_t channel_cfg         :3;   // MPEG-4 Channel Configuration
  uint8_t originality         :1;   // 1 -> signals originality of the audio, 0 -> no originality
  uint8_t home                :1;   // 1 -> signals  home usage of the audio, 0 -> no home usage
  uint8_t copyright_id        :1;   // Copyright ID bit, the next bit of a centrally registered copyright identifier.
  uint8_t copyright_id_start  :1;   // Copyright ID start, signals that this frame's Copyright ID bit is the first one by setting 1 and 0 otherwise.
  uint16_t frame_length       :13;  // Frame length, length of the ADTS frame including headers and CRC check.
  uint16_t buffer_fullness    :11;  // states the bit-reservoir per frame.
  uint8_t frame_cnt           :2;   // Number of AAC frames (RDBs (Raw Data Blocks)) in ADTS frame minus 1.
  uint16_t crc                :16;  // CRC check (as of ISO/IEC 11172-3, subclause 2.4.3.1), if Protection absent is 0
};
*/

const adts_profiles = [
  'AAC Main',
  'AAC LC',
  'AAC SSR',
  'AAC LTP',
  'SBR',
  'AAC Scalable',
];
const adts_sample_rates = [96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350];

export function getAudioConfigFromADTS(data){
  var sync_word = data[0] + ((data[1] >> 4) << 8);
  var mpeg_version = (data[1] & 0xf) >> 3;
  var layer = ((data[1] & 0xf) >> 1) & 1;
  var no_crc = data[1] & 1;
  var profile = data[2] >> 6;
  var smpl_freq_idx = (data[2] >> 2) & 0x0f;
  var priv = (data[2] >> 1) & 1;
  var channel_cfg = ((data[2] & 1) << 2) | (data[3] >> 6);

  var originality = (data[3] >> 2) & 1;
  var home = (data[3] >> 3) & 1;
  var copyright_id = (data[3] >> 4) & 1;
  var copyright_id_start = (data[3] >> 5) & 1;
  var frame_length = ((data[3] & 0x3) << 11) + (data[4] << 3) | (data[5] >> 5);
  var buffer_fullness = ((data[5] & 0x1f) << 6) | (data[6] >> 2)
  var frame_cnt = data[6] & 0x3;
  var crc = no_crc ? undefined : (data[7] << 8) | data[8];

  return {
    codec: 'mp4a.40.' + (profile + 1),
    profile: adts_profiles[profile],
    sampleRate: adts_sample_rates[smpl_freq_idx],
    numberOfChannels: channel_cfg,
    adts_header: {
      sync_word,
      mpeg_version,
      layer,
      no_crc,
      profile,
      smpl_freq_idx,
      priv,
      channel_cfg,
      originality,
      home,
      copyright_id,
      copyright_id_start,
      frame_length,
      buffer_fullness,
      frame_cnt,
      crc,
    },
    adts_pkt: data,
  };
}

// good quality with minimal latency
export function WebCodecAudioPlayer(mime){
  var audioCtx, renderer, decoder = new AudioDecoder({
    output: frame => {
      var num_channels = frame.numberOfChannels;
      var ab = audioCtx.createBuffer(num_channels, frame.numberOfFrames, audioCtx.sampleRate);
      for(var ch = 0; ch < num_channels; ch++){
        var conf = {planeIndex: ch};
        var buff = new ArrayBuffer(frame.allocationSize(conf));
        frame.copyTo(buff, conf);
        ab.copyToChannel(new Float32Array(buff), ch);
      }
      frame.close();
      renderer.input(ab);
    },
    error : e => console.log(e, e.code),
  });

  var play = data => decoder.decode(new EncodedAudioChunk({data: data, type: 'key', timestamp: 0}));

  this.play = data => {
    if(mime.indexOf('aac') < 0) return;
    var config = getAudioConfigFromADTS(new Uint8Array(data));
    console.log('config', config);
    decoder.configure(config);
    audioCtx = new AudioContext({sampleRate: config.sampleRate});
    renderer = new AudioContextRenderer(audioCtx);
    play(data);
    this.play = play.bind(this);
  }
}
