package crypto;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.Mac;
import java.nio.charset.StandardCharsets;
public class LabelCrypto {
    public static  byte[] prefix;

    private static final ThreadLocal<Mac>baseMac =
            ThreadLocal.withInitial(()->{
                try{
                    Mac mac = Mac.getInstance("HmacSHA256");
                    mac.init(new SecretKeySpec(
                                    "SECRET_KEY_FOR_DUMMY".getBytes(StandardCharsets.UTF_8),
                                    "HmacSHA256"
                            )
                    );
                    mac.update(prefix);
                    return mac;
                }catch(Exception e){
                    throw new RuntimeException(e);
                }
            });

    private static final ThreadLocal<Mac> TL_MAC =
            ThreadLocal.withInitial(()->{
                try{
                    Mac mac = Mac.getInstance("HmacSHA256");
                    mac.init(new SecretKeySpec(
                            "SECRET_KEY_FOR_DUMMY".getBytes(StandardCharsets.UTF_8),
                            "HmacSHA256"
                            )
                    );
                    return mac;
                }catch(Exception e){
                    throw new RuntimeException(e);
                }
            });
    public static byte[] getHash(byte[] integer) throws CloneNotSupportedException {
        Mac mac = (Mac) (baseMac.get()).clone();
        mac.update(integer);
        return mac.doFinal();
    }
    public static byte[] getHashInBytes(byte[] payload){
        try{
            Mac mac = TL_MAC.get();
            return mac.doFinal(payload);
        }catch(Exception e){
            throw new RuntimeException("hash computation failed",e);
        }
    }
    public static String toHex(byte[]bytes){
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes){
            sb.append(String.format("%02x",b));
        }
        return String.valueOf(sb);
    }
}
