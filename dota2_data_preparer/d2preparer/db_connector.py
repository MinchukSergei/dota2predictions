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

error_log = Table(
    'error_log',
    metadata,
    Column('error_log_pk', BigInteger, primary_key=True),
    Column('error_message', JSONB, nullable=False),
    Column('module_name', VARCHAR(255), nullable=False),
    Column('date_creation', TIMESTAMP)
)
