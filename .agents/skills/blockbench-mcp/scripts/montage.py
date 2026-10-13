import sys
from PIL import Image, ImageDraw
out=sys.argv[1]; files=sys.argv[2:]
ims=[Image.open(f).convert('RGB') for f in files]
h=max(i.height for i in ims); w=sum(i.width for i in ims)
M=Image.new('RGB',(w,h+14),'white'); x=0; d=ImageDraw.Draw(M)
for f,i in zip(files,ims):
    M.paste(i,(x,14)); d.text((x+4,1),f.split('shot_')[-1][:-4],fill='black'); x+=i.width
M.save(out,quality=85)
