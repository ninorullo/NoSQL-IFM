/**
 * @author Antonino Rullo, Domenico Saccà, University of Calabria, 2018.
 * @author Edoardo Serra, Boise State University, 2018
 * @copyright GNU General Public License v3
 * No reproduction in whole or part without maintaining this copyright notice
 * and imposing this condition on any subsequent users.
 */

import gnu.trove.iterator.TIntIterator;
import gnu.trove.set.hash.TIntHashSet;

import java.util.*;

public class Itemset
{
	private final int support;
	private final TIntHashSet setOfItems = new TIntHashSet();
	private final int id;
	
	public Itemset(final int[] itmst, final int sup, final int id)
	{
		support = sup;
		this.id = id;
		
		for(final int i : itmst)
			setOfItems.add(i);
	}
	
	public int getId()
	{
		return id;
	}
	
	public TIntHashSet getSetOfItems()
	{
		return setOfItems;
	}
	
	public int getSupport()
	{
		return support;
	}
	
	public String toString()
	{
		String toReturn = "[ ";
		
		TIntIterator iterator = setOfItems.iterator();
		
		while(iterator.hasNext())
			toReturn += iterator.next() + " ";
		
		toReturn += "] (" + support + ")";
		
		return toReturn;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((setOfItems == null) ? 0 : setOfItems.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Itemset other = (Itemset) obj;
		if (setOfItems == null) {
			if (other.setOfItems != null)
				return false;
		} else if (!setOfItems.equals(other.setOfItems))
			return false;
		return true;
	}
	
	
}
