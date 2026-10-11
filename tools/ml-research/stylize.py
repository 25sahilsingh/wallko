import sys, numpy as np
from PIL import Image
from ai_edge_litert.interpreter import Interpreter
M,S,photo,out=sys.argv[1:5]
def sq(im,n):
    w,h=im.size; s=min(w,h); return im.crop(((w-s)//2,(h-s)//2,(w-s)//2+s,(h-s)//2+s)).resize((n,n),Image.BILINEAR)
p=Interpreter(M+'/style_predict.tflite'); p.allocate_tensors()
t=Interpreter(M+'/style_transfer.tflite'); t.allocate_tensors()
print('predict in',[(d['shape'],d['dtype']) for d in p.get_input_details()])
print('transfer in',[(d['name'],d['shape'],d['dtype']) for d in t.get_input_details()], 'out',t.get_output_details()[0]['shape'])
row=[sq(Image.open(photo).convert('RGB'),384)]
for name in ['watercolour','flat_pastel','crayon']:
    st=sq(Image.open(f'{S}/{name}.jpg').convert('RGB'),256)
    p.set_tensor(p.get_input_details()[0]['index'],(np.asarray(st,np.float32)/255)[None]); p.invoke()
    v=p.get_tensor(p.get_output_details()[0]['index'])
    ins=t.get_input_details()
    for d in ins:
        if d['shape'][-1]==3 and d['shape'][1]>1: t.set_tensor(d['index'],(np.asarray(row[0],np.float32)/255)[None])
        else: t.set_tensor(d['index'],v.reshape(d['shape']))
    t.invoke(); r=t.get_tensor(t.get_output_details()[0]['index'])[0]
    print(name,'vec range %.2f..%.2f'%(v.min(),v.max()),'out range %.2f..%.2f mean'%(r.min(),r.max()),r.reshape(-1,3).mean(0).round(2))
    row+= [st.resize((384,384)), Image.fromarray((np.clip(r,0,1)*255).astype(np.uint8))]
o=Image.new('RGB',(384*len(row),384))
for k,im in enumerate(row): o.paste(im,(384*k,0))
o.resize((o.width//2,192)).save(out)
