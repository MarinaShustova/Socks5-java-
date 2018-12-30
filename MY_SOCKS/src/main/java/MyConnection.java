import java.nio.channels.SocketChannel;

public class MyConnection {
    int port;
    SocketChannel socketChannel;

    MyConnection(int p, SocketChannel sc){
        port = p;
        socketChannel = sc;
    }
}
