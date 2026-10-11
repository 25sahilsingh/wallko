import sys, re, json, numpy as np, torch, open_clip
mdir, aiy_en, out = sys.argv[1:4]
model, _, pre = open_clip.create_model_and_transforms('ViT-B-16', pretrained=f'{mdir}/open_clip_pytorch_model.bin'); model.eval()
tok = open_clip.get_tokenizer('ViT-B-16')
print(pre)
# 1. Image encoder -> ONNX (fp32). Output is L2-normalised so the app only does a dot product.
class Img(torch.nn.Module):
    def __init__(s, m): super().__init__(); s.v = m.visual
    def forward(s, x): f = s.v(x); return f / f.norm(dim=-1, keepdim=True)
torch.onnx.export(Img(model), torch.randn(1,3,224,224), f'{out}/bioclip_img_fp32.onnx', input_names=['pixels'], output_names=['embedding'], opset_version=17, dynamo=False)
# 2. Text table, computed once on the PC. Names use the scientific-name-only wording that tested best.
HOUSE = [l.split('|') for l in open(sys.argv[4]).read().strip().split('\n')]
rows = []
for line in open(aiy_en).read().split('\n'):
    if not line.strip(): continue
    m = re.match(r'^(.*?)\s*\(([^()]*)\)\s*$', line.strip())
    lat, com = (m.group(1), m.group(2)) if m else (line.strip(), None)
    rows.append((lat, None if (com or '').lower() in ('', 'n/a', 'na') else com, 'wild'))
rows += [(l, c, 'house') for l, c in HOUSE]
with torch.no_grad():
    T = torch.cat([model.encode_text(tok([f'a photo of {r[0]}.' for r in rows[i:i+256]])) for i in range(0, len(rows), 256)])
    T = T / T.norm(dim=-1, keepdim=True)
np.save(f'{out}/text_table.npy', T.numpy().astype(np.float16))
json.dump([{'latin': r[0], 'common': r[1], 'group': r[2]} for r in rows], open(f'{out}/text_labels.json', 'w'))
print('rows', len(rows), 'scale', float(model.logit_scale.exp()))
