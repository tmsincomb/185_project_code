import subprocess as sb
import sys
import os
import matplotlib.pyplot as plt
import numpy.random as rnd
from matplotlib.patches import Ellipse
from PIL import Image, ImageDraw
import matplotlib.image as mpimg
import numpy as np

try:
    if sys.argv[1] == '-h':
        print('*'*20)
        print('Example code:')
        print('>python3 cellcounter.py -t <0 or 1> -i <path to image> -o <output directory>' + '\n')
        print('-i : input file path to image')
        print('-o : output directory')
        print('-t 0 : If cells are not darker than background')
        print('-t 1 : If cells are darker than than background:')
        print('*'*20)
    else:
        option = sys.argv[sys.argv.index('-t')+1]
        input_file = sys.argv[sys.argv.index('-i')+1]
        output_file = sys.argv[sys.argv.index('-o')+1]

        if output_file == '.':
            output_file = ''
        if option == '1':
            input_file=sb.getoutput('python3 gfp.py '+input_file)

        r = sb.getoutput('java -jar cellcounter.jar '+input_file)

        #meta -> area=# x=# y=# width=# height=#
        meta = [[v.split()[1]] + v.split()[5:] for v in r.split('\n')[:]]

        f = open(output_file+'meta_'+input_file.split('/')[-1][:-4]+'.txt', 'w')
        pad = [[count]+['='+str(v[0])]+v[1:] for count,v in list(enumerate(meta[:]))]
        f.write('\n'.join(list(map(str, pad))))

        meta_int = [list(map(float, [v[0]] + [p.split('=')[1][:-1] for p in v[2:]])) for v in meta[:]]

        """
        rest of the code is just to get superimposed image
        """
        #meta_int -> area=# x=# y=# width=# height=#
        im = Image.open(input_file)
        w, h = im.size

        array = sorted([int(v[0]) for v in meta_int])[int(len(meta_int)/2):]
        mean = np.mean(array)
        ells = [Ellipse(xy=[v[1]+15, h-v[2]-15], width=v[3], height=v[4], angle=360) for v in meta_int if int(v[0]) > mean/20.0]

        fig = plt.figure(0)
        ax = fig.add_subplot(111, aspect='equal')

        for e in ells:
            ax.add_artist(e)
            e.set_clip_box(ax.bbox)
            e.set_facecolor('red')
        f.write('\n'+"approximate number of cells ~"+str(len(ells)))
        f.close()
        print('approximate number of cells ~'+str(len(ells)))
        ax.set_xlim(0, w)
        ax.set_ylim(0, h)
        ax.set_axis_off()
        plot_ = 'plot_'+input_file.split('/')[-1]
        plt.savefig(plot_)
        plt.close()

        img = mpimg.imread(input_file)
        pimg = plt.imshow(img)
        plt.gca().set_axis_off()

        primed_ = 'primed_'+input_file.split('/')[-1]
        plt.savefig(primed_)
        plt.close()

        """
        final comparison
        """
        im = Image.open(primed_)
        im2 = Image.open(plot_)

        im=im.convert('L')
        im=im.convert('RGB')
        im2=im2.convert('RGB')

        result = Image.blend(im, im2, alpha=.5)
        superimposed_ = 'superimposed_'+input_file.split('/')[-1]
        result.save(output_file+superimposed_)

        sb.call('rm '+plot_, shell=True)
        sb.call('rm '+primed_, shell=True)
except:
    print('Crashed!!!')
    print('To get help > python3 cellcounter.py -h')
