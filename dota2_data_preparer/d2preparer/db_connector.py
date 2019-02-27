from sqlalchemy import create_engine, Table, MetaData, Column, BigInteger, Boolean, Integer, VARCHAR, TIMESTAMP
from sqlalchemy.dialects.postgresql import JSONB
from d2preparer.envvar import db_host, db_name, db_pass, db_port, db_user

engine = create_engine(f'postgresql+psycopg2://{db_user}:{db_pass}@{db_host}:{db_port}/{db_name}')

conn = engine.connect()

metadata = MetaData()

public_match = Table(
    'public_match',
    metadata,
    Column('match_pk', BigInteger, primary_key=True),
    Column('match_seq_num', BigInteger, nullable=False),
    Column('radiant_win', Boolean, nullable=False),
    Column('duration', Integer),
    Column('avg_mmr', Integer),
    Column('game_mode', Integer),
    Column('radiant_team', VARCHAR(25)),
    Column('dire_team', VARCHAR(25))
)

pro_match = Table(
    'pro_match',
    metadata,
    Column('match_pk', BigInteger, primary_key=True),
    Column('match_seq_num', Integer),
    Column('radiant_win', Boolean),
    Column('duration', Integer),
    Column('game_mode', Integer),
    Column('radiant_team_id', Integer),
    Column('radiant_name', VARCHAR(255)),
    Column('dire_team_id', Integer),
    Column('dire_name', VARCHAR(255)),
    Column('radiant_score', Integer),
    Column('dire_score', Integer),
    Column('radiant_team', VARCHAR(25)),
    Column('dire_team', VARCHAR(25)),
    Column('radiant_team_bans', VARCHAR(25)),
    Column('dire_team_bans', VARCHAR(25)),
    Column('first_blood_time', Integer),
    Column('human_players', Integer),
    Column('start_time', Integer),
    Column('replay_url', VARCHAR(255)),
    Column('downloaded_replay', Boolean)
)

error_log = Table(
    'error_log',
    metadata,
    Column('error_log_pk', BigInteger, primary_key=True),
    Column('error_message', JSONB, nullable=False),
    Column('module_name', VARCHAR(255), nullable=False),
    Column('date_creation', TIMESTAMP)
)
