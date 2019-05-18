import json
from db_connector.db_connector import conn, error_log


def db_log(module_name, error_message):
    ins = error_log.insert().values(module_name=module_name, error_message=json.dumps(error_message))
    return conn.execute(ins)
