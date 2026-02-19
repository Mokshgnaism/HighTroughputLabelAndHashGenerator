package model;

public class Pallet {
    public String serialId;
    public String HASH;
    public String HASH_PREFIX;
    public Pallet(String serialId, String HASH, String HASH_PREFIX,String parentPalletId) {
        this.serialId = serialId;
        this.HASH = HASH;
        this.HASH_PREFIX = HASH_PREFIX;
    }
    public String getSerialId() {
        return serialId;
    }
    public void setSerialId(String serialId) {
        this.serialId = serialId;
    }
    public String getHASH() {
        return HASH;
    }
    public void setHASH(String HASH) {
        this.HASH = HASH;
    }
    public String getHASH_PREFIX() {
        return HASH_PREFIX;
    }
    public void setHASH_PREFIX(String HASH_PREFIX) {
        this.HASH_PREFIX = HASH_PREFIX;
    }
}
