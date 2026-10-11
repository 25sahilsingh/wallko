import sys, numpy as np
from PIL import Image
from ai_edge_litert.interpreter import Interpreter
M,S,photo,out=sys.argv[1:5]
def sq(im,n):
    w,h=im.size; s=min(w,h); return im.crop(((w-s)//2,(h-s)//2,(w-s)//2+s,(h-s)//2+s)).resize((n,n),Image.BILINEAR)
p=Interpreter(M+'/style_predict.tflite'); p.allocate_tensors()
t=Interpreter(M+'/style_transfer.tflite'); t.allocate_tensors()
def vec(im):
    p.set_tensor(p.get_input_details()[0]['index'],(np.asarray(sq(im,256),np.float32)/255)[None]); p.invoke()
    return p.get_tensor(p.get_output_details()[0]['index']).copy()
def run(content,v):
    for d in t.get_input_details():
        if d['shape'][-1]==3 and d['shape'][1]>1: t.set_tensor(d['index'],(np.asarray(content,np.float32)/255)[None])
        else: t.set_tensor(d['index'],v.reshape(d['shape']))
    t.invoke(); return Image.fromarray((np.clip(t.get_tensor(t.get_output_details()[0]['index'])[0],0,1)*255).astype(np.uint8))
ph=Image.open(photo).convert('RGB'); c=sq(ph,384); cv=vec(ph)
rows=[]
for name in ['watercolour','flat_pastel','crayon']:
    sv=vec(Image.open(f'{S}/{name}.jpg').convert('RGB'))
    rows.append([c]+[run(c,r*sv+(1-r)*cv) for r in (1.0,0.6,0.4,0.25)])
o=Image.new('RGB',(384*5,384*3))
for y,row in enumerate(rows):
    for x,im in enumerate(row): o.paste(im,(384*x,384*y))
o.resize((o.width//2,o.height//2)).save(out)
