import MP4 from './mp4-generator.js'
import ExpGolomb from './exp-golomb.js'

function NALU(data){
  var header = data[4];
  new DataView(data.buffer).setUint32(0, data.length - 4);

  return{
    data,
    header,
    // ref_idc: (header >> 5) & 0x3,
    unit_type: header & 0x1f,
    forbidden_zero_bit: header & 0x80,
  }
}

var kNALUTypes = {
  NDR: 1,
  IDR: 5,
  SEI: 6,
  SPS: 7,
  PPS: 8,
  AUD: 9,
}

var kNALUStrings = {
  1: 'NDR',
  5: 'IDR',
  6: 'SEI',
  7: 'SPS',
  8: 'PPS',
  9: 'AUD',
};

function splitNalu(arr) {
  var result = [], idx = 0, lastIdx = -1, zeroes = 0, length = arr.byteLength;

  while(idx < length){
    var val = arr[idx++];

    if(zeroes < 3){
      if(val == 0) zeroes += 1;
      else zeroes = 0;
      continue;
    }

    if(val == 0) continue;

    if(val == 1 && idx < length){
      if(lastIdx >= 0) result.push(arr.subarray(lastIdx, idx - zeroes -1));
      lastIdx = idx - zeroes -1;
    }
    zeroes = 0;
  }
  if(lastIdx >= 0) result.push(arr.subarray(lastIdx, length));
  return result;
}

export function H264Track(opts){
  this.dts = 0;
  this.timescale = 1e5;

  var seq = 1, codec = '';
  var mdat = MP4.box('mdat'), mdatView = new DataView(mdat.buffer);
  var frameDuration = Math.round(this.timescale / (opts.fps || 60)) - 1;

  console.log('frameDuration', frameDuration, 'timescale', this.timescale);

  var flags = {
    // isNonSync: 1,
    // dependsOn: 1,
    // isLeading: 0,
    // degradPrio: 0,
    // paddingValue: 0,
    // isDependedOn: 0,
    // hasRedundancy: 0,
  };

  var sample = {
    cts: 0,
    // size: 0,
    duration: frameDuration,
    flags: flags,
  };

  var track = {
    id:  1,
    sps: [],
    pps: [],
    name: document.title,
    width: opts.width,
    height: opts.height,
    // pixelRatio: [1,1],
    duration: +Infinity,
    timescale: this.timescale,
    samples: [sample],
    // samples: [],
    // dataLen: 0,
  };

  this.feed = data => {
    var chunks = splitNalu(data);
    chunks.forEach(chunk => {
      var nalu = NALU(chunk);
      if(nalu.unit_type == kNALUTypes.SPS){
        track.sps = [new Uint8Array(nalu.data.subarray(4))];
        // var conf = new ExpGolomb(track.sps[0]).readSPS();
        // track.width = conf.width;
        // track.height = conf.height;
        // track.pixelRatio = conf.pixelRatio;
        codec = 'avc1.';
        for(var i = 1; i < 4; i++){
          var hex = track.sps[0][i].toString(16);
          if(hex.length < 2) hex = '0' + hex;
          codec += hex;
        }
      }
      else if(nalu.unit_type == kNALUTypes.PPS){
        track.pps = [new Uint8Array(nalu.data.subarray(4))];
      }
      else if(nalu.unit_type == kNALUTypes.IDR && track.sps.length && track.pps.length && codec.length){
        opts.onReady(codec);
        MP4.initSegment([track]).forEach(__out);
        this.feed = __feed.bind(this);
        this.feed(chunk);
      }
    });
  }

  var __out = (buffer, isKeyFrame) => opts.onData({buffer, isKeyFrame});

  var sampleLen = 10;

  var __feed = data =>{
    var nalu = NALU(data);
    var isKeyFrame = nalu.unit_type == kNALUTypes.IDR;

    if(!(nalu.unit_type == kNALUTypes.NDR || isKeyFrame) || nalu.forbidden_zero_bit != 0){
      console.warn('dropping nalu', kNALUStrings[nalu.unit_type], nalu);
      return;
    }

    sample.size = nalu.data.length;
    flags.isNonSync = isKeyFrame ? 0 : 1;
    flags.dependsOn = isKeyFrame ? 2 : 1;

    mdatView.setUint32(0, mdat.byteLength + nalu.data.byteLength);
    __out(MP4.moof(seq, this.dts, track));
    __out(Uint8Array.from(mdat));
    __out(nalu.data, isKeyFrame);

    seq += 1;
    this.dts += frameDuration;

    // var sample = {
    //   cts: 0,
    //   data: nalu.data,
    //   size: nalu.data.byteLength,
    //   duration: frameDuration,
    //   isKeyFrame,
    //   flags: {
    //     isNonSync: isKeyFrame ? 0 : 1,
    //     dependsOn: isKeyFrame ? 2 : 1,
    //   },
    // };

    // track.dataLen += sample.size;
    // track.samples.push(sample);

    // if(track.samples.length < sampleLen) return;

    // __out(MP4.moof(seq, this.dts, track));

    // mdatView.setUint32(0, mdat.byteLength + track.dataLen);
    // __out(Uint8Array.from(mdat));

    // while(track.samples.length){
    //   var sample = track.samples.shift();
    //   __out(sample.data, sample.isKeyFrame);
    // }

    // track.dataLen = 0;

    // seq += 1;
    // this.dts += frameDuration * sampleLen;
  }
}
