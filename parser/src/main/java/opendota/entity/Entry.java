package opendota.entity;

public class Entry {
    //output values

    //entity fields
    public String gold0;
    public String gold1;
    public String gold2;
    public String gold3;
    public String gold4;
    public String gold5;
    public String gold6;
    public String gold7;
    public String gold8;
    public String gold9;

    public String lastHits0;
    public String lastHits1;
    public String lastHits2;
    public String lastHits3;
    public String lastHits4;
    public String lastHits5;
    public String lastHits6;
    public String lastHits7;
    public String lastHits8;
    public String lastHits9;

    public String xp0;
    public String xp1;
    public String xp2;
    public String xp3;
    public String xp4;
    public String xp5;
    public String xp6;
    public String xp7;
    public String xp8;
    public String xp9;

    public String level0;
    public String level1;
    public String level2;
    public String level3;
    public String level4;
    public String level5;
    public String level6;
    public String level7;
    public String level8;
    public String level9;

    public String kills0;
    public String kills1;
    public String kills2;
    public String kills3;
    public String kills4;
    public String kills5;
    public String kills6;
    public String kills7;
    public String kills8;
    public String kills9;

    public String deaths0;
    public String deaths1;
    public String deaths2;
    public String deaths3;
    public String deaths4;
    public String deaths5;
    public String deaths6;
    public String deaths7;
    public String deaths8;
    public String deaths9;

    public String assists0;
    public String assists1;
    public String assists2;
    public String assists3;
    public String assists4;
    public String assists5;
    public String assists6;
    public String assists7;
    public String assists8;
    public String assists9;

    public String denies0;
    public String denies1;
    public String denies2;
    public String denies3;
    public String denies4;
    public String denies5;
    public String denies6;
    public String denies7;
    public String denies8;
    public String denies9;

    //tower and roshan death info
    public String rt1t;
    public String rt2t;
    public String rt3t;
    public String rt1m;
    public String rt2m;
    public String rt3m;
    public String rt1b;
    public String rt2b;
    public String rt3b;

    public String rRosh;

    public String dt1t;
    public String dt2t;
    public String dt3t;
    public String dt1m;
    public String dt2m;
    public String dt3m;
    public String dt1b;
    public String dt2b;
    public String dt3b;

    public String dRosh;

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