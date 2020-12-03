package com.boggyb.androidmirror.net;


import android.content.res.AssetManager;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;

/**
 * Implementation of a very basic HTTP server. The contents are loaded from the assets folder. This
 * server handles one request at a time. It only supports GET method.
 */
public class SimpleWebServer implements Runnable {

  private static final String TAG = "SimpleWebServer";

  /**
   * The port number we listen to
   */
  private final int mPort;

  /**
   * {@link android.content.res.AssetManager} for loading files to serve.
   */
  private final AssetManager mAssets;

  /**
   * True if the server is running.
   */
  private boolean mIsRunning;

  /**
   * The {@link java.net.ServerSocket} that we listen to.
   */
  private ServerSocket mServerSocket;

  /**
   * WebServer constructor.
   */
  public SimpleWebServer(int port, AssetManager assets) {
    mPort = port;
    mAssets = assets;
  }

  /**
   * This method starts the web server listening to the specified port.
   */
  public void start() {
    mIsRunning = true;
    new Thread(this).start();
  }

  /**
   * This method stops the web server
   */
  public void stop() {
    try {
      mIsRunning = false;
      if (null != mServerSocket) {
        mServerSocket.close();
        mServerSocket = null;
      }
    } catch (IOException e) {
      Log.e(TAG, "Error closing the server socket.", e);
    }
  }

  public int getPort() {
    return mPort;
  }

  @Override
  public void run() {
    try {
      mServerSocket = new ServerSocket(mPort);
      while (mIsRunning) {
        final Socket socket = mServerSocket.accept();
        if(socket == null) continue;
        new Thread(new Runnable() {
          @Override
          public void run() {
            try{
              handle(socket);
              socket.close();
            }
            catch (Exception e){
              Log.e(TAG, "socket thread err: ", e);
            }
          }
        }).start();
      }
    } catch (SocketException e) {
      // The server was stopped; ignore.
    } catch (IOException e) {
      Log.e(TAG, "Web server error.", e);
    }
  }

  /**
   * Respond to a request from a client.
   *
   * @param socket The client socket.
   * @throws IOException
   */
  private void handle(Socket socket) throws IOException {
    BufferedReader reader = null;
    PrintStream output = null;
    try {
      String route = null;

      // Read HTTP headers and parse out the route.
      reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
      String line;
      while (!TextUtils.isEmpty(line = reader.readLine())) {
        if (line.startsWith("GET /")) {
          int start = line.indexOf('/') + 1;
          int end = line.indexOf(' ', start);
          route = line.substring(start, end);
          break;
        }
      }

      // Output stream that we send the response to
      output = new PrintStream(socket.getOutputStream());

      // Prepare the content to send.
      if (null == route || route.isEmpty()) {
        route = "index.html";
      }
      byte[] bytes = loadContent("public/" + route, mAssets);
      if (null == bytes) {
        writeServerError(output);
        return;
      }

      // Send out the content.
      output.println("HTTP/1.0 200 OK");
      output.println("Content-Type: " + detectMimeType(route));
      output.println("Content-Length: " + bytes.length);
      output.println();
      output.write(bytes);
      output.flush();
    } finally {
      if (null != output) {
        output.close();
      }
      if (null != reader) {
        reader.close();
      }
    }
  }

  /**
   * Writes a server error response (HTTP/1.0 500) to the given output stream.
   *
   * @param output The output stream.
   */
  private void writeServerError(PrintStream output) {
    output.println("HTTP/1.0 500 Internal Server Error");
    output.flush();
  }

  /**
   * Loads all the content of {@code fileName}.
   *
   * @param fileName The name of the file.
   * @return The content of the file.
   * @throws IOException
   */
  static byte[] loadContent(String fileName, AssetManager mAssets) throws IOException {
    InputStream input = null;
    try {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      input = mAssets.open(fileName);
      byte[] buffer = new byte[1024];
      int size;
      while (-1 != (size = input.read(buffer))) {
        output.write(buffer, 0, size);
      }
      output.flush();
      return output.toByteArray();
    } catch (FileNotFoundException e) {
      return null;
    } finally {
      if (null != input) {
        input.close();
      }
    }
  }

  private static final HashMap<String, String> kMimeTypes = new HashMap<String, String>() {{
    put("css", "text/css");
    put("htm", "text/html");
    put("html", "text/html");

    put("wasm", "application/wasm");
    put("js", "application/javascript");

    // put("xml", "text/xml");
    // put("java", "text/x-java-source, text/java");
    // put("md", "text/plain");
    // put("txt", "text/plain");
    // put("asc", "text/plain");
    // put("gif", "image/gif");
    // put("jpg", "image/jpeg");
    // put("jpeg", "image/jpeg");
    // put("png", "image/png");
    // put("svg", "image/svg+xml");
    // put("mp3", "audio/mpeg");
    // put("m3u", "audio/mpeg-url");
    // put("mp4", "video/mp4");
    // put("ogv", "video/ogg");
    // put("flv", "video/x-flv");
    // put("mov", "video/quicktime");
    // put("swf", "application/x-shockwave-flash");
    // put("pdf", "application/pdf");
    // put("doc", "application/msword");
    // put("ogg", "application/x-ogg");
    // put("zip", "application/octet-stream");
    // put("exe", "application/octet-stream");
    // put("class", "application/octet-stream");
    // put("m3u8", "application/vnd.apple.mpegurl");
    // put("ts", " video/mp2t");
  }};

  private static String getFileExtension(String fileName) {
    int lastIndexOf = fileName.lastIndexOf(".");
    if (lastIndexOf == -1 || lastIndexOf == fileName.length() - 1) return ""; // empty extension
    return fileName.substring(lastIndexOf + 1);
  }

  /**
   * Detects the MIME type from the {@code fileName}.
   *
   * @param fileName The name of the file.
   * @return A MIME type.
   */
  static String detectMimeType(String fileName) {
    if (fileName == null || fileName.isEmpty()) return null;
    String ext = getFileExtension(fileName);
//    Log.d(TAG, "fileName: " + fileName + ", ext: " + ext);
    String mime = kMimeTypes.get(ext);
    if(mime != null){
      return mime;
    } else {
      return "application/octet-stream";
    }
  }
}
