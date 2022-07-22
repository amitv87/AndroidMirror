import { H264Track, NALU, kNALUTypes, splitNalu } from './h264Muxer.js';
import { WebGLUtils } from './webgl_utils.js'
import { WebGLRenderer } from './webgl_renderer.js'

var iscssminmaxsupported = false;
var videoCont, glRenderer, video, canvas, worker;

function getVid(){
  if(!video){
    video = document.createElement('video');
    // video.controls = true;
    video.style.width = '100%';
    videoCont.append(video);
    video.onleavepictureinpicture = () => setTimeout(video.play.bind(video), 1);
  }
  resizePlayerView();
  return video;
}

function clearCanvas(){
  if(canvas) canvas.remove();
  canvas = undefined;
}

function getCanvas(displayOnCanvas){
  if(!canvas){
    canvas = document.createElement('canvas');
    if(displayOnCanvas || !canvas.captureStream){
      canvas.style.width = '100%';
      canvas.style.height = '100%';
      videoCont.append(canvas);
    }
    else setTimeout(()=>{
      var vid = getVid();
      vid.srcObject = canvas.captureStream();
      vid.play();
    });
  }
  resizePlayerView();
  return canvas;
}

function getRenderer(displayOnCanvas){
  if(!glRenderer) glRenderer = new WebGLRenderer(getCanvas(displayOnCanvas));
  resizePlayerView();
  return glRenderer;
}

function getWorker(opts){
  if(!worker) worker = new Worker(opts.av ? '/js/wasm/libav.js' : '/js/wasm/openh264.js');
  return worker;
}

function hexdump(buffer, blockSize) {
  if(buffer instanceof ArrayBuffer && buffer.byteLength !== undefined){
    buffer = String.fromCharCode.apply(String, [].slice.call(new Uint8Array(buffer)));
  }else if(Array.isArray(buffer)){
    buffer = String.fromCharCode.apply(String, buffer);
  }else if (buffer.constructor === Uint8Array) {
    buffer = String.fromCharCode.apply(String, [].slice.call(buffer));
  }else{
    console.log("Error: buffer is unknown...");
    return false;
  }

  blockSize = blockSize || 16;
  var lines = [];
  var hex = "0123456789ABCDEF";
  for (var b = 0; b < buffer.length; b += blockSize) {
    var block = buffer.slice(b, Math.min(b + blockSize, buffer.length));
    var addr = ("0000" + b.toString(16)).slice(-4);
    var codes = block.split('').map(function (ch) {
      var code = ch.charCodeAt(0);
      return " " + hex[(0xF0 & code) >> 4] + hex[0x0F & code];
    }).join("");
    codes += "   ".repeat(blockSize - block.length);
    var chars = block.replace(/[\x00-\x1F\x20]/g, '.');
    chars +=  " ".repeat(blockSize - block.length);
    lines.push(addr + " " + codes + "  " + chars);
  }
  return lines.join("\n");
}

function resizePlayerView(){
  if(!window.conf) return;
  var ar = conf.width / conf.height;
  if(iscssminmaxsupported){
    videoCont.style.width = 'min(100vh * ' + ar + ', 100vw)';
    videoCont.style.height = 'min(100vw / ' + ar + ', 100vh)';
  }
  else{
    videoCont.style.width = Math.min(window.innerHeight * ar, window.innerWidth) + 'px';
    videoCont.style.height = Math.min(window.innerWidth / ar, window.innerHeight) + 'px';
  }
}

function cssPropertyValueSupported(prop, value) {
  var d = document.createElement('div');
  d.style[prop] = value;
  return d.style[prop] === value;
}

export function InitVideo(cont){
  if(!videoCont){
    videoCont = cont;
    if(cssPropertyValueSupported('width', 'min(100vh, 50px)')) iscssminmaxsupported = true;
    else window.addEventListener('resize', resizePlayerView, false);
  }
  return resizePlayerView;
}

export function requestPiP(){
  if(document.pictureInPictureElement)
    document.exitPictureInPicture().catch((e)=>console.error('exitPictureInPicture error', e));
  else
    video && video.requestPictureInPicture().catch((e) => console.error('requestPictureInPicture error', e));
}

export function MseH264Player(opts){
  var track, sourceBuffer, syncJob;
  var queue = [], sbqueue = [], mediaSource = new MediaSource(), vid = getVid();

  vid.onplay = e => vid.oncanplay = null;
  vid.onpause = e => console.log('onpause');
  vid.oncanplay = e => console.log('oncanplay') || vid.play();

  var requestKeyFrame = ()=>{
    if(vid.paused) return;
    var diff = (track.dts / track.timescale) - vid.currentTime;
    console.log('diff', diff);
    if(Math.abs(diff) >= 0.2) opts.requestKeyFrame();
  }

  vid.src = URL.createObjectURL(mediaSource);

  mediaSource.addEventListener('sourceopen', ()=>{
    console.log('MseH264Player on sourceopen');

    var append = (data)=>{
      sourceBuffer.appendBuffer(data.buffer);
      if(data.isKeyFrame){
        console.log('got keyFrame', data.buffer.length);
        if(!vid.paused) setTimeout(() => vid.currentTime = (track.dts / track.timescale), 1);
      }
    }

    track = new H264Track({
      fps: conf.fps || 60,
      width: conf.width,
      height: conf.height,
      onData: (data)=>{
        // console.log(hexdump(data));
        if(!sourceBuffer || sourceBuffer.updating || sbqueue.length > 0) sbqueue.push(data);
        else append(data);
      },
      onReady: (codec)=>{
        if(!vid.src) return;
        console.log('MseH264Player onReady', codec);
        sourceBuffer = mediaSource.addSourceBuffer('video/mp4; codecs="' + codec + '"');

        window.sb = sourceBuffer;
        mediaSource.duration = +Infinity;
        sourceBuffer.addEventListener('error', e => console.log('on error', e));
        sourceBuffer.addEventListener('update', e => sbqueue.length > 0 && !sourceBuffer.updating && append(sbqueue.shift()));

        setTimeout(e => vid.onplay = e => {
          vid.oncanplay = null
          opts.requestKeyFrame.bind(opts);
          clearInterval(syncJob);
          requestKeyFrame();
          syncJob = setInterval(requestKeyFrame, 1000);
        }, 100);

        syncJob = setInterval(requestKeyFrame, 1000);
      }
    });
    this.play = data => track.feed(new Uint8Array(data));
    while(queue.length > 0) this.play(queue.shift());
  });

  this.play = data => queue.push(data);

  this.release = ()=>{
    sbqueue = [];
    vid.src = '';
    vid.removeAttribute('src');
    vid.onplay = vid.onpause = vid.oncanplay = undefined;
    clearInterval(syncJob);
  }
}

export function WasmH264Player(opts){
  var renderer, decoder = new Module.H264Decoder();

  decoder.init();

  this.play = buffer => {
    var _frame = decoder.decode(buffer);

    if(_frame.y.width <= 0) return;

    var frame = {};

    ['y','u','v'].forEach( x => {
      var plane = _frame[x];
      frame[x] = {
        data: plane.data,
        width: plane.width,
        height: plane.height,
        stride: plane.stride,
      };
    });

    if(!renderer){
      renderer = getRenderer(opts.displayOnCanvas);
      console.log('WasmH264Player stride: ' + frame.y.stride + ', dims: ' + frame.y.width + 'x' + frame.y.height);
    }

    renderer.drawFrame(frame);
  }

  this.release = () => decoder.delete();
}

export function WasmWorkerH264Player(opts){
  var renderer, offscreenCanvas, canvas;

  var worker = getWorker(opts);

  if(opts.offscreen && document.createElement('canvas').transferControlToOffscreen && (canvas = getCanvas(opts.displayOnCanvas))){
    offscreenCanvas = canvas.transferControlToOffscreen();
    worker.postMessage(offscreenCanvas, [offscreenCanvas]);
  }

  worker.onmessage = e => {
    var frame = e.data;

    if(!renderer){
      renderer = getRenderer(opts.displayOnCanvas);
      console.log('WasmWorkerH264Player stride: ' + frame.y.stride + ', dims: ' + frame.y.width + 'x' + frame.y.height);
    }

    renderer.drawFrame(frame);
  }

  this.play = data => worker.postMessage(data, [data]);
  this.release = () => {
    // worker.terminate();
    if(offscreenCanvas) clearCanvas();
  }
}

export function WebCodecH264Player(opts){
  var ctx_gl, ctx_2d, canvas = getCanvas(opts.displayOnCanvas);

  var render_2d = frame => {
    if(!ctx_2d){
      console.log('init ctx_2d');
      canvas.width = frame.codedWidth;
      canvas.height = frame.codedHeight;
      ctx_2d = canvas.getContext('2d');
    }
    ctx_2d.drawImage(frame, 0, 0);
    frame.close();
  };

  var render_gl = async frame => {
    if(!ctx_gl) {
      console.log('init ctx_gl');
      canvas.width = frame.codedWidth;
      canvas.height = frame.codedHeight;
      ctx_gl = canvas.getContext("webgl");
      ctx_gl.viewport(0, 0, canvas.width, canvas.height);
      ctx_gl.pixelStorei(ctx_gl.UNPACK_FLIP_Y_WEBGL, true);
      ctx_gl.pixelStorei(ctx_gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL, false);
      ctx_gl.bindTexture(ctx_gl.TEXTURE_2D, ctx_gl.createTexture());
      ctx_gl.texParameteri(ctx_gl.TEXTURE_2D, ctx_gl.TEXTURE_MIN_FILTER, ctx_gl.NEAREST);
      ctx_gl.texParameteri(ctx_gl.TEXTURE_2D, ctx_gl.TEXTURE_MAG_FILTER, ctx_gl.NEAREST);
      ctx_gl.texParameteri(ctx_gl.TEXTURE_2D, ctx_gl.TEXTURE_WRAP_S, ctx_gl.CLAMP_TO_EDGE);
      ctx_gl.texParameteri(ctx_gl.TEXTURE_2D, ctx_gl.TEXTURE_WRAP_T, ctx_gl.CLAMP_TO_EDGE);
      ctx_gl.uniform1i(ctx_gl.getUniformLocation(WebGLUtils.setupTexturedQuad(ctx_gl), "tex"), 0);
    }
    ctx_gl.texImage2D(ctx_gl.TEXTURE_2D, 0, ctx_gl.RGB, ctx_gl.RGB, ctx_gl.UNSIGNED_BYTE, frame);
    WebGLUtils.drawQuad(ctx_gl, [0, 0, 0, 255]);
    ctx_gl.finish();
    frame.close();
  }

  var decoder = new VideoDecoder({
    output: opts.use_gl ? render_gl : render_2d,
    error : e => console.log(e, e.code),
  });

  var type = 'key';
  var _play = data => decoder.decode(new EncodedVideoChunk({type: type, data: data, timestamp: 0}));

  this.play = data => {
    data = new Uint8Array(data);
    var chunks = splitNalu(data);

    chunks.forEach(chunk => {
      var nalu = NALU(chunk, true);
      if(nalu.unit_type == kNALUTypes.SPS){
        this.sps = nalu.data;
        var codec = 'avc1.';
        for(var i = 5; i < 8; i++){
          var hex = nalu.data[i].toString(16);
          if(hex.length < 2) codec += '0';
          codec += hex;
        }

        var config = {
          codec: codec,
          optimizeForLatency: true,
          hardwareAcceleration: 'prefer-hardware',
        };

        console.log('config', config);
        decoder.configure(config);

        opts.requestKeyFrame();
      }
      else if(nalu.unit_type == kNALUTypes.PPS){
        this.pps = nalu.data;
      }
      else if(nalu.unit_type == kNALUTypes.IDR && this.sps.length && this.pps.length){
        this.play = _play;
        var init_frame = new Uint8Array([...this.sps, ...this.pps, ...nalu.data]);
        this.play(init_frame);
        type = 'delta';
      }
    });
  }
  this.release = e => {};
}
