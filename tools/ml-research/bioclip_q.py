import sys
import json, time, numpy as np, onnxruntime as ort
from onnxruntime.quantization import quantize_dynamic, QuantType
from PIL import Image
out = sys.argv[1]; photos = sys.argv[2:]
q = f'{out}/bioclip_img_int8.onnx'
import os
if not os.path.exists(q):
    quantize_dynamic(f'{out}/bioclip_img_fp32.onnx', q, weight_type=QuantType.QInt8)
print('int8 size %.0f MB' % (os.path.getsize(q)/1e6))
T = np.load(f'{out}/text_table.npy').astype(np.float32); L = json.load(open(f'{out}/text_labels.json'))
MEAN = np.array([0.48145466, 0.4578275, 0.40821073], np.float32); STD = np.array([0.26862954, 0.26130258, 0.27577711], np.float32)
def prep(im):
    w, h = im.size; k = 224 / min(w, h); im = im.resize((max(224, round(w*k)), max(224, round(h*k))), Image.BICUBIC)
    w, h = im.size; im = im.crop(((w-224)//2, (h-224)//2, (w-224)//2+224, (h-224)//2+224))
    a = (np.asarray(im, np.float32)/255 - MEAN) / STD
    return np.ascontiguousarray(a.transpose(2, 0, 1)[None])
sess = {n: ort.InferenceSession(f'{out}/bioclip_img_{n}.onnx', providers=['CPUExecutionProvider']) for n in ('fp32', 'int8')}
def name(i): return L[i]['common'] or L[i]['latin']
for spec in photos:
    path, _, box = spec.partition('@'); im = Image.open(path).convert('RGB')
    if box: im = im.crop(tuple(int(v) for v in box.split(',')))
    x = prep(im); line = f"{path.split('/')[-1][9:15]}{'@'+box if box else '':<18}"
    embs = {}
    for n, s in sess.items():
        t = time.time(); e = s.run(None, {'pixels': x})[0][0]; dt = (time.time()-t)*1000; embs[n] = e
        z = 100 * (T @ e); p = np.exp(z - z.max()); p /= p.sum(); top = np.argsort(-p)[:2]
        line += f" | {n} {dt:4.0f}ms: " + ', '.join(f'{name(i)} {p[i]:.2f}' for i in top)
    print(line, f"| fp32~int8 similarity {float(embs['fp32'] @ embs['int8']):.3f}")
