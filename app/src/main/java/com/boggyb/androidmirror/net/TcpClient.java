package com.boggyb.androidmirror.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;

public class TcpClient {

  public interface Callback{
    void onOpen(TcpClient tcpClient);
    void onClose(TcpClient tcpClient);
    void onData(TcpClient tcpClient, ByteBuffer buffer, int length);
  }

  private static final CompletionHandler<Void, TcpClient> connectHandler = new CompletionHandler<Void, TcpClient>() {
    @Override
    public void completed(Void result, TcpClient tcpClient) {
      tcpClient.callback.onOpen(tcpClient);
      tcpClient.read();
    }

    @Override
    public void failed(Throwable exc, TcpClient tcpClient) {
      tcpClient.onClose();
    }
  };

  private static final CompletionHandler<Integer, TcpClient> readHandler = new CompletionHandler<Integer, TcpClient>() {
    @Override
    public void completed(Integer result, TcpClient tcpClient) {
      if(result < 0){
        tcpClient.callback.onClose(tcpClient);
        return;
      }
      tcpClient.callback.onData(tcpClient, tcpClient.buffer, result);
      tcpClient.buffer.clear();
      tcpClient.read();
    }

    @Override
    public void failed(Throwable exc, TcpClient tcpClient) {
      tcpClient.onClose();
    }
  };

  private final Callback callback;
  private final ByteBuffer buffer = ByteBuffer.allocate(128);
  private AsynchronousSocketChannel socketChannel;

  public TcpClient(Callback callback){
    this.callback = callback;
  }

  public TcpClient(Callback callback, AsynchronousSocketChannel socketChannel){
    this.callback = callback;
    this.socketChannel = socketChannel;
    read();
  }

  private void read(){
    socketChannel.read(buffer, this, readHandler);
  }

  private void onClose(){
    socketChannel = null;
    callback.onClose(this);
  }

  public boolean Connect(String ip, int port) throws IOException {
    if(socketChannel != null) return false;
    socketChannel = AsynchronousSocketChannel.open();
    socketChannel.connect(new InetSocketAddress(ip, port), this, connectHandler);
    return true;
  }

  public boolean close() throws IOException {
    if(socketChannel == null) return false;
    socketChannel.close();
    socketChannel = null;
    return true;
  }
}
