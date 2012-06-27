
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;

import javax.swing.*;

import ij.*;
import ij.gui.*;
import ij.plugin.*;
import ij.plugin.filter.*;
import ij.process.*;

import static java.lang.Math.*;


/**
 * This is a singleton ImageJ plugin class for overlaying node
 * selections on top of a single or stack of confocal images.
 * 
 * @author mschachter
 *
 */
public class Morph_Dig implements PlugInFilter
{
	protected static int regionSize = 100;
	
	protected static int nodePixelRadius = 4;	
	protected static Color nodeColor = Color.blue;
	
	protected static boolean lineMode;	
	protected static boolean somaMode;
		
	protected static Node startNode;
	
	protected static Point startPoint;
	protected static Point endPoint;
	
	protected static MorphCanvas morphCanvas;
	
	protected static ImagePlus img;	
	
	protected static ImageProcessor ip;
	
	protected static boolean initialized = false;
	
	protected static Node somaNode;
	
	protected static Map<Integer, Node> nodeMap;
	
	protected static IDGenerator idGenerator;
	
	protected static ImageWindow window;
	
	protected static java.util.List<java.util.List<Integer>> regionList;
	
	protected static int numRows;
	protected static int numCols;
	
	protected static Font statusFont;
	
	protected static Cursor overNodeCursor = new Cursor(Cursor.HAND_CURSOR);
	protected static Cursor normalCursor = new Cursor(Cursor.DEFAULT_CURSOR);
	
	protected static NodeInfoDialog nodeInfoDialog;	
	
	protected static String statusText;
		
	public Morph_Dig() { }
	
	public static void save()
	{
		JFileChooser fc = new JFileChooser();
		fc.setDialogType(JFileChooser.SAVE_DIALOG);
		int retval = fc.showOpenDialog(window);
		
		if (retval == JFileChooser.APPROVE_OPTION) {			
            File file = fc.getSelectedFile();
            try {            	
            	PrintWriter pw = new PrintWriter(file);
            	pw.printf("#\tnode\tparent\tdia\txbio\tybio\tzbio\tregion\tdendr\n");
            	            	
            	pw.printf("\t0\t0\t%.2f\t%.2f\t%.2f\t%.2f\t%s\t0\n",
            			  new Object[] {somaNode.diameter,
            			                somaNode.location.x, somaNode.location.y, somaNode.location.z,
            						    somaNode.region});            	
            	for (int k = 0; k < somaNode.connections.size(); k++) {
            		Node n = nodeMap.get(somaNode.connections.get(k));
            		nodeToFile(n, somaNode.id, pw);
            	}            	
            	pw.flush();            	
            	pw.close();
            } catch (Exception e) {
            	StringWriter sw = new StringWriter();
            	PrintWriter pw = new PrintWriter(sw);
            	e.printStackTrace(pw);
            	pw.flush();
            	IJ.log(sw.getBuffer().toString());
            }
		}	
	}
	
	public static void load()
	{
		JFileChooser fc = new JFileChooser();
		fc.setDialogType(JFileChooser.OPEN_DIALOG);
		int retval = fc.showOpenDialog(window);
		
		if (retval == JFileChooser.APPROVE_OPTION) {			
            File file = fc.getSelectedFile();
            try {
            	
            	nodeMap.clear();
            	for (int k = 0; k < regionList.size(); k++)  regionList.get(k).clear();
            	            	
            	int lineno = 0;
            	String line;
            	StringTokenizer st;
            	BufferedReader br = new BufferedReader(new FileReader(file));
            	while ((line = br.readLine()) != null) {
            		lineno++;
            		if ((line.length() > 1) && (!line.startsWith("#"))) {
            		
            			st = new StringTokenizer(line, "\t ");
            			
            			if (!st.hasMoreTokens()) throw new Exception("Line " + lineno + ": node number missing!");
            			int id = Integer.parseInt(st.nextToken());
            			if (!st.hasMoreTokens()) throw new Exception("Line " + lineno + ": parent node missing!");
            			int par = Integer.parseInt(st.nextToken());
            			if (!st.hasMoreTokens()) throw new Exception("Line " + lineno + ": diameter node missing!");
            			double dia = Double.parseDouble(st.nextToken());
            			if (!st.hasMoreTokens()) throw new Exception("Line " + lineno + ": x location missing!");
            			double xloc = Double.parseDouble(st.nextToken());
            			if (!st.hasMoreTokens()) throw new Exception("Line " + lineno + ": y location missing!");
            			double yloc = Double.parseDouble(st.nextToken());
            			if (!st.hasMoreTokens()) throw new Exception("Line " + lineno + ": z location missing!");
            			double zloc = Double.parseDouble(st.nextToken());
            			if (!st.hasMoreTokens()) throw new Exception("Line " + lineno + ": region missing!");
            			String reg = st.nextToken().trim();
            			
            			Node n = new Node();
            			n.id = id;
            			n.diameter = dia;
            			n.location.x = xloc;
            			n.location.y = yloc;
            			n.location.z = zloc;
            			n.region = reg;
            			
            			addNode(n);            			
            			if (n.id == 0) {
            				somaNode = n;
            				somaMode = false;
            			}
            			
            			if (nodeMap.containsKey(par)) connectNodes(n, nodeMap.get(par), false);
            		}
            	}
            	idGenerator.setNextId(nodeMap.size());
            	morphCanvas.changeBuffer();
            	morphCanvas.changeStatusText("Loaded file " + file.getName());
            	morphCanvas.updateNodeTree();
            	morphCanvas.repaint();
            	
            } catch (Exception e) {
            	StringWriter sw = new StringWriter();
            	PrintWriter pw = new PrintWriter(sw);
            	e.printStackTrace(pw);
            	pw.flush();
            	IJ.log(sw.getBuffer().toString());
            }
           
		}
	}
	
	/**
	 * A recursive function that prints node data to a file.
	 */
	protected static void nodeToFile(Node n, int parentId, PrintWriter pw)
	{
		pw.printf("\t%d\t%d\t%.2f\t%.2f\t%.2f\t%.2f\t%s\t0\n",
				  new Object[] {n.id, parentId, n.diameter, n.location.x, n.location.y, n.location.z, n.region});
		Node child;
		int id;
		for (int k = 0; k < n.connections.size(); k++) {
			child = nodeMap.get(n.connections.get(k));
			if (child.id != parentId)  nodeToFile(child, n.id, pw);
		}		
	}
	
	protected static void checkForLoops()
	{
		java.util.List<Integer> idList = new ArrayList<Integer>();
		
		try {		
	    	for (int k = 0; k < somaNode.connections.size(); k++) {
	    		Node n = nodeMap.get(somaNode.connections.get(k));
	    		visitNode(n, somaNode.id, idList);
	    	}            	
		} catch (LoopException e) {
			JOptionPane.showMessageDialog(window, "Loop encountered! Node " + e.getId() + " visited twice.");
		}
	}
	
	/**
	 * Much like nodeToFile, except keeps track of nodes that have
	 * been visited already in order to check for loops.
	 */
	protected static void visitNode(Node n, int parentId, java.util.List<Integer> idList) throws LoopException
	{
		Node child;
				
		if (n.id != 0) {
			if (idList.contains(n.id)) throw new LoopException(n.id);
			idList.add(n.id);
			
			for (int k = 0; k < n.connections.size(); k++) {
				child = nodeMap.get(n.connections.get(k));
				if (child.id != parentId)  visitNode(child, n.id, idList);
			}	
		}
	}
	
	public ImageWindow getWindow() { return window; }
	
	protected void init()
	{
		System.err.println("[MorphDigPlugin] init()");
		somaMode = true;
		lineMode = false;
		startPoint = new Point();
		endPoint = new Point();
		
		nodeMap = new HashMap<Integer, Node>();		
		statusFont = new Font("Times New Roman", Font.BOLD, 12);
			
		nodeInfoDialog = new NodeInfoDialog(this);		
		idGenerator = new IDGenerator();
	}
	
	public int setup(String arg, ImagePlus img)
	{
		if (!initialized && ("run".equals(arg))) {
			System.err.println("\n[MorphDigPlugin] setup()... arg=" + arg);
			init();				
			this.img = img;
			IJ.register(Morph_Dig.class);
		}
		
		if ("save".equals(arg)) save();
		if ("load".equals(arg)) load();
		
		return DOES_ALL+NO_CHANGES;
	}
	
	public void run(ImageProcessor ip)
	{
		if (!initialized) {
			System.err.println("[MorphDigPlugin] run()");
			
			this.ip = ip;
			
			int nslices = img.getNSlices();
			System.err.println("# of slices: " + nslices);
			
			morphCanvas = new MorphCanvas(img);
			if (img.getStackSize() > 1) {
	           window = new StackWindow(img, morphCanvas);
			} else {
	           window = new ImageWindow(img, morphCanvas);
			}
			
			//100% zoom
			IJ.run("View 100%");
			//maximize window
			window.setExtendedState(Frame.MAXIMIZED_BOTH);
			
			morphCanvas.changeBuffer();
			
			somaMode = true;
			initialized = true;
		}
	}
	
	/**
	 * Microns to pixels.
	 */
	private static final int micronToPixel(double p)
	{			
		return (int) round(p / img.getCalibration().pixelWidth);
	}
	
	/**
	 * Pixel to microns.
	 */
	private static final double pixelToMicron(int p)
	{
		return p * img.getCalibration().pixelWidth;
	}
	
	/**
	 * Called from NodeInfoFrame when user requests that a
	 * node's information be changed.
	 */
	public void fireNodeModified(Node newNode, Node oldNode)
	{
		if (newNode.id == oldNode.id) {
			
			Node n = nodeMap.get(newNode.id);
			if (n != null) {  //if it is null, then the node hasn't been added yet, which is OK
				n.diameter = newNode.diameter;
				n.location.x = newNode.location.x;
				n.location.y = newNode.location.y;
				n.location.z = newNode.location.z;
				n.region = newNode.region;
			}
		} else {
			System.err.println("Cannot modify ID!!!");
		}
	}
	
	public void fireNodeRemoved(Node n)
	{	
		removeNode(n);		
		//chagen offscreen image buffer
		morphCanvas.changeBuffer();		
		//redraw
		morphCanvas.updateNodeTree();
	}
	
	protected void removeNode(Node n)
	{
		int k, j;
		
		//return ID to the pool
		idGenerator.returnId(n.id);
		
		//remove connections from each connected node to node n
		for (k = 0; k < n.connections.size(); k++) {
			Node cNode = nodeMap.get(n.connections.get(k));
			for (j = 0; j < cNode.connections.size(); j++) {				
				if (cNode.connections.get(j) == n.id) {
					cNode.connections.remove(j);
					break;
				}
			}
		}
		n.connections.clear();
		
		//remove node from each region it occupies
		java.util.List<Integer> regions =
			getRegionIndiciesAtPoint(micronToPixel(n.location.x), micronToPixel(n.location.y));

		java.util.List<Integer> region;
		for (k = 0; k < regions.size(); k++) {
			region = regionList.get(regions.get(k));
			for (j = 0; j < region.size(); j++) {
				if (region.get(j) == n.id) {
					region.remove(j);
					break;
				}
			}
		}
		
		//remove node from nodeMap
		nodeMap.remove(n.id);
		
		if (n.id == somaNode.id) {
			somaNode = null;
			somaMode = true;
		}
	}
	
	/**
	 * Return a list of region numbers that a node exists in at a given (x,y) location,
	 * in pixels. Since graphically, a node is a circle and not a single point, it can
	 * exist in multiple regions.
	 * 
	 * @param x Location x in pixels
	 * @param y Location y in pixels
	 * @return A list of region numbers that a node would exist in if it was placed at (x,y).
	 */
	protected static java.util.List<Integer> getRegionIndiciesAtPoint(int x, int y)
	{
		java.util.List<Integer> retlist = new ArrayList<Integer>();
		int[] rlist = new int[4];
		int row, col;
		
		col = (int) round(ceil(x / regionSize));  
		row = (int) round(ceil((y-nodePixelRadius) / regionSize));
		rlist[0] = (row*numCols)+col;
		col = (int) round(ceil(x / regionSize));  
		row = (int) round(ceil((y-nodePixelRadius) / regionSize));
		rlist[1] = (row*numCols)+col;
		col = (int) round(ceil((x+nodePixelRadius) / regionSize));  
		row = (int) round(ceil(y / regionSize));
		rlist[2] = (row*numCols)+col;
		col = (int) round(ceil((x-nodePixelRadius) / regionSize));  
		row = (int) round(ceil(y / regionSize));
		rlist[3] = (row*numCols)+col;
		
		for (int k = 0; k < 4; k++) {
			if (!retlist.contains(rlist[k]))  retlist.add(rlist[k]);
		}
		
		return retlist;
	}
	
	/**
	 * Add a node to the nodeMap and add its ID to regions it
	 * occupies.
	 */
	protected static void addNode(Node n)
	{
		//add node to map
		nodeMap.put(n.id, n);
		
		//add node id to each region its affiliated with
		java.util.List<Integer> reglist = getRegionIndiciesAtPoint(micronToPixel(n.location.x),
															       micronToPixel(n.location.y));
		java.util.List<Integer> rlist;
		for (int k = 0; k < reglist.size(); k++) {
			rlist = regionList.get(reglist.get(k));
			rlist.add(n.id);
		}
	}
	
	/**
	 * Get the node at pixel location (x, y), or null if no
	 * such node exists.
	 */
	protected Node getNodeAt(int x, int y)
    {
    	int j, k;
    	double dist;
		Node n = null;
		java.util.List<Integer> region;
		java.util.List<Integer> reglist = getRegionIndiciesAtPoint(x, y);
		for (k = 0; k < reglist.size(); k++)  {
			region = regionList.get(reglist.get(k));
			for (j = 0; j < region.size(); j++) {
				n = nodeMap.get(region.get(j));
				dist = sqrt(pow(micronToPixel(n.location.x) - x, 2)
						    + pow(micronToPixel(n.location.y) - y, 2));
				if (dist <= nodePixelRadius) {
					return n;
				}
			}
		}
    	return null;
    }
	
	/**
	 * Create a connection between two nodes, unless a connection already exists.
	 */
	protected static void connectNodes(Node n1, Node n2, boolean checkLoops)
	{
		boolean make21 = true, make12 = true;  //make connect from 2->1, 1->2
		int k;
		//make sure nodes aren't already connected
		for (k = 0; k < n2.connections.size(); k++) {
			if (n2.connections.get(k) == n1.id) make21 = false;
		}
		for (k = 0; k < n1.connections.size(); k++) {				
			if (n1.connections.get(k) == n2.id) make12 = false;
		}
		//connect nodes
		if (make12) n1.connections.add(n2.id);
		if (make21) n2.connections.add(n1.id);
		
		if (checkLoops) checkForLoops();
	}
	
	/**
	 * Create a node at pixel coordinates (x,y), with
	 * the ID specified.
	 */
	protected static Node createNodeAt(int x, int y, int id)
	{
		Node n = new Node();
		n.location.x = pixelToMicron(x);
    	n.location.y = pixelToMicron(y);
    	n.location.z = (img.getNSlices()-img.getCurrentSlice())*img.getCalibration().pixelDepth;
    	n.id = id;
    	n.diameter = 0.0;
    			
		return n;
	}
	
	/**
	 * Create a node at pixel coordinates (x,y), with
	 * an automatically generated unique ID.
	 */
	protected Node createNodeAt(int x, int y) {	
		return createNodeAt(x, y, idGenerator.getID());
	}
	
	
	
	/**
	 * Inner class used to provide drawing routines for overlaid
	 * nodes, connections, and scale bar.
	 */
	class MorphCanvas extends ImageCanvas
	{	
		protected Image offscreenImage;
		
		protected Graphics gBuffer;
		
		public MorphCanvas(ImagePlus imp)
		{
			super(imp);
			createRegionList();
						
			System.err.println("img width=" + img.getWidth() + ", height=" + img.getHeight());
			offscreenImage = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
			gBuffer = offscreenImage.getGraphics();
		}
		
		protected void createRegionList()
		{
			numRows = (int) round(ceil(img.getHeight() / regionSize)) + 1;
			numCols = (int) round(ceil(img.getWidth() / regionSize)) + 1;
			
			regionList = new ArrayList<java.util.List<Integer>>();
			for (int k = 0; k < (numRows*numCols); k++) regionList.add(new ArrayList<Integer>());
			
			System.err.println("      Regions: " + numRows + " X " + numCols);
			System.err.println("Slice Spacing: " + img.getCalibration().pixelDepth + " um");
			System.err.println("   Slice Size: " + pixelToMicron(img.getWidth()) + "um  X "
					           + pixelToMicron(img.getHeight()) + " um");
		}
				
		/**
		 * Overridden to repaint the nodes when the user scrolls the image
		 * with the hand tool.
		 */
		protected void scroll(int sx, int sy)
		{			
			super.scroll(sx, sy);
			changeBuffer();
			updateNodeTree();
		}

		public void paint(Graphics g)
		{
		    super.paint(g);
            
		    g.drawImage(offscreenImage, 0, 0, this);
            
            if (lineMode) {
            	drawNodeAt(startPoint, g);
            	drawNodeAt(endPoint, g);
            	drawNodeConnection(startPoint, endPoint, g);
            }
            
            if (somaMode)  statusText = "Click the center of the soma to create the first node.";
            if (statusText != null) drawStatus(statusText, g);
        }		
		
		void drawStatus(String status, Graphics g)
		{
			g.setColor(Color.white);
			g.setFont(statusFont);
			g.drawString(status, 20, 20);
		}
		
		void drawNodeAt(Point p, Graphics g)
		{
			g.fillOval(p.x-nodePixelRadius, p.y-nodePixelRadius, 2*nodePixelRadius, 2*nodePixelRadius);
		}
		
		void drawNodeConnection(Point p1, Point p2, Graphics g)
		{
			g.setColor(nodeColor);
			g.drawLine(p1.x, p1.y, p2.x, p2.y);
		}
		
		public void changeBuffer()
		{
			offscreenImage = new BufferedImage(srcRect.width, srcRect.height, BufferedImage.TYPE_INT_ARGB);
			gBuffer = offscreenImage.getGraphics();
		}
		
		/**
		 * Redraw the buffer containing nodes and their connections,
		 * then repaint.
		 */
		protected void updateNodeTree()
		{	
			int k;
			Node cNode;
			
			Point p1 = new Point();
			Point p2 = new Point();
			
			Collection<Node> nodes = nodeMap.values();
						
			//draw nodes and their connections
			for (Node n : nodes) {
				p1.x = micronToPixel(n.location.x);
				p1.y = micronToPixel(n.location.y);
				
				if (srcRect.contains(p1)) {					
					p1.x -= srcRect.x;
					p1.y -= srcRect.y;
					for (k = 0; k < n.connections.size(); k++) {
						cNode = nodeMap.get(n.connections.get(k));
						p2.x =  micronToPixel(cNode.location.x) - srcRect.x;
						p2.y =  micronToPixel(cNode.location.y) - srcRect.y;
						drawNodeConnection(p1, p2, gBuffer);						
					}										
					if ((somaNode != null) && (n.id == somaNode.id))  gBuffer.setColor(Color.orange);
					else  gBuffer.setColor(nodeColor);
					drawNodeAt(p1, gBuffer);
				}
			}
			drawScaleBar(gBuffer);
			repaint();
		}

		
		void drawScaleBar(Graphics g)
		{				
			int liney = getHeight() - 50;
			int linewidth = micronToPixel(50);
			int x1 = getWidth()-15-linewidth;
			int x2 = getWidth()-15;
			
			g.setColor(Color.white);
			g.drawLine(x1, liney, x2, liney);
			g.drawString("50 um", ((x1+x2)/2)-10, liney+15);
		}
    
		/**
		 * Overridden method that handles mouse button clicks.
		 */
        public void mousePressed(MouseEvent e)
        {   
        	int locx = offScreenX(e.getX());
        	int locy = offScreenY(e.getY());
        	        	
        	//only do mouse-click handling if the hand tool isn't selected
        	if (Toolbar.getToolId() == Toolbar.HAND)  {
        		super.mousePressed(e);
        		return;
        	} else {
        	
	        	if (e.getButton() == MouseEvent.BUTTON3) {
	        		Node n = getNodeAt(locx, locy);
	        		if (n != null) {    
	        			nodeInfoDialog.setNewNode(false);
	        			nodeInfoDialog.setNode(n);
	        			nodeInfoDialog.setVisible(true);
	        			return;
	        		}
	        	}
	        	
	            super.mousePressed(e);    
	            
	            if (e.getButton() == MouseEvent.BUTTON1) {
	            
		            if (!lineMode && !somaMode) {
		            	lineMode = true;		            	
		            	Node n = getNodeAt(locx, locy);	            	
		            	if (n == null) {
		            		n = createNodeAt(locx, locy);
		            		nodeInfoDialog.setNewNode(true);
		            		nodeInfoDialog.setNode(n);
		            		nodeInfoDialog.setVisible(true);
		            		if (nodeInfoDialog.isCancelled()) {
		            			lineMode = false;		
		            			idGenerator.returnId(n.id);
		            		} else {
		            			addNode(n);
		            			morphCanvas.updateNodeTree();
		            		}
		            	}
		            	startNode = n;
		            	startPoint.x = micronToPixel(n.location.x) - srcRect.x;
		            	startPoint.y = micronToPixel(n.location.y) - srcRect.y;
		            	endPoint.x = micronToPixel(n.location.x) - srcRect.x;
		            	endPoint.y = micronToPixel(n.location.y) - srcRect.y;
		            	changeStatusText("Create Connection Mode: press ESC to cancel");
		            } else {	 
		            	if (!somaMode) {
			            	Node n = getNodeAt(locx, locy);	            	
			            	if (n == null) {
			            		n = createNodeAt(locx, locy);
			            		n.diameter = startNode.diameter;
			            		n.region = startNode.region;
			            		
			            		nodeInfoDialog.setNewNode(true);
			            		nodeInfoDialog.setNode(n);
			            		nodeInfoDialog.setVisible(true);
			            		if (nodeInfoDialog.isCancelled()) {
			            			idGenerator.returnId(n.id);
			            			lineMode = false;
			            			return;		            			
			            		}
			            		addNode(n);
			            	}	            	
			            	endPoint.x = micronToPixel(n.location.x) - srcRect.x;
			            	endPoint.y = micronToPixel(n.location.y) - srcRect.y;
			            	
			            	connectNodes(startNode, n, true);
			            	
			            	lineMode = false;
			            	morphCanvas.changeBuffer();
			            	morphCanvas.updateNodeTree();
		            	}
		            }            
		            
		            if (somaMode) {
		            	somaNode = createNodeAt(locx, locy, 0);
		            	somaNode.region = "SOMA";
		            	
		            	nodeInfoDialog.setNewNode(true);
		            	nodeInfoDialog.setNode(somaNode);
		            	nodeInfoDialog.setVisible(true);
		            	
		            	if (!nodeInfoDialog.isCancelled()) {
		            		addNode(somaNode);
		            		somaMode = false;
		            		changeStatusText(null);
		            	
		            		morphCanvas.updateNodeTree();
		            	} else {
		            		somaNode = null;
		            	}
		            }
	            }
        	}
        }

        /**
         * Overridden method that handles mouse movement.
         */
		public void mouseMoved(MouseEvent e)
		{	
			super.mouseMoved(e);
			
			int locx = offScreenX(e.getX());
        	int locy = offScreenY(e.getY());
			
			if (!somaMode) {
				//check to see if the mouse is over a node, change the
				//cursor and display the node number if it is
				Node n = getNodeAt(locx, locy);
				if (n != null) {
					setCursor(overNodeCursor);
					changeStatusText("Node " + n.id);
				} else {
					if (statusText != null) {
						changeStatusText(null);
						setCursor(defaultCursor);							
					}
				}					
			}
			
			if (lineMode) {
				if (!IJ.escapePressed()) {
					endPoint.x = e.getX();
					endPoint.y = e.getY();					
				} else {
					lineMode = false;
					changeStatusText(null);
					IJ.resetEscape();
				}
				
				int minx = min(startPoint.x, endPoint.x);
				int miny = min(startPoint.y, endPoint.y);
				int maxx = max(startPoint.x, endPoint.x);
				int maxy = max(startPoint.y, endPoint.y);
				int offset = 100;				
				repaint(max(minx-offset, 0), max(miny-offset, 0),
						min(maxx-minx+offset, img.getWidth()), min(maxy-miny+offset, img.getHeight()));
			}
		}
		
		protected void changeStatusText(String str)
		{	
			statusText = str;
			repaint(0, 0, 300, 50);
		}		
	}
}

class LoopException extends Exception
{
	protected int id;
	
	public LoopException(int id)
	{
		this.id = id;
	}

	public String getMessage()
	{
		return "Revisited node: " + id;
	}
	
	public int getId()
	{
		return id;
	}
}
