import time

import backoff
import requests
from sqlalchemy import select

from d2preparer.db_connector import conn, pro_match
from d2preparer.error_log import db_log

MODULE_NAME = __file__


def main():
    match_sheets = 1000  # 100 per one sheet

    for i in range(match_sheets):
        print(f'Number of sheet: {i}')
        last_match_pk = get_last_match_pk()

        pro_match_url = 'https://api.opendota.com/api/proMatches'

        if last_match_pk is not None:
            res = get_pro_match(pro_match_url, params={'less_than_match_id': last_match_pk['match_pk']})
        else:
            res = get_pro_match(pro_match_url)

        matches_json = res.json()
        save_match_in_db(matches_json)

        time.sleep(0.3)


def save_match_in_db(matches_json):
    matches = []

    for match in matches_json:
        matches.append({
            'match_pk': match['match_id'],
            'radiant_win': match['radiant_win'],
            'duration': match['duration'],
            'radiant_team_id': match['radiant_team_id'],
            'radiant_name': match['radiant_name'],
            'dire_team_id': match['dire_team_id'],
            'dire_name': match['dire_name'],
            'radiant_score': match['radiant_score'],
            'dire_score': match['dire_score'],
            'start_time': match['start_time']
        })

    return conn.execute(pro_match.insert(), matches)


def pro_match_predicate(res):
    try:
        matches_len = len(res.json())
    except ValueError:
        matches_len = 0

    return res.status_code != 200 or matches_len == 0


def pro_match_backoff_handler(details):
    res = details['value']

    try:
        matches_len = len(res.json())
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


@backoff.on_exception(backoff.expo,
                      requests.exceptions.RequestException,
                      max_tries=8)
@backoff.on_predicate(backoff.expo,
                      pro_match_predicate,
                      max_tries=8,
                      on_backoff=pro_match_backoff_handler)
def get_pro_match(url, params=None):
    return requests.get(url, params)


def get_last_match_pk():
    sel = select([pro_match.c.match_pk]).order_by(pro_match.c.match_pk)
    res = conn.execute(sel)
    return res.fetchone()


if __name__ == '__main__':
    main()
