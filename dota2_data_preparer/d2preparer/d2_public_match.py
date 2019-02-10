import requests
from d2preparer.db_connector import conn, public_match
from sqlalchemy import select
import time


def main():
    for i in range(1000):
        last_match = get_last_match_id()
        public_match_url = 'https://api.opendota.com/api/publicMatches'
        success = False
        attempts = 10
        min_sleep = 1
        max_sleep = 3

        while not success and attempts > 0:
            try:
                if last_match is None:
                    r = requests.get(public_match_url)
                else:
                    r = requests.get(f"{public_match_url}?less_than_match_id={last_match['match_id']}")
            except requests.exceptions.RequestException as e:
                print(e)
                attempts = attempts - 1
                time.sleep(max_sleep)
                continue

            json_result = r.json()

            if r.status_code == 200 and len(json_result) > 0:
                success = True
                time.sleep(min_sleep)
            else:
                print(f'Status code: {r.status_code}. Response: {r.text}')
                attempts = attempts - 1
                time.sleep(max_sleep)
    pass


def get_last_match_id():
    sel = select([public_match.c.match_id]).order_by(public_match.c.match_id)
    res = conn.execute(sel)
    return res.fetchone()


if __name__ == '__main__':
    main()
