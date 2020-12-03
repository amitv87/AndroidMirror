import fs from 'fs';
import WebSocket from 'ws';
import { H264Track } from 'webapp/js/h264Muxer.js';

global.document = {
  title: 'node.js',
}

var track, file;

var dest = process.argv[2] || './scr.mp4';

const ws = new WebSocket('ws://192.168.0.137:5050/video');

ws.on('open', function open() {
  console.log('open');
});

ws.on('message', function incoming(data) {
  if(typeof data == 'string'){
    var obj = JSON.parse(data);
    var action = obj.a, data = obj.d;
    if(action == 'config'){
      var conf = data;
      console.log('conf', conf);

      file = fs.createWriteStream(dest);

      track = new H264Track({
        fps: conf.fps || 60,
        width: conf.width,
        height: conf.height,
        onData: data => {
          if(file) file.write(data.buffer);
          else console.log('onData', data);
        },
        onReady: (codec)=>{
          console.log('onReady', codec);
        }
      });
    }
  }
  else if(track) track.feed(new Uint8Array(data));
});
