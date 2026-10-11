import sys, json, time, numpy as np, onnxruntime as ort
from PIL import Image
kind, model, cfg = sys.argv[1:4]; photos = sys.argv[4:]
labels = list(json.load(open(cfg))['id2label'].values())
s = ort.InferenceSession(model, providers=['CPUExecutionProvider']); name = s.get_inputs()[0].name
def prep(im):
    if kind == 'mobilevit':
        w,h = im.size; k = 288/min(w,h); im = im.resize((round(w*k),round(h*k)), Image.BILINEAR)
        w,h = im.size; im = im.crop(((w-256)//2,(h-256)//2,(w-256)//2+256,(h-256)//2+256))
        a = np.asarray(im, np.float32)/255; a = a[..., ::-1]
    else:
        a = (np.asarray(im.resize((224,224), Image.BILINEAR), np.float32)/255 - 0.5)/0.5
    return np.ascontiguousarray(a.transpose(2,0,1)[None])
for spec in photos:
    path, _, box = spec.partition('@'); im = Image.open(path).convert('RGB')
    if box: im = im.crop(tuple(int(v) for v in box.split(',')))
    t = time.time(); z = s.run(None, {name: prep(im)})[0][0]; dt = (time.time()-t)*1000
    p = np.exp(z - z.max()); p /= p.sum(); top = np.argsort(-p)[:3]
    print(f"{path.split('/')[-1][9:15]}{'@'+box if box else '':<22} {dt:5.0f}ms  " + " | ".join(f"{labels[i]} {p[i]:.2f}" for i in top))
