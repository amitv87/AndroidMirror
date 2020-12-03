var gid = e => document.getElementById(e);

var fsbutimg, sendMain, sendInput, resizePlayerView;
var body = gid('mc');
var expcol = gid('expcol');
var butcont = gid('butcont');
var clipdata = gid('clipdata');
var clipcont = gid('clipcont');
var settings = gid('settings');
var vidBitrate = gid('vidBitrate');
var butcontcont = gid('butcontcont');

var androidRotationMap = {v:{0:2,2:0,d:0}, h:{1:3,3:1,d:1}};
var androidRotationMapRev = ['v','h','v','h'];

var ffMap = [
  [['exitFullscreen', 'webkitExitFullscreen',], document],
  [['requestFullscreen', 'webkitRequestFullscreen',], body],
]

var isFullScreen = () => document.fullscreenElement || document.webkitFullscreenElement;

['fullscreenchange', 'webkitfullscreenchange'].forEach(t =>
  document.addEventListener(t, e => fsbutimg && fsbutimg.setAttributeNS('http://www.w3.org/1999/xlink', 'href', isFullScreen() ? '#fse' : '#fs'), false)
);

function doFF(e, el){
  if(!fsbutimg) fsbutimg = el.children[0].children[0];
  var target = ffMap[isFullScreen() ? 0 : 1];
  target[0].every(t => target[1][t] ? (target[1][t]() && 0) : 1);
}

function expandCollapse(){
  var isCollapsed = butcontcont.style.top != '0px';
  if(isCollapsed){
    butcontcont.style.top = '0px';
    butcontcont.style.overflowX = 'scroll';
    expcol.style.transform = 'rotate(45deg)';
  }
  else{
    butcontcont.style.top = '-36px';
    butcontcont.style.overflow = 'hidden';
    expcol.style.transform = 'rotate(0deg)';
  }
}

function newElement(html){
  var template = document.createElement('template');
  template.innerHTML = html;
  return template.content.firstChild;
}

function rotateDev(type){
  var map = androidRotationMap[type];
  var rotation = map[conf.rotation];
  if(rotation == undefined) rotation = map.d;
  sendInput(['rotate', rotation]);
}

function syncClipboard(upload){
  var name = upload ? 'clipboard-read' : 'clipboard-write';

  if(upload){
    clipdata.disabled = false;
    clipdata.value = "";
    clipdata.placeholder = "set clipboard text on device";
    clipcont.style.display = null;
  }
  else{
    sendMain({a:'get_clip'});
  }
}

function initSettings(){
  vidBitrate.value = conf[kActionVideoBitrate];
  vidBitrate.onchange();  
}

var showInputControls = show => document.querySelectorAll('.input').forEach(btn => btn.style.display = (show && conf[kActionImStatus]) ? 'block' : 'none');

const kActionLost = "lost";
const kActionClip = "clip";
const kActionConfig = "config";
const kActionImStatus = "im_status";
const kActionRotation = "rotation";
const kActionVideoBitrate = "vid_bitrate";

function onDeviceEvent(action, data){
  console.log(action, data);
  if(action == kActionConfig){
    initSettings();
    showInputControls(true);
  }
  else if(action == kActionLost) showInputControls(false);
  else if(action == kActionClip){
    clipdata.value = data;
    clipdata.disabled = true;
    clipdata.placeholder = "no clipboard text from device";
    clipcont.style.display = null;
  }
  else if(data != undefined){
    window.conf[action] = data;
    if(action == kActionVideoBitrate) initSettings();
    else if(action == kActionImStatus) showInputControls(true);
  }
}

function createButton(img, cb, cssClass){
  cssClass = cssClass ? ' ' + cssClass : '';
  var str = '<button title="' + img + '" class="imgButton' + cssClass + '">';
  str += '<svg><use xlink:href="#' + img + '"></use></svg>';
  str += '</button>';
  var el = newElement(str);
  el.onclick = cb ?  e => cb(e, el) : undefined;
  butcont.append(el);
  return el;
}

function draggable(target, cont, onclick){
  target.style.top = '40px';
  target.style.right = '0px';
  target.style.display = null;

  onclick = onclick || target.onclick;
  target.onclick = null;
  target.onmousedown = onmousedown;
  target.addEventListener('touchstart', onmousedown, false);

  var diffX, diffY, fe;
  var contRrect = cont.getBoundingClientRect();
  var targetRrect = target.getBoundingClientRect();

  function onmousedown(e){
    e.preventDefault();
    if(e.changedTouches) e = e.changedTouches[0];
    fe = e;

    contRrect = cont.getBoundingClientRect();
    targetRrect = target.getBoundingClientRect();

    diffX = e.clientX - targetRrect.left;
    diffY = e.clientY - targetRrect.top;

    target.shouldFireClick = true;

    cont.onmouseup = onmouseup;
    cont.onmousemove = onmousemove;
    cont.addEventListener('touchend', onmouseup, false);
    cont.addEventListener('touchmove', onmousemove, false);
  }

  function onmousemove(e){
    e.preventDefault();
    if(e.changedTouches) e = e.changedTouches[0];
    if(fe.clientX != e.clientX || fe.clientY != e.clientY) target.shouldFireClick = false;
    var top = (e.clientY - diffY);
    var left = (e.clientX - diffX);
    if(top >= 0 && top + targetRrect.height <= contRrect.height) target.style.top = top + 'px';
    if(left >= 0 && left + targetRrect.width <= contRrect.width) target.style.left = left + 'px';
  }

  function onmouseup(e){
    cont.onmouseup = null;
    cont.onmousemove = null;
    cont.removeEventListener('touchend', onmouseup);
    cont.removeEventListener('touchmove', onmousemove);
    if(target.shouldFireClick && onclick) onclick(e);
  }

  function layoutChange(e){
    target.resizeJob = null;
    contRrect = cont.getBoundingClientRect();
    targetRrect = target.getBoundingClientRect();

    if(targetRrect.x + targetRrect.width > contRrect.width) target.style.left = null, target.style.right = '0px';
    if(targetRrect.y + targetRrect.height > contRrect.height) target.style.top = null, target.style.bottom = '0px';
  }

  layoutChange();

  window.addEventListener('resize', e => target.resizeJob || (target.resizeJob = setTimeout(layoutChange, 100)), true);
}

function suppress(e){
  e.cancelBubble = true;
  if(e.stopPropagation) e.stopPropagation();
  e.preventDefault();
  return true;
}

function getLineHeight(element){
  var ret = 0;
  try{
    var temp = document.createElement(element.nodeName);
    temp.setAttribute("style","margin:0px;padding:0px;font-family:"+element.style.fontFamily+";font-size:"+element.style.fontSize);
    temp.innerHTML = "test";
    temp = element.parentNode.appendChild(temp);
    ret = temp.clientHeight;
    temp.parentNode.removeChild(temp);
  }
  catch(e){
    console.error('getLineHeight error', e);
  }
  return ret;
}

window.clipOkay = ()=>{
  clipcont.style.display = 'none';
  if(clipdata.disabled) return;
  if(clipdata.value.length > 0) sendMain({a:'set_clip', d: clipdata.value});
  else console.log('input text empty');
}

vidBitrate.onchange = e => {
  vidBitrate.nextElementSibling.nextElementSibling.innerText = vidBitrate.value;
  if(e) sendMain({a:'vid_bitrate', d: Number(vidBitrate.value)});
}

window.settingsToggle = ()=> settings.style.display = settings.style.display ? null : 'none';

export function initInteractions(canvas, requestPiP, sm, se, rspv){
  sendMain = sm;
  sendInput = se;
  resizePlayerView = rspv;
  canvas.tabIndex = 1000;
  canvas.contenteditable = true;
  canvas.oncontextmenu = e => false;

  // from android MotionEvent
  const ACTION_DOWN = 0;
  const ACTION_UP   = 1;
  const ACTION_MOVE = 2;

  var prevMouseAction = ACTION_UP;

  // attach keyboard listeners
  canvas.onkeyup = e => e.metaKey || sendKey(ACTION_UP, e);
  canvas.onkeydown = e => e.metaKey || sendKey(ACTION_DOWN, e);

  function sendKey(action, e){
    var key = e.keyCode;
    var alt = e.altKey ? 1 : 0;
    var shift = e.shiftKey ? 1 : 0;
    var ctrl = e.ctrlKey ? 1 : 0;
    sendInput([action, key != 224 ? key : 91, alt, shift, ctrl]);
    return suppress(e);
  }

  // attach mouse listeners
  canvas.onmouseup = doMouseUp;
  canvas.onmousedown = doMouseDown;
  canvas.onmouseleave = doMouseLeave;
  // canvas.onmousemove = doMouseMove; // attach this only when required

  if("wheel" in window) canvas.onwheel = doMouseWheel;
  else if("onmousewheel" in window) canvas.onmousewheel = doMouseWheel;

  // attach touch listeners
  [
    ['touchend', e => sendTouch(ACTION_UP, e)],
    ['touchmove', e => sendTouch(ACTION_MOVE, e)],
    ['touchstart', e => updateRect() && sendTouch(ACTION_DOWN, e)],
  ].forEach(t => canvas.addEventListener(t[0], t[1], true));

  var rect;

  var updateRect = () => rect = canvas.getBoundingClientRect();

  function doMouseDown(e){
    updateRect();
    canvas.focus();
    if(e.button == 0) {
      canvas.onmousemove = doMouseMove;
      sendMouse(ACTION_DOWN, e);
    }
    return suppress(e);
  }

  function doMouseUp(e){
    canvas.onmousemove = null;
    if(e.button == 0) sendMouse(ACTION_UP, e);
    return suppress(e);
  }

  function doMouseMove(e){
    if(prevMouseAction != ACTION_UP) sendMouse(ACTION_MOVE, e);
    return suppress(e);
  }

  function doMouseLeave(e){
    canvas.onmousemove = null;
    if(prevMouseAction != ACTION_UP) sendMouse(ACTION_UP, e);
    return suppress(e);
  }

  function getXY(action, e){
    return[
      action,
      Math.round(conf.d_width * (e.clientX - rect.left) / canvas.clientWidth),
      Math.round(conf.d_height * (e.clientY - rect.top) / canvas.clientHeight),
    ];
  }

  function sendMouse(action, e){
    prevMouseAction = action;
    sendInput(getXY(action, e));
  }

  function sendTouch(action, e){
    var tarr = [];
    for(var i = 0; i < e.changedTouches.length; i++){
      var t = e.changedTouches[i];
      tarr.push(getXY(t.identifier, t));
    }
    sendInput([action, tarr]);
    return suppress(e);
  }

  var lineHeight = getLineHeight(canvas.parentElement);
  // console.log('lineHeight', lineHeight);

  function doMouseWheel(e){
    if(e.ctrlKey) return;

    var multiplier = 1 / 120;
    var dx = e.wheelDeltaX * multiplier;
    var dy = e.wheelDeltaY * multiplier;

    if(e.deltaMode == 1){
      dx *= lineHeight;
      dy *= lineHeight;
    }

    updateRect();
    var xy = getXY(null, e);

    sendInput([xy[1], xy[2], dx, dy]);
    return suppress(e);
  }

  var ua = navigator.userAgent.toLowerCase();
  if(ua.indexOf('safari') >= 0 && ua.indexOf('chrome') == -1 && ua.indexOf('mac os') >= 0){
    console.log('safari detected');

    [
      ['gesturestart', e => updateRect() && sendGesture(ACTION_DOWN, e)],
      ['gesturechange', e => sendGesture(ACTION_MOVE, e)],
      ['gestureend', e => sendGesture(ACTION_UP, e)],
    ].forEach(t => canvas.addEventListener(t[0], t[1]));

    function sendGesture(action, e){
      // console.log(action, e.type, e.scale, e.rotation, e);

      var angle = e.rotation * 0.0174533;
      var distance = e.scale * (conf.d_width/10);

      var xscale = distance * Math.cos(angle);
      var yscale = distance * Math.sin(angle);

      var touch0 = {identifier: 0, clientX: e.clientX - xscale, clientY: e.clientY - yscale};
      var touch1 = {identifier: 1, clientX: e.clientX + xscale, clientY: e.clientY + yscale};

      var event = {
        changedTouches:[touch0],
        preventDefault:()=>{},
      };

      if(action == ACTION_MOVE) event.changedTouches.push(touch1);

      sendTouch(action, event);

      if(action != ACTION_MOVE){
        event.changedTouches[0] = touch1;
        sendTouch(action, event);
      }

      return suppress(e);
    }
  }

  // controls section
  if(document.fullscreenEnabled || document.webkitFullscreenEnabled) createButton("fs", doFF);
  if(document.pictureInPictureEnabled) createButton("pip", requestPiP);

  [
    ["setup", settingsToggle, true],
    ["portrait", e => rotateDev('v')],
    ["landscape", e => rotateDev('h')],
    ["clip_up", e => syncClipboard(true)],
    ["clip_down", e => syncClipboard(false)],
  ].forEach(ctrl => createButton(ctrl[0], ctrl[1], ctrl[2] ? undefined : 'input'));

  // [
  //   'back', 'home', 'apps', 'menu',
  //   'power', 'web', 'search',
  //   'vup', 'vdown', 'vmute',
  //   'play', 'pause', 'prev', 'next', 'stop',
  // ]
  'back,home,apps,menu,power,web,search,vup,vdown,vmute,play,pause,prev,next,stop'.split(',')
  .forEach(ctrl => createButton(ctrl, (e) => sendInput(['button', ctrl]), 'input'));

  draggable(gid('assist'), body, expandCollapse);

  return onDeviceEvent;
}
