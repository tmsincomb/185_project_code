from PIL import Image, ImageDraw
import PIL.ImageOps
import sys
"""
GFP counter
"""
target = sys.argv[1]
im = Image.open(target)
im = im.convert('L')
im = PIL.ImageOps.invert(im)

black = (0,0,0)
white = (255,255,255)

width, height = im.size

im = im.convert('RGB')

alt_im = Image.new("RGB", (width, height))

# Process every pixel
for x in range(width):
    for y in range(height):
        r,b,a = im.getpixel((x,y))
        if r >= 245: #and b > 100 and a > 100:
            new_color = white
        else:
            new_color = black
        alt_im.putpixel( (x,y), new_color)

try:
    target = '/'.join(target.split('/')[:-1])+'/primed_'+target.split('/')[-1]
except:
    target = 'primed_'+target
alt_im.save(target)
print(target)
