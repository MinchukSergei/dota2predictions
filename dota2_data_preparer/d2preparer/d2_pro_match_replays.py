from pathlib import Path

import backoff
import requests
from sqlalchemy import select, and_

from d2preparer.db_connector import conn, pro_match
from d2preparer.error_log import db_log

MODULE_NAME = __file__


def main():
    match_sheets = 10000

    for i in range(match_sheets):
        match_to_download = get_match_pk_not_downloaded()

        if match_to_download is None:
            return

        last_match_pk = match_to_download['match_pk']
        replay_url = match_to_download['replay_url']

        print(f'Number of sheet: {i}. Match url: {replay_url}')

        res = download_replay(replay_url)

        replays_folder = Path('E:/Programming/bsuir/diss/replays')
        file_name = replay_url.rsplit('/', 1)[-1]

        if res.status_code == 200 and res.content:
            open(replays_folder / file_name, 'wb').write(res.content)
            save_match_in_db(last_match_pk, True)
        else:
            save_match_in_db(last_match_pk, False)

    # time.sleep(0.3)


def save_match_in_db(match_pk, success):
    update_statement = pro_match.update().where(
        pro_match.c.match_pk == match_pk
    ).values(
        downloaded_replay=success
    )
    return conn.execute(update_statement)


def pro_match_predicate(res):
    return res.status_code != 200 or not res.content


def pro_match_backoff_handler(details):
    res = details['value']

    e_msg = {
        'reason': 'Request failed',
        'response_text': res.text,
        'status_code': res.status_code,
        'url': res.url,
        'content_len': len(res.content)
    }

    db_log(MODULE_NAME, e_msg)


@backoff.on_exception(backoff.expo,
                      requests.exceptions.RequestException,
                      max_tries=5)
@backoff.on_predicate(backoff.expo,
                      pro_match_predicate,
                      max_tries=5,
                      on_backoff=pro_match_backoff_handler)
def download_replay(url):
    print(f'Try: {url}')
    return requests.get(url)


def get_match_pk_not_downloaded():
    sel = select([
        pro_match.c.match_pk,
        pro_match.c.replay_url
    ]).where(
        and_(
            pro_match.c.game_mode == 2,
            pro_match.c.replay_url != None,
            pro_match.c.downloaded_replay == None
        )
    ).order_by(
        pro_match.c.match_pk.desc()
    )
    res = conn.execute(sel)
    return res.fetchone()


if __name__ == '__main__':
    main()
