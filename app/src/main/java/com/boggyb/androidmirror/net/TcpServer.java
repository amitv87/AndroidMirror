package com.boggyb.androidmirror.net;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;

public class TcpServer implements TcpClient.Callback {

  private static CompletionHandler<AsynchronousSocketChannel, TcpServer> handler = new CompletionHandler<AsynchronousSocketChannel, TcpServer>() {
    @Override
    public void completed(AsynchronousSocketChannel socketChannel, TcpServer tcpServer) {
      tcpServer.accept();
      if(socketChannel != null && !tcpServer.callback.onOpen(socketChannel))
        tcpServer.onOpen(new TcpClient(tcpServer, socketChannel));
    }

    @Override
    public void failed(Throwable exc, TcpServer tcpServer) {
      tcpServer.callback.onStatus(tcpServer, exc);
    }
  };

  public interface Callback{
    void onStatus(TcpServer tcpServer, Throwable exc);
    boolean onOpen(AsynchronousSocketChannel socketChannel);
    void onOpen(TcpClient tcpClient);
    void onClose(TcpClient tcpClient);
    void onData(TcpClient tcpClient, ByteBuffer buffer, int length);
  }

  private final Callback callback;
  private AsynchronousServerSocketChannel socketChannel;

  public TcpServer(Callback callback){
    this.callback = callback;
  }

  public boolean listen(InetAddress ip, int port, int maxCons) throws IOException {
    if(socketChannel != null) return false;
    socketChannel = AsynchronousServerSocketChannel.open().bind(new InetSocketAddress(ip, port), Math.max(maxCons, 0));
    accept();
    return true;
  }

  public boolean stop() throws IOException {
    if(socketChannel == null) return false;
    socketChannel.close();
    socketChannel = null;
    return false;
  }

  private void accept(){
    socketChannel.accept(this, handler);
  }

  @Override
  public void onOpen(TcpClient tcpClient) {
    callback.onOpen(tcpClient);
  }

  @Override
  public void onClose(TcpClient tcpClient) {
    callback.onClose(tcpClient);
  }

  @Override
  public void onData(TcpClient tcpClient, ByteBuffer buffer, int length) {
    callback.onData(tcpClient, buffer, length);
  }

}
