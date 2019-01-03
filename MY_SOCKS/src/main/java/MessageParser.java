import org.xbill.DNS.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class MessageParser {
    private byte MY_VERSION = 0x05;
    private byte MY_AUTH = 0x00;    //also used everywhere 0x00 needed
    private byte MY_COMMAND = 0x01;
    private byte IPv4 = 0x01;
    private byte DOMAIN = 0x03;
    private int ACCEPTED = 0;
    private int CONNECTED = 1;
    private int CONNECTION = 2;

    public boolean checkIfCorrect(ByteBuffer message, int state){
        boolean conclusion = false;
        byte[] msg = message.array();
        if (state == ACCEPTED) {
            if (msg[0] != MY_VERSION) {
                return conclusion;
            }
            else {
                int num_methods = message.get(1);//(int)msg[1];
                boolean found = false;
                for (int i = 2; i<num_methods+2; ++i){
                    if (msg[i] == MY_AUTH){
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return conclusion;
                }
                else {
                    conclusion = true;
                    return conclusion;
                }
            }
        }
        else if (state == CONNECTED){
            if (msg[1] != MY_COMMAND){
                return conclusion;
            }
            else{
                if ((msg[3] == IPv4)||(msg[3] == DOMAIN)){
                    conclusion = true;
                    return conclusion;
                }
                else{
                    return conclusion;
                }
            }
        }
        return conclusion;
    }

    public InetAddress getAddress(ByteBuffer message) throws UnknownHostException, DomainException {
        byte[] msg = message.array();
        if (msg[3] == IPv4) {
            byte[] addr = new byte[]{msg[4], msg[5], msg[6], msg[7]};
            return InetAddress.getByAddress(addr);
        }
        else
            throw new DomainException();
    }

    public int getPort(ByteBuffer message, int len){
        byte[] msg = message.array();
        int dest_port = (((0xFF & msg[len-2]) << 8) + (0xFF & msg[len-1]));
        System.out.println(dest_port);
        return dest_port;
    }

    public String getDomain(ByteBuffer message){
        int len = message.array()[4];
        byte[] domain_bytes = Arrays.copyOfRange(message.array(), 5, len + 5);
        String res = new String(domain_bytes);
        return res;
    }

    public ByteBuffer makeAcceptionAnswer(){
        ByteBuffer outBuffer = ByteBuffer.allocate(2);
        outBuffer.put(MY_VERSION);
        outBuffer.put(MY_AUTH);
        return outBuffer;
    }

    public ByteBuffer makeConnectionAnswer(int lport, boolean isConnected) throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(10);
        byte[] arr;
        if (isConnected)
            arr = new byte[]{MY_VERSION,
                MY_AUTH, //access granted
                MY_AUTH, //reserved
                IPv4,
                0x7F,
                0x00,
                0x00,
                0x01, //localhost
                (byte) ((lport >> 8) & 0xFF), (byte) (lport & 0xFF)};
        else
            arr = new byte[]{MY_VERSION,
                    0x01, //SOCKS issue
                    MY_AUTH, //reserved
                    IPv4,
                    0x7F,
                    0x00,
                    0x00,
                    0x01, //localhost
                    (byte) ((lport >> 8) & 0xFF), (byte) (lport & 0xFF)};
        bb.put(arr);
        return bb;
    }
}
