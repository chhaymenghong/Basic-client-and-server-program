public interface Serverable {
     int server_port_number = 12235;
     int header_len = 12;
     int packet_id_size = 4;
     int stageA_pay_load_len = 16;

     int stageC_pay_load_len = 13;
     int stageC_padding = 3;

     int tcp_port_len = 4;
     int secret_len = 4;
     int ack_len = 4;
     int time_out = 3000;

     String stageA = "Stage A";
     String stageB = "Stage B";
     String stageC = "Stage C";
     String stageD = "Stage D";

}