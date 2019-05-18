from sqlalchemy import select, func
import numpy as np
import csv
from keras.models import Sequential, load_model
from keras.layers import Dense, Flatten
from keras.optimizers import Adam
from keras.regularizers import l1
from keras.callbacks import EarlyStopping, ModelCheckpoint
from pathlib import Path

from db_connector.db_connector import conn, public_match

models_path = Path('./models')

one_hot_enc = {}


def main():
    generate_one_hot_enc()
    encode_heroes()


def encode_heroes():
    models = list(models_path.glob('*.hdf5'))
    models.sort(reverse=True)
    best_model = models[0] if len(models) > 0 else None

    if best_model:
        model = load_model(str(best_model.absolute()))
    else:
        model = Sequential([
            Dense(128, input_shape=(4, len(one_hot_enc)), kernel_initializer='glorot_uniform',
                  kernel_regularizer=l1(0)),
            Flatten(),
            Dense(len(one_hot_enc), activation='softmax')
        ])

        model.compile(
            optimizer=Adam(lr=0.001, beta_1=0.9, beta_2=0.999),
            loss='categorical_crossentropy',
            metrics=['accuracy']
        )

        public_match_count = get_public_match_count()[0]
        offset = 0
        limit = 50000

        for i in range(0, public_match_count, limit):
            (x_train, y_train), (x_valid, y_valid) = generate_pick_samples(0.97, 0.03, 0, limit, offset)[0:2]

            early_stopping = EarlyStopping(monitor='val_loss', patience=7, verbose=0, mode='min')
            mcp_save = ModelCheckpoint('./models/weights.{acc:.5f}.hdf5', save_best_only=True, monitor='val_acc',
                                       mode='max')

            model.fit(x_train, y_train, 512, 10, validation_data=(x_valid, y_valid),
                      callbacks=[early_stopping, mcp_save])

            offset += limit

    heroes_embeddings = model.layers[0].get_weights()[0]
    np.save('./heroes_data/heroes_embeddings', heroes_embeddings)


def get_public_match_count():
    sel = select([func.count()]).select_from(public_match)
    res = conn.execute(sel)
    return res.fetchone()


def get_all_heroes():
    sel = select([func.count()]).select_from(public_match)
    res = conn.execute(sel)
    return res.fetchone()


def convert_str_to_arr(heroes):
    h = heroes[0].split(',')
    if len(h) > 4:
        return [int(h[0]), int(h[1]), int(h[2]), int(h[3]), int(h[4])]
    else:
        return None


def generate_one_hot_enc():
    with open('./heroes_data/unique_heroes.csv') as csv_heroes:
        heroes = list(csv.reader(csv_heroes))
        heroes_len = len(heroes)

        for i, hero in enumerate(heroes):
            hero_id = int(hero[0])
            hero_enc = [0] * heroes_len
            hero_enc[i] = 1
            one_hot_enc[hero_id] = hero_enc


def generate_pick_samples(train, valid, test, limit, offset):
    picks = get_all_public_picks(limit, offset)
    picks = list(map(convert_str_to_arr, picks))
    picks = list(filter(None, picks))
    picks = np.array(picks)

    one_hot_enc_picks = one_hot_encode(picks)

    x = []
    y = []

    for p in one_hot_enc_picks:
        for i in range(5):
            x.append(list(p[0: i]) + list(p[i + 1:]))
            y.append(p[i])

    x = np.array(x)
    y = np.array(y)
    len_x = len(x)
    l_train = int(len_x * train)
    l_valid = int(len_x * valid)
    l_test = int(len_x * test)

    return (x[:l_train], y[:l_train]), \
           (x[l_train:l_train + l_valid], y[l_train:l_train + l_valid]), \
           (x[l_train + l_valid:l_train + l_valid + l_test], y[l_train + l_valid:l_train + l_valid + l_test])


def one_hot_encode(picks):
    one_hot_enc_picks = []

    for pick in picks:
        one_hot_enc_pick = []
        for hero in pick:
            one_hot_enc_pick.append(one_hot_enc[hero])
        one_hot_enc_picks.append(one_hot_enc_pick)

    return one_hot_enc_picks


def get_all_public_picks(limit, offset):
    sel_radiant = select(
        [public_match.c.radiant_team.label('heroes')]
    ).where(
        public_match.c.radiant_team != None
    )
    sel_dire = select(
        [public_match.c.dire_team.label('heroes')]
    ).where(
        public_match.c.dire_team != None
    )
    res = conn.execute(sel_radiant.union_all(sel_dire).limit(limit).offset(offset))
    return res.fetchall()


if __name__ == '__main__':
    main()
