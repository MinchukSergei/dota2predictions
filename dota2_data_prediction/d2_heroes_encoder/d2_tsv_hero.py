import numpy as np
# t-sne params: perplexity = 5, lr = 1, it = 418


def main():
    hero_embeddings = np.load('./heroes_data/heroes_embeddings.npy')

    with open('./heroes_data/heroes_embeddings.tsv', 'w') as f:
        for i, row in enumerate(hero_embeddings):
            if i == 0:
                continue
            for j, cell in enumerate(row):
                print(f'{cell}', file=f, end='')
                if j < len(row):
                    print('\t', file=f, end='')
            print(file=f)
    pass


if __name__ == '__main__':
    main()
