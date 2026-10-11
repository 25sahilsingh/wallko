# Fetch only the plant columns of BioCLIP's TreeOfLife species text embeddings via HTTP Range requests.
# usage: tol_fetch.py OUT_DIR   (needs OUT_DIR/tol/txt_emb_species.json)
import sys, json, ast, time, urllib.request, numpy as np
from concurrent.futures import ThreadPoolExecutor
out = sys.argv[1] + '/tol'
URL = 'https://huggingface.co/datasets/imageomics/TreeOfLife-10M/resolve/main/embeddings/txt_emb_species.npy'
N, C0, C1 = 384490, 256216, 384357; W = C1 - C0 + 1
got = [0]
def rng(a, b):
    for k in range(6):
        try:
            r = urllib.request.urlopen(urllib.request.Request(URL, headers={'Range': f'bytes={a}-{b}'}), timeout=120)
            d = r.read()
            if len(d) == b - a + 1: got[0] += len(d); return d
        except Exception as e: err = e
        time.sleep(2 ** k)
    raise RuntimeError(f'range {a}-{b} failed: {err}')
h = rng(0, 255); hl = int.from_bytes(h[8:10], 'little'); hdr = ast.literal_eval(h[10:10+hl].decode('latin1'))
off = 10 + hl; print('header', hdr, 'data offset', off)
assert hdr['shape'] == (512, N) and hdr['descr'] == '<f4' and not hdr['fortran_order']
def row(i): a = off + (i * N + C0) * 4; return np.frombuffer(rng(a, a + W * 4 - 1), '<f4')
t = time.time()
with ThreadPoolExecutor(8) as ex: M = np.stack(list(ex.map(row, range(512))))  # [512, W]
print(f'fetched {got[0]/1e6:.1f} MB ({got[0]} bytes) in {time.time()-t:.0f}s')
names = json.load(open(f'{out}/txt_emb_species.json'))[C0:C1+1]
keep = [i for i, (tx, _) in enumerate(names) if tx[0] in ('Plantae', 'Viridiplantae')]
E = np.ascontiguousarray(M[:, keep].T)
n = np.linalg.norm(E, axis=1); print(f'{len(keep)} plants; row norms min {n.min():.4f} mean {n.mean():.4f} max {n.max():.4f}')
if abs(n - 1).max() > 1e-3: E /= n[:, None]; print('L2-normalised rows')
np.save(f'{out}/plant_emb.npy', E.astype(np.float32))
L = [{'latin': f'{names[i][0][5]} {names[i][0][6]}', 'common': names[i][1], 'family': names[i][0][4]} for i in keep]
json.dump(L, open(f'{out}/plant_labels.json', 'w'))
json.dump({'bytes': got[0], 'n': len(keep)}, open(f'{out}/fetch_stats.json', 'w'))
