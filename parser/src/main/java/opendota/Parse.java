package opendota;

import opendota.entity.DeathInfo;
import opendota.entity.Entry;
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
import java.util.HashMap;

public class Parse {
    private final float INTERVAL = 1;
    private float nextInterval = 0;
    private Integer time = 0;

    private int numPlayers = 10;
    private int[] validIndices = new int[numPlayers];
    private boolean init = false;
    private int gameStartTime = 0;
    private boolean postGame = false; // true when ancient destroyed
    private DeathInfo deathInfo;
    private HashMap<String, Integer> slotByName;
    private HashMap<Integer, Integer> heroesOrder;
    private ReplayResponse replayResponse;

    public Parse(InputStream is, ReplayResponse replayResponse) throws IOException {
        this.deathInfo = new DeathInfo();
        this.slotByName = new HashMap<>();
        this.replayResponse = replayResponse;
        this.heroesOrder = replayResponse.getHeroesOrder();

        this.setTimeCounter(is);
    }

    private void setTimeCounter(InputStream is) throws IOException {
        long tStart = System.currentTimeMillis();
        new SimpleRunner(new InputStreamSource(is)).runWith(this);
        long tMatch = System.currentTimeMillis() - tStart;

        System.err.format("Total time taken: %s.\n", (tMatch) / 1000.0);
    }

    private void output(Entry e) {
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
                    updateTowerDeathInfo(combatLogEntry);
                }

                if (cle.getTargetName().contains("roshan")) {
                    updateRoshanDeathInfo(combatLogEntry);
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
                if (isRadiant) {
                    deathInfo.radiantTower1Top = false;
                } else {
                    deathInfo.direTower1Top = false;
                }
            }
            if (targetName.contains("tower2")) {
                if (isRadiant) {
                    deathInfo.radiantTower2Top = false;
                } else {
                    deathInfo.direTower2Top = false;
                }
            }
            if (targetName.contains("tower3")) {
                if (isRadiant) {
                    deathInfo.radiantTower3Top = false;
                } else {
                    deathInfo.direTower3Top = false;
                }
            }
        }

        if (targetName.contains("mid")) {
            if (targetName.contains("tower1")) {
                if (isRadiant) {
                    deathInfo.radiantTower1Mid = false;
                } else {
                    deathInfo.direTower1Mid = false;
                }
            }
            if (targetName.contains("tower2")) {
                if (isRadiant) {
                    deathInfo.radiantTower2Mid = false;
                } else {
                    deathInfo.direTower2Mid = false;
                }
            }
            if (targetName.contains("tower3")) {
                if (isRadiant) {
                    deathInfo.radiantTower3Mid = false;
                } else {
                    deathInfo.direTower3Mid = false;
                }
            }
        }

        if (targetName.contains("bot")) {
            if (targetName.contains("tower1")) {
                if (isRadiant) {
                    deathInfo.radiantTower1Bot = false;
                } else {
                    deathInfo.direTower1Bot = false;
                }
            }
            if (targetName.contains("tower2")) {
                if (isRadiant) {
                    deathInfo.radiantTower2Bot = false;
                } else {
                    deathInfo.direTower2Bot = false;
                }
            }
            if (targetName.contains("tower3")) {
                if (isRadiant) {
                    deathInfo.radiantTower3Bot = false;
                } else {
                    deathInfo.direTower3Bot = false;
                }
            }
        }
    }

    private void updateRoshanDeathInfo(Entry combatLogEntry) {
        String heroName = combatLogEntry.sourcename.replaceAll("_", "");
        Integer slot = slotByName.get(heroName);

        boolean isRadiant = slot >= 0 && slot <= 4;

        if (isRadiant) {
            deathInfo.radiantRoshan++;
        } else {
            deathInfo.direRoshan++;
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

                for (int i = 0; i < numPlayers; i++) {
                    int idx = validIndices[i];
                    Integer hero = getEntityProperty(pr, "m_vecPlayerTeamData.%i.m_nSelectedHeroID", idx);
                    int handle = getEntityProperty(pr, "m_vecPlayerTeamData.%i.m_hSelectedHero", idx);
                    int playerTeam = getEntityProperty(pr, "m_vecPlayerData.%i.m_iPlayerTeam", idx);
                    int teamSlot = getEntityProperty(pr, "m_vecPlayerTeamData.%i.m_iTeamSlot", idx);

                    //2 is radiant, 3 is dire, 1 is other?
                    Entity dataTeam = playerTeam == 2 ? rData : dData;

                    if (playerTeam == 2 || playerTeam == 3 && teamSlot >= 0) {
                        Integer level = getEntityProperty(pr, "m_vecPlayerTeamData.%i.m_iLevel", idx);
                        Integer kills = getEntityProperty(pr, "m_vecPlayerTeamData.%i.m_iKills", idx);
                        Integer deaths = getEntityProperty(pr, "m_vecPlayerTeamData.%i.m_iDeaths", idx);
                        Integer assists = getEntityProperty(pr, "m_vecPlayerTeamData.%i.m_iAssists", idx);
                        Integer denies = getEntityProperty(dataTeam, "m_vecDataTeam.%i.m_iDenyCount", teamSlot);
                        Integer gold = getEntityProperty(dataTeam, "m_vecDataTeam.%i.m_iTotalEarnedGold", teamSlot);
                        Integer lh = getEntityProperty(dataTeam, "m_vecDataTeam.%i.m_iLastHitCount", teamSlot);
                        Integer xp = getEntityProperty(dataTeam, "m_vecDataTeam.%i.m_iTotalEarnedXP", teamSlot);

                        if (idx == 0) {
                            entry.level0 = level;
                            entry.kills0 = kills;
                            entry.deaths0 = deaths;
                            entry.assists0 = assists;
                            entry.denies0 = denies;
                            entry.gold0 = gold;
                            entry.lastHits0 = lh;
                            entry.xp0 = xp;
                        } else if (idx == 1) {
                            entry.level1 = level;
                            entry.kills1 = kills;
                            entry.deaths1 = deaths;
                            entry.assists1 = assists;
                            entry.denies1 = denies;
                            entry.gold1 = gold;
                            entry.lastHits1 = lh;
                            entry.xp1 = xp;
                        } else if (idx == 2) {
                            entry.level2 = level;
                            entry.kills2 = kills;
                            entry.deaths2 = deaths;
                            entry.assists2 = assists;
                            entry.denies2 = denies;
                            entry.gold2 = gold;
                            entry.lastHits2 = lh;
                            entry.xp2 = xp;
                        } else if (idx == 3) {
                            entry.level3 = level;
                            entry.kills3 = kills;
                            entry.deaths3 = deaths;
                            entry.assists3 = assists;
                            entry.denies3 = denies;
                            entry.gold3 = gold;
                            entry.lastHits3 = lh;
                            entry.xp3 = xp;
                        } else if (idx == 4) {
                            entry.level4 = level;
                            entry.kills4 = kills;
                            entry.deaths4 = deaths;
                            entry.assists4 = assists;
                            entry.denies4 = denies;
                            entry.gold4 = gold;
                            entry.lastHits4 = lh;
                            entry.xp4 = xp;
                        } else if (idx == 5) {
                            entry.level5 = level;
                            entry.kills5 = kills;
                            entry.deaths5 = deaths;
                            entry.assists5 = assists;
                            entry.denies5 = denies;
                            entry.gold5 = gold;
                            entry.lastHits5 = lh;
                            entry.xp5 = xp;
                        } else if (idx == 6) {
                            entry.level6 = level;
                            entry.kills6 = kills;
                            entry.deaths6 = deaths;
                            entry.assists6 = assists;
                            entry.denies6 = denies;
                            entry.gold6 = gold;
                            entry.lastHits6 = lh;
                            entry.xp6 = xp;
                        } else if (idx == 7) {
                            entry.level7 = level;
                            entry.kills7 = kills;
                            entry.deaths7 = deaths;
                            entry.assists7 = assists;
                            entry.denies7 = denies;
                            entry.gold7 = gold;
                            entry.lastHits7 = lh;
                            entry.xp7 = xp;
                        } else if (idx == 8) {
                            entry.level8 = level;
                            entry.kills8 = kills;
                            entry.deaths8 = deaths;
                            entry.assists8 = assists;
                            entry.denies8 = denies;
                            entry.gold8 = gold;
                            entry.lastHits8 = lh;
                            entry.xp8 = xp;
                        } else if (idx == 9) {
                            entry.level9 = level;
                            entry.kills9 = kills;
                            entry.deaths9 = deaths;
                            entry.assists9 = assists;
                            entry.denies9 = denies;
                            entry.gold9 = gold;
                            entry.lastHits9 = lh;
                            entry.xp9 = xp;
                        }

                        //get the player's hero entity
                        Entity e = ctx.getProcessor(Entities.class).getByHandle(handle);
//                        if (this.slotByName.size() != numPlayers) {
                        if (e != null) {
                            String unit = e.getDtClass().getDtName();
                            String ending = unit.substring("CDOTA_Unit_Hero_".length());
                            String combatLogName = "npc_dota_hero_" + ending.toLowerCase();
                            combatLogName = combatLogName.replaceAll("_", "");

                            if (slotByName.get(combatLogName) != null) {
                                if (!slotByName.get(combatLogName).equals(idx)) {
                                    throw new Exception(String.format("Mismatch index and name %s, %d", combatLogName, idx));
                                }
                            }

                            if (heroesOrder.get(idx) != null) {
                                if (!heroesOrder.get(idx).equals(hero)) {
                                    throw new Exception(String.format("Mismatch index and heroId %d, %d", idx, hero));
                                }
                            }

                            heroesOrder.putIfAbsent(idx, hero);
                            slotByName.putIfAbsent(combatLogName, idx);
                        }
//                        }
                    } else {
                        String errorMessage = String.format("Incorrect player team or team slot: playerTeam = %d, teamSlot = %d", playerTeam, teamSlot);
                        throw new Exception(errorMessage);
                    }
                }

                entry.rt1t = deathInfo.radiantTower1Top;
                entry.rt2t = deathInfo.radiantTower2Top;
                entry.rt3t = deathInfo.radiantTower3Top;
                entry.rt1m = deathInfo.radiantTower1Mid;
                entry.rt2m = deathInfo.radiantTower2Mid;
                entry.rt3m = deathInfo.radiantTower3Mid;
                entry.rt1b = deathInfo.radiantTower1Bot;
                entry.rt2b = deathInfo.radiantTower2Bot;
                entry.rt3b = deathInfo.radiantTower3Bot;

                entry.rRosh = deathInfo.radiantRoshan;

                entry.dt1t = deathInfo.direTower1Top;
                entry.dt2t = deathInfo.direTower2Top;
                entry.dt3t = deathInfo.direTower3Top;
                entry.dt1m = deathInfo.direTower1Mid;
                entry.dt2m = deathInfo.direTower2Mid;
                entry.dt3m = deathInfo.direTower3Mid;
                entry.dt1b = deathInfo.direTower1Bot;
                entry.dt2b = deathInfo.direTower2Bot;
                entry.dt3b = deathInfo.direTower3Bot;

                entry.dRosh = deathInfo.direRoshan;

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