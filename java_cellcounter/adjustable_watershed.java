import ij.plugin.filter.PlugInFilter;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.plugin.filter.MaximumFinder;
import ij.plugin.filter.EDM;
import ij.*;
import ij.gui.GenericDialog;
import ij.gui.DialogListener;
import ij.process.*;
import java.awt.*;

/** This ImageJ plugin does Watershed segmentation of the EDM, similar to
  * Process>Binary>Watershed but with adjustable sensitivity.
  * The ImageJ Process>Binary>Watershed algorithm has a tolerance of 0.5.
  *
  * 2012-May-08 Michael Schmid
  */

public class adjustable_watershed implements ExtendedPlugInFilter, DialogListener {

    protected final static int FLAGS = DOES_8G | PARALLELIZE_STACKS;
    protected static double toleranceS = 1.;
    protected double tolerance = 1.;
    protected int nPasses = 1;
    protected int pass = 0;
    protected boolean background255;
    protected boolean interrupted;        /*whether watershed segmentation has been interrupted by the user */
    protected MaximumFinder maxFinder = new MaximumFinder();  /*we use only one MaximumFinder (nicer progress bar)*/
    int width, height, xmax, ymax;

    /** 
     * Setup of the PlugInFilter. Returns the flags specifying the capabilities and needs
     * of the filter.
     *
     * @param arg	Defines type of filter operation
     * @param imp	The ImagePlus to be processed
     * @return		Flags specifying further action of the PlugInFilterRunner
     */
    public int setup(String arg, ImagePlus imp) {
        if (imp!=null && !imp.getProcessor().isBinary()) {
            IJ.error("Binary Image required");
            return DONE;
        }
        return FLAGS;
    }

    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
        boolean invertedLut = imp.isInvertedLut();
        background255 = (invertedLut && Prefs.blackBackground) ||
                (!invertedLut && !Prefs.blackBackground);
        width = imp.getWidth();
        height = imp.getHeight();
        xmax = width - 1;
        ymax = height - 1;
        
        GenericDialog gd = new GenericDialog(command+"...");
        gd.addNumericField("Tolerance", toleranceS, 1, 5, "(0.5 is ImageJ standard)");
            gd.addPreviewCheckbox(pfr);     /* passing pfr makes the filter ready for preview */
            gd.addDialogListener(this);     /* the DialogItemChanged method will be called on user input */
            gd.showDialog();                /* display the dialog; preview runs in the  now */
            if (gd.wasCanceled()) return DONE;
            toleranceS = tolerance;
        return IJ.setupDialog(imp, FLAGS);
    }

    public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
        tolerance = gd.getNextNumber();
        if (gd.invalidNumber() || tolerance<=0)
            return false;
        interrupted = false;
        return true;
    }

    public void run(ImageProcessor ip) {
        if (interrupted) return;
        maxFinder = new MaximumFinder(); // for more than one image
        int backgroundValue = background255 ? (byte)255 : 0;
        FloatProcessor floatEdm = new EDM().makeFloatEDM(ip, backgroundValue, false);
        ByteProcessor newIp = maxFinder.findMaxima(floatEdm, tolerance,
                ImageProcessor.NO_THRESHOLD, MaximumFinder.SEGMENTED, false, true);
        if (newIp == null) {  //segmentation cancelled by user?
            interrupted = true;
            return;
        }
        drawSegmentationLines(ip, backgroundValue, newIp);
        ip.setBinaryThreshold();
    }

    /** Draw the segmentation lines in the original image.
     *  Note that segmentation has eliminated particles with radius less than
     *  the tolerance.  Leave these small particles untouched. */
    private void drawSegmentationLines(ImageProcessor ip, int backgroundValue, ByteProcessor segmentedIp) {
        byte[] origPixels = (byte[])ip.getPixels();
        byte[] segmPixels = (byte[])segmentedIp.getPixels();
        for (int p=0; p<origPixels.length; p++)
            if (segmPixels[p] == 0) origPixels[p] = (byte)backgroundValue;

    }
        /* pixel offsets of the 8 neighbor pixels in direction 'd' = 0...7 for direct addressing */
/*        int[] dirOffset  = new int[] {-width, -width+1, +1, +width+1, +width, +width-1,   -1, -width-1 };
        byte foregroundValue = (byte)~backgroundValue;
        byte smallParticle = 10; /* any value but 0, 255, used to mark small particles */
/*        FloodFiller floodFiller = new FloodFiller(ip);
        for (int y=0, p=0; y<height; y++)
            for (int x=0; x<width; x++,p++)
                if (origPixels[p] != backgroundValue && origPixels[p] != smallParticle && segmPixels[p] == 0) {
                    /* This pixel has been set to background. Walk around it; */
                    /* if we have at least two foreground-background transitions */
                    /* this is a segmentation line that should be background. */
                    /* (out-of-image pixels count as background) */
/*                    int nTransitions = 0;
                    boolean lastWasForeground = isWithin(x, y, 7) && segmPixels[p+dirOffset[7]]!=0;
                    for (int d=0; d<8; d++) {
                        boolean isForeground = isWithin(x, y, d) && segmPixels[p+dirOffset[d]]!=0;
                        if (isForeground && !lastWasForeground) nTransitions++;
                        lastWasForeground = isForeground;
                    }
                    if (nTransitions<2) {
                        origPixels[p] = smallParticle;
                        floodFiller.fill8(x,y); /* mark the whole small particle as such */
//                    } else
/*                        origPixels[p] = (byte)backgroundValue;   /* a segmentation line */
/*                }
        for (int p=0; p<origPixels.length; p++)
            if (origPixels[p] == smallParticle) origPixels[p] = foregroundValue;
        
    }

    /* whether a neighbor of pixel x, y in direction 'd' is inside the image */
    private boolean isWithin(int x, int y, int direction) {
        switch(direction) {
            case 0: return (y>0);
            case 1: return (x<xmax && y>0);
            case 2: return (x<xmax);
            case 3: return (x<xmax && y<ymax);
            case 4: return (y<ymax);
            case 5: return (x>0 && y<ymax);
            case 6: return (x>0);
            case 7: return (x>0 && y>0);
        }
        return false;   /* to make the compiler happy :-) */
    }

    /** This method is called by ImageJ to set the number of calls to run(ip)
     *	corresponding to 100% of the progress bar. We transfer it to the
     *  MaximumFinder since it will need most of the processing time */
    public void setNPasses (int nPasses) {
        maxFinder.setNPasses(nPasses);
    }

}
