import backoff
import requests
import time
from sqlalchemy import select, bindparam

from d2preparer.db_connector import conn, pro_match
from d2preparer.error_log import db_log

MODULE_NAME = __file__


def main():
    match_sheets = 1000

    for i in range(match_sheets):
        print(f'Number of sheet: {i}')

        last_match_pk = get_last_match_pk_where_seq_num_is_none()

        if last_match_pk is None:
            return

        pro_match_url = f'https://api.opendota.com/api/explorer'

        limit = 1000
        select_pro_details_sql = f'''with pbpr as (
                                      select match_id, array_to_string(array_agg(hero_id), ',') radiant_team
                                      from picks_bans
                                      where is_pick = true
                                        and team = 0
                                      group by match_id
                                    ),
                                         pbpd as (
                                           select match_id, array_to_string(array_agg(hero_id), ',') dire_team
                                           from picks_bans
                                           where is_pick = true
                                             and team = 1
                                           group by match_id
                                         ),
                                         pbbr as (
                                           select match_id, array_to_string(array_agg(hero_id), ',') radiant_team_bans
                                           from picks_bans
                                           where is_pick = false
                                             and team = 0
                                           group by match_id
                                         ),
                                         pbbd as (
                                           select match_id, array_to_string(array_agg(hero_id), ',') dire_team_bans
                                           from picks_bans
                                           where is_pick = false
                                             and team = 1
                                           group by match_id
                                         )
                                    select mat.match_id,
                                           mat.match_seq_num,
                                           mat.game_mode,
                                           mat.dire_score,
                                           mat.first_blood_time,
                                           mat.human_players,
                                           pbpr.radiant_team,
                                           pbpd.dire_team,
                                           pbbr.radiant_team_bans,
                                           pbbd.dire_team_bans
                                    from (select *
                                          from matches
                                          where match_id >= {last_match_pk}
                                          order by match_id
                                          limit {limit}) mat
                                           LEFT JOIN pbpr on mat.match_id = pbpr.match_id
                                           LEFT JOIN pbpd on mat.match_id = pbpd.match_id
                                           LEFT JOIN pbbr on mat.match_id = pbbr.match_id
                                           LEFT JOIN pbbd on mat.match_id = pbbd.match_id
                                    order by mat.match_id'''

        res = get_pro_match_details(pro_match_url, params={'sql': select_pro_details_sql})

        matches_json = res.json()
        save_match_in_db(matches_json)

        time.sleep(0.3)


def save_match_in_db(matches_json):
    matches = []

    for match in matches_json['rows']:
        matches.append({
            '_match_pk': match['match_id'],
            '_match_seq_num': match['match_seq_num'],
            '_game_mode': match['game_mode'],
            '_dire_score': match['dire_score'],
            '_first_blood_time': match['first_blood_time'],
            '_human_players': match['human_players'],
            '_radiant_team': match['radiant_team'],
            '_dire_team': match['dire_team'],
            '_radiant_team_bans': match['radiant_team_bans'],
            '_dire_team_bans': match['dire_team_bans']
        })

    update_statement = pro_match.update() \
        .where(pro_match.c.match_pk == bindparam('_match_pk')) \
        .values({
        'match_seq_num': bindparam('_match_seq_num'),
        'game_mode': bindparam('_game_mode'),
        'dire_score': bindparam('_dire_score'),
        'first_blood_time': bindparam('_first_blood_time'),
        'human_players': bindparam('_human_players'),
        'radiant_team': bindparam('_radiant_team'),
        'dire_team': bindparam('_dire_team'),
        'radiant_team_bans': bindparam('_radiant_team_bans'),
        'dire_team_bans': bindparam('_dire_team_bans')
    })

    return conn.execute(update_statement, matches)


def pro_match_predicate(res):
    try:
        matches_len = get_matches_len(res)
    except ValueError:
        matches_len = 0

    return res.status_code != 200 or matches_len == 0


def pro_match_backoff_handler(details):
    res = details['value']

    try:
        matches_len = get_matches_len(res)
    except ValueError:
        matches_len = f'can not parse response.text: {res.text}.'

    e_msg = {
        'reason': 'Request failed',
        'response_text': res.text,
        'status_code': res.status_code,
        'url': res.url,
        'matches_len': matches_len
    }

    db_log(MODULE_NAME, e_msg)


def get_matches_len(res):
    matches_json = res.json()
    return matches_json['rows'] if 'rows' in matches_json else 0


@backoff.on_exception(backoff.expo,
                      requests.exceptions.RequestException,
                      max_tries=8)
@backoff.on_predicate(backoff.expo,
                      pro_match_predicate,
                      max_tries=8,
                      on_backoff=pro_match_backoff_handler)
def get_pro_match_details(url, params=None):
    return requests.get(url, params)


def get_last_match_pk_where_seq_num_is_none():
    sel = select([pro_match.c.match_pk]) \
        .where(pro_match.c.match_seq_num == None) \
        .order_by(pro_match.c.match_pk)
    res = conn.execute(sel)
    return res.fetchone()


if __name__ == '__main__':
    main()
