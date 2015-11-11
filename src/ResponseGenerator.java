import java.util.Random;

public class ResponseGenerator {
    private Random rand;
    public ResponseGenerator(){
        rand = new Random();

    }

    // Return the number of packets required from the client
    // Ranges from 1 to 100
    public int generateNum() {
        return rand.nextInt(100) + 1;
    }

    // Return the number of bytes for each payload
    // range from 1 to 100
    public int generateLen() {
        int len;
        do {
            len = rand.nextInt(100) + 1;
        } while (len % 4 != 0);
        return len;
    }

    public char generateChar() {
        return (char)(rand.nextInt(26) + 97);
    }

    // Return the secret
    public int generateSecret() {
        return rand.nextInt(200);
    }
}