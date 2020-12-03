// https://github.com/video-dev/hls.js/blob/master/src/remux/mp4-generator.js

var types = {};
var u8 = arg0 => new Uint8Array(arg0);
var fourcc = type => types[type] || (types[type] = u8(type.split('').map(c => c.charCodeAt(0))));

function box(type) {
  var payload = Array.prototype.slice.call(arguments, 1),
    size = 8, i = payload.length, len = i;

  while (i--) size += payload[i].byteLength;

  var result = u8(size);
  result[0] = (size >> 24) & 0xff;
  result[1] = (size >> 16) & 0xff;
  result[2] = (size >> 8) & 0xff;
  result[3] = size & 0xff;
  result.set(fourcc(type), 4);

  for (i = 0, size = 8; i < len; i++) {
    result.set(payload[i], size);
    size += payload[i].byteLength;
  }
  return result;
}

function hdlr(type) {
  return box('hdlr', u8([
    0x00, // version 0
    0x00, 0x00, 0x00, // flags
    0x6d, 0x68, 0x6c, 0x72, // pre_defined
    // 0x00, 0x00, 0x00, 0x00, // pre_defined
    0x76, 0x69, 0x64, 0x65, // handler_type: 'vide'
    0x00, 0x00, 0x00, 0x00, // reserved
    0x00, 0x00, 0x00, 0x00, // reserved
    0x00, 0x00, 0x00, 0x00, // reserved
    0x56, 0x69, 0x64, 0x65,
    0x6f, 0x48, 0x61, 0x6e,
    0x64, 0x6c, 0x65, 0x72, 0x00 // name: 'VideoHandler'
  ]));
}

function mdat(data) {
  return box('mdat', data);
}

function mdhd(timescale, duration) {
  return box('mdhd', u8([
    0x00, // version 0
    0x00, 0x00, 0x00, // flags
    0x00, 0x00, 0x00, 0x02, // creation_time
    0x00, 0x00, 0x00, 0x03, // modification_time
    (timescale >> 24) & 0xFF,
    (timescale >> 16) & 0xFF,
    (timescale >> 8) & 0xFF,
    timescale & 0xFF, // timescale
    (duration >> 24),
    (duration >> 16) & 0xFF,
    (duration >> 8) & 0xFF,
    duration & 0xFF,
    0x55, 0xc4, // 'und' language (undetermined)
    0x00, 0x00
  ]));
}

function mdia(track) {
  return box('mdia'
    ,mdhd(track.timescale, track.duration)
    ,hdlr(track.type)
    ,minf(track)
  );
}

function mfhd(sequenceNumber) {
  return box('mfhd', u8([
    0x00,
    0x00, 0x00, 0x00, // flags
    (sequenceNumber >> 24),
    (sequenceNumber >> 16) & 0xFF,
    (sequenceNumber >> 8) & 0xFF,
    sequenceNumber & 0xFF // sequence_number
  ]));
}

function minf(track) {
  return box('minf',
    box('vmhd', u8([
      0x00, // version
      0x00, 0x00, 0x01, // flags
      0x00, 0x00, // graphicsmode
      0x00, 0x00,
      0x00, 0x00,
      0x00, 0x00, // opcolor
    ])),
    box('dinf', box('dref', u8([
      0x00, // version 0
      0x00, 0x00, 0x00, // flags
      0x00, 0x00, 0x00, 0x01, // entry_count
      0x00, 0x00, 0x00, 0x0c, // entry_size
      0x75, 0x72, 0x6c, 0x20, // 'url' type
      0x00, // version 0
      0x00, 0x00, 0x01, // entry_flags
    ]))),
    stbl(track)
  );
}

function moof(sn, baseMediaDecodeTime, track) {
  return box('moof', mfhd(sn), traf(track, baseMediaDecodeTime));
}

function moov(tracks) {
  let
    i = tracks.length,
    boxes = [];

  while (i--) {
    boxes[i] = trak(tracks[i]);
  }

  return box.apply(null, ['moov', mvhd(tracks[0].timescale, tracks[0].duration)].concat(boxes).concat(mvex(tracks)));
}

function mvex(tracks) {
  let
    i = tracks.length,
    boxes = [];

  while (i--) {
    boxes[i] = trex(tracks[i]);
  }

  return box.apply(null, ['mvex'].concat(boxes));
}

function mvhd(timescale, duration) {
  let
    bytes = u8([
      0x00, // version 0
      0x00, 0x00, 0x00, // flags
      0x00, 0x00, 0x00, 0x01, // creation_time
      0x00, 0x00, 0x00, 0x02, // modification_time
      (timescale >> 24) & 0xFF,
      (timescale >> 16) & 0xFF,
      (timescale >> 8) & 0xFF,
      timescale & 0xFF, // timescale
      (duration >> 24),
      (duration >> 16) & 0xFF,
      (duration >> 8) & 0xFF,
      duration & 0xFF,
      0x00, 0x01, 0x00, 0x00, // 1.0 rate
      0x01, 0x00, // 1.0 volume
      0x00, 0x00, // reserved
      0x00, 0x00, 0x00, 0x00, // reserved
      0x00, 0x00, 0x00, 0x00, // reserved
      0x00, 0x01, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00,
      0x00, 0x01, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00,
      0x40, 0x00, 0x00, 0x00, // transformation: unity matrix
      0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00, // pre_defined
      0xff, 0xff, 0xff, 0xff // next_track_ID
    ]);
  return box('mvhd', bytes);
}

function sdtp(track) {
  let
    samples = track.samples || [],
    bytes = u8(4 + samples.length),
    flags,
    i;
  // leave the full box header (4 bytes) all zero
  // write the sample table
  for (i = 0; i < samples.length; i++) {
    flags = samples[i].flags;
    bytes[i + 4] = (flags.dependsOn << 4) |
      (flags.isDependedOn << 2) |
      (flags.hasRedundancy);
  }

  return box('sdtp', bytes);
}

function stbl(track) {
  var stxx = u8(new Array(8).fill(0));
  return box('stbl'
    ,stsd(track)
    ,box('stts', stxx)
    ,box('stsc', stxx)
    ,box('stsz', u8(new Array(12).fill(0)))
    ,box('stco', stxx)
  );
}

function avc1(track) {
  var sps = [], pps = [], i, data, len;
  // assemble the SPSs

  for (i = 0; i < track.sps.length; i++) {
    data = track.sps[i];
    len = data.byteLength;
    sps.push((len >>> 8) & 0xFF);
    sps.push((len & 0xFF));

    // SPS
    sps = sps.concat(Array.prototype.slice.call(data));
  }

  // assemble the PPSs
  for (i = 0; i < track.pps.length; i++) {
    data = track.pps[i];
    len = data.byteLength;
    pps.push((len >>> 8) & 0xFF);
    pps.push((len & 0xFF));
    pps = pps.concat(Array.prototype.slice.call(data));
  }

  var avcc = box('avcC', u8([
    0x01, // version
    sps[3], // profile
    sps[4], // profile compat
    sps[5], // level
    0xff, // lengthSizeMinusOne, hard-coded to 4 bytes
  ].concat([track.sps.length], sps, [track.pps.length], pps)));
  var width = track.width, height = track.height;
  // var hSpacing = track.pixelRatio[0], vSpacing = track.pixelRatio[1];

  var compressor_name = new Array(33).fill(0);
  track.name.split('').forEach((c,i) => {
    if(i < compressor_name.length - 1) compressor_name[i] = c.charCodeAt(0);
  });

  return box('avc1', u8([
      0x00, 0x00, 0x00, // reserved
      0x00, 0x00, 0x00, // reserved
      0x00, 0x01, // data_reference_index
      0x00, 0x00, // pre_defined
      0x00, 0x00, // reserved
      0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00, // pre_defined
      (width >> 8) & 0xFF,
      width & 0xff, // width
      (height >> 8) & 0xFF,
      height & 0xff, // height
      0x00, 0x48, 0x00, 0x00, // horizresolution
      0x00, 0x48, 0x00, 0x00, // vertresolution
      0x00, 0x00, 0x00, 0x00, // reserved
      0x00, 0x01, // frame_count
    ].concat(compressor_name, [
      0x18, // depth = 24
      0xff, 0xff, // color_table_index
    ]))
    ,avcc
  );
}

function stsd(track) {
  return box('stsd', u8([
      0x00, // version 0
      0x00, 0x00, 0x00, // flags
      0x00, 0x00, 0x00, 0x01, // entry_count
    ])
    ,avc1(track)
  );
}

function tkhd(track) {
  let id = track.id,
    duration = track.duration,
    width = track.width,
    height = track.height;
  return box('tkhd', u8([
    0x00, // version 0
    0x00, 0x00, 0x07, // flags
    0x00, 0x00, 0x00, 0x00, // creation_time
    0x00, 0x00, 0x00, 0x00, // modification_time
    (id >> 24) & 0xFF,
    (id >> 16) & 0xFF,
    (id >> 8) & 0xFF,
    id & 0xFF, // track_ID
    0x00, 0x00, 0x00, 0x00, // reserved
    (duration >> 24),
    (duration >> 16) & 0xFF,
    (duration >> 8) & 0xFF,
    duration & 0xFF,
    0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, // reserved
    0x00, 0x00, // layer
    0x00, 0x00, // alternate_group
    0x00, 0x00, // non-audio track volume
    0x00, 0x00, // reserved
    0x00, 0x01, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00,
    0x00, 0x01, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00,
    0x40, 0x00, 0x00, 0x00, // transformation: unity matrix
    (width >> 8) & 0xFF,
    width & 0xFF,
    0x00, 0x00, // width
    (height >> 8) & 0xFF,
    height & 0xFF,
    0x00, 0x00 // height
  ]));
}

function traf(track, baseMediaDecodeTime) {
  let sampleDependencyTable = sdtp(track), id = track.id;
  return box('traf',
    box('tfhd', u8([
      0x00, // version 0
      0x00, 0x00, 0x00, // flags
      (id >> 24) & 0xFF,
      (id >> 16) & 0xFF,
      (id >> 8) & 0xFF,
      id & 0xFF, // track_ID
    ])),
    box('tfdt', u8([
      0x00, // version 1
      0x00, 0x00, 0x00, // flags
      (baseMediaDecodeTime >> 24) & 0XFF,
      (baseMediaDecodeTime >> 16) & 0XFF,
      (baseMediaDecodeTime >> 8) & 0XFF,
      (baseMediaDecodeTime & 0xFF),
    ])),
    trun(track,
      sampleDependencyTable.length +
      16 + // tfhd
      16 + // tfdt
      8 + // traf header
      16 + // mfhd
      8 + // moof header
      8), // mdat header
    sampleDependencyTable
  );
}

function trak(track) {
  track.duration = track.duration || 0xffffffff;
  return box('trak', tkhd(track), mdia(track));
}

function trex(track) {
  let id = track.id;
  return box('trex', u8([
    0x00, // version 0
    0x00, 0x00, 0x00, // flags
    (id >> 24) & 0xFF,
    (id >> 16) & 0xFF,
    (id >> 8) & 0xFF,
    id & 0xFF, // track_ID
    0x00, 0x00, 0x00, 0x01, // default_sample_description_index
    0x00, 0x00, 0x00, 0x00, // default_sample_duration
    0x00, 0x00, 0x00, 0x00, // default_sample_size
    0x00, 0x01, 0x00, 0x01 // default_sample_flags
  ]));
}

function trun(track, offset) {
  let samples = track.samples || [],
    len = samples.length,
    arraylen = 12 + (16 * len),
    array = u8(arraylen),
    i, sample, duration, size, flags, cts;
  offset += 8 + arraylen;
  array.set([
    0x00, // version 0
    0x00, 0x0f, 0x01, // flags
    (len >>> 24) & 0xFF,
    (len >>> 16) & 0xFF,
    (len >>> 8) & 0xFF,
    len & 0xFF, // sample_count
    (offset >>> 24) & 0xFF,
    (offset >>> 16) & 0xFF,
    (offset >>> 8) & 0xFF,
    offset & 0xFF // data_offset
  ], 0);
  for (i = 0; i < len; i++) {
    sample = samples[i];
    duration = sample.duration;
    size = sample.size;
    flags = sample.flags;
    cts = sample.cts;
    array.set([
      (duration >>> 24) & 0xFF,
      (duration >>> 16) & 0xFF,
      (duration >>> 8) & 0xFF,
      duration & 0xFF, // sample_duration
      (size >>> 24) & 0xFF,
      (size >>> 16) & 0xFF,
      (size >>> 8) & 0xFF,
      size & 0xFF, // sample_size
      flags.dependsOn, flags.isNonSync, 0, 0,
      // (flags.isLeading << 2) | flags.dependsOn,
      // (flags.isDependedOn << 6) | (flags.hasRedundancy << 4) | (flags.paddingValue << 1) | flags.isNonSync,
      // flags.degradPrio & 0xF0 << 8,
      // flags.degradPrio & 0x0F, // sample_flags
      (cts >>> 24) & 0xFF,
      (cts >>> 16) & 0xFF,
      (cts >>> 8) & 0xFF,
      cts & 0xFF // sample_composition_time_offset
    ], 12 + 16 * i);
  }
  return box('trun', array);
}

function initSegment(tracks) {
  return [
    box('ftyp',
      fourcc('isom'),
      u8([0, 0, 0, 1]),
      fourcc('avc1'),
      fourcc('isom'),
    ),
    moov(tracks)
  ];
}

export default {
  box,
  // mdat,
  moof,
  initSegment,
};
