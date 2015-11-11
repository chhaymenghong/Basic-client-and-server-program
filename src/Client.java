import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Arrays;


/**
 * Created by mengheng on 10/15/15.
 */
public class Client {
    public static String HOST_NAME = "localhost";
    public static final int PORT_NUM = 12235;
    public static Socket socket;        //tcp_socket

    public static void main(String[] args) throws IOException{
        if (args.length == 1) {
            HOST_NAME = args[0];
        }

        // Stage 1
        byte[] response1 = stage1();

        // Stage 2
        byte[] response2 = stage2(response1);

        // Stage 3
        byte[] response3 = stage3(response2);

        // Stage 4
        byte[] response4 = stage4(response3);

    }

    public static byte[] stage1() throws IOException {

        // Get datagram socket
        DatagramSocket socket = new DatagramSocket();

        // Construct our buf
        byte[] buf = new byte[24];                      //header=12 bytes, string = 11 bytes, padding = 1 byte
        ByteBuffer byteBuffer = ByteBuffer.wrap(buf);   //change in byteBuffer will also reflect in buf

        //header
        int packetLength = 12;
        int secret = 0;
        short stepNum = (short)1;
        short last3Digit = (short)469;
        byteBuffer.putInt(packetLength);
        byteBuffer.putInt(secret);
        byteBuffer.putShort(stepNum);
        byteBuffer.putShort(last3Digit);

        // content
        byte[] content = "hello world\0".getBytes();
        byteBuffer.put(content);


        // Sent request
        InetAddress ipAddress = InetAddress.getByName(HOST_NAME);
        DatagramPacket packet = new DatagramPacket(buf, buf.length, ipAddress, PORT_NUM);
        socket.send(packet);

        // Get response
        buf = new byte[28];
        packet = new DatagramPacket(buf, buf.length);
        socket.receive(packet);

        System.out.println("StageA data: " + Arrays.toString(packet.getData()));
        socket.close();
        return packet.getData();

    }

    public static byte[] stage2(byte[] response) throws IOException {
        ByteBuffer responseBuffer = ByteBuffer.wrap(response);

        // store all the data
        int num = responseBuffer.getInt(12);
        int len = responseBuffer.getInt(16);
        int realLength = len + 4;
        int udp_port = responseBuffer.getInt(20);
        int secretA = responseBuffer.getInt(24);

        //=====================================================
        // create bufferPacket for sending to the server
        //=====================================================

        // construct header
        ByteBuffer header = ByteBuffer.allocate(12);

        header.putInt(realLength);
        header.putInt(secretA);
        header.putShort((short)1);
        header.putShort((short)469);

        // make payload size divisible by 4
        // int payloadLen = (int) Math.ceil(len * 1.0 / 4) * 4;
        int payloadLen = realLength;
        while(payloadLen % 4 != 0) {
            payloadLen++;
        }


        // construct an array of packets
        ByteBuffer[] payloads = new ByteBuffer[num];
        for(int i = 0; i < num; i++) {
            ByteBuffer payloadBody = ByteBuffer.allocate(payloadLen);

            //packet_id
            payloadBody.putInt(i);

            // construct a packet by concatenating header and payload
            ByteBuffer payload = ByteBuffer.allocate(12 + payloadLen);
            payload.put(header.array());
            payload.put(payloadBody.array());
            payloads[i] = payload;
        }

        //=====================================================
        // Sends packets to the server
        //=====================================================

        // get datagram socket
        DatagramSocket socket = new DatagramSocket();
        InetAddress ipAddress = InetAddress.getByName(HOST_NAME);

        // send all the packets to the server
        for(int i = 0; i < payloads.length; i++) {
            byte[] packetBytes = payloads[i].array();

            // create data packet and send
            DatagramPacket packet = new DatagramPacket(packetBytes, packetBytes.length, ipAddress, udp_port);
            socket.send(packet);

            // Get response
            byte[] responsePacketBytes = new byte[16];
            DatagramPacket responsePacket = new DatagramPacket(responsePacketBytes, responsePacketBytes.length);
            socket.setSoTimeout(500);
            while(true) {
                try {
                    socket.receive(responsePacket);
                } catch(SocketTimeoutException e) {
                    // resend
                    socket.send(packet);
                    continue;
                }
                System.out.println("Packet stage B received: " + Arrays.toString(responsePacket.getData()));
                // if we get here, it means the packet is received
                break;
            }
        }

        // once the server received all the packets, it sends a UDP packet back
        byte[] bytes = new byte[20];
        DatagramPacket resultPacket = new DatagramPacket(bytes, bytes.length);
        socket.receive(resultPacket);

        System.out.println("StageB : " + Arrays.toString(resultPacket.getData()));
        socket.close();
        return resultPacket.getData();

    }

    public static byte[] stage3(byte[] response) throws IOException{
        ByteBuffer responseBuffer = ByteBuffer.wrap(response);
        int tcp_port = responseBuffer.getInt(12);
        int secretB = responseBuffer.getInt(16);

        socket = new Socket(HOST_NAME, tcp_port);
        DataInputStream dIn = new DataInputStream(socket.getInputStream());

        // read response from the server
        byte[] responseBytes = new byte[28];
        dIn.read(responseBytes, 0, responseBytes.length);

        System.out.println("StageC: " + Arrays.toString(responseBytes));


        return responseBytes;
    }

    public static byte[] stage4(byte[] response) throws IOException {
        ByteBuffer responseBuffer = ByteBuffer.wrap(response);
        int num = responseBuffer.getInt(12);
        int len = responseBuffer.getInt(16);
        int secretC = responseBuffer.getInt(20);
        byte character = responseBuffer.get(24);

        // Make payloadLen 4-bytes aligned
        int payloadLen = len;
        while(payloadLen % 4 != 0) {
            payloadLen++;
        }

        // construct header
        ByteBuffer header = ByteBuffer.allocate(12);
        header.putInt(len);
        header.putInt(secretC);
        header.putShort((short)1);
        header.putShort((short)469);


        // construct payload
        ByteBuffer payload = ByteBuffer.allocate(payloadLen);
        for(int i = 0; i < payloadLen; i ++) {
            payload.put(character);
        }

        // construct whole packet
        ByteBuffer packetBuf = ByteBuffer.allocate(12 + payloadLen);
        packetBuf.put(header.array());
        packetBuf.put(payload.array());
        byte[] packetBytes = packetBuf.array();


        DataOutputStream dOut = new DataOutputStream(socket.getOutputStream());
        DataInputStream dIn = new DataInputStream(socket.getInputStream());

        // send num number of packets
        for(int i = 0; i < num; i++) {
            dOut.write(packetBytes, 0, packetBytes.length);
        }

        // read the response from the server
        byte[] responseBytes = new byte[16];
        dIn.read(responseBytes, 0, responseBytes.length);
        System.out.println("StageD  " + Arrays.toString(responseBytes));
        socket.close();

        return responseBytes;
    }
}
