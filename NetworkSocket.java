import java.util.Scanner;
import java.util.Random;
import java.util.Scanner;
import java.util.Random;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.lang.Object;
import java.util.ArrayList;
import java.util.List;
/*
 * Tips
 * • Use the nslookup command as an example of what information to display and
 * how.
 * • You only need to issue a single Type A question in the query. Since we are
 * only
 * working on a client program, multithreading is not necessary, either.
 * • Read RFC 1034 and RFC 1035 for protocol details of the DNS.
 * • Beware of byte- and bit-level operation of integer data types, and the
 * potential
 * overflow of two’s complement representation.
 * • Use Wireshark to monitor your communication with the DNS server when
 * debugging
 * our project.
 */

public class NetworkSocket {
    private static Scanner scanner = new Scanner(System.in); // define single Scanner obj as
    // static so it can be shared across methods

    public static void main(String[] args) throws Exception {
        String input = collectInput();
        if (input.length() == 0) {
            System.out.println("Hostname must have a value");
        }
        byte[] query = createQuery(input);
        DatagramPacket response = sendReceiveQuery(query);
        // question sect of parse response
        System.out.println("\n QUESTION SECTION:");
        System.out.println(input);
        parseResponse(response, response.getLength());
        scanner.close();
    }

    public static String collectInput() {
        System.out.print("Enter the hostname: ");
        String hostName = scanner.nextLine();
        return hostName;
    }

    public static byte[] createQuery(String hostName) throws Exception {
        // build dns header
        /*
         * Transaction ID (2 bytes)
         * Flags (2 bytes) -> recursive query
         * QDCOUNT (2 bytes) → number of questions -> usually 1, all others 0
         * ANCOUNT (2 bytes) → number of answers
         * NSCOUNT (2 bytes) → number of authority records
         * ARCOUNT (2 bytes) → number of additional records
         * 
         * manually place into byte array
         */
        // [header bytes] + [QNAME bytes] + [QTYPE bytes] + [QCLASS bytes] → full DNS
        // query

        Random random = new Random();
        int transactionID = random.nextInt(65536);

        byte[] header = new byte[12];
        header[0] = (byte) ((transactionID >> 8) & 0xFF); // High 8 bits
        header[1] = (byte) (transactionID & 0xFF); // Low 8 bits

        // flag: 0x0100 by default
        header[2] = 0x01; // high byte
        header[3] = 0x00; // low byte

        // QDCount
        header[4] = 0x00; // high byte
        header[5] = 0x01; // low byte
        header[6] = 0x00;
        header[7] = 0x00;
        header[8] = 0x00;
        header[9] = 0x00;
        header[10] = 0x00;
        header[11] = 0x00;

        // question
        // QNAME:the domain name you’re querying, encoded in DNS format (length-prefixed
        // labels)
        // QTYPE: 2 bytes, the type of record you want (e.g., 1 = A record for IPv4)
        // QCLASS → 2 bytes, the class of the query (almost always 1 = IN for Internet)

        // qname:
        List<String> labels = new ArrayList<>();
        List<Integer> lengths = new ArrayList<>();
        String newStr = "";
        for (int i = 0; i < hostName.length(); i++) {
            char c = hostName.charAt(i);

            if (c == '.') {
                // store the label and its length
                if (!newStr.isEmpty()) {
                    labels.add(newStr);
                    lengths.add(newStr.length());
                    newStr = ""; // reset for next label
                }
            } else {
                newStr += c;
            }
        }
        // Add the last label (after the final dot, if any)
        if (!newStr.isEmpty()) {
            labels.add(newStr);
            lengths.add(newStr.length());
        }

        /*
         * String qName = "";
         * for (int i = 0; i < labels.size(); i++) {
         * qName = qName + labels.get(i) + Integer.toString(lengths.get(i));
         * 
         * }
         * more deubgging
         */
        // System.out.println(qName); debugging

        // QType + Qclass = 4 bytes

        // now need to decode back into bytes
        int totalLength = 0;
        for (int i = 0; i < labels.size(); i++) {
            totalLength += 1 + lengths.get(i); // www will be stored as (length, www) w/ length -> 1 byte and www as 3
                                               // bytes
            // len: # of chars in label, 1 -> extra byte to store len of label
        }
        totalLength += 1; // final byte
        byte[] qnameBytes = new byte[totalLength];
        int index = 0;
        for (int i = 0; i < labels.size(); i++) {
            qnameBytes[index++] = lengths.get(i).byteValue(); // length byte
            for (char c : labels.get(i).toCharArray()) {
                qnameBytes[index++] = (byte) c; // character bytes
            }
        }
        qnameBytes[index] = 0; // mark terminated

        byte[] query = new byte[header.length + qnameBytes.length + 4]; // allocate query size to array
        int pos = 0;
        System.arraycopy(header, 0, query, pos, header.length); // System.arraycopy(source, sourceStart, destination,
                                                                // destStart, length)
        /*
         * Same as:
         * for (int i = 0; i < header.length; i++) {
         * query[i] = header[i];
         * }
         */
        // need to copy array to transfer bytes, then:
        pos += header.length; // move 12 bytes
        System.arraycopy(qnameBytes, 0, query, pos, qnameBytes.length);
        pos += qnameBytes.length; // find end of data, afer this add qtype/qclass

        // qtype = 1 (A record), give IPv4 address for hostname
        query[pos++] = 0x00; // higher bit
        query[pos++] = 0x01; // lower bit

        // qclass: internet, always 1:
        query[pos++] = 0x00;
        query[pos++] = 0x01;
        return query;
        // System.out.println(Arrays.toString(query));
    }

    public static DatagramPacket sendReceiveQuery(byte[] queryPacket) throws Exception {
        // need either IP of local dns: 127.0.0.1 or known public server like
        // google:8.8.8.8
        // port 53

        // create packet for connectionless delivery service
        InetAddress serverAddress = InetAddress.getByName("8.8.8.8");
        int port = 53;
        DatagramPacket pkt = new DatagramPacket(queryPacket, queryPacket.length, serverAddress, port);
        // create datagram socket
        DatagramSocket socket = new DatagramSocket(); // UDP so don't need to connect yet to specific host
        socket.setSoTimeout(5000); // 5 seconds
        socket.send(pkt);
        byte[] buffer = new byte[512];
        DatagramPacket response = new DatagramPacket(buffer, buffer.length);
        socket.receive(response);
        /*
         * debugging:
         * System.out.println(Arrays.toString(buffer));
         * System.out.println("Bytes received: " + response.getLength());
         */
        socket.close();
        return response;
    }

    public static void parseResponse(DatagramPacket response, int responseLength) throws Exception {
        //
        byte[] data = response.getData(); // extract data
        // header[0] = (byte) ((transactionID >> 8) & 0xFF); // High 8 bits
        // header[1] = (byte) (transactionID & 0xFF); // Low 8 bits
        // get header first

        // QDCOUNT: Question Count, skip because we only have 1 question
        int ancount = ((data[6] & 0xFF) << 8 | data[7] & 0xFF); // answer count
        int nscount = ((data[8] & 0xFF) << 8 | data[9] & 0xFF); // authority count
        int arcount = ((data[10] & 0xFF) << 8 | data[11] & 0xFF); // Additional count
        // System.out.println("ancount" + Integer.toString(ancount) + "nscount" + Integer.toString(nscount) + "arcount" + Integer.toString(arcount));
        // returns 600 for response length 12, 800 for response length 5

        // question starts at byte 12 (QNAME)
        int i = 12;
        while (data[i] != 0) {
            i++;
        }
        i += 5; // including null term/QTYPE/QCLASS

        

        // start by parsing w/ ans
        System.out.println("\n ANSWER SECTION:");
        int count = 0;
        while (count < ancount) {
            StringBuilder name = new StringBuilder();
            // name record
            // compress part of the names using pointers to avoid duplicates
            // check top two bits: if 11, pointer, if not, then regular length byte
            // data[index] & 0xC0 == 0xC0 (top two bits 11)
            
            if ((data[i] & 0xC0) == 0xC0) {
                // compression ponter: combine two butes to get offset
                int offset = ((data[i] & 0x3F) << 8) | (data[i + 1] & 0xFF); // upper/lower 6 bits of offset
                i += 2;
                int ptr = offset; // initalize pointer
                // read labels from offset pos
                while (data[ptr] != 0) {
                    int labelLen = data[ptr] & 0xFF; // convert to unsigned int
                    ptr++;
                    for (int j = 0; j < labelLen; j++) {
                        name.append((char) data[ptr++]);
                    }
                    name.append("."); // each . in hostname
                }
    
            } else {
                // regular labels
                while (data[i] != 0) {
                    int labelLen = data[i] & 0xFF; 
                    i++;
                    for (int j = 0; j < labelLen; j++) {
                        name.append((char) data[i++]);
                    }
                    name.append(".");
                }
                i++; // skip null terminator
            }

            // type record
            int type = ((data[i] & 0xFF) << 8 | data[i+1] & 0xFF);
            i += 2;
            // class
            int c = ((data[i] & 0xFF) << 8 | data[i+1] & 0xFF);
            i += 2;

            // TTL
            int ttl = ((data[i] & 0xFF) << 24 | (data[i+1] & 0xFF) << 16 | (data[i+2] & 0xFF) << 8 | (data[i+3] & 0xFF));
            i += 4;
            
            // RDLENGTH
            int rdLength = ((data[i] & 0xFF) << 8 | data[i+1] & 0xFF);
            i += 2;

            // RDATA: A exactly 4 bytes representing IPv4 address: 142 250 80 46, join with string after
            int j = 0;
            StringBuilder rData = new StringBuilder();
            while (j < rdLength) {
                int curr = data[i] & 0xFF;
                rData.append(curr);
    
                if (j < rdLength - 1) {
                    rData.append(".");
                }
                i++;
                j++;
            }
            System.out.println("Name: " + name + "Type: " + type + "Class" + c + "TTL" + ttl + "RDLength: " + rdLength + "RDATA: " + rData);
            count++;
        }

        System.out.println("\n AUTHORITY SECTION:");
        // nscount
        count = 0;
        while (count < nscount) {
            StringBuilder name = new StringBuilder();
            // name record
            // compress part of the names using pointers to avoid duplicates
            // check top two bits: if 11, pointer, if not, then regular length byte
            // data[index] & 0xC0 == 0xC0 (top two bits 11)
            
            if ((data[i] & 0xC0) == 0xC0) {
                // compression ponter: combine two butes to get offset
                int offset = ((data[i] & 0x3F) << 8) | (data[i + 1] & 0xFF); // upper/lower 6 bits of offset
                i += 2;
                int ptr = offset; // initalize pointer
                // read labels from offset pos
                while (data[ptr] != 0) {
                    int labelLen = data[ptr] & 0xFF; // convert to unsigned int
                    ptr++;
                    for (int j = 0; j < labelLen; j++) {
                        name.append((char) data[ptr++]);
                    }
                    name.append("."); // each . in hostname
                }
    
            } else {
                // regular labels
                while (data[i] != 0) {
                    int labelLen = data[i] & 0xFF; 
                    i++;
                    for (int j = 0; j < labelLen; j++) {
                        name.append((char) data[i++]);
                    }
                    name.append(".");
                }
                i++; // skip null terminator
            }

            // type record
            int type = ((data[i] & 0xFF) << 8 | data[i+1] & 0xFF);
            i += 2;
            // class
            int c = ((data[i] & 0xFF) << 8 | data[i+1] & 0xFF);
            i += 2;

            // TTL
            int ttl = ((data[i] & 0xFF) << 24 | (data[i+1] & 0xFF) << 16 | (data[i+2] & 0xFF) << 8 | (data[i+3] & 0xFF));
            i += 4;
            
            // RDLENGTH
            int rdLength = ((data[i] & 0xFF) << 8 | data[i+1] & 0xFF);
            i += 2;


            // RDATA: A exactly 4 bytes representing IPv4 address: 142 250 80 46, join with string after
            StringBuilder rData = new StringBuilder();
            if (type == 1) {
                int j = 0;
                while (j < rdLength) {
                    int curr = data[i] & 0xFF;
                    rData.append(curr);
        
                    if (j < rdLength - 1) {
                        rData.append(".");
                    }
                    i++;
                    j++;
                }
            } else {
                i += rdLength; // if not type A
            }
            System.out.println("Name: " + name + "Type: " + type + "Class" + c + "TTL" + ttl + "RDLength: " + rdLength + "RDATA: " + rData);
            count++;
            
        }

        // arcount
        System.out.println("\n ADDITIONAL SECTION:");
        count = 0;
        while (count < arcount) {
            StringBuilder name = new StringBuilder();
            // name record
            // compress part of the names using pointers to avoid duplicates
            // check top two bits: if 11, pointer, if not, then regular length byte
            // data[index] & 0xC0 == 0xC0 (top two bits 11)
            
            if ((data[i] & 0xC0) == 0xC0) {
                // compression ponter: combine two butes to get offset
                int offset = ((data[i] & 0x3F) << 8) | (data[i + 1] & 0xFF); // upper/lower 6 bits of offset
                i += 2;
                int ptr = offset; // initalize pointer
                // read labels from offset pos
                while (data[ptr] != 0) {
                    int labelLen = data[ptr] & 0xFF; // convert to unsigned int
                    ptr++;
                    for (int j = 0; j < labelLen; j++) {
                        name.append((char) data[ptr++]);
                    }
                    name.append("."); // each . in hostname
                }
    
            } else {
                // regular labels
                while (data[i] != 0) {
                    int labelLen = data[i] & 0xFF; 
                    i++;
                    for (int j = 0; j < labelLen; j++) {
                        name.append((char) data[i++]);
                    }
                    name.append(".");
                }
                i++; // skip null terminator
            }

            // type record
            int type = ((data[i] & 0xFF) << 8 | data[i+1] & 0xFF);
            i += 2;
            // class
            int c = ((data[i] & 0xFF) << 8 | data[i+1] & 0xFF);
            i += 2;

            // TTL
            int ttl = ((data[i] & 0xFF) << 24 | (data[i+1] & 0xFF) << 16 | (data[i+2] & 0xFF) << 8 | (data[i+3] & 0xFF));
            i += 4;
            
            // RDLENGTH
            int rdLength = ((data[i] & 0xFF) << 8 | data[i+1] & 0xFF);
            i += 2;
            StringBuilder rData = new StringBuilder();
            // RDATA: A exactly 4 bytes representing IPv4 address: 142 250 80 46, join with string after
            if (type == 1) {
                int j = 0;
                while (j < rdLength) {
                    int curr = data[i] & 0xFF;
                    rData.append(curr);
        
                    if (j < rdLength - 1) {
                        rData.append(".");
                    }
                    i++;
                    j++;
                }
            } else {
                i += rdLength; // if not type A
            }
        
            System.out.println("Name: " + name + "Type: " + type + "Class" + c + "TTL" + ttl + "RDLength: " + rdLength + "RDATA: " + rData);
            count++;
        }

        
        
    }

}   