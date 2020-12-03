package com.boggyb.androidmirror.net;

import android.content.res.AssetManager;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

import org.java_websocket.WebSocket;
import org.java_websocket.enums.ReadyState;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPOutputStream;

public class WSTransport extends DataTransport {
  private static final String TAG = WSTransport.class.getCanonicalName();

  private class HTTPResp{
    byte[] raw;
    byte[] gzip;
  }

  private final WebSocketServer wss;
  private static ConcurrentHashMap<String, HTTPResp> respMap = new ConcurrentHashMap<>();

  private static class WS extends DataTransport.Channel{

    private final WebSocket conn;

    WS(WebSocket conn){
      this.conn = conn;
      this.conn.setAttachment(this);
    }

    @Override
    public void close() {
      conn.close(CloseFrame.EXTENSION, "unknown");
    }

    @Override
    public void send(String data) {
      if(conn.getReadyState() == ReadyState.OPEN) conn.send(data);
    }

    @Override
    public void send(byte[] data) {
      if(conn.getReadyState() == ReadyState.OPEN) conn.send(data);
    }
  }

  public WSTransport(Handler handler, Callback callback, InetAddress ip, int port, AssetManager assets) {
    super(handler, callback);

    wss = new WebSocketServer(new InetSocketAddress(ip, port)) {
      @Override
      public boolean onHTTPRequest(WebSocket conn, ByteBuffer socketBuffer) {
        try {
          String req = new String(socketBuffer.array(), socketBuffer.position(), socketBuffer.remaining(), StandardCharsets.UTF_8);
//          Log.d(TAG, "onHTTPRequest: " + req);

          BufferedReader reader = new BufferedReader(new StringReader(req));

          boolean acceptsGzip = false;
          String line, route = null;

          while (!TextUtils.isEmpty(line = reader.readLine())) {
            String lLine = line.toLowerCase();
            if (lLine.startsWith("get /")) {
              int start = line.indexOf('/');
              int end = line.indexOf(' ', start);
              if(end <= 0) end = line.length() - 1;
              URL url = new URL("http://am" + line.substring(start, end));
              route = url.getPath();
              if(acceptsGzip) break;
            }
            else if(lLine.startsWith("accept-encoding:") && lLine.indexOf("gzip") > 0){
              acceptsGzip = true;
              if(route != null) break;
            }
          }

          if(route == null || route.equals("/")) route = "/index.html";

          byte[] bytes = SimpleWebServer.loadContent("dist" + route, assets);
          if(bytes != null && acceptsGzip) bytes = gzipCompress(bytes);

          // byte[] bytes = null;
          // HTTPResp resp = respMap.get(route);
          // if(resp != null){
          //   bytes = acceptsGzip ? resp.gzip : resp.raw;
          // }

          // if(bytes == null) {
          //   bytes = SimpleWebServer.loadContent("dist" + route, assets);
          //   if(bytes != null) {
          //     if(resp == null) respMap.put(route, resp = new HTTPResp());
          //     resp.raw = bytes;
          //     resp.gzip = gzipCompress(resp.raw);
          //     if (acceptsGzip) bytes = resp.gzip;
          //   }
          // }

          if (bytes != null){
            conn.sendRaw((
              "HTTP/1.0 200 OK\r\n"
              + (acceptsGzip ? "Content-Encoding: gzip\r\n" : "")
              + "Content-Type: " + SimpleWebServer.detectMimeType(route) + "\r\n"
              + "Content-Length: " + bytes.length + "\r\n"
              + "\r\n"
            ).getBytes());
            conn.sendRaw(bytes);
            return true;
          }
        }
        catch (Exception e){
          Log.e(TAG, "onHTTPRequest err", e);
        }

        return false;
      }

      @Override
      public void onOpen(WebSocket conn, ClientHandshake handshake) {
        WS ws = new WS(conn);
        handler.post(() -> {
          callback.onChannel(WSTransport.this, ws, handshake.getResourceDescriptor());
          if(ws.receiver != null) ws.receiver.onOpen(ws);
        });
      }

      @Override
      public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        WS ws = conn.getAttachment();
        handler.post(() -> {
          if(ws.receiver != null) ws.receiver.onClose(ws);
        });
      }

      @Override
      public void onMessage(WebSocket conn, String message) {
        WS ws = conn.getAttachment();
        handler.post(() -> {
          if(ws.receiver != null) ws.receiver.onMessage(ws, message);
        });
      }

      @Override
      public void onMessage(WebSocket conn, ByteBuffer message) {
        final byte[] bytes = new byte[message.capacity()];
        message.get(bytes);
        WS ws = conn.getAttachment();
        handler.post(() -> {
          if(ws.receiver != null) ws.receiver.onMessage(ws, bytes);
        });
      }

      @Override
      public void onError(WebSocket conn, Exception ex) {
        handler.post(() -> {
          if(conn == null){
            Log.e(TAG, "onError", ex);
            callback.onStart(WSTransport.this, false);
          }
          else{
            WS ws = conn.getAttachment();
            if(ws.receiver != null)ws.receiver.onClose(ws);
          }
        });
      }

      @Override
      public void onStart() {
        handler.post(() -> callback.onStart(WSTransport.this, true));
      }
    };
    wss.setReuseAddr(true);
  }

  @Override
  public void start() {
    wss.start();
  }

  @Override
  public void stop() {
    try {
      wss.stop();
    } catch (Exception e) {
      Log.e(TAG, "wss.stop err", e);
    }
  }

  private static byte[] gzipCompress(byte[] uncompressedData) {
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream(uncompressedData.length);
         GZIPOutputStream gzipOS = new GZIPOutputStream(bos)) {
      gzipOS.write(uncompressedData);
      gzipOS.close();
      return bos.toByteArray();
    } catch (IOException e) {
      Log.e(TAG, "gzipCompress err", e);
    }
    return null;
  }
}
