import json
import csv


def main():
    prefix = './heroes_data'

    with open(f'{prefix}/heroes.json', 'r') as f:
        heroes = json.load(f)

    with open(f'{prefix}/unique_heroes.csv', 'r') as f:
        heroes_unique = list(csv.reader(f))

    heroes_dict = {}
    for h in heroes:
        heroes_dict[h['id']] = h

    with open(f'{prefix}/heroes_labels.tsv', 'w') as f:
        for h in heroes_unique:
            if h[0] == '0':
                continue
            print(heroes_dict[int(h[0])]['localized_name'], file=f)
    pass


if __name__ == '__main__':
    main()
