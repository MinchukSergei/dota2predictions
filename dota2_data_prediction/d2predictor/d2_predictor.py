import csv
import time

import numpy as np
from keras import Input, Model
from keras.callbacks import ModelCheckpoint, TensorBoard
from keras.layers import Dense, concatenate, AlphaDropout
from keras.optimizers import Adam
from keras.regularizers import l2, l1, l1_l2
from matplotlib import pyplot as plt
from sklearn.preprocessing import StandardScaler
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score
from sqlalchemy import select, and_, text
from tqdm import tqdm

from db_connector.db_connector import pro_match, pro_match_details, conn, engine


def main():
    # build_kill_per_death_plot()
    # build_class_hist()
    # build_duration_hist()
    # build_gpm_xpm_plot()
    model = build_model()
    fit_model(model)
    # fit_model_log_reg()


def build_gpm_xpm_plot():
    gold_per_hero, xp_per_hero = get_gold_xp_per_minute()
    plt.bar(np.arange(len(gold_per_hero.keys())), gold_per_hero.values())
    plt.ylabel('Gold per minute')
    plt.xlabel('Heroes')
    plt.show()

    plt.bar(np.arange(len(xp_per_hero.keys())), xp_per_hero.values())
    plt.ylabel('XP per minute')
    plt.xlabel('Heroes')
    plt.show()


def build_kill_per_death_plot():
    kill_per_death_per_hero = get_kill_per_death()
    heroes = list(kill_per_death_per_hero.keys())
    plt.bar(np.arange(len(heroes)), kill_per_death_per_hero.values())

    print(heroes[:5])
    print(heroes[55:60])
    print(heroes[-5:])

    plt.ylabel('Kill per death')
    plt.xlabel('Heroes')
    plt.show()


def build_duration_hist():
    durations = get_duration_pro_match()
    durations = np.array(durations).flatten() / 60

    print(np.average(durations))

    plt.hist(durations, 100)
    plt.xlabel('Duration (min)')
    plt.xticks(np.arange(0, 121, 7))
    plt.show()


def build_class_hist():
    radiant_win = get_radiant_win_pro_match()
    radiant_win = np.array(radiant_win).flatten()

    plt.bar(['Dire (темные)', 'Radiant (светлые)'], [radiant_win[0], radiant_win[2]], width=.2)
    plt.show()


def build_model():
    l2_reg_m = 0.0001
    l1_reg_h = 0.01

    input_meta = Input(shape=(12,))
    input_heroes = Input(shape=(128,))

    x = Dense(256, kernel_regularizer=l2(l2_reg_m), activation='selu', kernel_initializer='lecun_normal')(input_meta)
    x = AlphaDropout(0.07)(x)
    x = Dense(256, kernel_regularizer=l2(l2_reg_m), activation='selu', kernel_initializer='lecun_normal')(x)
    x = AlphaDropout(0.07)(x)
    x = Dense(256, kernel_regularizer=l2(l2_reg_m), activation='selu', kernel_initializer='lecun_normal')(x)
    x = AlphaDropout(0.07)(x)
    x = Dense(256, kernel_regularizer=l2(l2_reg_m), activation='selu', kernel_initializer='lecun_normal')(x)
    x = Model(inputs=input_meta, outputs=x)

    y = Dense(64, kernel_regularizer=l1(l1_reg_h), activation='selu', kernel_initializer='lecun_normal')(
        input_heroes)
    y = AlphaDropout(0.5)(y)
    y = Dense(64, kernel_regularizer=l1(l1_reg_h), activation='selu', kernel_initializer='lecun_normal')(y)
    y = AlphaDropout(0.5)(y)
    y = Dense(64, kernel_regularizer=l1(l1_reg_h), activation='selu', kernel_initializer='lecun_normal')(y)
    y = Model(inputs=input_heroes, outputs=y)

    combined = concatenate([x.output, y.output])

    z = Dense(2, kernel_regularizer=l1_l2(l1_reg_h, l2_reg_m), activation='selu', kernel_initializer='lecun_normal')(
        combined)
    z = AlphaDropout(0.1)(z)
    z = Dense(2, activation='softmax')(z)

    model = Model(inputs=[x.input, y.input], outputs=z)

    model.compile(
        optimizer=Adam(lr=0.001, beta_1=0.9, beta_2=0.999),
        loss='categorical_crossentropy',
        metrics=['accuracy']
    )

    return model


def fit_model(model):
    data = np.load('./prepared_data.npz')
    x_meta = data['x_meta']
    x_heroes = data['x_heroes']
    y_labels = data['y_labels'].astype('int8')

    x_meta_len = len(x_meta)
    part = int(x_meta_len * 0.25)
    rnd_idx = np.random.randint(0, x_meta_len - part - 1)
    rnd_idx = 4069960

    x_meta = x_meta[rnd_idx:rnd_idx + part]
    x_heroes = x_heroes[rnd_idx:rnd_idx + part]
    y_labels = y_labels[rnd_idx:rnd_idx + part]

    x_meta = np.delete(x_meta, [2, 3], 1)  # remove xp

    train_percent = 0.92
    valid_percent = 0.03
    test_percent = 0.05

    x_meta_train, x_meta_valid, x_meta_test = split_data(x_meta, train_percent, valid_percent, test_percent)
    x_heroes_train, x_heroes_valid, x_heroes_test = split_data(x_heroes, train_percent, valid_percent, test_percent)
    y_labels_train, y_labels_valid, y_labels_test = split_data(y_labels, train_percent, valid_percent, test_percent)

    use_saved_scaler = True

    scaler_meta = StandardScaler()
    scaler_heroes = StandardScaler()

    if use_saved_scaler:
        load_scaler('meta', scaler_meta)
        load_scaler('heroes', scaler_heroes)

    if not use_saved_scaler:
        scaler_meta.partial_fit(x_meta_train)
        scaler_heroes.partial_fit(x_heroes_train)
    else:
        x_meta_train = scaler_meta.fit_transform(x_meta_train)
        x_heroes_train = scaler_heroes.fit_transform(x_heroes_train)
        x_meta_valid = scaler_meta.transform(x_meta_valid)
        x_heroes_valid = scaler_heroes.transform(x_heroes_valid)

        model_checkpoint = ModelCheckpoint('./models/weights.{val_acc:.3f}' + f'.{rnd_idx}.hdf5', monitor='val_loss',
                                           save_best_only=True)
        millis = int(round(time.time() * 1000))
        tensorboard = TensorBoard(f'./tensorboard/{millis}')

        model.fit(x=[x_meta_train, x_heroes_train], y=y_labels_train,
                  validation_data=([x_meta_valid, x_heroes_valid], y_labels_valid), epochs=10, batch_size=1024,
                  callbacks=[
                      model_checkpoint,
                      tensorboard
                  ])

        x_meta_test = scaler_meta.transform(x_meta_test)
        x_heroes_test = scaler_heroes.transform(x_heroes_test)
        scores = model.evaluate(x=[x_meta_test, x_heroes_test], y=y_labels_test, batch_size=1024)
        print(f'test_loss: {scores[0]:.4f} - test_acc: {scores[1]:.4f}')

    if not use_saved_scaler:
        save_scaler('meta', scaler_meta)
        save_scaler('heroes', scaler_heroes)


def fit_model_log_reg():
    data = np.load('./prepared_data.npz')
    x_meta = data['x_meta']
    x_heroes = data['x_heroes']
    y_labels = data['y_labels'].astype('int8')

    x_meta_len = len(x_meta)
    part = int(x_meta_len * 0.25)
    start_index = 4069960

    x_meta = x_meta[start_index:start_index + part]
    x_heroes = x_heroes[start_index:start_index + part]
    y_labels = y_labels[start_index:start_index + part]

    x_meta = np.delete(x_meta, [2, 3], 1)  # remove xp

    X = np.concatenate((x_meta, x_heroes), axis=1)
    y = y_labels[:, 0]

    train_percent = 0.95
    valid_percent = 0.0
    test_percent = 0.05

    scaler = StandardScaler()

    x_train, x_valid, x_test = split_data(X, train_percent, valid_percent, test_percent)
    y_labels_train, y_labels_valid, y_labels_test = split_data(y, train_percent, valid_percent, test_percent)

    clf = LogisticRegression(max_iter=10, solver='saga', verbose=1)

    scaler.fit_transform(x_train)
    scaler.transform(x_test)

    clf.fit(x_train, y_labels_train)

    y_labels_pred = clf.predict(x_train)
    print(accuracy_score(y_labels_train, y_labels_pred))

    y_labels_pred = clf.predict(x_test)
    print(accuracy_score(y_labels_test, y_labels_pred))


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


def get_gold_xp_per_minute():
    sql = text('''select max(gold0) / (max(pmd.time_part) / 60) avg_g0,
       max(gold1) / (max(pmd.time_part) / 60) avg_g1,
       max(gold2) / (max(pmd.time_part) / 60) avg_g2,
       max(gold3) / (max(pmd.time_part) / 60) avg_g3,
       max(gold4) / (max(pmd.time_part) / 60) avg_g4,
       max(gold5) / (max(pmd.time_part) / 60) avg_g5,
       max(gold6) / (max(pmd.time_part) / 60) avg_g6,
       max(gold7) / (max(pmd.time_part) / 60) avg_g7,
       max(gold8) / (max(pmd.time_part) / 60) avg_g8,
       max(gold9) / (max(pmd.time_part) / 60) avg_g9,
       max(xp0) / (max(pmd.time_part) / 60) avg_xp0,
       max(xp1) / (max(pmd.time_part) / 60) avg_xp1,
       max(xp2) / (max(pmd.time_part) / 60) avg_xp2,
       max(xp3) / (max(pmd.time_part) / 60) avg_xp3,
       max(xp4) / (max(pmd.time_part) / 60) avg_xp4,
       max(xp5) / (max(pmd.time_part) / 60) avg_xp5,
       max(xp6) / (max(pmd.time_part) / 60) avg_xp6,
       max(xp7) / (max(pmd.time_part) / 60) avg_xp7,
       max(xp8) / (max(pmd.time_part) / 60) avg_xp8,
       max(xp9) / (max(pmd.time_part) / 60) avg_xp9,
       pm.hero0,
       pm.hero1,
       pm.hero2,
       pm.hero3,
       pm.hero4,
       pm.hero5,
       pm.hero6,
       pm.hero7,
       pm.hero8,
       pm.hero9
    from pro_match_details pmd,
         pro_match pm
    where pmd.match_pk = pm.match_pk
    group by pmd.match_pk, pm.hero0, pm.hero1, pm.hero2, pm.hero3, pm.hero4, pm.hero5, pm.hero6, pm.hero7, pm.hero8, pm.hero9''')

    res = engine.execute(sql)
    res = list(res)

    gold_per_hero = {}
    xp_per_hero = {}

    for i in tqdm(res):
        for j in range(10):
            h = i[f'hero{j}']
            g = i[f'avg_g{j}']
            xp = i[f'avg_xp{j}']
            if h in gold_per_hero:
                gold_per_hero[h] += g
            else:
                gold_per_hero[h] = 0
            if h in xp_per_hero:
                xp_per_hero[h] += xp
            else:
                xp_per_hero[h] = 0

    gold_per_hero_sorted = {}
    gold_per_hero_s = sorted(gold_per_hero.items(), key=lambda a: a[1], reverse=True)
    for i in gold_per_hero_s:
        gold_per_hero_sorted[i[0]] = i[1] / len(gold_per_hero)

    xp_per_hero_sorted = {}
    for g in gold_per_hero_s:
        xp_per_hero_sorted[g[0]] = xp_per_hero[g[0]] / len(xp_per_hero)

    return gold_per_hero_sorted, xp_per_hero_sorted


def get_duration_pro_match():
    sel = select([
        pro_match.c.duration
    ]).where(
        and_(
            pro_match.c.parse_success == True
        )
    )

    res = conn.execute(sel)
    return res.fetchall()


def get_radiant_win_pro_match():
    sql = text('''
    select count(radiant_win), radiant_win
    from (select * from pro_match
           where parse_success = TRUE
             and duration > 23 * 60
             and duration < 45 * 60
           limit 1300) pm,
         pro_match_details pmd
    where pm.parse_success = TRUE
      and pm.match_pk = pmd.match_pk
      and time_part < 35 * 60
    group by radiant_win;
    ''')

    res = engine.execute(sql)
    res = list(res)

    return res


def get_kill_per_death():
    sql = text('''
    select cast(max(kills0) as decimal) / (max(deaths0) + max(assists0) + 1) avg_kills0,
       cast(max(kills1) as decimal) / (max(deaths1) + max(assists1) + 1) avg_kills1,
       cast(max(kills2) as decimal) / (max(deaths2) + max(assists2) + 1) avg_kills2,
       cast(max(kills3) as decimal) / (max(deaths3) + max(assists3) + 1) avg_kills3,
       cast(max(kills4) as decimal) / (max(deaths4) + max(assists4) + 1) avg_kills4,
       cast(max(kills5) as decimal) / (max(deaths5) + max(assists5) + 1) avg_kills5,
       cast(max(kills6) as decimal) / (max(deaths6) + max(assists6) + 1) avg_kills6,
       cast(max(kills7) as decimal) / (max(deaths7) + max(assists7) + 1) avg_kills7,
       cast(max(kills8) as decimal) / (max(deaths8) + max(assists8) + 1) avg_kills8,
       cast(max(kills9) as decimal) / (max(deaths9) + max(assists9) + 1) avg_kills9,
       pm.hero0,
       pm.hero1,
       pm.hero2,
       pm.hero3,
       pm.hero4,
       pm.hero5,
       pm.hero6,
       pm.hero7,
       pm.hero8,
       pm.hero9
    from pro_match_details pmd,
         pro_match pm
    where pmd.match_pk = pm.match_pk
    group by pmd.match_pk, pm.hero0, pm.hero1, pm.hero2, pm.hero3, pm.hero4, pm.hero5, pm.hero6, pm.hero7, pm.hero8,
         pm.hero9
    ''')

    res = engine.execute(sql)
    res = list(res)

    kill_per_death_per_hero = {}

    for i in tqdm(res):
        for j in range(10):
            h = i[f'hero{j}']
            kd = i[f'avg_kills{j}']

            if h in kill_per_death_per_hero:
                kill_per_death_per_hero[h] += kd
            else:
                kill_per_death_per_hero[h] = 0

    gold_per_hero_sorted = {}
    gold_per_hero_s = sorted(kill_per_death_per_hero.items(), key=lambda a: a[1], reverse=True)
    for i in gold_per_hero_s:
        gold_per_hero_sorted[i[0]] = i[1] / len(kill_per_death_per_hero)

    return gold_per_hero_sorted


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
