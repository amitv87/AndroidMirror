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


// Choppy but very little lag
// Using HTML5 audo context
export function AudioPlayerContext(mime){
  var audioCtx = new (window.AudioContext || window.webkitAudioContext)();
  var queue = [], minQlength = 10, byte_length = 0, play = buffer =>{
    var source = audioCtx.createBufferSource();
    source.buffer = buffer;
    source.connect(audioCtx.destination);
    source.start(0);
  }

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
