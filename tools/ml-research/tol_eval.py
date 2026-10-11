# Compare our 2,162-name text table against BioCLIP's precomputed TreeOfLife plant embeddings (int8 image encoder).
# usage: tol_eval.py OUT_DIR PHOTO...
import sys, json, time, random, numpy as np, onnxruntime as ort
from PIL import Image, ImageOps
out = sys.argv[1]; photos = sys.argv[2:]
MEAN = np.array([0.48145466, 0.4578275, 0.40821073], np.float32); STD = np.array([0.26862954, 0.26130258, 0.27577711], np.float32)
def prep(im):
    w, h = im.size; k = 224 / min(w, h); im = im.resize((max(224, round(w*k)), max(224, round(h*k))), Image.BICUBIC)
    w, h = im.size; im = im.crop(((w-224)//2, (h-224)//2, (w-224)//2+224, (h-224)//2+224))
    a = (np.asarray(im, np.float32)/255 - MEAN) / STD
    return np.ascontiguousarray(a.transpose(2, 0, 1)[None])
TRUTH = {'areca_1': {'Dypsis lutescens', 'Chrysalidocarpus lutescens'}, 'bamboo_1': {'Dracaena sanderiana', 'Dracaena braunii'},
         'bean_1': ('Phaseolus', 'Lablab', 'Vigna'), 'moneyplant_1_crop': {'Epipremnum aureum'}, 'tulsi_1': {'Ocimum tenuiflorum', 'Ocimum sanctum'},
         'screen_flower_1': {'Silene dioica'}, 'scan_20261011_122823_947': {'Portulacaria afra'}}
def ok(key, latin):
    t = TRUTH.get(key)
    if t is None: return None
    return latin.split()[0] in t if isinstance(t, tuple) else latin in t
ours_T = np.load(f'{out}/text_table.npy').astype(np.float32); ours_L = json.load(open(f'{out}/text_labels.json'))
E = np.load(f'{out}/tol/plant_emb.npy'); P = json.load(open(f'{out}/tol/plant_labels.json'))
ours_latin = {l['latin'] for l in ours_L}
truth_latin = set().union(*[t for t in TRUTH.values() if isinstance(t, set)])
common = [i for i, l in enumerate(P) if l['common']]
random.seed(0); extra = set(random.sample(common, 300))
sub = [i for i, l in enumerate(P) if l['latin'] in ours_latin or l['latin'] in truth_latin or i in extra]
print(f'our latins found in ToL: {len(ours_latin & {l["latin"] for l in P})}/{len(ours_latin)}; truth species in ToL:',
      sorted(l['latin'] for l in P if l['latin'] in truth_latin))
V = {'a ours': (ours_T, ours_L), 'b tol-all': (E, P), 'c tol-common': (E[common], [P[i] for i in common]),
     'd tol-ours+300': (E[sub], [P[i] for i in sub])}
for k, (T, L) in V.items(): print(f'{k}: {len(L)} rows')
sess = ort.InferenceSession(f'{out}/bioclip_img_int8.onnx', providers=['CPUExecutionProvider'])
def nm(l): return f"{l['latin']}" + (f" ({l['common']})" if l['common'] else '')
score = {k: [0, 0] for k in V}; e = None
for path in photos:
    key = path.split('/')[-1].rsplit('.', 1)[0]
    e = sess.run(None, {'pixels': prep(ImageOps.exif_transpose(Image.open(path)).convert('RGB'))})[0][0]
    print(f'\n== {key}  truth: {TRUTH.get(key, "non-plant")}')
    for k, (T, L) in V.items():
        z = 100 * (T @ e); p = np.exp(z - z.max()); p /= p.sum(); top = np.argsort(-p)[:3]
        r = ok(key, L[top[0]]['latin'])
        if r is not None: score[k][0] += r; score[k][1] += 1
        rank = next((j+1 for j, i in enumerate(np.argsort(-p)) if ok(key, L[i]['latin'])), None) if r is not None else None
        print(f"  {k:<14} {'OK ' if r else '-- ' if r is not None else '   '}" + '; '.join(f'{nm(L[i])} {p[i]:.2f}' for i in top)
              + (f'  [truth rank {rank}]' if rank else ''))
print('\ntop-1 accuracy:', {k: f'{a}/{b}' for k, (a, b) in score.items()})
print('\nspeed (one embedding x table, dot + softmax, numpy) and size on disk:')
for n in (len(ours_L), len(sub), len(common), len(P)):
    for dt_ in (np.float32, np.float16):
        T = E[:n].astype(dt_); x = e.astype(dt_)
        for _ in range(3): z = (T @ x).astype(np.float32)
        t = time.perf_counter(); R = 20
        for _ in range(R): z = 100 * (T @ x).astype(np.float32); p = np.exp(z - z.max()); p /= p.sum()
        ms = (time.perf_counter() - t) / R * 1000
        print(f'  n={n:>6} {np.dtype(dt_).name:<7} {ms:7.2f} ms', end='')
    print(f'   | fp32 {n*2048/1e6:6.1f} MB  fp16 {n*1024/1e6:6.1f} MB  int8+scale {n*516/1e6:6.1f} MB')
