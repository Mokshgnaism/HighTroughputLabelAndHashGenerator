package model;

public class Carton {
    private String serialId;
    private String HASH;
    private String HASH_PREFIX;
    private String parentPalletId;

    public Carton(String serialId, String HASH, String HASH_PREFIX,String parentPalletId) {
        this.serialId = serialId;
        this.HASH = HASH;
        this.parentPalletId = parentPalletId;
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
    public String getParentPalletId() {
        return parentPalletId;
    }
    public void setParentPalletId(String parentPalletId) {
        this.parentPalletId = parentPalletId;
    }
    public String getHASH_PREFIX() {
        return HASH_PREFIX;
    }
}
