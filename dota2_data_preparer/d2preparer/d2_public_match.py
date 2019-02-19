import backoff
import requests
import time
from sqlalchemy import select

from d2preparer.db_connector import conn, public_match
from d2preparer.error_log import db_log

MODULE_NAME = __file__


def main():
    match_sheets = 1000  # 100 per one sheet

    for i in range(match_sheets):
        print(f'Number of sheet: {i}')
        last_match = get_last_match_pk()

        public_match_url = 'https://api.opendota.com/api/publicMatches'

        if last_match is not None:
            last_match_pk = last_match['match_pk']
            public_match_url = f"{public_match_url}?less_than_match_id={last_match_pk}"

        res = get_pub_match(public_match_url)

        matches_json = res.json()
        save_match_in_db(matches_json)

        time.sleep(1)


def save_match_in_db(matches_json):
    matches = []

    for match in matches_json:
        matches.append({
            'match_pk': match['match_id'],
            'match_seq_num': match['match_seq_num'],
            'radiant_win': match['radiant_win'],
            'duration': match['duration'],
            'avg_mmr': match['avg_mmr'],
            'game_mode': match['game_mode'],
            'radiant_team': match['radiant_team'],
            'dire_team': match['dire_team']
        })

    return conn.execute(public_match.insert(), matches)


def pub_match_predicate(res):
    try:
        matches_len = len(res.json())
    except ValueError:
        matches_len = 0

    return res.status_code != 200 or matches_len == 0


def pub_match_backoff_handler(details):
    res = details['value']

    try:
        matches_len = len(res.json())
    except ValueError:
        matches_len = f'can not parse response.text: {res.text}'

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
                      pub_match_predicate,
                      max_tries=8,
                      on_backoff=pub_match_backoff_handler
                      )
def get_pub_match(url):
    return requests.get(url)


def get_last_match_pk():
    sel = select([public_match.c.match_pk]).order_by(public_match.c.match_pk)
    res = conn.execute(sel)
    return res.fetchone()


if __name__ == '__main__':
    main()
