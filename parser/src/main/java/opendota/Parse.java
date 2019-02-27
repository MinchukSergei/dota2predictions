package opendota;

import com.google.gson.Gson;
import opendota.entity.DeathInfo;
import opendota.entity.Entry;
import opendota.entity.Item;
import opendota.entity.TeamWorthInfo;
import opendota.exception.UnknownItemFoundException;
import skadistats.clarity.decoder.Util;
import skadistats.clarity.model.CombatLogEntry;
import skadistats.clarity.model.Entity;
import skadistats.clarity.model.FieldPath;
import skadistats.clarity.model.StringTable;
import skadistats.clarity.processor.entities.Entities;
import skadistats.clarity.processor.entities.UsesEntities;
import skadistats.clarity.processor.gameevents.OnCombatLogEntry;
import skadistats.clarity.processor.reader.OnTickStart;
import skadistats.clarity.processor.runner.Context;
import skadistats.clarity.processor.runner.SimpleRunner;
import skadistats.clarity.processor.stringtables.StringTables;
import skadistats.clarity.processor.stringtables.UsesStringTable;
import skadistats.clarity.source.InputStreamSource;
import skadistats.clarity.wire.common.proto.DotaUserMessages;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Parse {
    private OutputStream os;

    private float INTERVAL = 1;
    private float nextInterval = 0;
    private Integer time = 0;
    private int numPlayers = 10;
    private int[] validIndices = new int[numPlayers];
    private boolean init = false;
    private int gameStartTime = 0;
    private boolean postGame = false; // true when ancient destroyed
    private Gson g = new Gson();
    private DeathInfo deathInfo;
    private HashMap<String, Integer> slotByName;

    private ArrayList<Entry> logBuffer = new ArrayList<>();

    public Parse(InputStream is, OutputStream os) throws IOException {
        this.os = os;
        this.deathInfo = new DeathInfo();
        this.slotByName = new HashMap<>();

        this.setTimeCounter(is);
    }

    private void setTimeCounter(InputStream is) throws IOException {
        long tStart = System.currentTimeMillis();
        new SimpleRunner(new InputStreamSource(is)).runWith(this);
        long tMatch = System.currentTimeMillis() - tStart;

        System.err.format("total time taken: %s\n", (tMatch) / 1000.0);
    }

    private void output(Entry e) throws Exception {
        try {
            if (gameStartTime == 0) {
                logBuffer.add(e);
            } else {
                e.time -= gameStartTime;
                this.os.write((g.toJson(e) + "\n").getBytes());
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            throw new Exception(String.format("Exception during writing to file: %s", ex.getMessage()));
        }
    }

    private void flushLogBuffer() throws Exception {
        for (Entry e : logBuffer) {
            output(e);
        }
        logBuffer = null;
    }

    @OnCombatLogEntry
    public void onCombatLogEntry(Context ctx, CombatLogEntry cle) {
        try {
            time = Math.round(cle.getTimestamp());
            //create a new entry
            Entry combatLogEntry = new Entry(time);
            combatLogEntry.type = cle.getType().name();
            //translate the fields using string tables if necessary (get*Name methods)
            combatLogEntry.attackername = cle.getAttackerName();
            combatLogEntry.targetname = cle.getTargetName();
            combatLogEntry.sourcename = cle.getDamageSourceName();
            combatLogEntry.targetsourcename = cle.getTargetSourceName();
            combatLogEntry.inflictor = cle.getInflictorName();
            combatLogEntry.attackerhero = cle.isAttackerHero();
            combatLogEntry.targethero = cle.isTargetHero();
            combatLogEntry.attackerillusion = cle.isAttackerIllusion();
            combatLogEntry.targetillusion = cle.isTargetIllusion();
            combatLogEntry.value = cle.getValue();

            if (combatLogEntry.type.equals("DOTA_COMBATLOG_GAME_STATE") && combatLogEntry.value == 6) {
                postGame = true;
            }
            if (combatLogEntry.type.equals("DOTA_COMBATLOG_GAME_STATE") && combatLogEntry.value == 5) {
                //alternate to combat log for getting game zero time (looks like this is set at the same time as the game start, so it's not any better for streaming)
                // int currGameStartTime = Math.round( (float) grp.getProperty("m_pGameRules.m_flGameStartTime"));
                if (gameStartTime == 0) {
                    gameStartTime = combatLogEntry.time;
//                    flushLogBuffer();
                }
            }

            if (cle.getType().equals(DotaUserMessages.DOTA_COMBATLOG_TYPES.DOTA_COMBATLOG_DEATH) && cle.getType().ordinal() <= 19) {
                if (cle.getTargetName().contains("tower")) {
                    this.updateTowerDeathInfo(combatLogEntry);
//                    output(combatLogEntry);
                }

                if (cle.getTargetName().contains("roshan")) {
                    this.updateRoshanDeathInfo(combatLogEntry);
//                    output(combatLogEntry);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(cle);
        }
    }

    private void updateTowerDeathInfo(Entry combatLogEntry) {
        String targetName = combatLogEntry.targetname;
        boolean isRadiant = targetName.contains("goodguys");

        if (targetName.contains("top")) {
            if (targetName.contains("tower1")) {
                if (!isRadiant) {
                    this.deathInfo.radiantTower1Top = 1;
                } else {
                    this.deathInfo.direTower1Top = 1;
                }
            }
            if (targetName.contains("tower2")) {
                if (!isRadiant) {
                    this.deathInfo.radiantTower2Top = 1;
                } else {
                    this.deathInfo.direTower2Top = 1;
                }
            }
            if (targetName.contains("tower3")) {
                if (!isRadiant) {
                    this.deathInfo.radiantTower3Top = 1;
                } else {
                    this.deathInfo.direTower3Top = 1;
                }
            }
        }

        if (targetName.contains("mid")) {
            if (targetName.contains("tower1")) {
                if (!isRadiant) {
                    this.deathInfo.radiantTower1Mid = 1;
                } else {
                    this.deathInfo.direTower1Mid = 1;
                }
            }
            if (targetName.contains("tower2")) {
                if (!isRadiant) {
                    this.deathInfo.radiantTower2Mid = 1;
                } else {
                    this.deathInfo.direTower2Mid = 1;
                }
            }
            if (targetName.contains("tower3")) {
                if (!isRadiant) {
                    this.deathInfo.radiantTower3Mid = 1;
                } else {
                    this.deathInfo.direTower3Mid = 1;
                }
            }
        }

        if (targetName.contains("bot")) {
            if (targetName.contains("tower1")) {
                if (!isRadiant) {
                    this.deathInfo.radiantTower1Bot = 1;
                } else {
                    this.deathInfo.direTower1Bot = 1;
                }
            }
            if (targetName.contains("tower2")) {
                if (!isRadiant) {
                    this.deathInfo.radiantTower2Bot = 1;
                } else {
                    this.deathInfo.direTower2Bot = 1;
                }
            }
            if (targetName.contains("tower3")) {
                if (!isRadiant) {
                    this.deathInfo.radiantTower3Bot = 1;
                } else {
                    this.deathInfo.direTower3Bot = 1;
                }
            }
        }
    }

    private void updateRoshanDeathInfo(Entry combatLogEntry) {
        String heroName = combatLogEntry.sourcename.replaceAll("_", "");
        Integer slot = this.slotByName.get(heroName);

        boolean isRadiant = slot >= 0 && slot <= 4;

        if (isRadiant) {
            this.deathInfo.radiantRoshan++;
        } else {
            this.deathInfo.direRoshan++;
        }
    }

    @UsesStringTable("EntityNames")
    @UsesEntities
    @OnTickStart
    public void onTickStart(Context ctx, boolean synthetic) throws Exception {
        //s1 DT_DOTAGameRulesProxy
        Entity grp = ctx.getProcessor(Entities.class).getByDtName("CDOTAGamerulesProxy");
        Entity pr = ctx.getProcessor(Entities.class).getByDtName("CDOTA_PlayerResource");
        Entity dData = ctx.getProcessor(Entities.class).getByDtName("CDOTA_DataDire");
        Entity rData = ctx.getProcessor(Entities.class).getByDtName("CDOTA_DataRadiant");

        if (grp != null) {
            time = Math.round(getEntityProperty(grp, "m_pGameRules.m_fGameTime", null));
            //initialize nextInterval value
            if (nextInterval == 0) {
                nextInterval = time;
            }
        }
        if (pr != null) {
            //Radiant coach shows up in vecPlayerTeamData as position 5
            //all the remaining dire entities are offset by 1 and so we miss reading the last one and don't get data for the first dire player
            //coaches appear to be on team 1, radiant is 2 and dire is 3?
            //construct an array of valid indices to get vecPlayerTeamData from
            if (!init) {
                int added = 0;
                int i = 0;

                //according to @Decoud Valve seems to have fixed this issue and players should be in first 10 slots again
                //sanity check of i to prevent infinite loop when <10 players?
                while (added < numPlayers && i < 100) {
                    try {
                        int playerTeam = getEntityProperty(pr, "m_vecPlayerData.%i.m_iPlayerTeam", i);
                        if (playerTeam == 2 || playerTeam == 3) {
                            validIndices[added] = i;
                            added += 1;
                        }
                    } catch (Exception e) {
                        //swallow the exception when an unexpected number of players (!=10)
                        //System.err.println(e);
                    }

                    i += 1;
                }

                if (added != numPlayers) {
                    throw new Exception(String.format("Incorrect number of players: %d", added));
                }

                init = true;
            }

            if (!postGame && time >= nextInterval) {
                Entry entry = new Entry();
                entry.time = time;
                TeamWorthInfo teamWorthInfo = new TeamWorthInfo();

                for (int i = 0; i < numPlayers; i++) {
                    Integer hero = getEntityProperty(pr, "m_vecPlayerTeamData.%i.m_nSelectedHeroID", validIndices[i]);
                    int handle = getEntityProperty(pr, "m_vecPlayerTeamData.%i.m_hSelectedHero", validIndices[i]);
                    int playerTeam = getEntityProperty(pr, "m_vecPlayerData.%i.m_iPlayerTeam", validIndices[i]);
                    int teamSlot = getEntityProperty(pr, "m_vecPlayerTeamData.%i.m_iTeamSlot", validIndices[i]);

                    //2 is radiant, 3 is dire, 1 is other?
                    Entity dataTeam = playerTeam == 2 ? rData : dData;

                    if (playerTeam == 2 || playerTeam == 3 && teamSlot >= 0) {

//                        entry.type = "interval";
//                        entry.slot = i;

                        Integer level = getEntityProperty(pr, "m_vecPlayerTeamData.%i.m_iLevel", validIndices[i]);
                        Integer kills = getEntityProperty(pr, "m_vecPlayerTeamData.%i.m_iKills", validIndices[i]);
                        Integer deaths = getEntityProperty(pr, "m_vecPlayerTeamData.%i.m_iDeaths", validIndices[i]);
                        Integer assists = getEntityProperty(pr, "m_vecPlayerTeamData.%i.m_iAssists", validIndices[i]);
                        Integer denies = getEntityProperty(dataTeam, "m_vecDataTeam.%i.m_iDenyCount", teamSlot);
                        Integer gold = getEntityProperty(dataTeam, "m_vecDataTeam.%i.m_iTotalEarnedGold", teamSlot);
                        Integer lh = getEntityProperty(dataTeam, "m_vecDataTeam.%i.m_iLastHitCount", teamSlot);
                        Integer xp = getEntityProperty(dataTeam, "m_vecDataTeam.%i.m_iTotalEarnedXP", teamSlot);

                        if (i <= 4) {
                            teamWorthInfo.rLevel += level;
                            teamWorthInfo.rKills += kills;
                            teamWorthInfo.rDeaths += deaths;
                            teamWorthInfo.rAssists += assists;
                            teamWorthInfo.rDenies += denies;
                            teamWorthInfo.rGold += gold;
                            teamWorthInfo.rLh += lh;
                            teamWorthInfo.rXp += xp;
                        } else {
                            teamWorthInfo.dLevel += level;
                            teamWorthInfo.dKills += kills;
                            teamWorthInfo.dDeaths += deaths;
                            teamWorthInfo.dAssists += assists;
                            teamWorthInfo.dDenies += denies;
                            teamWorthInfo.dGold += gold;
                            teamWorthInfo.dLh += lh;
                            teamWorthInfo.dXp += xp;
                        }

                        //get the player's hero entity
                        Entity e = ctx.getProcessor(Entities.class).getByHandle(handle);
                        if (this.slotByName.size() != numPlayers) {
                            if (e != null) {
                                //get the hero's entity name, ex: CDOTA_Hero_Zuus
//                            entry.unit = e.getDtClass().getDtName();
//                            entry.hero_id = hero;

                                String unit = e.getDtClass().getDtName();
                                String ending = unit.substring("CDOTA_Unit_Hero_".length());
                                String combatLogName = "npc_dota_hero_" + ending.toLowerCase();
                                combatLogName = combatLogName.replaceAll("_", "");
                                this.slotByName.putIfAbsent(combatLogName, i);
                                //check if hero has been assigned to entity
//                        if (hero > 0) {
                                //get the hero's entity name, ex: CDOTA_Hero_Zuus
//                            String unit = e.getDtClass().getDtName();
                                //grab the end of the name, lowercase it
//                            String ending = unit.substring("CDOTA_Unit_Hero_".length());
                                //valve is bad at consistency and the combat log name could involve replacing camelCase with _ or not!
                                //double map it so we can look up both cases
//                            String combatLogName = "npc_dota_hero_" + ending.toLowerCase();
                                //don't include final underscore here since the first letter is always capitalized and will be converted to underscore

//                            entry.hero_inventory = getHeroInventory(ctx, e);
//                            if (entry.hero_inventory != null) {
//                                for (Item item : entry.hero_inventory) {
//                                    Entry startingItemsEntry = new Entry(time);
//                                    startingItemsEntry.type = "DOTA_ITEM";
//                                    startingItemsEntry.slot = entry.slot;
//                                    startingItemsEntry.value = (entry.slot < 5 ? 0 : 123) + entry.slot;
//                                    startingItemsEntry.item_id = item.id;
//                                    startingItemsEntry.valuename = item.name;
//
//                                    startingItemsEntry.targetname = combatLogName;
//                                    output(startingItemsEntry);
//                                }
//                            }
//                        }
                            }
                        }
                    } else {
                        String errorMessage = String.format("Incorrect player team or team slot: playerTeam = %d, teamSlot = %d", playerTeam, teamSlot);
                        throw new Exception(errorMessage);
                    }
                }

//                time /= 60;
                time = 1;

                String precision = "%.0f";

                entry.rLevel = String.format(precision, teamWorthInfo.rLevel / (double) time);
                entry.rKills = String.format(precision, teamWorthInfo.rKills / (double) time);
                entry.rDeaths = String.format(precision, teamWorthInfo.rDeaths / (double) time);
                entry.rAssists = String.format(precision, teamWorthInfo.rAssists / (double) time);
                entry.rDenies = String.format(precision, teamWorthInfo.rDenies / (double) time);
                entry.rGold = String.format(precision, teamWorthInfo.rGold / (double) time);
                entry.rLh = String.format(precision, teamWorthInfo.rLh / (double) time);
                entry.rXp = String.format(precision, teamWorthInfo.rXp / (double) time);

                entry.dLevel = String.format(precision, teamWorthInfo.dLevel / (double) time);
                entry.dKills = String.format(precision, teamWorthInfo.dKills / (double) time);
                entry.dDeaths = String.format(precision, teamWorthInfo.dDeaths / (double) time);
                entry.dAssists = String.format(precision, teamWorthInfo.dAssists / (double) time);
                entry.dDenies = String.format(precision, teamWorthInfo.dDenies / (double) time);
                entry.dGold = String.format(precision, teamWorthInfo.dGold / (double) time);
                entry.dLh = String.format(precision, teamWorthInfo.dLh / (double) time);
                entry.dXp = String.format(precision, teamWorthInfo.dXp / (double) time);

                entry.rt1t = String.format(precision, this.deathInfo.radiantTower1Top / (double) time);
                entry.rt2t = String.format(precision, this.deathInfo.radiantTower2Top / (double) time);
                entry.rt3t = String.format(precision, this.deathInfo.radiantTower3Top / (double) time);
                entry.rt1m = String.format(precision, this.deathInfo.radiantTower1Mid / (double) time);
                entry.rt2m = String.format(precision, this.deathInfo.radiantTower2Mid / (double) time);
                entry.rt3m = String.format(precision, this.deathInfo.radiantTower3Mid / (double) time);
                entry.rt1b = String.format(precision, this.deathInfo.radiantTower1Bot / (double) time);
                entry.rt2b = String.format(precision, this.deathInfo.radiantTower2Bot / (double) time);
                entry.rt3b = String.format(precision, this.deathInfo.radiantTower3Bot / (double) time);

                entry.rRosh = String.format(precision, this.deathInfo.radiantRoshan / (double) time);

                entry.dt1t = String.format(precision, this.deathInfo.direTower1Top / (double) time);
                entry.dt2t = String.format(precision, this.deathInfo.direTower2Top / (double) time);
                entry.dt3t = String.format(precision, this.deathInfo.direTower3Top / (double) time);
                entry.dt1m = String.format(precision, this.deathInfo.direTower1Mid / (double) time);
                entry.dt2m = String.format(precision, this.deathInfo.direTower2Mid / (double) time);
                entry.dt3m = String.format(precision, this.deathInfo.direTower3Mid / (double) time);
                entry.dt1b = String.format(precision, this.deathInfo.direTower1Bot / (double) time);
                entry.dt2b = String.format(precision, this.deathInfo.direTower2Bot / (double) time);
                entry.dt3b = String.format(precision, this.deathInfo.direTower3Bot / (double) time);

                entry.dRosh = String.format(precision, this.deathInfo.direRoshan / (double) time);

                output(entry);

                nextInterval += INTERVAL;
            }
        }
    }

    private List<Item> getHeroInventory(Context ctx, Entity eHero) {
        List<Item> inventoryList = new ArrayList<>(6);

        for (int i = 0; i < 6; i++) {
            try {
                Item item = getHeroItem(ctx, eHero, i);
                if (item != null) {
                    inventoryList.add(item);
                }
            } catch (UnknownItemFoundException e) {
                return null;
            }
        }

        return inventoryList;
    }

    /**
     * Uses "EntityNames" string table and Entities processor
     *
     * @param ctx   Context
     * @param eHero Hero entity
     * @param idx   0-5 - inventory, 6-8 - backpack, 9-16 - stash
     * @return {@code null} - empty slot. Throws @{@link UnknownItemFoundException} if item information can't be extracted
     */
    private Item getHeroItem(Context ctx, Entity eHero, int idx) throws UnknownItemFoundException {
        StringTable stEntityNames = ctx.getProcessor(StringTables.class).forName("EntityNames");
        Entities entities = ctx.getProcessor(Entities.class);

        Integer hItem = eHero.getProperty("m_hItems." + Util.arrayIdxToString(idx));
        if (hItem == 0xFFFFFF) {
            return null;
        }
        Entity eItem = entities.getByHandle(hItem);
        if (eItem == null) {
            throw new UnknownItemFoundException(String.format("Can't find item by its handle (%d)", hItem));
        }

        Integer itemId = eItem.getProperty("m_pEntity.m_nameStringableIndex");
        String itemName = stEntityNames.getNameByIndex(itemId);
        if (itemName == null) {
            throw new UnknownItemFoundException("Can't get item name from EntityName string table");
        }

        Item item = new Item();
        item.id = itemId;
        item.name = itemName;

        return item;
    }

    private <T> T getEntityProperty(Entity e, String property, Integer idx) {
        try {
            if (e == null) {
                return null;
            }
            if (idx != null) {
                property = property.replace("%i", Util.arrayIdxToString(idx));
            }
            FieldPath fp = e.getDtClass().getFieldPathForName(property);
            return e.getPropertyForFieldPath(fp);
        } catch (Exception ex) {
            return null;
        }
    }
}