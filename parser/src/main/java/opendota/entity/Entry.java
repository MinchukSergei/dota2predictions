package opendota.entity;

public class Entry {
    //output values

    //entity fields
    public int gold0;
    public int gold1;
    public int gold2;
    public int gold3;
    public int gold4;
    public int gold5;
    public int gold6;
    public int gold7;
    public int gold8;
    public int gold9;

    public int lastHits0;
    public int lastHits1;
    public int lastHits2;
    public int lastHits3;
    public int lastHits4;
    public int lastHits5;
    public int lastHits6;
    public int lastHits7;
    public int lastHits8;
    public int lastHits9;

    public int xp0;
    public int xp1;
    public int xp2;
    public int xp3;
    public int xp4;
    public int xp5;
    public int xp6;
    public int xp7;
    public int xp8;
    public int xp9;

    public int level0;
    public int level1;
    public int level2;
    public int level3;
    public int level4;
    public int level5;
    public int level6;
    public int level7;
    public int level8;
    public int level9;

    public int kills0;
    public int kills1;
    public int kills2;
    public int kills3;
    public int kills4;
    public int kills5;
    public int kills6;
    public int kills7;
    public int kills8;
    public int kills9;

    public int deaths0;
    public int deaths1;
    public int deaths2;
    public int deaths3;
    public int deaths4;
    public int deaths5;
    public int deaths6;
    public int deaths7;
    public int deaths8;
    public int deaths9;

    public int assists0;
    public int assists1;
    public int assists2;
    public int assists3;
    public int assists4;
    public int assists5;
    public int assists6;
    public int assists7;
    public int assists8;
    public int assists9;

    public int denies0;
    public int denies1;
    public int denies2;
    public int denies3;
    public int denies4;
    public int denies5;
    public int denies6;
    public int denies7;
    public int denies8;
    public int denies9;

    //tower and roshan death info
    public boolean rt1t;
    public boolean rt2t;
    public boolean rt3t;
    public boolean rt1m;
    public boolean rt2m;
    public boolean rt3m;
    public boolean rt1b;
    public boolean rt2b;
    public boolean rt3b;

    public int rRosh;

    public boolean dt1t;
    public boolean dt2t;
    public boolean dt3t;
    public boolean dt1m;
    public boolean dt2m;
    public boolean dt3m;
    public boolean dt1b;
    public boolean dt2b;
    public boolean dt3b;

    public int dRosh;

    public Integer time;
    public String type;

    public Integer value;

    //combat log fields
    public String attackername;
    public String targetname;
    public String sourcename;
    public String targetsourcename;
    public Boolean attackerhero;
    public Boolean targethero;
    public Boolean attackerillusion;
    public Boolean targetillusion;
    public String inflictor;

    public Entry() {
    }

    public Entry(Integer time) {
        this.time = time;
    }
}