from keras import Sequential, Input, Model
from keras.layers import Dense, Dropout, concatenate
from keras.optimizers import Adam
from keras.regularizers import l2, l1, l1_l2
from sklearn.preprocessing import StandardScaler
from sqlalchemy import select, and_
import random
import numpy as np
import csv
from tqdm import tqdm

from db_connector.db_connector import pro_match, pro_match_details, conn


def main():
    model = build_model()
    fit_model(model)


def build_model():
    l2_reg_m = 0.001
    l1_reg_h = 0.01

    input_meta = Input(shape=(14,))
    input_heroes = Input(shape=(128,))

    x = Dense(512, kernel_regularizer=l2(l2_reg_m), activation='selu', kernel_initializer='lecun_normal')(input_meta)
    x = Dropout(0.3)(x)
    x = Dense(512, kernel_regularizer=l2(l2_reg_m), activation='selu', kernel_initializer='lecun_normal')(x)
    x = Dropout(0.2)(x)
    x = Dense(512, kernel_regularizer=l2(l2_reg_m), activation='selu', kernel_initializer='lecun_normal')(x)
    x = Model(inputs=input_meta, outputs=x)

    y = Dense(64, kernel_regularizer=l1(l1_reg_h), activation='selu', kernel_initializer='lecun_normal')(
        input_heroes)
    y = Dropout(0.5)(y)
    y = Dense(64, kernel_regularizer=l1(l1_reg_h), activation='selu', kernel_initializer='lecun_normal')(y)
    y = Dropout(0.5)(y)
    y = Dense(64, kernel_regularizer=l1(l1_reg_h), activation='selu', kernel_initializer='lecun_normal')(y)
    y = Model(inputs=input_heroes, outputs=y)

    combined = concatenate([x.output, y.output])

    z = Dense(2, kernel_regularizer=l1_l2(l1_reg_h, l2_reg_m), activation='selu')(combined)
    z = Dropout(0.1)(z)
    z = Dense(2, activation='softmax')(z)

    model = Model(inputs=[x.input, y.input], outputs=z)

    # model = Sequential([
    #     Dense(512, input_dim=162, kernel_regularizer=l2(l2_reg), activation='selu', kernel_initializer='lecun_normal'),
    #     Dropout(0.5),
    #     Dense(512, kernel_regularizer=l2(l2_reg), activation='selu', kernel_initializer='lecun_normal'),
    #     Dropout(0.5),
    #     Dense(512, kernel_regularizer=l2(l2_reg), activation='selu', kernel_initializer='lecun_normal'),
    #     Dropout(0.5),
    #     Dense(512, kernel_regularizer=l2(l2_reg), activation='selu', kernel_initializer='lecun_normal'),
    #     Dropout(0.5),
    #     Dense(512, kernel_regularizer=l2(l2_reg), activation='selu', kernel_initializer='lecun_normal'),
    #     Dense(2, activation='softmax')
    # ])

    model.compile(
        optimizer=Adam(lr=0.001, beta_1=0.9, beta_2=0.999),
        loss='categorical_crossentropy',
        metrics=['accuracy']
    )

    return model


def fit_model(model):
    pro_matches = get_pro_matches()
    random.shuffle(pro_matches)

    train_data, valid_data, test_data = split_data(pro_matches, 0.9, 0.03, 0.07)

    heroes_embeddings = np.load('../d2_heroes_encoder/heroes_data/heroes_embeddings.npy')
    embeddings_ids = get_embeddings_ids()

    # x_meta_arr = []
    # x_heroes_arr = []
    # y_labels_arr = []
    #
    # for i in range(0, len(pro_matches) // 100):
    #     x_meta, x_heroes, y_labels = prepare_data(pro_matches[i * 100: (i + 1) * 100], heroes_embeddings, embeddings_ids)
    #     x_meta_arr.extend(x_meta)
    #     x_heroes_arr.extend(x_heroes)
    #     y_labels_arr.extend(y_labels)
    #
    # np.savez('./prepared_data', x_meta=np.array(x_meta_arr), x_heroes=np.array(x_heroes_arr), y_labels=np.array(y_labels_arr))
    # return

    use_saved_scaler = True

    scaler_meta = StandardScaler()
    scaler_heroes = StandardScaler()

    if use_saved_scaler:
        load_scaler('meta', scaler_meta)
        load_scaler('heroes', scaler_heroes)

        x_meta_valid, x_heroes_valid, y_valid = prepare_data(valid_data, heroes_embeddings, embeddings_ids)
        x_meta_valid = scaler_meta.transform(x_meta_valid)
        x_heroes_valid = scaler_heroes.transform(x_heroes_valid)

    batch_size = 100
    for i in tqdm(range(len(train_data) // batch_size)):
        next_batch = train_data[i * batch_size: (i + 1) * batch_size]

        x_meta_train, x_heroes_train, y_train = prepare_data(next_batch, heroes_embeddings, embeddings_ids)

        if not use_saved_scaler:
            scaler_meta.partial_fit(x_meta_train)
            scaler_heroes.partial_fit(x_heroes_train)
        else:
            x_meta_train = scaler_meta.transform(x_meta_train)
            x_heroes_train = scaler_heroes.transform(x_heroes_train)
            model.fit(x=[x_meta_train, x_heroes_train], y=y_train,
                      validation_data=([x_meta_valid, x_heroes_valid], y_valid), epochs=1, batch_size=512)

    if not use_saved_scaler:
        save_scaler('meta', scaler_meta)
        save_scaler('heroes', scaler_heroes)


def save_scaler(name, scaler):
    scale_ = scaler.scale_
    mean_ = scaler.mean_
    var_ = scaler.var_
    n_samples_seen = scaler.n_samples_seen_
    np.savez(f'./scaler_{name}_param', scale_=scale_, mean_=mean_, var_=var_, n_samples_seen_=n_samples_seen)


def load_scaler(name, scaler):
    scaler_param = np.load(f'./scaler_{name}_param.npz')
    scaler.scale_ = scaler_param['scale_']
    scaler.mean_ = scaler_param['mean_']
    scaler.var_ = scaler_param['var_']
    scaler.n_samples_seen_ = int(scaler_param['n_samples_seen_'])


def split_data(data, train, valid, test):
    data_len = len(data)
    train_l = int(train * data_len)
    valid_l = int(valid * data_len)
    test_l = int(test * data_len)

    return data[:train_l], data[train_l:train_l + valid_l], data[train_l + valid_l: train_l + valid_l + test_l]


def prepare_data(data, embeddings, embeddings_ids):
    ids = list(map(lambda b: b['match_pk'], data))
    details = get_pro_match_details(ids)
    data_dict = {m['match_pk']: m for m in data}

    x_meta = []
    x_heroes = []
    y = []
    match_pk_idx = 1
    time_part_idx = -1
    radian_win_idx = 1

    for d in tqdm(details):
        x_meta_row = []
        x_heroes_row = []
        y_row = []
        # x_row.extend(d[2:time_part_idx])  # exclude match_pk, match_details_pk, time_part

        # Gold R 2-6
        x_meta_row.append(sum(d[2:7]))
        # Gold D 7-11
        x_meta_row.append(sum(d[7:12]))

        # XP R 12-16
        x_meta_row.append(sum(d[12:17]))
        # XP D 17-21
        x_meta_row.append(sum(d[17:22]))

        # LH R 22-26
        x_meta_row.append(sum(d[22:27]))
        # LH D 27-31
        x_meta_row.append(sum(d[27:32]))

        # # LVL R 32-36
        # x_row.append(sum(d[32:37]))
        # # LVL D 37-41
        # x_row.append(sum(d[37:42]))

        # KILLS R 42-46
        x_meta_row.append(sum(d[42:47]))
        # KILLS D 47-51
        x_meta_row.append(sum(d[47:52]))

        # DEATHS R 52-56
        x_meta_row.append(sum(d[52:57]))
        # DEATHS D 57-61
        x_meta_row.append(sum(d[57:62]))

        # ASSIST R 62-66
        x_meta_row.append(sum(d[62:67]))
        # ASSIST D 67-71
        x_meta_row.append(sum(d[67:72]))

        # DENIES R 72-76
        x_meta_row.append(sum(d[72:77]))
        # DENIES D 77-81
        x_meta_row.append(sum(d[77:82]))

        # x_meta_row.extend(d[82:time_part_idx])

        match_pk = d[match_pk_idx]
        match = data_dict[match_pk]

        heroes_ids = match[2:]  # exclude match_pk, radiant_win

        heroes_radiant = []
        for h_i in heroes_ids[:5]:
            heroes_radiant.append(embeddings[embeddings_ids[h_i]])

        x_heroes_row.extend(np.mean(heroes_radiant, axis=0))

        heroes_dire = []
        for h_i in heroes_ids[5:]:
            heroes_dire.append(embeddings[embeddings_ids[h_i]])

        x_heroes_row.extend(np.mean(heroes_dire, axis=0))

        y_row.append(match[radian_win_idx])
        y_row.append(not match[radian_win_idx])

        x_meta.append(x_meta_row)
        x_heroes.append(x_heroes_row)
        y.append(y_row)

    return np.array(x_meta), np.array(x_heroes, dtype='float16'), np.array(y)


def get_pro_matches():
    min_duration = 20

    sel = select(
        [
            pro_match.c.match_pk,
            pro_match.c.radiant_win,
            pro_match.c.hero0,
            pro_match.c.hero1,
            pro_match.c.hero2,
            pro_match.c.hero3,
            pro_match.c.hero4,
            pro_match.c.hero5,
            pro_match.c.hero6,
            pro_match.c.hero7,
            pro_match.c.hero8,
            pro_match.c.hero9,
        ]
    ).where(
        and_(
            pro_match.c.parse_success == True,
            pro_match.c.duration > min_duration * 60
        )
    )

    res = conn.execute(sel)
    return res.fetchall()


def get_pro_match_details(match_ids):
    start_time = 5

    sel = select(
        ['*']
    ).where(
        and_(
            pro_match_details.c.match_pk.in_(tuple(match_ids)),
            pro_match_details.c.time_part > start_time * 60
        )
    )

    res = conn.execute(sel)
    return res.fetchall()


def get_embeddings_ids():
    with open('../d2_heroes_encoder/heroes_data/unique_heroes.csv') as csv_heroes:
        heroes = list(csv.reader(csv_heroes))
        ids = {}

        for i, hero in enumerate(heroes):
            hero_id = int(hero[0])
            ids[hero_id] = i

    return ids


if __name__ == '__main__':
    main()
