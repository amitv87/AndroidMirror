if(ENVIRONMENT_IS_WORKER){

var __decoder, buffer_queue = [], renderer;
onmessage = e => {
  // console.log('onmessage', e.data);
  if(!(e.data instanceof ArrayBuffer)){
    console.log('init webgl', e.data);
    renderer = new WebGLRenderer(e.data);
    return;
  }
  if(!__decoder){
    if(Module.H264Decoder){
      __decoder = new Module.H264Decoder();
      var rc = __decoder.init();
      console.log('__decoder.init()', rc);
    }
    else {
      buffer_queue.push(e.data);
      return;
    }
    while(buffer_queue.length > 0) __decode(buffer_queue.shift());
  }
  __decode(e.data);
}

function __decode(buff){
  var frame = __decoder.decode(buff);
  if(frame.y.width <= 0) return;

  if(renderer){
    renderer.drawFrame(frame);
    return;
  }

  var newFrame = {};

  var xfer = [];

  ['y','u','v'].forEach( x => {
    var plane = frame[x];
    var data = new Uint8Array(plane.data.length);
    xfer.push(data.buffer);
    newFrame[x] = {
      data: data,
      width: plane.width,
      height: plane.height,
      stride: plane.stride,
    };

    data.set(plane.data, 0, plane.data.length);
  });

  postMessage(newFrame, xfer);
}

var vertex = `
precision lowp float;

varying vec2 vLumaPosition, vChromaPosition;
attribute vec2 aPosition, aLumaPosition, aChromaPosition;

void main() {
  gl_Position = vec4(aPosition, 0, 1);
  vLumaPosition = aLumaPosition;
  vChromaPosition = aChromaPosition;
}`;

var fragment = `
precision lowp float;

varying vec2 vLumaPosition, vChromaPosition;
uniform sampler2D uTextureY, uTextureU, uTextureV;

void main() {
  float fY = texture2D(uTextureY, vLumaPosition).x;
  float fCb = texture2D(uTextureU, vChromaPosition).x;
  float fCr = texture2D(uTextureV, vChromaPosition).x;

  float fYmul = fY * 1.1643828125;

  gl_FragColor = vec4(
    fYmul + 1.59602734375 * fCr - 0.87078515625,
    fYmul - 0.39176171875 * fCb - 0.81296875 * fCr + 0.52959375,
    fYmul + 2.017234375   * fCb - 1.081390625,
    1
  );
}`;

var vertexStripe = `
precision lowp float;

varying vec2 vTexturePosition;
attribute vec2 aPosition, aTexturePosition;

void main() {
  gl_Position = vec4(aPosition, 0, 1);
  vTexturePosition = aTexturePosition;
}`;

var fragmentStripe = `
precision lowp float;

varying vec2 vTexturePosition;
uniform sampler2D uStripe, uTexture;

void main() {
   float fLuminance = dot(texture2D(uStripe, vTexturePosition), texture2D(uTexture, vTexturePosition));
   gl_FragColor = vec4(fLuminance, fLuminance, fLuminance, 1);
}`;

var shaders = {vertex, fragment, vertexStripe, fragmentStripe};

function WebGLRenderer(canvas) {
  var gl = canvas.getContext('webgl');
  var stripe = navigator.userAgent.indexOf('Windows') !== -1;

  function compileShader(type, source) {
    var shader = gl.createShader(type);
    gl.shaderSource(shader, source);
    gl.compileShader(shader);

    if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
      var err = gl.getShaderInfoLog(shader);
      gl.deleteShader(shader);
      throw new Error('GL shader compilation for ' + type + ' failed: ' + err);
    }

    return shader;
  }

  var rectangle = new Float32Array([
    // First triangle (top left, clockwise)
    -1.0, -1.0,
    +1.0, -1.0,
    -1.0, +1.0,

    // Second triangle (bottom right, clockwise)
    -1.0, +1.0,
    +1.0, -1.0,
    +1.0, +1.0
  ]);

  var textures = {}, framebuffers = {}, stripes = {};

  var program, unpackProgram, err;
  var buf, positionLocation, unpackPositionLocation;
  var unpackTexturePositionBuffer, unpackTexturePositionLocation;
  var stripeLocation, unpackTextureLocation;
  var lumaPositionBuffer, lumaPositionLocation;
  var chromaPositionBuffer, chromaPositionLocation;

  function createOrReuseTexture(name) {
    if (!textures[name]) {
      textures[name] = gl.createTexture();
    }
    return textures[name];
  }

  function uploadTexture(name, width, height, data) {
    var texture = createOrReuseTexture(name);
    gl.activeTexture(gl.TEXTURE0);

    if (stripe) {
      var uploadTemp = !textures[name + '_temp'];
      var tempTexture = createOrReuseTexture(name + '_temp');
      gl.bindTexture(gl.TEXTURE_2D, tempTexture);
      if (uploadTemp) {
        // new texture
        gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
        gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
        gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.NEAREST);
        gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.NEAREST);
        gl.texImage2D(
          gl.TEXTURE_2D,
          0, // mip level
          gl.RGBA, // internal format
          width / 4,
          height,
          0, // border
          gl.RGBA, // format
          gl.UNSIGNED_BYTE, // type
          data // data!
        );
      } else {
        // update texture
        gl.texSubImage2D(
          gl.TEXTURE_2D,
          0, // mip level
          0, // x offset
          0, // y offset
          width / 4,
          height,
          gl.RGBA, // format
          gl.UNSIGNED_BYTE, // type
          data // data!
        );
      }

      var stripeTexture = textures[name + '_stripe'];
      var uploadStripe = !stripeTexture;
      if (uploadStripe) {
        stripeTexture = createOrReuseTexture(name + '_stripe');
      }
      gl.bindTexture(gl.TEXTURE_2D, stripeTexture);
      if (uploadStripe) {
        gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
        gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
        gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.NEAREST);
        gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.NEAREST);
        gl.texImage2D(
          gl.TEXTURE_2D,
          0, // mip level
          gl.RGBA, // internal format
          width,
          1,
          0, // border
          gl.RGBA, // format
          gl.UNSIGNED_BYTE, //type
          buildStripe(width, 1) // data!
        );
      }

    } else {
      gl.bindTexture(gl.TEXTURE_2D, texture);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
      gl.texImage2D(
        gl.TEXTURE_2D,
        0, // mip level
        gl.LUMINANCE, // internal format
        width,
        height,
        0, // border
        gl.LUMINANCE, // format
        gl.UNSIGNED_BYTE, //type
        data // data!
      );
    }
  }

  function unpackTexture(name, width, height) {
    var texture = textures[name];

    // Upload to a temporary RGBA texture, then unpack it.
    // This is faster than CPU-side swizzling in ANGLE on Windows.
    gl.useProgram(unpackProgram);

    var fb = framebuffers[name];
    if (!fb) {
      // Create a framebuffer and an empty target size
      gl.activeTexture(gl.TEXTURE0);
      gl.bindTexture(gl.TEXTURE_2D, texture);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
      gl.texImage2D(
        gl.TEXTURE_2D,
        0, // mip level
        gl.RGBA, // internal format
        width,
        height,
        0, // border
        gl.RGBA, // format
        gl.UNSIGNED_BYTE, //type
        null // data!
      );

      fb = framebuffers[name] = gl.createFramebuffer();
    }

    gl.bindFramebuffer(gl.FRAMEBUFFER, fb);
    gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, texture, 0);

    var tempTexture = textures[name + '_temp'];
    gl.activeTexture(gl.TEXTURE1);
    gl.bindTexture(gl.TEXTURE_2D, tempTexture);
    gl.uniform1i(unpackTextureLocation, 1);

    var stripeTexture = textures[name + '_stripe'];
    gl.activeTexture(gl.TEXTURE2);
    gl.bindTexture(gl.TEXTURE_2D, stripeTexture);
    gl.uniform1i(stripeLocation, 2);

    // Rectangle geometry
    gl.bindBuffer(gl.ARRAY_BUFFER, buf);
    gl.enableVertexAttribArray(positionLocation);
    gl.vertexAttribPointer(positionLocation, 2, gl.FLOAT, false, 0, 0);

    // Set up the texture geometry...
    gl.bindBuffer(gl.ARRAY_BUFFER, unpackTexturePositionBuffer);
    gl.enableVertexAttribArray(unpackTexturePositionLocation);
    gl.vertexAttribPointer(unpackTexturePositionLocation, 2, gl.FLOAT, false, 0, 0);

    // Draw into the target texture...
    gl.viewport(0, 0, width, height);

    gl.drawArrays(gl.TRIANGLES, 0, rectangle.length / 2);

    gl.bindFramebuffer(gl.FRAMEBUFFER, null);
  }

  function attachTexture(name, register, index) {
    gl.activeTexture(register);
    gl.bindTexture(gl.TEXTURE_2D, textures[name]);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);

    gl.uniform1i(gl.getUniformLocation(program, name), index);
  }

  function buildStripe(width) {
    if (stripes[width]) {
      return stripes[width];
    }
    var len = width,
      out = new Uint32Array(len);
    for (var i = 0; i < len; i += 4) {
      out[i] = 0x000000ff;
      out[i + 1] = 0x0000ff00;
      out[i + 2] = 0x00ff0000;
      out[i + 3] = 0xff000000;
    }
    return stripes[width] = new Uint8Array(out.buffer);
  }

  function initProgram(vertexShaderSource, fragmentShaderSource) {
    var vertexShader = compileShader(gl.VERTEX_SHADER, vertexShaderSource);
    var fragmentShader = compileShader(gl.FRAGMENT_SHADER, fragmentShaderSource);

    var program = gl.createProgram();
    gl.attachShader(program, vertexShader);
    gl.attachShader(program, fragmentShader);

    gl.linkProgram(program);
    if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
      var err = gl.getProgramInfoLog(program);
      gl.deleteProgram(program);
      throw new Error('GL program linking failed: ' + err);
    }

    return program;
  }

  function init() {
    if (stripe) {
      unpackProgram = initProgram(shaders.vertexStripe, shaders.fragmentStripe);
      unpackPositionLocation = gl.getAttribLocation(unpackProgram, 'aPosition');

      unpackTexturePositionBuffer = gl.createBuffer();
      var textureRectangle = new Float32Array([
        0, 0,
        1, 0,
        0, 1,
        0, 1,
        1, 0,
        1, 1
      ]);
      gl.bindBuffer(gl.ARRAY_BUFFER, unpackTexturePositionBuffer);
      gl.bufferData(gl.ARRAY_BUFFER, textureRectangle, gl.STATIC_DRAW);

      unpackTexturePositionLocation = gl.getAttribLocation(unpackProgram, 'aTexturePosition');
      stripeLocation = gl.getUniformLocation(unpackProgram, 'uStripe');
      unpackTextureLocation = gl.getUniformLocation(unpackProgram, 'uTexture');
    }
    program = initProgram(shaders.vertex, shaders.fragment);

    buf = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, buf);
    gl.bufferData(gl.ARRAY_BUFFER, rectangle, gl.STATIC_DRAW);

    positionLocation = gl.getAttribLocation(program, 'aPosition');
    lumaPositionBuffer = gl.createBuffer();
    lumaPositionLocation = gl.getAttribLocation(program, 'aLumaPosition');
    chromaPositionBuffer = gl.createBuffer();
    chromaPositionLocation = gl.getAttribLocation(program, 'aChromaPosition');
  }

  function clear() {
    gl.viewport(0, 0, canvas.width, canvas.height);
    gl.clearColor(0.0, 0.0, 0.0, 0.0);
    gl.clear(gl.COLOR_BUFFER_BIT);
  };


  this.canvas = canvas;

  this.drawFrame = frame => {
    var formatUpdate = (!program || canvas.width !== frame.y.width || canvas.height !== frame.y.height);
    if (formatUpdate) {
      canvas.width = frame.y.width;
      canvas.height = frame.y.height;
      clear();
    }

    if (!program) init();

    if (formatUpdate) {
      var setupTexturePosition = function(buffer, location, texWidth) {
        // Warning: assumes that the stride for Cb and Cr is the same size in output pixels
        var textureX0 = 0;
        var textureX1 = frame.y.width / texWidth;
        var textureY0 = 1;
        var textureY1 = 0;
        var textureRectangle = new Float32Array([
          textureX0, textureY0,
          textureX1, textureY0,
          textureX0, textureY1,
          textureX0, textureY1,
          textureX1, textureY0,
          textureX1, textureY1
        ]);

        gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
        gl.bufferData(gl.ARRAY_BUFFER, textureRectangle, gl.STATIC_DRAW);
      };
      setupTexturePosition(
        lumaPositionBuffer,
        lumaPositionLocation,
        frame.y.stride);
      setupTexturePosition(
        chromaPositionBuffer,
        chromaPositionLocation,
        frame.u.stride * frame.y.width / frame.u.width);
    }

    // Create or update the textures...
    uploadTexture('uTextureY', frame.y.stride, frame.y.height, frame.y.data);
    uploadTexture('uTextureU', frame.u.stride, frame.u.height, frame.u.data);
    uploadTexture('uTextureV', frame.v.stride, frame.v.height, frame.v.data);

    if (stripe) {
      // Unpack the textures after upload to avoid blocking on GPU
      unpackTexture('uTextureY', frame.y.stride, frame.y.height);
      unpackTexture('uTextureU', frame.u.stride, frame.u.height);
      unpackTexture('uTextureV', frame.v.stride, frame.v.height);
    }

    // Set up the rectangle and draw it
    gl.useProgram(program);
    gl.viewport(0, 0, canvas.width, canvas.height);

    attachTexture('uTextureY', gl.TEXTURE0, 0);
    attachTexture('uTextureU', gl.TEXTURE1, 1);
    attachTexture('uTextureV', gl.TEXTURE2, 2);

    // Set up geometry
    gl.bindBuffer(gl.ARRAY_BUFFER, buf);
    gl.enableVertexAttribArray(positionLocation);
    gl.vertexAttribPointer(positionLocation, 2, gl.FLOAT, false, 0, 0);

    // Set up the texture geometry...
    gl.bindBuffer(gl.ARRAY_BUFFER, lumaPositionBuffer);
    gl.enableVertexAttribArray(lumaPositionLocation);
    gl.vertexAttribPointer(lumaPositionLocation, 2, gl.FLOAT, false, 0, 0);

    gl.bindBuffer(gl.ARRAY_BUFFER, chromaPositionBuffer);
    gl.enableVertexAttribArray(chromaPositionLocation);
    gl.vertexAttribPointer(chromaPositionLocation, 2, gl.FLOAT, false, 0, 0);

    // Aaaaand draw stuff.
    gl.drawArrays(gl.TRIANGLES, 0, rectangle.length / 2);
  };

  clear();
}

}
