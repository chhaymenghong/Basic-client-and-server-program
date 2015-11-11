import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class PortGenerator {
    private static final int START_PORT_NUM = 12236;
    private static final int END_PORT_NUM = 60000;
    private static final int TOTAL_PORT_NUM = 60000 - 12236 + 1;
    private Set<Integer> set_of_used_ports;
    private Random rand;
    public PortGenerator() {
        set_of_used_ports = new HashSet<>();
        rand = new Random();
    }

    // Return -1 upon failure
    synchronized public int getPortNumber() {
        if (set_of_used_ports.size() == TOTAL_PORT_NUM) {
            return -1;
        }
        int ret_port;
        do  {
           ret_port = rand.nextInt(47765) + START_PORT_NUM;
        } while (!set_of_used_ports.add(ret_port));
        System.out.println( "Port Num: " + ret_port + ". Port should be between: " + START_PORT_NUM + " and " + END_PORT_NUM);
        return ret_port;
    }
    synchronized public void recycle_port(int port) {
        set_of_used_ports.remove(port);
    }
}