import java.util.*;


public class IDGenerator
{	
	protected int numUsedIds;
	
	protected List<Integer> pool;
	
	public IDGenerator()
	{
		numUsedIds = 0;
		pool = new ArrayList<Integer>();
	}
	
	
	public void returnId(int id)
	{
		numUsedIds--;
		pool.add(id);
	}
	
	public void setNextId(int id)
	{
		numUsedIds = id-1;
	}
	
	
	public int getID()
	{
		int id;
		
		numUsedIds++;
		if (pool.size() > 0) {
			id = pool.get(0);
			pool.remove(0);
		} else {
			id = numUsedIds;
		}
		return id;
	}
}
