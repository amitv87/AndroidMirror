import { H264Track } from './h264Muxer.js';
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
