import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.nio.ByteBuffer;

/**
 *
 * Created by menghongchhay on 10/15/15.
 */
public class ServerProtocol implements Serverable {

    private ResponseGenerator responseGenerator;
    private PortGenerator portGenerator;

    private int prev_secret;
    private short student_id;


    public int num_packets;
    public int len_of_each_packet;
    public int current_port;
    public char character;
    public ServerProtocol(ResponseGenerator responseGenerator, PortGenerator portGenerator) {
        this.responseGenerator = responseGenerator;
        this.portGenerator = portGenerator;
        this.prev_secret = 0;
    }

    public void setSecret(int new_secret) {
        this.prev_secret = new_secret;
    }
    public int getSecret() {
        return this.prev_secret;
    }

    public short getStudentId() {
        return student_id;
    }
    public void setStudentId(short student_id) {
        this.student_id = student_id;
    }

    public void generateHeader(ByteBuffer byteBuffer, int payload_len, short step) {
        byteBuffer.putInt(payload_len);
        byteBuffer.putInt(getSecret());
        byteBuffer.putShort(step);
        byteBuffer.putShort(getStudentId());
    }

    public void setPort(int port) {
        this.current_port = port;
    }

    public int getPort() {
        return this.current_port;
    }

    public void generatePayLoadStageA(ByteBuffer byteBuffer) {
        this.num_packets = responseGenerator.generateNum();
        this.len_of_each_packet = responseGenerator.generateLen();
        this.current_port = portGenerator.getPortNumber();

        byteBuffer.putInt(num_packets);
        byteBuffer.putInt(len_of_each_packet);
        byteBuffer.putInt(current_port);

        // Reset expected header info for next stage
        int second_secret = responseGenerator.generateSecret();
        setSecret(second_secret);

        byteBuffer.putInt(second_secret);
    }

    public void generatePayLoadStageB1(ByteBuffer byteBuffer, int packet_id) {
        byteBuffer.putInt(packet_id);
    }

    /**
     * Return tcp port number
     *
     * @param byteBuffer used to send store data for sending to user
     * @return tcp port number
     */
    public int generatePayLoadStageB2(ByteBuffer byteBuffer) {
        int secret = responseGenerator.generateSecret();
        setSecret(secret);

        int tcp_port = portGenerator.getPortNumber();
        byteBuffer.putInt(tcp_port);
        byteBuffer.putInt(secret);
        return tcp_port;
    }

    /**
     * Generate payload for stage C
     *
     * @param byteBuffer used to send store data for sending to user
     * @throws IOException
     */
    public void generatePayLoadStageC(ByteBuffer byteBuffer) throws IOException {
        int new_secret = responseGenerator.generateSecret();
        setSecret(new_secret);

        this.num_packets = responseGenerator.generateNum();
        this.len_of_each_packet = responseGenerator.generateLen();
        byteBuffer.putInt(this.num_packets);
        byteBuffer.putInt(this.len_of_each_packet);
        int secret = responseGenerator.generateSecret();
        setSecret(secret);
        byteBuffer.putInt(secret);
        this.character = responseGenerator.generateChar();

        byteBuffer.put((byte) this.character);

        byteBuffer.put((byte) this.character).put((byte) this.character).put((byte) this.character); // padding
    }

    public void generatePayLoadStageD(ByteBuffer byteBuffer) throws  IOException {
        int secret = responseGenerator.generateSecret();
        byteBuffer.putInt(secret);
    }


    public boolean validateHeader(DatagramPacket packet_received, short expected_step, boolean checkStudentId) {
        // packet must be at least 12 bytes (must have header)
        // packet must be 4 byte align
        int packet_len = packet_received.getLength();
        if (packet_len < header_len || packet_len % 4 != 0) {
            return false;
        }
        byte[] packet_data = packet_received.getData();
        ByteBuffer byteBuffer = ByteBuffer.wrap(packet_data);
        int payload_len = byteBuffer.getInt(); // payload_len must be positive
        if (packet_len < 0) {
            return false;
        }
        int psecret = byteBuffer.getInt();
        short step = byteBuffer.getShort();
        short student_id = byteBuffer.getShort();

        if (psecret != this.prev_secret || step != expected_step) {
            return false;
        }
        if (checkStudentId) {
            return student_id == this.student_id;
        } else {
            this.student_id = student_id;
        }


        return true;
    }

    private ByteBuffer[] sliceArray(byte[] bytes) {
        ByteBuffer[] list_buffers = new ByteBuffer[this.num_packets];
        int packet_size = this.len_of_each_packet + header_len;
        for (int i = 0; i < this.num_packets; i++) {
            byte[] temp = new byte[packet_size];
            for (int j = 0; j < packet_size; j++) {
                temp[j] = bytes[i * packet_size + j];
            }
            list_buffers[i] = ByteBuffer.wrap(temp);
        }
        return list_buffers;
    }


    public boolean validatePacket(InputStream inputStream, int whole_transaction_size) {
        byte[] whole_data = new byte[whole_transaction_size];
        int offset = 0;
        try {
            int num_read = 0;
            int total_read = 0;


            while ((num_read = inputStream.read(whole_data, offset, whole_transaction_size - total_read)) > 0 )  {
                total_read += num_read;
                offset = total_read;
                if (total_read >= whole_transaction_size) {
                    break;
                }
            }

            if (total_read != whole_transaction_size || total_read % 4 != 0) {
                return false;
            }

            ByteBuffer[] list_buffers = sliceArray(whole_data);
           int packet_size = this.len_of_each_packet + header_len;
            for (int i = 0; i < this.num_packets; i++) {
                // now whole data has all the information
                ByteBuffer each_packet = list_buffers[i];


                int actual_len = each_packet.getInt();
                int prev_secret = each_packet.getInt();
                short step = each_packet.getShort();
                short studentID = each_packet.getShort();

                // validate Header
                if (actual_len != this.len_of_each_packet || prev_secret != this.getSecret() || step != (short)1 || studentID != this.getStudentId()) {
                    return false;
                }

                // validate payload

                for (int j = 0; j < this.len_of_each_packet; j++) {
                    if (each_packet.get() != this.character) {
                        return false;
                    }
                }
            }

        } catch (IOException e) {

        }
        return true;
    }



    public boolean validatePayLoadStageA(DatagramPacket packet_received) {
        byte[] original_packet = packet_received.getData();
        byte[] payload = getPayLoadArray(original_packet);
        byte[] hello_world = "hello world\0".getBytes();
        int len = hello_world.length;
        if (payload.length != len) {
            return false;
        }

        for(int i = 0; i < len; i++) {
            if (hello_world[i] != payload[i]) {
                return false;
            }
        }
        return true;
    }


    /**
     * Validate payload for stage B
     *
     * Return packet_id or -1
     *
     * @param packet_received  packet received from client
     * @return packet_id or -1 if the payload is not valid
     */
    public int validatePayLoadStageB(DatagramPacket packet_received) {
        byte[] original_packet = packet_received.getData();
        byte[] payload = getPayLoadArray(original_packet);

        // assert the size of the payload (packet_id + len_of_each_packet)
        if (payload.length != packet_id_size + len_of_each_packet) {
            return -1;
        }
        ByteBuffer byteBuffer = ByteBuffer.wrap(payload);
        int packet_id = byteBuffer.getInt();

        if (!(packet_id >= 0 && packet_id < num_packets)) {
            return -1;
        }
        // payload size excluding the packet_id field
        int payload_size_without_packet_id = payload.length - packet_id_size;

        // check for 0s
        for (int i = 0; i < payload_size_without_packet_id; i++) {
            if (byteBuffer.get(packet_id_size+i) != 0) {
                return -1;
            }
        }
        return packet_id;
    }

    public void recycle_port(int port) {
        portGenerator.recycle_port(port);
    }

    private byte[] getPayLoadArray(byte[] bytes) {
        int original_size = bytes.length;
        byte[] payload = new byte[original_size - header_len];
        int new_size = payload.length;
        for (int i = 0; i < new_size; i++) {
            payload[i] = bytes[i + header_len];
        }
         return payload;
    }


}
