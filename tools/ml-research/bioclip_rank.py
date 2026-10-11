import sys, re, torch, open_clip
from PIL import Image
mdir, aiy_en, img = sys.argv[1:4]
model, _, pre = open_clip.create_model_and_transforms('ViT-B-16', pretrained=f'{mdir}/open_clip_pytorch_model.bin'); model.eval()
tok = open_clip.get_tokenizer('ViT-B-16')
names = []
for line in open(aiy_en).read().split('\n'):
    if not line.strip(): continue
    m = re.match(r'^(.*?)\s*\(([^()]*)\)\s*$', line.strip())
    names.append((m.group(1), m.group(2)) if m else (line.strip(), None))
VARIANTS = [('Epipremnum aureum','Money plant (Golden pothos)'), ('Epipremnum aureum','Golden pothos'), ('Epipremnum aureum',"Devil's ivy"),
            ('Epipremnum aureum', None), ('Scindapsus aureus','Pothos'), ('Tradescantia fluminensis','Wandering Jew'), ('Dracaena sanderiana','Lucky bamboo')]
prompts = [f'a photo of {l}' + (f' with common name {c}.' if c else '.') for l,c in names] + \
          [f'a photo of {l}' + (f' with common name {c}.' if c else '.') for l,c in VARIANTS]
with torch.no_grad():
    T = torch.cat([model.encode_text(tok(prompts[i:i+256])) for i in range(0,len(prompts),256)]); T = T/T.norm(dim=-1,keepdim=True)
    for box in [None, (0,1150,450,1600), (0,900,700,1600)]:
        im = Image.open(img).convert('RGB'); im = im.crop(box) if box else im
        f = model.encode_image(pre(im)[None]); f = f/f.norm(dim=-1,keepdim=True)
        s = (f @ T.T)[0]; order = s.argsort(descending=True).tolist()
        print('crop', box, 'top:', [prompts[i][11:60] for i in order[:3]])
        for k,(l,c) in enumerate(VARIANTS):
            i = len(names)+k; print(f'   rank {order.index(i)+1:4d}/{len(prompts)} sim {s[i]:.3f}  {l} / {c}')
