import java.awt.*;
import java.util.*;

public class Node
{
	public int id;
	
	public java.util.List<Integer> connections;
	
	public Point3D location;
	
	public double diameter;
	
	public String region;
	
	public Node()
	{
		id = -1;
		diameter = 0.0;
		location = new Point3D();
		connections = new ArrayList<Integer>();
		region = "DEND";
	}
}
