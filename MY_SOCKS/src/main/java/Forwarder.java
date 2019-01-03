import org.xbill.DNS.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;

public class Forwarder {
    int size = 1024*8;
    int port;
    ByteBuffer buf;
    private int ACCEPTED = 0;
    private int CONNECTED = 1;
    private int CONNECTION = 2;
//    private DatagramChannel udpsocket;
    private Map<SocketChannel, SocketChannel> connections = new HashMap<>();
    private Map<Integer, MyConnection> dnslist = new HashMap<>();
    private Map<SocketChannel, Meta> information = new HashMap<>();
    private MessageParser messageParser = new MessageParser();
    private Selector selector;
    Forwarder(Integer lport) throws SocketException {
        port = lport;
        buf = ByteBuffer.allocate(size);
    }

    public void forward() throws IOException {

        selector = Selector.open();

//        ServerSocketChannel ssChannel = ServerSocketChannel.open();
//        ssChannel.configureBlocking(false);
//        ssChannel.socket().bind(new InetSocketAddress("localhost", port));
//        ssChannel.register(selector, SelectionKey.OP_ACCEPT);
//
        String dnsServers[] = ResolverConfig.getCurrentConfig().servers();
//        udpsocket = DatagramChannel.open();
//        udpsocket.configureBlocking(false);
//        udpsocket.connect(new InetSocketAddress(dnsServers[0],53));
//        udpsocket.register(selector, SelectionKey.OP_READ);

        SelectionKey key;
        ByteBuffer messagebuffer = ByteBuffer.allocate(size);
        try (ServerSocketChannel ssChannel = ServerSocketChannel.open();
        DatagramChannel udpsocket = DatagramChannel.open()){
            ssChannel.configureBlocking(false);
            ssChannel.socket().bind(new InetSocketAddress("localhost", port));
            ssChannel.register(selector, SelectionKey.OP_ACCEPT);
//            udpsocket = data;
            udpsocket.configureBlocking(false);
            udpsocket.connect(new InetSocketAddress(dnsServers[0],53));
            udpsocket.register(selector, SelectionKey.OP_READ);
            while (true) {
                selector.select();
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectedKeys.iterator();
                while (iterator.hasNext()) {
                    key = (SelectionKey) iterator.next();
                    iterator.remove();
                    if (key.isValid()) {
                        if (key.isAcceptable()) {
                            SocketChannel sc = ssChannel.accept();
                            sc.configureBlocking(false);
                            sc.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE | SelectionKey.OP_CONNECT);
                            Meta info = new Meta(size);
                            info.state = ACCEPTED;
                            information.put(sc, info);
                        }
                        if (key.isConnectable()) {
                            ((SocketChannel) key.channel()).finishConnect();
                        }
                        if (key.isReadable()) {
                            boolean is_dns = !(key.channel() instanceof SocketChannel);
                            if (!is_dns) {
                                Meta info = information.get((SocketChannel) key.channel());
                                if (info == null) {
                                    info = new Meta(size);
                                    info.state = ACCEPTED;
                                    information.put((SocketChannel) key.channel(), info);
                                }
                                SocketChannel sc = (SocketChannel) key.channel();
                                int res = -1;
                                boolean correct;
                                if ((info.state == ACCEPTED) || (info.state == CONNECTED)) {
                                    try {
                                        res = sc.read(messagebuffer);
                                    }catch (IOException e){
                                        e.printStackTrace();
                                        close(key);
                                    }
                                    if (res < 0) {
                                        information.remove((SocketChannel) key.channel());
                                        close(key);
                                    }
                                    else {
                                        correct = messageParser.checkIfCorrect(messagebuffer, info.state);
                                        if (correct) {
                                            if (info.state == ACCEPTED) {
                                                ByteBuffer outBuffer = messageParser.makeAcceptionAnswer();
                                                sc.write(ByteBuffer.wrap(outBuffer.array(), 0, 2));
                                                info.state = CONNECTED;
                                            } else if (info.state == CONNECTED) {
                                                try {
                                                    InetAddress address = messageParser.getAddress(messagebuffer);
                                                    int connection_port = messageParser.getPort(messagebuffer, res);
                                                    if (connect(address, connection_port, sc, key))
                                                        info.state = CONNECTION;
                                                } catch (DomainException e) {
                                                    Name name = org.xbill.DNS.Name.fromString(messageParser.getDomain(messagebuffer), Name.root);
                                                    Record rec = Record.newRecord(name, Type.A, DClass.IN);
                                                    Message dns_message = Message.newQuery(rec);
                                                    udpsocket.write(ByteBuffer.wrap(dns_message.toWire()));
                                                    int port = messageParser.getPort(messagebuffer, res);
                                                    dnslist.put(dns_message.getHeader().getID(), new MyConnection(port, sc));
                                                }
                                            }
                                            messagebuffer.clear();
                                        }
                                    }
                                } else if (info.state == CONNECTION) {
//                                System.out.println("Connection!");
                                    SocketChannel connection = connections.get(sc);

                                System.out.println("CONNECTION (SOURCE) : " +sc.toString() + sc.isConnected());
                                System.out.println("CONNECTION (DEST) : " +connection.toString() + connection.isConnected());
                                    if (connection.isConnected()) {
                                        int amount;
                                        try {
                                            amount = sc.read(messagebuffer);
                                            if (amount == -1) {
                                                close(key);
                                            } else {
                                            System.out.println(amount);
                                            System.out.println(Arrays.toString(messagebuffer.array()));
                                                connection.write(ByteBuffer.wrap(messagebuffer.array(), 0, amount));
                                            }
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                            System.out.println("Going to close the connection");
                                            close(key);
                                        }
                                    }
                                    messagebuffer.clear();
                                }
                            } else {
                                ByteBuffer buffer = ByteBuffer.allocate(1024);
                                int len = udpsocket.read(buffer);
                                if (len <= 0) continue;
                                Message msg = new Message(buffer.array());
                                Record[] recs = msg.getSectionArray(1);
                                for (Record rec : recs) {
                                    if (rec instanceof ARecord) {
                                        ARecord arec = (ARecord) rec;
                                        InetAddress adr = arec.getAddress();
                                        int id = msg.getHeader().getID();
                                        MyConnection myConnection = dnslist.get(id);
                                        int port = myConnection.port;
                                        if (connect(adr, port, dnslist.get(id).socketChannel, key)) {
                                            information.get(dnslist.get(id).socketChannel).state = CONNECTION;
                                        }
                                        dnslist.remove(id);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }catch (Exception e){
            e.printStackTrace();
//            ssChannel.close();
//            udpsocket.close();
        }
    }

    private void close(SelectionKey key) throws IOException {
        SocketChannel sc = connections.get((SocketChannel) key.channel());
        if (sc != null) {
            sc.close();
            connections.remove(connections.get((SocketChannel) key.channel()));
            connections.remove((SocketChannel) key.channel());
        }
        key.channel().close();
    }

    private boolean connect(InetAddress address, int connection_port, SocketChannel sc, SelectionKey key) throws IOException {
        SocketChannel connection = SocketChannel.open(new InetSocketAddress(address, connection_port));
        ByteBuffer bb = messageParser.makeConnectionAnswer(port, connection.isConnected());
//        System.out.println("writing "+ Arrays.toString(bb.array())+ " to "+sc.toString());
        if (!connection.isConnected()) {
            close(key);
            return connection.isConnected();
        }
        try {
            sc.write(ByteBuffer.wrap(bb.array(), 0, 10));
        }catch (ClosedChannelException e){
            e.printStackTrace();
            return false;
        }
        connection.configureBlocking(false);
        connection.register(selector, SelectionKey.OP_READ|SelectionKey.OP_CONNECT);
        connections.put(sc, connection);
        connections.put(connection, sc);
        information.put(connection, new Meta(size));
        information.get(connection).state = CONNECTION;
        return connection.isConnected();
    }
}
