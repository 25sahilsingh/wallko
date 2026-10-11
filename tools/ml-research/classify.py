import sys, glob, numpy as np
from PIL import Image
from ai_edge_litert.interpreter import Interpreter
M=sys.argv[1]; photos=sys.argv[2:]
it=Interpreter(M+'/aiy_plants_v1.tflite'); it.allocate_tensors()
i=it.get_input_details()[0]; o=it.get_output_details()[0]
en=open(M+'/aiy_plants_labels_en.txt').read().split('\n'); en.append('__background__')
sc,zp=o['quantization']
for p in photos:
    im=Image.open(p).convert('RGB'); w,h=im.size; s=min(w,h)
    im=im.crop(((w-s)//2,(h-s)//2,(w-s)//2+s,(h-s)//2+s)).resize((224,224),Image.BILINEAR)
    it.set_tensor(i['index'],np.asarray(im,np.uint8)[None]); it.invoke()
    out=(it.get_tensor(o['index'])[0].astype(np.float32)-zp)*sc
    top=np.argsort(-out)[:4]
    print(p.split('/')[-1], 'background=%.2f'%out[2101], ' | '.join('%s %.2f'%(en[k],out[k]) for k in top))
