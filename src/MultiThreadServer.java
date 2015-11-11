import java.io.*;
import java.lang.Thread;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class MultiThreadServer extends Thread implements Serverable {
    protected ServerProtocol server_protocol;
    protected DatagramPacket receive_packet;

    public MultiThreadServer(PortGenerator portGenerator, ResponseGenerator responseGenerator, DatagramPacket receive_packet) {
        this.receive_packet = receive_packet;
        this.server_protocol = new ServerProtocol (responseGenerator, portGenerator);
    }

    public void run() {
        try {
            stageA();
        } catch (IOException e) {

        }
    }

    public void stageA() throws IOException{
        printStartStatus(stageA);

        DatagramSocket stageA_socket = new DatagramSocket();
        boolean is_header_valid = server_protocol.validateHeader(this.receive_packet, (short)1, false); // validate header
        if (!is_header_valid) {
            printFailStatus(stageA);
            stageA_socket.close();
        }


        boolean is_payload_valid = server_protocol.validatePayLoadStageA(this.receive_packet);
        if (!is_payload_valid) {
            printFailStatus(stageA);
            stageA_socket.close();
        }

        // send back the response
        InetAddress address = this.receive_packet.getAddress();
        int client_port = this.receive_packet.getPort();

        // Populate response
        byte[] reponse_buf = new byte[28];
        ByteBuffer byteBuffer = ByteBuffer.wrap(reponse_buf);

        server_protocol.generateHeader(byteBuffer, stageA_pay_load_len, (short) 2);
        server_protocol.generatePayLoadStageA(byteBuffer);

        // send packet
        DatagramPacket packet_to_send = new DatagramPacket(reponse_buf, reponse_buf.length, address, client_port );
        stageA_socket.send(packet_to_send);
        stageA_socket.close();

        printSuccessStatus(stageA);

        stageB();
    }

    public void stageB() throws IOException {
        printStartStatus(stageB);
        // open connection on the new current_port
        DatagramSocket stageB_socket = new DatagramSocket(server_protocol.getPort());
        stageB_socket.setSoTimeout(time_out);
        Set<Integer> set_received_packets = new HashSet<>();

        int client_port = -1;
        InetAddress client_address = null;
        boolean firstTime = true;

        // stop when we have the required num_packets in the right order(from 0 to num_packets - 1)
        while (set_received_packets.size()  < server_protocol.num_packets) {
            int buffer_size = header_len + packet_id_size + server_protocol.len_of_each_packet;
            byte[] buf_receive = new byte[buffer_size];
            DatagramPacket packet_to_receive = new DatagramPacket(buf_receive, buffer_size);
            try {
                stageB_socket.receive(packet_to_receive);
            } catch (SocketTimeoutException e) {
                stageB_socket.close();
            }

            if (firstTime) {
                client_address = packet_to_receive.getAddress();
                client_port = packet_to_receive.getPort();
                firstTime = false;
                continue; // Once deny per transaction
            }

            boolean is_header_valid = server_protocol.validateHeader(packet_to_receive, (short)1, true);
            if (!is_header_valid) {
                printFailStatus(stageB);
                stageB_socket.close();
                server_protocol.recycle_port(server_protocol.getPort());
            }
            int packet_id = server_protocol.validatePayLoadStageB(packet_to_receive);
            if(packet_id == -1) {
                printFailStatus(stageB);
                stageB_socket.close();
                server_protocol.recycle_port(server_protocol.getPort());
            }

            // has to come in the correct order
            // send ack again if the client resends packet that has already been received
            // Send ack if recieved the packet in the right order or received the one that has already been acked
            boolean packet_received_before = set_received_packets.contains(packet_id);
            if (set_received_packets.size() == packet_id || packet_received_before ) {

                // Completely new packet, so add it to the pool
                if (!packet_received_before) {
                    set_received_packets.add(packet_id);
                }

                // then send an Acknowledgement
                byte[] buf_ack_response = new byte[header_len + ack_len];
                ByteBuffer ack_byte_buffer = ByteBuffer.wrap(buf_ack_response);
                server_protocol.generateHeader(ack_byte_buffer, ack_len, (short)1);
                server_protocol.generatePayLoadStageB1(ack_byte_buffer, packet_id);
                DatagramPacket b1_response = new DatagramPacket(buf_ack_response, buf_ack_response.length, client_address, client_port);
                stageB_socket.send(b1_response);
            }
        }

        // Send a final packet with tcp_port and new secret
        byte[] buf_response = new byte[header_len + tcp_port_len + secret_len];
        ByteBuffer response_byte_buffer = ByteBuffer.wrap(buf_response);
        server_protocol.generateHeader(response_byte_buffer, tcp_port_len + secret_len, (short)2);
        int tcp_port = server_protocol.generatePayLoadStageB2(response_byte_buffer);
        DatagramPacket b2_response = new DatagramPacket(buf_response, buf_response.length, client_address, client_port);

        // open tcp socket
        ServerSocket serverSocket = new ServerSocket(tcp_port);
        serverSocket.setSoTimeout(time_out);

        stageB_socket.send(b2_response);
        printSuccessStatus(stageB);
        stageB_socket.close();
        server_protocol.recycle_port(server_protocol.getPort());


        Socket client_socket;
        try {
            server_protocol.setPort(tcp_port);
            client_socket = serverSocket.accept();
            stageC(client_socket);
        } catch (SocketTimeoutException e) {
            serverSocket.close();
        }
    }

    public void stageC(Socket client_socket) {
        printStartStatus(stageC);
        try {

            ByteBuffer response_buf = ByteBuffer.allocate(header_len + stageC_pay_load_len + stageC_padding);
            server_protocol.generateHeader(response_buf, stageC_pay_load_len, (short)2);
            server_protocol.generatePayLoadStageC(response_buf);

            OutputStream outputStream = client_socket.getOutputStream();
            outputStream.write(response_buf.array());
            outputStream.flush();

            printSuccessStatus(stageC);

            stageD(client_socket);

        } catch(IOException e) {
            server_protocol.recycle_port(server_protocol.getPort());
            printFailStatus(stageC);
        }
    }

    public void stageD(Socket client_socket) {
        printStartStatus(stageD);

        // client will send num2 payload of length len2 filled with char c
        // validate client header
        int whole_transaction_size = (header_len + server_protocol.len_of_each_packet) * server_protocol.num_packets;
        try {
            InputStream inputStream = client_socket.getInputStream();
            DataOutputStream outputStream = new DataOutputStream( client_socket.getOutputStream() );
            // accept incoming packets
            boolean is_valid = server_protocol.validatePacket(inputStream, whole_transaction_size);
            if (is_valid) {
                ByteBuffer response_buf = ByteBuffer.allocate(header_len + secret_len);
                server_protocol.generateHeader(response_buf,secret_len,(short)2);
                server_protocol.generatePayLoadStageD(response_buf);
                outputStream.write(response_buf.array());
                outputStream.flush();

                printSuccessStatus(stageD);
            }
        } catch (IOException e) {

        }
    }

    private  void printStartStatus(String stage) {
        System.out.println("Server " + stage + "started...");
    }

    private void printSuccessStatus(String stage) {
        System.out.println("Server " + stage + " finished successfully...");
    }

    private void printFailStatus(String stage) {
        System.out.println("Server " + stage + "failed...");
    }

}