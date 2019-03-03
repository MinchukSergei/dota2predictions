import bz2
from pathlib import Path

import backoff
import requests
from sqlalchemy import select, join, and_

from d2preparer.db_connector import conn, pro_match, pro_match_details
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

        with replay_name.open('rb') as replay_f:
            res = handle_match(pro_match_url, bz2.decompress(replay_f.read()))

            parse_json_fail, res_json = try_to_parse_json(res)

            if res.status_code != 200 or parse_json_fail or 'error' in res_json['errorMessage']:
                save_match_in_db(res_json, last_match_pk, False)
            else:
                save_match_in_db(res_json, last_match_pk, True)

    # time.sleep(0.3)


def save_match_in_db(matche_json, match_pk, success):
    t = 0

    # update_statement = pro_match.update().where(
    #     pro_match.c.match_pk == match_pk
    # ).values(
    #     parse_fail=not success
    # )
    # conn.execute(update_statement)

    if not success:
        return


def pro_match_predicate(res):
    match_error = False

    parse_json_fail, res_json = try_to_parse_json(res)

    if not parse_json_fail:
        match_error = res_json['errorMessage']

    return res.status_code != 200 and parse_json_fail and match_error


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
def handle_match(url, data):
    print(f'Try: {url}')
    return requests.post(url, data=data)


def get_last_match_pk_to_handle():
    left_join = join(
        pro_match,
        pro_match_details,
        pro_match.c.match_pk == pro_match_details.c.match_pk,
        isouter=True
    )

    sel = select([
        pro_match.c.match_pk
    ]).select_from(
        left_join
    ).where(
        and_(
            pro_match.c.game_mode == 2,
            pro_match.c.downloaded_replay == True,
            pro_match.c.parse_fail == None,
            pro_match_details.c.match_pk == None
        )
    ).order_by(
        pro_match.c.match_pk.desc()
    )

    res = conn.execute(sel)
    return res.fetchone()


if __name__ == '__main__':
    main()
