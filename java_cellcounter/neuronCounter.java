import java.io.File;
import java.io.IOException;
import ij.IJ;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.process.ImageConverter;
import ij.process.*;

/*
 * This is for free and for public use. The end game is to make this into a web site that people can use 
 * to get quick neural density count approximations for research purposes.
 */

/**
 * Takes folder of just nissl brain images of meta {< 5microns/pixel, 25 micron cut density}
 * and returns the neuron count of each slide + the total amount of neurons in total.
 * @author Troy Michael Sincomb
 */
public class neuronCounter {//extends ImagePlus {
	
	protected static ImagePlus myImPlus;
	protected static ImageConverter myImConv; 
	protected static BinaryProcessor myImBinaryProcessor;
	
	static adjustable_watershed water = new adjustable_watershed();
	static Custom_Particle_Analyzer partA = new Custom_Particle_Analyzer();
	protected  static File myFile = null; // source file
  //protected static File myDir1 = null; // source file
	//protected static File myDir2 = null; // altered photo dump for visual testing
	protected static File[] myPhotoList = null; //list of photo in directory source
	protected static ImageProcessor myImProcessor = null; //alter tools for image
	
	public static void main(String[] args) throws IOException { //Method Belonging to PlugIn Implementation
	
		//String myDir1 = IJ.getDirectory("Select Image Source Folder...");		//This plugin opens the select folder window in ImageJ
		//myDir1 = new File(args[0]);
		myFile = new File(args[0]);
		//myFile = new File("/Users/love/Desktop/Prox1_8.jpg");
    //myPhotoList = myDir1.listFiles();	//This string contains all the files names in the source folder
		//if (myPhotoList==null) {System.out.println("No source folder found"); return;}
    if (myFile==null) {System.out.println("No source file found"); return;}
    //IJ.log("The source image folder chosen was"+myDir1);					//This command opens the log window and writes on the variables on it.

		//String myDir2 = IJ.getDirectory("Select Images Saving Folder...");  
		//myDir2 = new File(args[1]);
		//if (myDir2==null) {System.out.println("No output folder found"); return;}
		//IJ.log("The saving folder chosen was"+myDir2);
			
		//for (int i = 1; i < myPhotoList.length; i++) {
			
    //IJ.showProgress(i,myPhotoList.length);		//This command sets magnitude the progress bar on ImageJ
    //IJ.log((i)+": "+myPhotoList[i].getName());
    //IJ.showStatus(i+"/"+myPhotoList.length);	//This command sets a text in the ImageJ status bar

    //System.out.println(myPhotoList[i].getAbsolutePath());
    //Opener opener = new Opener();
    //myImPlus = IJ.openImage("~/Desktop/Prox1_8.jpg");

		myImPlus = IJ.openImage(myFile.getAbsolutePath()); //debug

		//myImPlus = opener.openImage(myFile.getAbsolutePath());

    //myImPlus.show("pending...");	//Opens a window with alt image; need to make while loop to work
    //myImPlus.updateAndDraw();
    //myImPlus.getImage();
    //Steps 1 - 7 for cell count.
    ImageConverter(myImPlus); // make black and white
    ImageProcessor(myImPlus); // the rest of the steps

    //IJ.saveAs("Tiff", myDir2+"/"+myPhotoList[i].getName()+"_Altered");	//This method saves the active image in a given format (like Tiff) in the provided folder
    //IJ.log("Completed "+ myPhotoList[i].getName()+"_Altered");	//PRECAUTION! It will replace an image in the folder if it has the same name. It will not warn you of this
    myImPlus.close();	//This method closes the image window and it sets the its image processor to null (this means it empties the corresponding memory in ImageJ.
  //}
		//IJ.log("Completed");
	}

	/**
	 * Converts image to gray scale
	 * @param ci current image
	 */
	private static void ImageConverter(ImagePlus ci) {
		myImConv = new ImageConverter(ci);
		myImConv.convertToGray8(); // (step 1)
	}
	
	/**
	 * converts image to bytes to process the individual neurons.
	 * @param ci current image
	 */
	private static void ImageProcessor(ImagePlus ci) {
		myImProcessor = ci.getProcessor(); //
		myImProcessor.invert(); // inverts img so neurons are white instead of black (step 2)
		myImProcessor.autoThreshold(); // makes the whites 0 and blacks 255 (step 3)
		//myImProcessor.fill();
		//ci.show("pending...");
		//IJ.save(ci, "/Users/love/Desktop/tesingImage.tif");
		water.run(myImProcessor); // water shed (steps 4 & 5) debug
		//myImProcessor.fill();
		myImProcessor.invert(); // inverts img so neurons are back. Better for countering.
		partA.run(ci); // analyze and print (step 6)
		
		//To make skeleton. Not useful so far
		//IJ.run("Fill Holes","");
		//ByteProcessor myImBtyeProcessor = (ByteProcessor) myImProcessor.convertToByte(true);
		//myImBinaryProcessor = new BinaryProcessor(myImBtyeProcessor);
		//myImBinaryProcessor.outline(); // makes just circle of black 
		//partA.run("");
	}
}
