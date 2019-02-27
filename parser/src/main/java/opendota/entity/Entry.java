package opendota.entity;

public class Entry {
    //output values

    //entity fields
    public String rGold;
    public String rLh;
    public String rXp;
    public String rLevel;
    public String rKills;
    public String rDeaths;
    public String rAssists;
    public String rDenies;

    public String dGold;
    public String dLh;
    public String dXp;
    public String dLevel;
    public String dKills;
    public String dDeaths;
    public String dAssists;
    public String dDenies;

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
    public Integer hero_id;
    public String type;
    public Integer slot;
    public String unit;

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

//    public Integer roshans_killed;
//    public Integer towers_killed;
//    public Integer item_id;
//    public transient List<Item> hero_inventory;
//	  public Integer obs_placed;
//	  public Integer sen_placed;
//	  public Integer rune_pickups;

    public Entry() {
    }

    public Entry(Integer time) {
        this.time = time;
    }
}