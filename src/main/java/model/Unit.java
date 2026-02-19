package model;

public class Unit {
    String serialId;
    String HASH;
    String HASH_PREFIX;
    String parentCartonId;
    public Unit(String serialId, String HASH, String HASH_PREFIX,String parentCartonId) {
        this.serialId = serialId;
        this.HASH = HASH;
        this.HASH_PREFIX = HASH_PREFIX;
        this.parentCartonId = parentCartonId;
    }
    public String getSerialId() {
        return serialId;
    }
    public String getHASH() {
        return HASH;
    }
    public String getHASH_PREFIX() {
        return HASH_PREFIX;
    }
    public String getParentCartonId() {
        return parentCartonId;
    }
}
