import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;

public class Server {


    public static final int PORT_NUM = 12235;
    public static void main(String[] args) throws IOException{
        PortGenerator portGenerator = new PortGenerator();
        ResponseGenerator responseGenerator = new ResponseGenerator();

        DatagramSocket server_socket = new DatagramSocket(PORT_NUM);


        while(true) {
            byte[] buf_receive = new byte[24];
            // create a packet to receive new client connection
            DatagramPacket packet_to_receive = new DatagramPacket(buf_receive, buf_receive.length);
            server_socket.receive(packet_to_receive);
            new MultiThreadServer(portGenerator, responseGenerator, packet_to_receive).start();
        }

    }

}