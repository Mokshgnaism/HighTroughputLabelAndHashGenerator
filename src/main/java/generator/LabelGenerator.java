package generator;
import crypto.LabelCrypto;
import model.Unit;
import model.Carton;
import model.Pallet;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;


public class LabelGenerator {

    private static final byte[] HEX =
            "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);

    private static void writeByteaHex(OutputStream w, byte[] data, int len) throws IOException {

        w.write('\\');   // \
        w.write('x');    // x

        for (int i = 0; i < len; i++) {
            int v = data[i] & 0xFF;
            w.write(HEX[v >>> 4]);
            w.write(HEX[v & 0x0F]);
        }
    }


    private static int writeIntAscii(int value, byte[] buffer, int offset) {
        if (value == 0) {
            buffer[offset] = '0';
            return 1;
        }
        int tmp = value;
        int digits = 0;
        while (tmp > 0) {
            tmp /= 10;
            digits++;
        }
        int pos = offset + digits - 1;
        tmp = value;
        while (tmp > 0) {
            buffer[pos--] = (byte) ('0' + (tmp % 10));
            tmp /= 10;
        }
        return digits;
    }

    //    total micro optimisations making sure we use the strings as low as possible.
    public static void generatePalletIdsAndInsert(int start,int end,ArrayBlockingQueue<byte[]>PalletQueue, PipedOutputStream pos,String companyPrefix,String factoryId,String employeeId)throws Exception{
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String prefix = companyPrefix+factoryId+employeeId+timestamp;
        final byte []prefixBytes = prefix.getBytes(StandardCharsets.UTF_8);
        LabelCrypto.prefix = prefixBytes;
//        we can use the streaming version for the hash api we will see which one is better .. later for now lets keep it simple and move on withour current one
//        and both are having literally equal tradeoff . but if prefix length is dominant i think i should considder going for the streaimign api only
        final byte comma = ',';
        final byte newLine = '\n';
        BufferedOutputStream writer = new BufferedOutputStream(pos,1024*64);
        byte []payloadBuffer = new byte[prefixBytes.length+10];
        System.arraycopy(prefixBytes, 0, payloadBuffer, 0, prefixBytes.length);
        int prefixBytelength = prefixBytes.length;
        for(int i=start; i<end; i++){
            int len = writeIntAscii(i,payloadBuffer,prefixBytelength);
            int totalLen = prefixBytelength + len;

            byte[] payload = Arrays.copyOf(payloadBuffer, totalLen);

            byte [] hash = LabelCrypto.getHashInBytes(payload);

            PalletQueue.put(payload); // array blocking queue it is .

//            writer.write(payload);
            writeByteaHex(writer,payload,payload.length);
            writer.write(comma);

//            writer.write(hash);
            writeByteaHex(writer,hash,hash.length);
            writer.write(comma);

//            writer.write(hash, 0, Math.min(8, hash.length));
            writeByteaHex(writer,hash,Math.min(8, hash.length));
            writer.write(newLine);

        }
        writer.close();
    }

    public static void generateCartonIdsAndInsert(ArrayBlockingQueue<byte[]>palletQueue,ArrayBlockingQueue<byte[]>cartonQueue,PipedOutputStream pos,int cartonsPerPallet,byte []poison) throws InterruptedException, IOException {
        BufferedOutputStream writer = new BufferedOutputStream(pos,1024*64);
        while(true){
            byte [] parentPaletSerialId = palletQueue.take();
//            we have to make sure we are correctly sending the address... this will actually compare address not the value..... and this is faster than checking the original value .
            if(parentPaletSerialId==poison){
                break;
            }
            byte underscore = '_';
            byte [] payloadBuffer = new byte[parentPaletSerialId.length+15];

            System.arraycopy(parentPaletSerialId, 0, payloadBuffer, 0, parentPaletSerialId.length);
            payloadBuffer[parentPaletSerialId.length] = underscore;

            int offset1 = parentPaletSerialId.length+1;

            byte comma = ',';
            byte newline = '\n';


            for(int i=1;i<=cartonsPerPallet;i++){
                int len = writeIntAscii(i,payloadBuffer,offset1);
                int totalLen = offset1+len;
                byte[] payload = Arrays.copyOf(payloadBuffer, totalLen);
                byte [] hash = LabelCrypto.getHashInBytes(payload);
                cartonQueue.put(payload);

                writeByteaHex(writer,payload,payload.length);
//                writer.write(payload);
                writer.write(comma);

//                writer.write(parentPaletSerialId);
                writeByteaHex(writer,parentPaletSerialId,parentPaletSerialId.length);
                writer.write(comma);

//                writer.write(hash);
                writeByteaHex(writer,hash,hash.length);
                writer.write(comma);

                writeByteaHex(writer,hash,Math.min(8, hash.length));
                writer.write(newline);

            }

        }
        writer.close();
    }
    public static void generateUnitIdsAndInsert(ArrayBlockingQueue<byte[]>cartonQueue,int unitsPerCarton,byte[]poison,PipedOutputStream pos) throws InterruptedException, IOException {
        BufferedOutputStream writer = new BufferedOutputStream(pos,1024*64);
        while(true){
            byte [] parentCartonSerialId = cartonQueue.take();
            if(parentCartonSerialId==poison){
//                posion forwarding will be handdled in the main logic since the number off posions that should be kept is not known... thats why
                break;
            }
            byte underscore = '_';
            byte [] payloadBuffer = new byte[parentCartonSerialId.length+15];
            System.arraycopy(parentCartonSerialId, 0, payloadBuffer, 0, parentCartonSerialId.length);
            payloadBuffer[parentCartonSerialId.length] = underscore;
            int offset1 = parentCartonSerialId.length+1;
            byte comma = ',';
            byte newline = '\n';

            for(int i=1;i<=unitsPerCarton;i++){
                int len = writeIntAscii(i,payloadBuffer,offset1);
                int totalLen = offset1+len;
                byte[] payload = Arrays.copyOf(payloadBuffer, totalLen);
                byte [] hash = LabelCrypto.getHashInBytes(payload);

//                writer.write(payload);
                writeByteaHex(writer,payload,payload.length);
                writer.write(comma);

//                writer.write(parentCartonSerialId);
                writeByteaHex(writer,parentCartonSerialId,parentCartonSerialId.length);
                writer.write(comma);

                writeByteaHex(writer,hash,hash.length);
                writer.write(comma);

                writeByteaHex(writer,hash,Math.min(8, hash.length));
                writer.write(newline);
            }
        }
        writer.close();
    }
}
