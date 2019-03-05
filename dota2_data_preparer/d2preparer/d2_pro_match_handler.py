import bz2
from pathlib import Path

import backoff
import requests
from sqlalchemy import select, and_

from d2preparer.db_connector import conn, Session, pro_match, pro_match_details
from d2preparer.error_log import db_log

MODULE_NAME = __file__


def main():
    root_replays_dir = Path('E:/Programming/bsuir/diss/temp')

    match_sheets = 1000

    for i in range(match_sheets):
        print(f'Number of sheet: {i}')

        last_match = get_last_match_pk_to_handle()

        if last_match is None:
            return

        last_match_pk = last_match['match_pk']
        replay_to_handle = list(root_replays_dir.glob(f'*{last_match_pk}_*.dem.bz2'))

        if len(replay_to_handle) != 1:
            raise Exception(f'Cannot find {last_match_pk} replay.')

        pro_match_url = f'http://localhost:5600'
        replay_name = replay_to_handle[0]

        try:
            with replay_name.open('rb') as replay_f:
                res = handle_match(pro_match_url, bz2.decompress(replay_f.read()), replay_name)

                parse_json_fail, res_json = try_to_parse_json(res)

                if res.status_code != 200 or parse_json_fail or 'error' in res_json['errorMessage']:
                    save_match_in_db(res_json, last_match_pk, False)
                else:
                    save_match_in_db(res_json, last_match_pk, True)
        except Exception as e:
            save_match_in_db(None, last_match_pk, False)
            pro_match_error_handler(last_match_pk, replay_name, str(e))
    # time.sleep(0.3)


def save_match_in_db(match_json, match_pk, success):
    session = Session()

    update_statement = pro_match.update().where(
        pro_match.c.match_pk == match_pk
    ).values(
        parse_success=success
    )
    session.execute(update_statement)

    if success:
        heroes_order = match_json['heroesOrder']

        update_statement = pro_match.update().where(
            pro_match.c.match_pk == match_pk
        ).values(
            hero0=heroes_order['0'],
            hero1=heroes_order['1'],
            hero2=heroes_order['2'],
            hero3=heroes_order['3'],
            hero4=heroes_order['4'],
            hero5=heroes_order['5'],
            hero6=heroes_order['6'],
            hero7=heroes_order['7'],
            hero8=heroes_order['8'],
            hero9=heroes_order['9']
        )
        session.execute(update_statement)

        match_entries = match_json['matchEntries']

        entries = [create_match_entry(e, match_pk) for e in match_entries]

        session.execute(pro_match_details.insert(), entries)

    session.commit()
    session.close()


def pro_match_predicate(res):
    match_error = False

    parse_json_fail, res_json = try_to_parse_json(res)

    if not parse_json_fail:
        match_error = res_json['errorMessage']

    return res.status_code != 200 and parse_json_fail and match_error


def pro_match_error_handler(match_pk, file_name, error_message):
    e_msg = {
        'reason': 'Parse failed',
        'match_pk': match_pk,
        'file_name': file_name,
        'error': error_message
    }

    db_log(MODULE_NAME, e_msg)


def pro_match_backoff_handler(details):
    res = details['value']

    parse_json_fail, res_json = try_to_parse_json(res)

    e_msg = {
        'reason': 'Request failed',
        'response_text': res.text,
        'status_code': res.status_code,
        'url': res.url,
        'parse_json_fail': parse_json_fail,
        'match_error': res_json['errorMessage'].error if not parse_json_fail else None
    }

    db_log(MODULE_NAME, e_msg)


def try_to_parse_json(res):
    parse_json_fail = False
    res_json = None

    try:
        res_json = res.json()
    except ValueError:
        parse_json_fail = True

    return parse_json_fail, res_json


@backoff.on_exception(backoff.expo,
                      requests.exceptions.RequestException,
                      max_tries=3)
@backoff.on_predicate(backoff.expo,
                      pro_match_predicate,
                      max_tries=3,
                      on_backoff=pro_match_backoff_handler)
def handle_match(url, data, replay_name):
    print(f'Try: {url}. Replay: {replay_name}')
    return requests.post(url, data=data)


def get_last_match_pk_to_handle():
    sel = select([
        pro_match.c.match_pk
    ]).where(
        and_(
            pro_match.c.game_mode == 2,
            pro_match.c.downloaded_replay == True,
            pro_match.c.parse_success == None
        )
    ).order_by(
        pro_match.c.match_pk.desc()
    )

    res = conn.execute(sel)
    return res.fetchone()


def create_match_entry(entry, match_pk):
    return {
        'match_pk': match_pk,
        'gold0': entry['gold0'],
        'gold1': entry['gold1'],
        'gold2': entry['gold2'],
        'gold3': entry['gold3'],
        'gold4': entry['gold4'],
        'gold5': entry['gold5'],
        'gold6': entry['gold6'],
        'gold7': entry['gold7'],
        'gold8': entry['gold8'],
        'gold9': entry['gold9'],
        'xp0': entry['xp0'],
        'xp1': entry['xp1'],
        'xp2': entry['xp2'],
        'xp3': entry['xp3'],
        'xp4': entry['xp4'],
        'xp5': entry['xp5'],
        'xp6': entry['xp6'],
        'xp7': entry['xp7'],
        'xp8': entry['xp8'],
        'xp9': entry['xp9'],
        'last_hits0': entry['lastHits0'],
        'last_hits1': entry['lastHits1'],
        'last_hits2': entry['lastHits2'],
        'last_hits3': entry['lastHits3'],
        'last_hits4': entry['lastHits4'],
        'last_hits5': entry['lastHits5'],
        'last_hits6': entry['lastHits6'],
        'last_hits7': entry['lastHits7'],
        'last_hits8': entry['lastHits8'],
        'last_hits9': entry['lastHits9'],
        'level0': entry['level0'],
        'level1': entry['level1'],
        'level2': entry['level2'],
        'level3': entry['level3'],
        'level4': entry['level4'],
        'level5': entry['level5'],
        'level6': entry['level6'],
        'level7': entry['level7'],
        'level8': entry['level8'],
        'level9': entry['level9'],
        'kills0': entry['kills0'],
        'kills1': entry['kills1'],
        'kills2': entry['kills2'],
        'kills3': entry['kills3'],
        'kills4': entry['kills4'],
        'kills5': entry['kills5'],
        'kills6': entry['kills6'],
        'kills7': entry['kills7'],
        'kills8': entry['kills8'],
        'kills9': entry['kills9'],
        'deaths0': entry['deaths0'],
        'deaths1': entry['deaths1'],
        'deaths2': entry['deaths2'],
        'deaths3': entry['deaths3'],
        'deaths4': entry['deaths4'],
        'deaths5': entry['deaths5'],
        'deaths6': entry['deaths6'],
        'deaths7': entry['deaths7'],
        'deaths8': entry['deaths8'],
        'deaths9': entry['deaths9'],
        'assists0': entry['assists0'],
        'assists1': entry['assists1'],
        'assists2': entry['assists2'],
        'assists3': entry['assists3'],
        'assists4': entry['assists4'],
        'assists5': entry['assists5'],
        'assists6': entry['assists6'],
        'assists7': entry['assists7'],
        'assists8': entry['assists8'],
        'assists9': entry['assists9'],
        'denies0': entry['denies0'],
        'denies1': entry['denies1'],
        'denies2': entry['denies2'],
        'denies3': entry['denies3'],
        'denies4': entry['denies4'],
        'denies5': entry['denies5'],
        'denies6': entry['denies6'],
        'denies7': entry['denies7'],
        'denies8': entry['denies8'],
        'denies9': entry['denies9'],
        'rt1t': entry['rt1t'],
        'rt2t': entry['rt2t'],
        'rt3t': entry['rt3t'],
        'rt1m': entry['rt1m'],
        'rt2m': entry['rt2m'],
        'rt3m': entry['rt3m'],
        'rt1b': entry['rt1b'],
        'rt2b': entry['rt2b'],
        'rt3b': entry['rt3b'],
        'r_rosh': entry['rRosh'],
        'dt1t': entry['dt1t'],
        'dt2t': entry['dt2t'],
        'dt3t': entry['dt3t'],
        'dt1m': entry['dt1m'],
        'dt2m': entry['dt2m'],
        'dt3m': entry['dt3m'],
        'dt1b': entry['dt1b'],
        'dt2b': entry['dt2b'],
        'dt3b': entry['dt3b'],
        'd_rosh': entry['dRosh'],
        'time_part': entry['time']
    }


if __name__ == '__main__':
    main()
