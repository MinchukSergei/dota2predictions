import backoff
import requests
from sqlalchemy import select

from d2preparer.db_connector import conn, pro_match
from d2preparer.error_log import db_log

MODULE_NAME = __file__


def main():
    match_sheets = 10000

    for i in range(match_sheets):
        print(f'Number of sheet: {i}')
        last_match_pk = get_last_match_pk_where_url_is_null()

        if last_match_pk is None:
            return

        last_match_pk = last_match_pk['match_pk']

        pro_match_url = f'https://api.opendota.com/api/matches/{last_match_pk}'

        res = get_match_info(pro_match_url)

        matches_json = res.json()
        save_match_in_db(matches_json)

    # time.sleep(0.3)


def save_match_in_db(matches_json):
    replay_url = 'MISSING'

    if 'replay_url' in matches_json:
        replay_url = matches_json['replay_url']

    update_statement = pro_match.update().where(
        pro_match.c.match_pk == matches_json['match_id']
    ).values(
        replay_url=replay_url
    )
    return conn.execute(update_statement)


def pro_match_predicate(res):
    parse_error = False
    miss_url = False
    miss_id = False

    try:
        parsed = res.json()
        if 'replay_url' not in parsed:
            miss_url = True
        if 'match_id' not in parsed:
            miss_id = True
    except ValueError:
        parse_error = True

    return res.status_code != 200 or parse_error or miss_url or miss_id


def pro_match_backoff_handler(details):
    res = details['value']

    parse_error = False
    miss_url = False
    miss_id = False

    try:
        parsed = res.json()
        if 'replay_url' not in parsed:
            miss_url = True
        if 'match_id' not in parsed:
            miss_id = True
    except ValueError:
        parse_error = True

    e_msg = {
        'reason': 'Request failed',
        'response_text': res.text,
        'status_code': res.status_code,
        'url': res.url,
        'parse_error': parse_error,
        'miss_url': miss_url,
        'miss_id': miss_id
    }

    db_log(MODULE_NAME, e_msg)


@backoff.on_exception(backoff.expo,
                      requests.exceptions.RequestException,
                      max_tries=9)
@backoff.on_predicate(backoff.expo,
                      pro_match_predicate,
                      max_tries=9,
                      on_backoff=pro_match_backoff_handler)
def get_match_info(url):
    print(f'Try: {url}')
    return requests.get(url)


def get_last_match_pk_where_url_is_null():
    sel = select([
        pro_match.c.match_pk
    ]).where(
        pro_match.c.replay_url == None
    ).order_by(
        pro_match.c.match_pk.desc()
    )
    res = conn.execute(sel)
    return res.fetchone()


if __name__ == '__main__':
    main()
