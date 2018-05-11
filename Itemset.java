/**
 * @author Antonino Rullo, Domenico Saccà, University of Calabria, 2018.
 * @author Edoardo Serra, Boise State University, 2018
 * @copyright GNU General Public License v3
 * No reproduction in whole or part without maintaining this copyright notice
 * and imposing this condition on any subsequent users.
 */

import gnu.trove.iterator.TLongIterator;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.set.hash.*;

import java.io.Serializable;
import java.util.*;

public class Itemset implements Serializable,Iterable<Long>
{	
	static transient private final long[] value;
	private transient static final int width;
	
	static
	{
		width = 63;
		value = new long[width];
		value[0] = 1;
		
		for(int i=1; i<value.length; i++)
			value[i]=value[i-1]*2;
	}
	
	private transient int support;
	private transient int id;
	TIntObjectHashMap<Pair> set = new TIntObjectHashMap<Pair>();
	
	public Itemset() {}
	
	public Itemset(final long[] val, final int sup, final int id)
	{
		for(int i=0; i<val.length; i++)
			add(val[i]);
		
		support = sup;
		this.id = id;
	}
	
	public int getSupport()
	{
		return support;
	}
	
	public void add(final long val)
	{
		long co = val/width;
		int v = (int) (val%width);
		long f = value[v];
		Pair c1 = set.get((int) co);
		if(c1 != null)
			c1.set = c1.set|f;
		else
		{
			c1 = new Pair(co,f);
			set.put((int) co, c1);
		}
	}	

	public TIntHashSet getElements()
	{
		TLongHashSet tid = new TLongHashSet();
		
		for(Pair c:set.valueCollection())
		{
			long h = c.set;
			long s = c.number*width;
			
			if(h != 0)
			{
				long h1 = Long.numberOfTrailingZeros(h);
		        tid.add((int) (s+h1));	
				h=h-value[(int) h1];
			} 
		}
		
		TIntHashSet tid2 = new TIntHashSet();
		
		TLongIterator iterator = tid.iterator();
		
		while(iterator.hasNext())
		{
			int i = (int) iterator.next();
			tid2.add(i);
		}

		return tid2;
	}

	@Override
	public Iterator<Long> iterator()
	{
		return new Iterator<Long>()
				{
            		Iterator<Pair> it = set.valueCollection().iterator();
            		long value;
            		long h=0;
			
            		@Override
            		public boolean hasNext()
            		{
            			return h>0||it.hasNext();
            		}

            		@Override
            		public Long next()
            		{
            			if(h == 0)
            			{
            				Pair c = it.next();
            				value = c.number * width;
            				h = c.set;
            			}
            			
            			int h1 = Long.numberOfTrailingZeros(h);
            			h = h-Itemset.value[h1];
            			
            			return h1 + value;
            		}

            		@Override
            		public void remove(){}
				};
	}
	
	@Override
	public String toString()
	{
		String s = "{";
		
		for(final long i : this)
			s += " " + i;
		
		s += "}";
		
		return s;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((set == null) ? 0 : set.hashCode());
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
		if (set == null) {
			if (other.set != null)
				return false;
		} else if (!set.equals(other.set))
			return false;
		return true;
	}

}
