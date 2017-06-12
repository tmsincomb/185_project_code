import ij.macro.Interpreter;
import ij.plugin.*;
import ij.plugin.filter.*;
import ij.plugin.frame.RoiManager;
import ij.text.TextPanel;
import ij.text.TextWindow;
import ij.util.Tools;
import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.LookUpTable;
import ij.Macro;
import ij.Prefs;
import ij.Undo;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.ImageWindow;
import ij.gui.Overlay;
import ij.gui.PolygonRoi;
import ij.gui.ProgressBar;
import ij.gui.Roi;
import ij.gui.Wand;
import ij.measure.Calibration;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.filter.Analyzer;
import ij.plugin.filter.PlugInFilter;
import ij.process.ByteProcessor;
import ij.process.ByteStatistics;
import ij.process.ColorProcessor;
import ij.process.ColorStatistics;
import ij.process.FloatProcessor;
import ij.process.FloatStatistics;
import ij.process.FloodFiller;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.PolygonFiller;
import ij.process.ShortProcessor;
import ij.process.ShortStatistics;
import java.awt.Color;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.util.Properties;

/**
* "AnalyzeParticles" for each slice of stack
*    record:
*    items checked in Set Measurements
*    slice# in "Slice" column
*    topLeft x,y and ncoords to allow re_autoOutline of particles
*
*/


public class Custom_Particle_Analyzer implements PlugIn {

	public void run(String arg){
	}


	public void run(ImagePlus imp) {
		if (IJ.versionLessThan("1.26i"))
			return;
		//ImagePlus imp = IJ.getImage();
		try {
			analyzeStackParticles(imp);
		} catch (IOException e) {
			e.printStackTrace();
    }
  }

	public void analyzeStackParticles(ImagePlus imp) throws IOException {
		if (imp.getBitDepth()==24)
			{IJ.error("Grayscale image required"); return;}
		CustomParticleAnalyzer pa = new CustomParticleAnalyzer();
		int flags = pa.setup("",imp); // ITS HERE
		if (flags==PlugInFilter.DONE)
			return;
		if ((flags&PlugInFilter.DOES_STACKS)!=0) {
			for (int i=1; i<=imp.getStackSize(); i++) {
				imp.setSlice(i);
				System.out.print("here");
				pa.run(imp.getProcessor());
			}
		} else {
			pa.run(imp.getProcessor());
		}
		pa.nelems();
		pa.closeBuffer();
	}
}
class CustomParticleAnalyzer implements PlugInFilter, Measurements {
	private static double staticMinSize = 0.0D;
	private static double staticMaxSize = 1.0D / 0.0;
	private static boolean pixelUnits;
	private static int staticOptions = Prefs.getInt("ap.options", 64);
	private static String[] showStrings = new String[]{"Nothing", "Outlines", "Bare Outlines", "Ellipses", "Masks", "Count Masks", "Overlay Outlines", "Overlay Masks"};
	private static double staticMinCircularity = 0.0D;
	private static double staticMaxCircularity = 1.0D;
	private static String prevHdr;
	protected static int staticShowChoice;
	protected ImagePlus imp;
	protected ResultsTable rt;
	protected Analyzer analyzer;
	protected int slice;

	protected boolean processStack;
	protected boolean showResults;
	protected boolean excludeEdgeParticles;
	protected boolean showSizeDistribution;
	protected boolean resetCounter;
	protected boolean showProgress;
	protected boolean recordStarts;
	protected boolean displaySummary;
	protected boolean floodFill;
	protected boolean addToManager;
	protected boolean inSituShow;
	private boolean showResultsWindow;
	private String summaryHdr;
	private double level1;
	private double level2;
	private double minSize;
	private double maxSize;
	private double minCircularity;
	private double maxCircularity;
	private int showChoice;
	private int options;
	private int measurements;
	private Calibration calibration;
	private String arg;
	private double fillColor;
	private boolean thresholdingLUT;
	private ImageProcessor drawIP;
	private int width;
	private int height;
	private boolean canceled;
	private ImageStack outlines;
	private IndexColorModel customLut;
	private int particleCount;
	private int maxParticleCount;
	private int totalCount;
	private TextWindow tw;
	private Wand wand;
	private int imageType;
	private int imageType2;
	private boolean roiNeedsImage;
	private int minX;
	private int maxX;
	private int minY;
	private int maxY;
	private ImagePlus redirectImp;
	private ImageProcessor redirectIP;
	private PolygonFiller pf;
	private Roi saveRoi;
	private int beginningCount;
	private Rectangle r;
	private ImageProcessor mask;
	private double totalArea;
	private FloodFiller ff;
	private Polygon polygon;
	private RoiManager roiManager;
	private static RoiManager staticRoiManager;
	private static ResultsTable staticResultsTable;
	private ImagePlus outputImage;
	private boolean hideOutputImage;
	private int roiType;
	private int wandMode;
	private Overlay overlay;
	boolean blackBackground;
	private static int defaultFontSize = 9;
	private static int nextFontSize;
	private static int nextLineWidth;
	private int fontSize;
	private int lineWidth;
	int counter;
	int total_count;
	static int firstParticle;
	static int lastParticle;

	File file = null;
	FileWriter fileWriter = null;
	BufferedWriter bufferWriter =null;
	Custom_GenericDialog var27 = null;
	protected int count = 0;

	public CustomParticleAnalyzer(int options, int measurements, ResultsTable rt, double minSize, double maxSize, double minCirc, double maxCirc) throws IOException {
		this.showResultsWindow = true;
		this.summaryHdr = "Slice\tCount\tTotal Area\tAverage Size\t%Area";
		this.maxParticleCount = 0;
		this.wandMode = 1;
		this.fontSize = nextFontSize;
		this.lineWidth = nextLineWidth;
		this.counter = 0;
		this.options = options;
		this.measurements = measurements;
		this.rt = rt;
		if(this.rt == null) {
			this.rt = new ResultsTable();
		}

		this.minSize = minSize;
		this.maxSize = maxSize;
		this.minCircularity = minCirc;
		this.maxCircularity = maxCirc;
		this.slice = 1;
		if((options & 16) != 0) {
			this.showChoice = 5;
		}

		if((options & '耀') != 0) {
			this.showChoice = 6;
		}

		if((options & 65536) != 0) {
			this.showChoice = 7;
		}

		if((options & 4) != 0) {
			this.showChoice = 1;
		}

		if((options & 4096) != 0) {
			this.showChoice = 4;
		}

		if((options & 512) != 0) {
			this.showChoice = 0;
		}

		if((options & 8192) != 0) {
			this.wandMode = 4;
			options |= 1024;
		}

		nextFontSize = defaultFontSize;
		nextLineWidth = 1;
	}

	public CustomParticleAnalyzer(int options, int measurements, ResultsTable rt, double minSize, double maxSize) throws IOException {
		this(options, measurements, rt, minSize, maxSize, 0.0D, 1.0D);
	}

	public CustomParticleAnalyzer() throws IOException {
		this.showResultsWindow = true;
		this.summaryHdr = "Slice\tCount\tTotal Area\tAverage Size\t%Area";
		this.maxParticleCount = 0;
		this.wandMode = 1;
		this.fontSize = nextFontSize;
		this.lineWidth = nextLineWidth;
		this.counter = 0;
		this.slice = 1;
	}

	public int setup(String arg, ImagePlus imp) {
		this.arg = arg;
		this.imp = imp;
		IJ.register(ParticleAnalyzer.class);
		if(imp == null) {
			IJ.noImage();
			return 4096;
		} else if(imp.getBitDepth() == 24 && !this.isThresholdedRGB(imp)) {
			IJ.error("Particle Analyzer", "RGB images must be thresholded using\nImage>Adjust>Color Threshold.");
			return 4096;
		} else if(!this.showDialog()) {
			return 4096;
		} else {
			short baseFlags = 415;
			int flags = IJ.setupDialog(imp, baseFlags);
			this.processStack = (flags & 32) != 0;
			this.slice = 0;
			this.saveRoi = imp.getRoi();
			if(this.saveRoi != null && this.saveRoi.getType() != 0 && this.saveRoi.isArea()) {
				this.polygon = this.saveRoi.getPolygon();
			}

			imp.startTiming();
			nextFontSize = defaultFontSize;
			nextLineWidth = 1;
			return flags;
		}
	}

	public void run(ImageProcessor ip) {
		/*
		file = new File("img_data/" + imp.getTitle() + ".txt"); //debug
		//file = new File(imp.getTitle() + ".txt"); //debug

		try {
			fileWriter = new FileWriter(file.getAbsoluteFile(), false);
		} catch (IOException e) {
			e.printStackTrace();
		}
		bufferWriter = new BufferedWriter(fileWriter);
		*/
		if(!this.canceled) {
			++this.slice;
			if(this.imp.getStackSize() > 1 && this.processStack) {
				this.imp.setSlice(this.slice);
			}

			if(this.imp.getType() == 4) {
				ip = (ImageProcessor)this.imp.getProperty("Mask");
				ip.setThreshold(255.0D, 255.0D, 2);
			}

			try {
				if(!this.analyze(this.imp, ip)) {
          this.canceled = true;
        }
			} catch (IOException e) {
				e.printStackTrace();
			}

			if(this.slice == this.imp.getStackSize()) {
				this.imp.updateAndDraw();
				if(this.saveRoi != null) {
					this.imp.setRoi(this.saveRoi);
				}
			}

		}

	}

	public boolean showDialog() { //debug
		Calibration cal = this.imp != null?this.imp.getCalibration():new Calibration();
		double unitSquared = cal.pixelWidth * cal.pixelHeight;
		if(pixelUnits) {
			unitSquared = 1.0D;
		}

		if(Macro.getOptions() != null) {
			//boolean gd = this.updateMacroOptions();
			//if(gd) {
			unitSquared = 1.0D;
			//}

			staticMinSize = 0.0D;
			staticMaxSize = 1.0D / 0.0;
			staticMinCircularity = 0.0D;
			staticMaxCircularity = 1.0D;
			staticShowChoice = 0;
		}

		GenericDialog var27 = new GenericDialog("analytics");
    //var27.Custom_GenericDialog();
		this.minSize = staticMinSize;
		this.maxSize = staticMaxSize;
		this.minCircularity = staticMinCircularity;
		this.maxCircularity = staticMaxCircularity;
		this.showChoice = staticShowChoice;
		if(this.maxSize == 999999.0D) {
			this.maxSize = 1.0D / 0.0;
		}

		this.options = staticOptions;
		String unit = cal.getUnit();
		boolean scaled = cal.scaled();
		if(unit.equals("inch")) {
			unit = "pixel";
			unitSquared = 1.0D;
			scaled = false;
			pixelUnits = true;
		}

		String units = unit + "^2";
		byte places = 0;
		double cmin = this.minSize * unitSquared;
		if((double)((int)cmin) != cmin) {
			places = 2;
		}

		double cmax = this.maxSize * unitSquared;
		if((double)((int)cmax) != cmax && cmax != 1.0D / 0.0) {
			places = 2;
		}

		String minStr = ResultsTable.d2s(cmin, places);
		if(minStr.indexOf("-") != -1) {
			for(int maxStr = places; maxStr <= 6; ++maxStr) {
				minStr = ResultsTable.d2s(cmin, maxStr);
				if(minStr.indexOf("-") == -1) {
					break;
				}
			}
		}

		String var28 = ResultsTable.d2s(cmax, places);
		if(var28.indexOf("-") != -1) {
			for(int labels = places; labels <= 6; ++labels) {
				var28 = ResultsTable.d2s(cmax, labels);
				if(var28.indexOf("-") == -1) {
					break;
				}
			}
		}

		if(scaled) {
			var27.setInsets(5, 0, 0);
		}

		var27.addStringField("Size (" + units + "):", minStr + "-" + var28, 12);
		if(scaled) {
			var27.setInsets(0, 40, 5);
			var27.addCheckbox("Pixel units", pixelUnits);
		}

		var27.addStringField("Circularity:", IJ.d2s(this.minCircularity) + "-" + IJ.d2s(this.maxCircularity), 12);
		var27.addChoice("Show:", showStrings, showStrings[this.showChoice]);
		String[] var29 = new String[8];
		boolean[] states = new boolean[8];
		var29[0] = "Display results";
		states[0] = true;//(this.options & 1) != 0;
		var29[1] = "Exclude on edges";
		states[1] = (this.options & 8) != 0;
		var29[2] = "Clear results";
		states[2] = (this.options & 64) != 0;
		var29[3] = "Include holes";
		states[3] = (this.options & 1024) != 0;
		var29[4] = "Summarize";
		states[4] = true;//(this.options & 256) != 0;
		var29[5] = "Record starts";
		states[5] = (this.options & 128) != 0;
		var29[6] = "Add to Manager";
		states[6] = (this.options & 2048) != 0;
		var29[7] = "In_situ Show";
		states[7] = (this.options & 16384) != 0;
		var27.addCheckboxGroup(4, 2, var29, states);
		var27.addHelp("http://imagej.nih.gov/ij/docs/menus/analyze.html#ap");
		//var27.showDialog();
		if(false){//var27.wasCanceled()) {
			return false;
		} else {
			String size = var27.getNextString();
			if(scaled) {
				pixelUnits = var27.getNextBoolean();
			}

			if(pixelUnits) {
				unitSquared = 1.0D;
			} else {
				unitSquared = cal.pixelWidth * cal.pixelHeight;
			}

			String[] minAndMax = Tools.split(size, " -");
			double mins = var27.parseDouble(minAndMax[0]);
			double maxs = minAndMax.length == 2?var27.parseDouble(minAndMax[1]):0.0D / 0.0;
			this.minSize = Double.isNaN(mins)?0.0D:mins / unitSquared;
			this.maxSize = Double.isNaN(maxs)?1.0D / 0.0:maxs / unitSquared;
			if(this.minSize < 0.0D) {
				this.minSize = 0.0D;
			}

			if(this.maxSize < this.minSize) {
				this.maxSize = 1.0D / 0.0;
			}

			staticMinSize = this.minSize;
			staticMaxSize = this.maxSize;
			minAndMax = Tools.split(var27.getNextString(), " -");
			double minc = var27.parseDouble(minAndMax[0]);
			double maxc = minAndMax.length == 2?var27.parseDouble(minAndMax[1]):0.0D / 0.0;
			this.minCircularity = Double.isNaN(minc)?0.0D:minc;
			this.maxCircularity = Double.isNaN(maxc)?1.0D:maxc;
			if(this.minCircularity < 0.0D || this.minCircularity > 1.0D) {
				this.minCircularity = 0.0D;
			}

			if(this.maxCircularity < this.minCircularity || this.maxCircularity > 1.0D) {
				this.maxCircularity = 1.0D;
			}

			if(this.minCircularity == 1.0D && this.maxCircularity == 1.0D) {
				this.minCircularity = 0.0D;
			}

			staticMinCircularity = this.minCircularity;
			staticMaxCircularity = this.maxCircularity;
			if(false) {
				IJ.error("Bins invalid.");
				this.canceled = true;
				return false;
			} else {
				this.showChoice = var27.getNextChoiceIndex();
				staticShowChoice = this.showChoice;
				if(var27.getNextBoolean()) {
					this.options |= 1;
				} else {
					this.options &= -2;
				}

				if(var27.getNextBoolean()) {
					this.options |= 8;
				} else {
					this.options &= -9;
				}

				if(var27.getNextBoolean()) {
					this.options |= 64;
				} else {
					this.options &= -65;
				}

				if(var27.getNextBoolean()) {
					this.options |= 1024;
				} else {
					this.options &= -1025;
				}

				if(var27.getNextBoolean()) {
					this.options |= 256;
				} else {
					this.options &= -257;
				}

				if(var27.getNextBoolean()) {
					this.options |= 128;
				} else {
					this.options &= -129;
				}

				if(var27.getNextBoolean()) {
					this.options |= 2048;
				} else {
					this.options &= -2049;
				}

				if(var27.getNextBoolean()) {
					this.options |= 16384;
				} else {
					this.options &= -16385;
				}

				staticOptions = this.options;
				this.options |= 32;
				if((this.options & 256) != 0) {
					Analyzer.setMeasurements(Analyzer.getMeasurements() | 1);
				}

				return true;
			}
		}
	}

	private boolean isThresholdedRGB(ImagePlus imp) {
		Object obj = imp.getProperty("Mask");
		if(obj != null && obj instanceof ImageProcessor) {
			ImageProcessor mask = (ImageProcessor)obj;
			return mask.getWidth() == imp.getWidth() && mask.getHeight() == imp.getHeight();
		} else {
			return false;
		}
	}

	boolean updateMacroOptions() {
		String options = Macro.getOptions();
		int index = options.indexOf("maximum=");
		if(index == -1) {
			return false;
		} else {
			index += 8;

			int len;
			for(len = options.length(); index < len - 1 && options.charAt(index) != 32; ++index) {
				;
			}

			if(index == len - 1) {
				return false;
			} else {
				int min = (int)Tools.parseDouble(Macro.getValue(options, "minimum", "1"));
				int max = (int)Tools.parseDouble(Macro.getValue(options, "maximum", "999999"));
				options = "size=" + min + "-" + max + options.substring(index, len);
				Macro.setOptions(options);
				return true;
			}
		}
	}

	public boolean analyze(ImagePlus imp) throws IOException {
		return this.analyze(imp, imp.getProcessor());
	}

	public boolean analyze(ImagePlus imp, ImageProcessor ip) throws IOException {
		if(this.imp == null) {
			this.imp = imp;
		}

		this.showResults = false;//(this.options & 1) != 0; //debug
		this.excludeEdgeParticles = (this.options & 8) != 0;
		this.resetCounter = (this.options & 64) != 0;
		this.showProgress = (this.options & 32) != 0;
		this.floodFill = (this.options & 1024) == 0;
		this.recordStarts = (this.options & 128) != 0;
		this.addToManager = (this.options & 2048) != 0;
		if(staticRoiManager != null) {
			this.addToManager = true;
			this.roiManager = staticRoiManager;
			staticRoiManager = null;
		}

		if(staticResultsTable != null) {
			this.rt = staticResultsTable;
			staticResultsTable = null;
			this.showResultsWindow = false;
		}

		this.displaySummary = (this.options & 256) != 0 || (this.options & 2) != 0;
		this.inSituShow = (this.options & 16384) != 0;
		this.outputImage = null;
		ip.snapshot();
		ip.setProgressBar((ProgressBar)null);
		if(Analyzer.isRedirectImage()) {
			this.redirectImp = Analyzer.getRedirectImage(imp);
			if(this.redirectImp == null) {
				return false;
			}

			int pixels = this.redirectImp.getStackSize();
			if(pixels > 1 && pixels == imp.getStackSize()) {
				ImageStack offset = this.redirectImp.getStack();
				this.redirectIP = offset.getProcessor(imp.getCurrentSlice());
			} else {
				this.redirectIP = this.redirectImp.getProcessor();
			}
		} else if(imp.getType() == 4) {
			ImagePlus var12 = (ImagePlus)imp.getProperty("OriginalImage");
			if(var12 != null && var12.getWidth() == imp.getWidth() && var12.getHeight() == imp.getHeight()) {
				this.redirectImp = var12;
				this.redirectIP = var12.getProcessor();
			}
		}

		if(!this.setThresholdLevels(imp, ip)) {
			return false;
		} else {
			this.width = ip.getWidth();
			this.height = ip.getHeight();
			if(this.showChoice != 0 && this.showChoice != 6 && this.showChoice != 7) {
				this.blackBackground = Prefs.blackBackground && this.inSituShow;
				if(this.slice == 1) {
					this.outlines = new ImageStack(this.width, this.height);
				}

				if(this.showChoice == 5) {
					this.drawIP = new ShortProcessor(this.width, this.height);
				} else {
					this.drawIP = new ByteProcessor(this.width, this.height);
				}

				this.drawIP.setLineWidth(this.lineWidth);
				if(this.showChoice != 5) {
					if(this.showChoice == 4 && !this.blackBackground) {
						this.drawIP.invertLut();
					} else if(this.showChoice == 1) {
						if(!this.inSituShow) {
							if(this.customLut == null) {
								this.makeCustomLut();
							}

							this.drawIP.setColorModel(this.customLut);
						}

						this.drawIP.setFont(new Font("SansSerif", 0, this.fontSize));
						if(this.fontSize > 12 && this.inSituShow) {
							this.drawIP.setAntialiasedText(true);
						}
					}
				}

				this.outlines.addSlice((String)null, this.drawIP);
				if(this.showChoice != 5 && !this.blackBackground) {
					this.drawIP.setColor(Color.white);
					this.drawIP.fill();
					this.drawIP.setColor(Color.black);
				} else {
					this.drawIP.setColor(Color.black);
					this.drawIP.fill();
					this.drawIP.setColor(Color.white);
				}
			}

			this.calibration = this.redirectImp != null?this.redirectImp.getCalibration():imp.getCalibration();
			if(this.rt == null) {
				this.rt = Analyzer.getResultsTable();
				this.analyzer = new Analyzer(imp);
			} else {
				if(this.measurements == 0) {
					this.measurements = Analyzer.getMeasurements();
				}

				this.analyzer = new Analyzer(imp, this.measurements, this.rt);
			}

			if(this.resetCounter && this.slice == 1 && !Analyzer.resetCounter()) {
				return false;
			} else {
				this.beginningCount = Analyzer.getCounter();
				byte[] var13 = null;
				if(ip instanceof ByteProcessor) {
					var13 = (byte[])((byte[])ip.getPixels());
				}

				if(this.r == null) {
					this.r = ip.getRoi();
					this.mask = ip.getMask();
					if(this.displaySummary) {
						if(this.mask != null) {
							this.totalArea = ImageStatistics.getStatistics(ip, 1, this.calibration).area;
						} else {
							this.totalArea = (double)this.r.width * this.calibration.pixelWidth * (double)this.r.height * this.calibration.pixelHeight;
						}
					}
				}

				this.minX = this.r.x;
				this.maxX = this.r.x + this.r.width;
				this.minY = this.r.y;
				this.maxY = this.r.y + this.r.height;
				if((this.r.width < this.width || this.r.height < this.height || this.mask != null) && !this.eraseOutsideRoi(ip, this.r, this.mask)) {
					return false;
				} else {
					int inc = Math.max(this.r.height / 25, 1);
					boolean mi = false;
					ImageWindow win = imp.getWindow();
					if(win != null) {
						win.running = true;
					}

					if(this.measurements == 0) {
						this.measurements = Analyzer.getMeasurements();
					}

					if(this.showChoice == 3) {
						this.measurements |= 2048;
					}

					this.measurements &= -257;
					this.roiNeedsImage = (this.measurements & 128) != 0 || (this.measurements & 8192) != 0 || (this.measurements & 16384) != 0;
					this.particleCount = 0;
					this.wand = new Wand(ip);
					this.pf = new PolygonFiller();
					if(this.floodFill) {
						ImageProcessor y = ip.duplicate();
						y.setValue(this.fillColor);
						this.ff = new FloodFiller(y);
					}

					this.roiType = Wand.allPoints()?3:4;

					for(int var15 = this.r.y; var15 < this.r.y + this.r.height; ++var15) {
						int var14 = var15 * this.width;

						for(int x = this.r.x; x < this.r.x + this.r.width; ++x) {
							double value;
							if(var13 != null) {
								value = (double)(var13[var14 + x] & 255);
							} else if(this.imageType == 1) {
								value = (double)ip.getPixel(x, var15);
							} else {
								value = (double)ip.getPixelValue(x, var15);
							}

							if(value >= this.level1 && value <= this.level2) {
								this.analyzeParticle(x, var15, imp, ip);
							}
						}

						if(this.showProgress && var15 % inc == 0) {
							IJ.showProgress((double)(var15 - this.r.y) / (double)this.r.height);
						}

						if(win != null) {
							this.canceled = !win.running;
						}

						if(this.canceled) {
							Macro.abort();
							break;
						}
					}

					if(this.showProgress) {
						IJ.showProgress(1.0D);
					}

					if(this.showResults && this.showResultsWindow) {
						this.rt.updateResults();
					}

					imp.deleteRoi();
					ip.resetRoi();
					ip.reset();
					if(this.displaySummary && IJ.getInstance() != null) {
						this.updateSliceSummary();
					}

					if(this.addToManager && this.roiManager != null) {
						this.roiManager.setEditMode(imp, true);
					}

					this.maxParticleCount = this.particleCount > this.maxParticleCount?this.particleCount:this.maxParticleCount;
					this.totalCount += this.particleCount;
					if(!this.canceled) {
						this.showResults();
					}

					return true;
				}
			}
		}
	}

	void updateSliceSummary() {
		int slices = this.imp.getStackSize();
		float[] areas = this.rt.getColumn(0);
		if(areas == null) {
			areas = new float[0];
		}

		String label = this.imp.getTitle();
		if(slices > 1) {
			label = this.imp.getStack().getShortSliceLabel(this.slice);
			label = label != null && !label.equals("")?label:"" + this.slice;
		}

		String aLine = null;
		double sum = 0.0D;
		int start = areas.length - this.particleCount;
		if(start >= 0) {
			int places;
			for(places = start; places < areas.length; ++places) {
				sum += (double)areas[places];
			}

			places = Analyzer.getPrecision();
			Calibration cal = this.imp.getCalibration();
			String total = "\t" + ResultsTable.d2s(sum, places);
			String average = "\t" + ResultsTable.d2s(sum / (double)this.particleCount, places);
			String fraction = "\t" + ResultsTable.d2s(sum * 100.0D / this.totalArea, places);
			aLine = label + "\t" + this.particleCount + total + average + fraction;
			aLine = this.addMeans(aLine, areas.length > 0?start:-1);
			if(slices == 1) {
				Frame title = WindowManager.getFrame("Summary");
				if(title != null && title instanceof TextWindow && this.summaryHdr.equals(prevHdr)) {
					this.tw = (TextWindow)title;
				}
			}

			if(this.tw == null) {
				String var14 = slices == 1?"Summary":"Summary of " + this.imp.getTitle();
				this.tw = new TextWindow(var14, this.summaryHdr, aLine, 450, 300);
				prevHdr = this.summaryHdr;
			} else {
				this.tw.append(aLine);
			}

		}
	}

	String addMeans(String line, int start) {
		if((this.measurements & 2) != 0) {
			line = this.addMean(1, line, start);
		}

		if((this.measurements & 8) != 0) {
			line = this.addMean(3, line, start);
		}

		if((this.measurements & 128) != 0) {
			line = this.addMean(10, line, start);
		}

		if((this.measurements & 2048) != 0) {
			line = this.addMean(15, line, start);
			line = this.addMean(16, line, start);
			line = this.addMean(17, line, start);
		}

		if((this.measurements & 8192) != 0) {
			line = this.addMean(18, line, start);
			line = this.addMean(35, line, start);
		}

		if((this.measurements & 16384) != 0) {
			line = this.addMean(19, line, start);
			line = this.addMean(29, line, start);
			line = this.addMean(30, line, start);
			line = this.addMean(31, line, start);
			line = this.addMean(32, line, start);
		}

		if((this.measurements & '耀') != 0) {
			line = this.addMean(20, line, start);
		}

		if((this.measurements & 65536) != 0) {
			line = this.addMean(21, line, start);
		}

		if((this.measurements & 131072) != 0) {
			line = this.addMean(22, line, start);
		}

		if((this.measurements & 262144) != 0) {
			line = this.addMean(23, line, start);
		}

		return line;
	}

	private String addMean(int column, String line, int start) {
		if(start == -1) {
			line = line + "\tNaN";
			this.summaryHdr = this.summaryHdr + "\t" + ResultsTable.getDefaultHeading(column);
		} else {
			float[] c = column >= 0?this.rt.getColumn(column):null;
			if(c != null) {
				FloatProcessor ip = new FloatProcessor(c.length, 1, c, (ColorModel)null);
				if(ip == null) {
					return line;
				}

				ip.setRoi(start, 0, ip.getWidth() - start, 1);
				ImageProcessor ip1 = ip.crop();
				FloatStatistics stats = new FloatStatistics(ip1);
				if(stats == null) {
					return line;
				}

				line = line + this.n(stats.mean);
			} else {
				line = line + "\tNaN";
			}

			this.summaryHdr = this.summaryHdr + "\t" + this.rt.getColumnHeading(column);
		}

		return line;
	}

	String n(double n) {
		String s;
		if((double)Math.round(n) == n) {
			s = ResultsTable.d2s(n, 0);
		} else {
			s = ResultsTable.d2s(n, Analyzer.getPrecision());
		}

		return "\t" + s;
	}

	boolean eraseOutsideRoi(ImageProcessor ip, Rectangle r, ImageProcessor mask) {
		int width = ip.getWidth();
		int height = ip.getHeight();
		ip.setRoi(r);
		if(this.excludeEdgeParticles && this.polygon != null) {
			ImageStatistics stats = ImageStatistics.getStatistics(ip, 16, (Calibration)null);
			if(this.fillColor >= stats.min && this.fillColor <= stats.max) {
				double replaceColor = this.level1 - 1.0D;
				int y;
				if(replaceColor < 0.0D || replaceColor == this.fillColor) {
					replaceColor = this.level2 + 1.0D;
					y = this.imageType == 0?255:'\uffff';
					if(replaceColor > (double)y || replaceColor == this.fillColor) {
						IJ.error("Particle Analyzer", "Unable to remove edge particles");
						return false;
					}
				}

				for(y = this.minY; y < this.maxY; ++y) {
					for(int x = this.minX; x < this.maxX; ++x) {
						int v = ip.getPixel(x, y);
						if((double)v == this.fillColor) {
							ip.putPixel(x, y, (int)replaceColor);
						}
					}
				}
			}
		}

		ip.setValue(this.fillColor);
		if(mask != null) {
			mask = mask.duplicate();
			mask.invert();
			ip.fill(mask);
		}

		ip.setRoi(0, 0, r.x, height);
		ip.fill();
		ip.setRoi(r.x, 0, r.width, r.y);
		ip.fill();
		ip.setRoi(r.x, r.y + r.height, r.width, height - (r.y + r.height));
		ip.fill();
		ip.setRoi(r.x + r.width, 0, width - (r.x + r.width), height);
		ip.fill();
		ip.resetRoi();
		return true;
	}

	boolean setThresholdLevels(ImagePlus imp, ImageProcessor ip) {
		double t1 = ip.getMinThreshold();
		double t2 = ip.getMaxThreshold();
		boolean invertedLut = imp.isInvertedLut();
		boolean byteImage = ip instanceof ByteProcessor;
		if(ip instanceof ShortProcessor) {
			this.imageType = 1;
		} else if(ip instanceof FloatProcessor) {
			this.imageType = 2;
		} else {
			this.imageType = 0;
		}

		if(t1 == -808080.0D) {
			ImageStatistics stats = imp.getStatistics();
			if(this.imageType != 0 || stats.histogram[0] + stats.histogram[255] != stats.pixelCount) {
				IJ.error("Particle Analyzer", "A thresholded image or 8-bit binary image is\nrequired. Threshold levels can be set using\nthe Image->Adjust->Threshold tool.");
				this.canceled = true;
				return false;
			}

			boolean threshold255 = invertedLut;
			if(Prefs.blackBackground) {
				threshold255 = !invertedLut;
			}

			if(threshold255) {
				this.level1 = 255.0D;
				this.level2 = 255.0D;
				this.fillColor = 64.0D;
			} else {
				this.level1 = 0.0D;
				this.level2 = 0.0D;
				this.fillColor = 192.0D;
			}
		} else {
			this.level1 = t1;
			this.level2 = t2;
			if(this.imageType == 0) {
				if(this.level1 > 0.0D) {
					this.fillColor = 0.0D;
				} else if(this.level2 < 255.0D) {
					this.fillColor = 255.0D;
				}
			} else if(this.imageType == 1) {
				if(this.level1 > 0.0D) {
					this.fillColor = 0.0D;
				} else if(this.level2 < 65535.0D) {
					this.fillColor = 65535.0D;
				}
			} else {
				if(this.imageType != 2) {
					return false;
				}

				this.fillColor = -3.4028234663852886E38D;
			}
		}

		this.imageType2 = this.imageType;
		if(this.redirectIP != null) {
			if(this.redirectIP instanceof ShortProcessor) {
				this.imageType2 = 1;
			} else if(this.redirectIP instanceof FloatProcessor) {
				this.imageType2 = 2;
			} else if(this.redirectIP instanceof ColorProcessor) {
				this.imageType2 = 3;
			} else {
				this.imageType2 = 0;
			}
		}

		return true;
	}

	void analyzeParticle(int x, int y, ImagePlus imp, ImageProcessor ip) throws IOException {
		ImageProcessor ip2 = this.redirectIP != null?this.redirectIP:ip;
		this.wand.autoOutline(x, y, this.level1, this.level2, this.wandMode);
		if(this.wand.npoints == 0) {
			IJ.log("wand error: " + x + " " + y);
		} else {
			PolygonRoi roi = new PolygonRoi(this.wand.xpoints, this.wand.ypoints, this.wand.npoints, this.roiType);
			Rectangle r = roi.getBounds();
			if(r.width > 1 && r.height > 1) {
				PolygonRoi stats = (PolygonRoi)roi;
				this.pf.setPolygon(stats.getXCoordinates(), stats.getYCoordinates(), stats.getNCoordinates());
				ip2.setMask(this.pf.getMask(r.width, r.height));
				if(this.floodFill) {
					this.ff.particleAnalyzerFill(x, y, this.level1, this.level2, ip2.getMask(), r);
				}
			}

			ip2.setRoi(r);
			ip.setValue(this.fillColor);
			ImageStatistics var16 = this.getStatistics(ip2, this.measurements, this.calibration);
			boolean include = true;
			if(this.excludeEdgeParticles) {
				if(r.x == this.minX || r.y == this.minY || r.x + r.width == this.maxX || r.y + r.height == this.maxY) {
					include = false;
				}

				if(this.polygon != null) {
					Rectangle mask = roi.getBounds();
					int perimeter = mask.x + this.wand.xpoints[this.wand.npoints - 1];
					int y1 = mask.y + this.wand.ypoints[this.wand.npoints - 1];

					for(int i = 0; i < this.wand.npoints; ++i) {
						int circularity = mask.x + this.wand.xpoints[i];
						int y2 = mask.y + this.wand.ypoints[i];
						if(!this.polygon.contains(circularity, y2)) {
							include = false;
							break;
						}

						if(perimeter == circularity && (double)ip.getPixel(perimeter, y1 - 1) == this.fillColor || y1 == y2 && (double)ip.getPixel(perimeter - 1, y1) == this.fillColor) {
							include = false;
							break;
						}

						perimeter = circularity;
						y1 = y2;
					}
				}
			}

			ImageProcessor var17 = ip2.getMask();
			if(this.minCircularity > 0.0D || this.maxCircularity < 1.0D) {
				double var18 = roi.getLength();
				double var19 = var18 == 0.0D?0.0D:12.566370614359172D * ((double)var16.pixelCount / (var18 * var18));
				if(var19 > 1.0D) {
					var19 = 1.0D;
				}

				if(var19 < this.minCircularity || var19 > this.maxCircularity) {
					include = false;
				}
			}

			if((double)var16.pixelCount >= this.minSize && (double)var16.pixelCount <= this.maxSize && include) {
				++this.particleCount;
				if(this.roiNeedsImage) {
					roi.setImage(imp);
				}

				var16.xstart = x;
				var16.ystart = y;
				this.saveResults(var16, roi);
				if(this.showChoice != 0) {
					this.drawParticle(this.drawIP, roi, var16, var17);
				}
			}

			if(this.redirectIP != null) {
				ip.setRoi(r);
			}

			ip.fill(var17);
		}
	}

	ImageStatistics getStatistics(ImageProcessor ip, int mOptions, Calibration cal) {
		switch(this.imageType2) {
			case 0:
				return new ByteStatistics(ip, mOptions, cal);
			case 1:
				return new ShortStatistics(ip, mOptions, cal);
			case 2:
				return new FloatStatistics(ip, mOptions, cal);
			case 3:
				return new ColorStatistics(ip, mOptions, cal);
			default:
				return null;
		}
	}

	protected void saveResults(ImageStatistics stats, Roi roi) throws IOException {
		this.analyzer.saveResults(stats, roi);
		//if (stats.area > 10 && stats.area < 800) {
			//bufferWriter.write(rt.getRowAsString(count) + stats + "\n");//debug*
			System.out.print(rt.getRowAsString(count) + " " + roi + "\n");
		total_count++;
		//}
		count++;
		//bufferWriter.flush(); //
		if(this.recordStarts) {
			this.rt.addValue("XStart", (double)stats.xstart);
			this.rt.addValue("YStart", (double)stats.ystart);
		}

		if(this.addToManager) {
			if(this.roiManager == null) {
				if(Macro.getOptions() != null && Interpreter.isBatchMode()) {
					this.roiManager = Interpreter.getBatchModeRoiManager();
				}

				if(this.roiManager == null) {
					Frame frame = WindowManager.getFrame("ROI Manager");
					if(frame == null) {
						IJ.run("ROI Manager...");
					}

					frame = WindowManager.getFrame("ROI Manager");
					if(frame == null || !(frame instanceof RoiManager)) {
						this.addToManager = false;
						return;
					}

					this.roiManager = (RoiManager)frame;
				}

				if(this.resetCounter) {
					this.roiManager.runCommand("reset");
				}
			}

			if(this.imp.getStackSize() > 1) {
				roi.setPosition(this.imp.getCurrentSlice());
			}

			if(this.lineWidth != 1) {
				roi.setStrokeWidth((float)this.lineWidth);
			}

			this.roiManager.add(this.imp, roi, this.rt.getCounter());
		}

		if(this.showResultsWindow && this.showResults) {
			this.rt.addResults();
		}

	}

	protected void drawParticle(ImageProcessor drawIP, Roi roi, ImageStatistics stats, ImageProcessor mask) {
		switch(this.showChoice) {
			case 1:
			case 2:
			case 6:
			case 7:
				this.drawOutline(drawIP, roi, this.rt.getCounter());
				break;
			case 3:
				this.drawEllipse(drawIP, stats, this.rt.getCounter());
				break;
			case 4:
				this.drawFilledParticle(drawIP, roi, mask);
				break;
			case 5:
				this.drawRoiFilledParticle(drawIP, roi, mask, this.rt.getCounter());
		}

	}

	void drawFilledParticle(ImageProcessor ip, Roi roi, ImageProcessor mask) {
		ip.setRoi(roi.getBounds());
		ip.fill(mask);
	}

	void drawOutline(ImageProcessor ip, Roi roi, int count) {
		if(this.showChoice != 6 && this.showChoice != 7) {
			Rectangle var11 = roi.getBounds();
			int nPoints = ((PolygonRoi)roi).getNCoordinates();
			int[] xp = ((PolygonRoi)roi).getXCoordinates();
			int[] yp = ((PolygonRoi)roi).getYCoordinates();
			int x = var11.x;
			int y = var11.y;
			if(!this.inSituShow) {
				ip.setValue(0.0D);
			}

			ip.moveTo(x + xp[0], y + yp[0]);

			for(int s = 1; s < nPoints; ++s) {
				ip.lineTo(x + xp[s], y + yp[s]);
			}

			ip.lineTo(x + xp[0], y + yp[0]);
			if(this.showChoice != 2) {
				String var12 = ResultsTable.d2s((double)count, 0);
				ip.moveTo(var11.x + var11.width / 2 - ip.getStringWidth(var12) / 2, var11.y + var11.height / 2 + this.fontSize / 2);
				if(!this.inSituShow) {
					ip.setValue(1.0D);
				}

				ip.drawString(var12);
			}
		} else {
			if(this.overlay == null) {
				this.overlay = new Overlay();
				this.overlay.drawLabels(true);
				this.overlay.setLabelFont(new Font("SansSerif", 0, this.fontSize));
			}

			Roi r = (Roi)roi.clone();
			r.setStrokeColor(Color.cyan);
			if(this.lineWidth != 1) {
				r.setStrokeWidth((float)this.lineWidth);
			}

			if(this.showChoice == 7) {
				r.setFillColor(Color.cyan);
			}

			this.overlay.add(r);
		}

	}

	void drawEllipse(ImageProcessor ip, ImageStatistics stats, int count) {
		stats.drawEllipse(ip);
	}

	void drawRoiFilledParticle(ImageProcessor ip, Roi roi, ImageProcessor mask, int count) {
		int grayLevel = count < '\uffff'?count:'\uffff';
		ip.setValue((double)grayLevel);
		ip.setRoi(roi.getBounds());
		ip.fill(mask);
	}

	void showResults() {
		int count = this.rt.getCounter();
		boolean lastSlice = !this.processStack || this.slice == this.imp.getStackSize();
		if((this.showChoice == 6 || this.showChoice == 7) && this.slice == 1 && count > 0) {
			this.imp.setOverlay(this.overlay);
		} else if(this.outlines != null && lastSlice) {
			String tp = this.imp != null?this.imp.getTitle():"Outlines";
			String prefix;
			if(this.showChoice == 4) {
				prefix = "Mask of ";
			} else if(this.showChoice == 5) {
				prefix = "Count Masks of ";
			} else {
				prefix = "Drawing of ";
			}

			this.outlines.update(this.drawIP);
			this.outputImage = new ImagePlus(prefix + tp, this.outlines);
			if(this.inSituShow) {
				if(this.imp.getStackSize() == 1) {
					Undo.setup(6, this.imp);
				}

				this.imp.setStack((String)null, this.outputImage.getStack());
			} else if(!this.hideOutputImage) {
				this.outputImage.show();
			}
		}

		if(this.showResults && !this.processStack) {
			if(this.showResultsWindow) {
				TextPanel tp1 = IJ.getTextPanel();
				if(this.beginningCount > 0 && tp1 != null && tp1.getLineCount() != count) {
					this.rt.show("Results");
				}
			}
			firstParticle = this.beginningCount;
			lastParticle = Analyzer.getCounter() - 1;
		} else {
			lastParticle = 0;
			firstParticle = 0;
		}

	}

	public ImagePlus getOutputImage() {
		return this.outputImage;
	}

	public void setHideOutputImage(boolean hideOutputImage) {
		this.hideOutputImage = hideOutputImage;
	}

	public static void setFontSize(int size) {
		nextFontSize = size;
	}

	public static void setLineWidth(int width) {
		nextLineWidth = width;
	}

	public static void setRoiManager(RoiManager manager) {
		staticRoiManager = manager;
	}

	public static void setResultsTable(ResultsTable rt) {
		staticResultsTable = rt;
	}

	int getColumnID(String name) {
		int id = this.rt.getFreeColumn(name);
		if(id == -2) {
			id = this.rt.getColumnIndex(name);
		}

		return id;
	}

	void makeCustomLut() {
		IndexColorModel cm = (IndexColorModel)LookUpTable.createGrayscaleColorModel(false);
		byte[] reds = new byte[256];
		byte[] greens = new byte[256];
		byte[] blues = new byte[256];
		cm.getReds(reds);
		cm.getGreens(greens);
		cm.getBlues(blues);
		reds[1] = -1;
		greens[1] = 0;
		blues[1] = 0;
		this.customLut = new IndexColorModel(8, 256, reds, greens, blues);
	}

	public static void savePreferences(Properties prefs) {
		prefs.put("ap.options", Integer.toString(staticOptions));
	}

	static {
		nextFontSize = defaultFontSize;
		nextLineWidth = 1;
	}
	protected void nelems() throws IOException {
		//System.out.println(count); //debug
		//System.out.println("Total count of cells: " + total_count);
		//bufferWriter.write(" has ~"+total_count+" neurons");
		//bufferWriter.flush();
		//bufferWriter.close();
		count = 0;
		total_count = 0;
	}

	protected void closeBuffer() throws IOException {
		//bufferWriter.close();
	}
}


/*
class CustomParticleAnalyzer extends ParticleAnalyzer implements PlugInFilter {

	private Roi saveRoi;
	private Polygon polygon;
	private static int nextFontSize;
	private static int nextLineWidth;
	private static int defaultFontSize = 9;
	private boolean canceled;
	static final double DEFAULT_MIN_SIZE = 0.0D;
	static final double DEFAULT_MAX_SIZE = 1.0D / 0.0;
	private static double staticMinSize = 0.0D;
	private static double staticMaxSize = 1.0D / 0.0;
	private static boolean pixelUnits;
	private static int staticOptions = Prefs.getInt("ap.options", 64);
	private static String[] showStrings = new String[]{"Nothing", "Outlines", "Bare Outlines", "Ellipses", "Masks", "Count Masks", "Overlay Outlines", "Overlay Masks"};
	private static double staticMinCircularity = 0.0D;
	private static double staticMaxCircularity = 1.0D;
	private double level1;
	private double level2;
	private double minSize;
	private double maxSize;
	private double minCircularity;
	private double maxCircularity;
	private int showChoice;
	private int options;
	private int measurements;
	private Calibration calibration;
	private String arg;
	private double fillColor;
	private boolean thresholdingLUT;
	private ImageProcessor drawIP;
	private int width;
	private int height;

	File file = new File("neuron_data.txt");
	FileWriter fileWriter = new FileWriter(file.getName(), false);
	BufferedWriter bufferWriter = new BufferedWriter(fileWriter);
	protected int count = 0;

	CustomParticleAnalyzer() throws IOException {
	}

	public int setup(String arg, ImagePlus imp) {
		this.arg = arg;
		this.imp = imp;
		IJ.register(ParticleAnalyzer.class);
		if(imp == null) {
			IJ.noImage();
			return 4096;
		} else if(imp.getBitDepth() == 24) {
			IJ.error("Particle Analyzer", "RGB images must be thresholded using\nImage>Adjust>Color Threshold.");
			return 4096;
		} else if(!this.showDialog()) {
			return 4096;
		} else {
			short baseFlags = 415;
			int flags = IJ.setupDialog(imp, baseFlags);
			this.processStack = (flags & 32) != 0;
			this.slice = 0;
			this.saveRoi = imp.getRoi();
			if(this.saveRoi != null && this.saveRoi.getType() != 0 && this.saveRoi.isArea()) {
				this.polygon = this.saveRoi.getPolygon();
			}

			imp.startTiming();
			nextFontSize = defaultFontSize;
			nextLineWidth = 1;
			return flags;
		}
	}

	public void run(ImageProcessor ip) {
		if(!this.canceled) {
			++this.slice;
			if(this.imp.getStackSize() > 1 && this.processStack) {
				this.imp.setSlice(this.slice);
			}

			if(this.imp.getType() == 4) {
				ip = (ImageProcessor)this.imp.getProperty("Mask");
				ip.setThreshold(255.0D, 255.0D, 2);
			}

			if(!this.analyze(this.imp, ip)) {
				this.canceled = true;
			}

			if(this.slice == this.imp.getStackSize()) {
				this.imp.updateAndDraw();
				if(this.saveRoi != null) {
					this.imp.setRoi(this.saveRoi);
				}
			}

		}
	}

	// Overrides method with the same in AnalyzeParticles that's called once for each particle
	protected void saveResults(ImageStatistics stats, Roi roi) {
		int coordinates = ((PolygonRoi)roi).getNCoordinates();
		Rectangle r = roi.getBoundingRect();
		int x = r.x+((PolygonRoi)roi).getXCoordinates()[coordinates-1];
		int y = r.y+((PolygonRoi)roi).getYCoordinates()[coordinates-1];
		analyzer.saveResults(stats, roi);
		rt.addValue("Slice", imp.getCurrentSlice());
		rt.addValue("Xtopl", x);
		rt.addValue("Ytopl", y);There are no images open.
		rt.addValue("nCoord", coordinates);
		if (showResults) {
			if (stats.area > 10) {
				analyzer.displayResults();
				try {
					bufferWriter.write(rt.getRowAsString(count) + "\n");
					bufferWriter.flush(); // YAS
				} catch (IOException e) {
					e.printStackTrace();
				}
				count++;
			} //else analyzer.displayResults();
		} else try {
      bufferWriter.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
	protected void nelems(){
		System.out.println(count);
		count = 0;
	}
}
*/