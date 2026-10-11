import sys, time, json, numpy as np, torch, open_clip
from PIL import Image
mdir, aiy_en = sys.argv[1], sys.argv[2]; photos = sys.argv[3:]
cfg = json.load(open(f'{mdir}/open_clip_config.json'))
model, _, preprocess = open_clip.create_model_and_transforms(
    cfg['model_cfg'] and 'ViT-B-16', pretrained=f'{mdir}/open_clip_pytorch_model.bin')
tok = open_clip.get_tokenizer('ViT-B-16'); model.eval()
vis = sum(p.numel() for p in model.visual.parameters())
print(f'image encoder params {vis/1e6:.1f}M -> fp32 {vis*4/1e6:.0f} MB, int8 ~{vis/1e6:.0f} MB')

classes = []  # (display, prompt, group)
import re
for line in open(aiy_en).read().split('\n'):
    if not line.strip(): continue
    m = re.match(r'^(.*?)\s*\(([^()]*)\)\s*$', line.strip())
    lat, com = (m.group(1), m.group(2)) if m else (line.strip(), None)
    if com and com.lower() in ('n/a','na'): com = None
    classes.append((f'{com or lat} [{lat}]', f'a photo of {lat}' + (f' with common name {com}.' if com else '.'), 'wild'))
HOUSE = """Epipremnum aureum|Money plant (Golden pothos)
Dracaena sanderiana|Lucky bamboo
Dypsis lutescens|Areca palm
Chamaedorea elegans|Parlor palm
Rhapis excelsa|Lady palm
Bambusa vulgaris|Common bamboo
Phyllostachys aurea|Golden bamboo
Monstera deliciosa|Monstera
Sansevieria trifasciata|Snake plant
Chlorophytum comosum|Spider plant
Zamioculcas zamiifolia|ZZ plant
Spathiphyllum wallisii|Peace lily
Ficus elastica|Rubber plant
Ficus benghalensis|Banyan
Ficus religiosa|Peepal tree
Aloe vera|Aloe vera
Ocimum tenuiflorum|Tulsi (Holy basil)
Syngonium podophyllum|Arrowhead plant
Philodendron hederaceum|Heartleaf philodendron
Aglaonema commutatum|Chinese evergreen
Dieffenbachia seguine|Dumb cane
Codiaeum variegatum|Croton
Crassula ovata|Jade plant
Portulacaria afra|Elephant bush
Pilea peperomioides|Chinese money plant
Pachira aquatica|Money tree
Schefflera arboricola|Dwarf umbrella tree
Dracaena fragrans|Corn plant
Dracaena marginata|Dragon tree
Dracaena trifasciata|Snake plant
Hedera helix|English ivy
Nephrolepis exaltata|Boston fern
Asplenium nidus|Bird's nest fern
Anthurium andraeanum|Anthurium
Begonia rex|Rex begonia
Calathea ornata|Calathea
Maranta leuconeura|Prayer plant
Tradescantia zebrina|Wandering dude
Peperomia obtusifolia|Baby rubber plant
Cycas revoluta|Sago palm
Beaucarnea recurvata|Ponytail palm
Hibiscus rosa-sinensis|Hibiscus
Rosa chinensis|China rose
Bougainvillea glabra|Bougainvillea
Jasminum sambac|Arabian jasmine (Mogra)
Tagetes erecta|Marigold
Catharanthus roseus|Madagascar periwinkle (Sadabahar)
Mangifera indica|Mango tree
Azadirachta indica|Neem tree
Musa acuminata|Banana plant
Carica papaya|Papaya tree
Cocos nucifera|Coconut palm
Areca catechu|Betel nut palm
Murraya koenigii|Curry leaf
Mentha spicata|Mint
Coleus scutellarioides|Coleus
Euphorbia milii|Crown of thorns
Adenium obesum|Desert rose
Nerium oleander|Oleander
Plumeria rubra|Frangipani""".split('\n')
for l in HOUSE:
    lat, com = l.split('|')
    classes.append((f'{com} [{lat}]', f'a photo of {lat} with common name {com}.', 'house'))
for n in ['the inside of a room','a curtain','a door','a wall','furniture','a person','a floor','a kitchen']:
    classes.append((f'NOT A PLANT: {n}', f'a photo of {n}.', 'none'))
print('classes', len(classes))

with torch.no_grad():
    t = time.time(); embs = []
    for i in range(0, len(classes), 256):
        e = model.encode_text(tok([c[1] for c in classes[i:i+256]])); embs.append(e / e.norm(dim=-1, keepdim=True))
    T = torch.cat(embs); print(f'text embeddings {time.time()-t:.0f}s (done once, at build time)')
    scale = model.logit_scale.exp()
    for spec in photos:
        path, _, box = spec.partition('@'); im = Image.open(path).convert('RGB')
        if box: im = im.crop(tuple(int(v) for v in box.split(',')))
        t = time.time(); f = model.encode_image(preprocess(im)[None]); dt = (time.time()-t)*1000
        f = f / f.norm(dim=-1, keepdim=True); p = (scale * f @ T.T).softmax(-1)[0]
        top = p.argsort(descending=True)[:3]
        print(f"{path.split('/')[-1][9:15]}{'@'+box if box else '':<20} {dt:4.0f}ms  " +
              ' | '.join(f'{classes[i][0]} {p[i]:.2f}' for i in top))
