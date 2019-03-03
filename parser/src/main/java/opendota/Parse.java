package opendota;

import com.google.gson.Gson;
import opendota.entity.DeathInfo;
import opendota.entity.Entry;
import opendota.entity.ErrorMessage;
import opendota.entity.ReplayResponse;
import skadistats.clarity.decoder.Util;
import skadistats.clarity.model.CombatLogEntry;
import skadistats.clarity.model.Entity;
import skadistats.clarity.model.FieldPath;
import skadistats.clarity.processor.entities.Entities;
import skadistats.clarity.processor.entities.UsesEntities;
import skadistats.clarity.processor.gameevents.OnCombatLogEntry;
import skadistats.clarity.processor.reader.OnTickStart;
import skadistats.clarity.processor.runner.Context;
import skadistats.clarity.processor.runner.SimpleRunner;
import skadistats.clarity.processor.stringtables.UsesStringTable;
import skadistats.clarity.source.InputStreamSource;
import skadistats.clarity.wire.common.proto.DotaUserMessages;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;

public class Parse {
    private OutputStream os;

    private final float INTERVAL = 1;
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
    private ReplayResponse replayResponse;

    public ReplayResponse getReplayResponse() {
        return replayResponse;
    }

    public void setReplayResponse(ReplayResponse replayResponse) {
        this.replayResponse = replayResponse;
    }

    public Parse(InputStream is, OutputStream os, ReplayResponse replayResponse) throws IOException {
        this.os = os;
        this.deathInfo = new DeathInfo();
        this.slotByName = new HashMap<>();
        this.replayResponse = replayResponse;

        this.setTimeCounter(is);
    }

    private void setTimeCounter(InputStream is) throws IOException {
        long tStart = System.currentTimeMillis();
        new SimpleRunner(new InputStreamSource(is)).runWith(this);
        long tMatch = System.currentTimeMillis() - tStart;

        System.err.format("total time taken: %s\n", (tMatch) / 1000.0);
    }

    private void output(Entry e) throws Exception {
        if (gameStartTime != 0) {
            e.time -= gameStartTime;
            replayResponse.getMatchEntries().add(e);
        }
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
                }
            }

            if (cle.getType().equals(DotaUserMessages.DOTA_COMBATLOG_TYPES.DOTA_COMBATLOG_DEATH) && cle.getType().ordinal() <= 19) {
                if (cle.getTargetName().contains("tower")) {
                    this.updateTowerDeathInfo(combatLogEntry);
                }

                if (cle.getTargetName().contains("roshan")) {
                    this.updateRoshanDeathInfo(combatLogEntry);
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

                time = 1;
                String precision = "%.0f";

                for (int i = 0; i < numPlayers; i++) {
                    Integer hero = getEntityProperty(pr, "m_vecPlayerTeamData.%i.m_nSelectedHeroID", validIndices[i]);
                    int handle = getEntityProperty(pr, "m_vecPlayerTeamData.%i.m_hSelectedHero", validIndices[i]);
                    int playerTeam = getEntityProperty(pr, "m_vecPlayerData.%i.m_iPlayerTeam", validIndices[i]);
                    int teamSlot = getEntityProperty(pr, "m_vecPlayerTeamData.%i.m_iTeamSlot", validIndices[i]);

                    //2 is radiant, 3 is dire, 1 is other?
                    Entity dataTeam = playerTeam == 2 ? rData : dData;

                    if (playerTeam == 2 || playerTeam == 3 && teamSlot >= 0) {
                        Integer level = getEntityProperty(pr, "m_vecPlayerTeamData.%i.m_iLevel", validIndices[i]);
                        Integer kills = getEntityProperty(pr, "m_vecPlayerTeamData.%i.m_iKills", validIndices[i]);
                        Integer deaths = getEntityProperty(pr, "m_vecPlayerTeamData.%i.m_iDeaths", validIndices[i]);
                        Integer assists = getEntityProperty(pr, "m_vecPlayerTeamData.%i.m_iAssists", validIndices[i]);
                        Integer denies = getEntityProperty(dataTeam, "m_vecDataTeam.%i.m_iDenyCount", teamSlot);
                        Integer gold = getEntityProperty(dataTeam, "m_vecDataTeam.%i.m_iTotalEarnedGold", teamSlot);
                        Integer lh = getEntityProperty(dataTeam, "m_vecDataTeam.%i.m_iLastHitCount", teamSlot);
                        Integer xp = getEntityProperty(dataTeam, "m_vecDataTeam.%i.m_iTotalEarnedXP", teamSlot);

                        if (i == 0) {
                            entry.level0 = String.format(precision, level / (double) time);
                            entry.kills0 = String.format(precision, kills / (double) time);
                            entry.deaths0 = String.format(precision, deaths / (double) time);
                            entry.assists0 = String.format(precision, assists / (double) time);
                            entry.denies0 = String.format(precision, denies / (double) time);
                            entry.gold0 = String.format(precision, gold / (double) time);
                            entry.lastHits0 = String.format(precision, lh / (double) time);
                            entry.xp0 = String.format(precision, xp / (double) time);
                        } else if (i == 1) {
                            entry.level1 = String.format(precision, level / (double) time);
                            entry.kills1 = String.format(precision, kills / (double) time);
                            entry.deaths1 = String.format(precision, deaths / (double) time);
                            entry.assists1 = String.format(precision, assists / (double) time);
                            entry.denies1 = String.format(precision, denies / (double) time);
                            entry.gold1 = String.format(precision, gold / (double) time);
                            entry.lastHits1 = String.format(precision, lh / (double) time);
                            entry.xp1 = String.format(precision, xp / (double) time);
                        } else if (i == 2) {
                            entry.level2 = String.format(precision, level / (double) time);
                            entry.kills2 = String.format(precision, kills / (double) time);
                            entry.deaths2 = String.format(precision, deaths / (double) time);
                            entry.assists2 = String.format(precision, assists / (double) time);
                            entry.denies2 = String.format(precision, denies / (double) time);
                            entry.gold2 = String.format(precision, gold / (double) time);
                            entry.lastHits2 = String.format(precision, lh / (double) time);
                            entry.xp2 = String.format(precision, xp / (double) time);
                        } else if (i == 3) {
                            entry.level3 = String.format(precision, level / (double) time);
                            entry.kills3 = String.format(precision, kills / (double) time);
                            entry.deaths3 = String.format(precision, deaths / (double) time);
                            entry.assists3 = String.format(precision, assists / (double) time);
                            entry.denies3 = String.format(precision, denies / (double) time);
                            entry.gold3 = String.format(precision, gold / (double) time);
                            entry.lastHits3 = String.format(precision, lh / (double) time);
                            entry.xp3 = String.format(precision, xp / (double) time);
                        } else if (i == 4) {
                            entry.level4 = String.format(precision, level / (double) time);
                            entry.kills4 = String.format(precision, kills / (double) time);
                            entry.deaths4 = String.format(precision, deaths / (double) time);
                            entry.assists4 = String.format(precision, assists / (double) time);
                            entry.denies4 = String.format(precision, denies / (double) time);
                            entry.gold4 = String.format(precision, gold / (double) time);
                            entry.lastHits4 = String.format(precision, lh / (double) time);
                            entry.xp4 = String.format(precision, xp / (double) time);
                        } else if (i == 5) {
                            entry.level5 = String.format(precision, level / (double) time);
                            entry.kills5 = String.format(precision, kills / (double) time);
                            entry.deaths5 = String.format(precision, deaths / (double) time);
                            entry.assists5 = String.format(precision, assists / (double) time);
                            entry.denies5 = String.format(precision, denies / (double) time);
                            entry.gold5 = String.format(precision, gold / (double) time);
                            entry.lastHits5 = String.format(precision, lh / (double) time);
                            entry.xp5 = String.format(precision, xp / (double) time);
                        } else if (i == 6) {
                            entry.level6 = String.format(precision, level / (double) time);
                            entry.kills6 = String.format(precision, kills / (double) time);
                            entry.deaths6 = String.format(precision, deaths / (double) time);
                            entry.assists6 = String.format(precision, assists / (double) time);
                            entry.denies6 = String.format(precision, denies / (double) time);
                            entry.gold6 = String.format(precision, gold / (double) time);
                            entry.lastHits6 = String.format(precision, lh / (double) time);
                            entry.xp6 = String.format(precision, xp / (double) time);
                        } else if (i == 7) {
                            entry.level7 = String.format(precision, level / (double) time);
                            entry.kills7 = String.format(precision, kills / (double) time);
                            entry.deaths7 = String.format(precision, deaths / (double) time);
                            entry.assists7 = String.format(precision, assists / (double) time);
                            entry.denies7 = String.format(precision, denies / (double) time);
                            entry.gold7 = String.format(precision, gold / (double) time);
                            entry.lastHits7 = String.format(precision, lh / (double) time);
                            entry.xp7 = String.format(precision, xp / (double) time);
                        } else if (i == 8) {
                            entry.level8 = String.format(precision, level / (double) time);
                            entry.kills8 = String.format(precision, kills / (double) time);
                            entry.deaths8 = String.format(precision, deaths / (double) time);
                            entry.assists8 = String.format(precision, assists / (double) time);
                            entry.denies8 = String.format(precision, denies / (double) time);
                            entry.gold8 = String.format(precision, gold / (double) time);
                            entry.lastHits8 = String.format(precision, lh / (double) time);
                            entry.xp8 = String.format(precision, xp / (double) time);
                        } else if (i == 9) {
                            entry.level9 = String.format(precision, level / (double) time);
                            entry.kills9 = String.format(precision, kills / (double) time);
                            entry.deaths9 = String.format(precision, deaths / (double) time);
                            entry.assists9 = String.format(precision, assists / (double) time);
                            entry.denies9 = String.format(precision, denies / (double) time);
                            entry.gold9 = String.format(precision, gold / (double) time);
                            entry.lastHits9 = String.format(precision, lh / (double) time);
                            entry.xp9 = String.format(precision, xp / (double) time);
                        }

                        //get the player's hero entity
                        Entity e = ctx.getProcessor(Entities.class).getByHandle(handle);
                        if (this.slotByName.size() != numPlayers) {
                            if (e != null) {
                                String unit = e.getDtClass().getDtName();
                                String ending = unit.substring("CDOTA_Unit_Hero_".length());
                                String combatLogName = "npc_dota_hero_" + ending.toLowerCase();
                                combatLogName = combatLogName.replaceAll("_", "");
                                this.slotByName.putIfAbsent(combatLogName, i);
                            }
                        }
                    } else {
                        String errorMessage = String.format("Incorrect player team or team slot: playerTeam = %d, teamSlot = %d", playerTeam, teamSlot);
                        throw new Exception(errorMessage);
                    }
                }

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