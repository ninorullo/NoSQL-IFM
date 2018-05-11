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

public class Table 
{
	private final List<Column<Integer>> singleValueAttributes;
	private final List<Column<TIntHashSet>> multiValueAttributes;
	private final int size;//number of rows
	private final String name;
	private final int numberOfSVAttributes;
	private final int numberOfMVAttributes;
	private final String[] columns;
	
	public Table(final List<Column<Integer>> singleValueAttr, final List<Column<TIntHashSet>> multiValueAttr, final String tableName, final String[] attributes)
	{
		singleValueAttributes = singleValueAttr;
		multiValueAttributes = multiValueAttr;		
		size = singleValueAttr.get(0).getValues().size();
		this.name = "transactional_" + tableName;
		numberOfSVAttributes = singleValueAttr.size();
		numberOfMVAttributes = multiValueAttr.size();
		
		columns = new String[attributes.length-1];
		
		for(int i=1; i<attributes.length; i++)
			columns[i-1] = attributes[i];
	}
	
	public List<Column<Integer>> get_SV_attributes()
	{
		return singleValueAttributes;
	}
	
	public List<Column<TIntHashSet>> get_MV_attributes()
	{
		return multiValueAttributes;
	}
	
	public int getNumberOfSVAttributes()
	{
		return numberOfSVAttributes;
	}
	
	public int getNumberOfMVAttribute()
	{
		return numberOfMVAttributes;
	}
	
	public String getName()
	{
		return name;
	}
	
	public Column<Integer> getSingleValueAttribute(final String attributeName)
	{
		for(final Column<Integer> column : singleValueAttributes)
			if(column.getName().equals(attributeName))
				return column;
		
		return null;
	}
	
	public Column<TIntHashSet> getMultiValueAttribute(final String attributeName)
	{
		for(final Column<TIntHashSet> column : multiValueAttributes)
			if(column.getName().equals(attributeName))
				return column;
		
		return null;
	}
	
	public int getSize()
	{
		return size;
	}
	
	public TIntHashSet domainSingleValueAttribute(final String attributeName)
	{
		final TIntHashSet domain = new TIntHashSet();
		
		for(final Column<Integer> column : singleValueAttributes)
			if(column.getName().equals(attributeName))
			{
				for(final int i : column.getValues())
					domain.add(i);
				
				break;
			}
		
		return domain;
	}
	
	public TIntHashSet domainMultiValueAttribute(final String attributeName)
	{
		final TIntHashSet domain = new TIntHashSet();
		
		for(final Column<TIntHashSet> column : multiValueAttributes)
			if(column.getName().equals(attributeName))
			{
				for(final TIntHashSet s : column.getValues())
				{
					final TIntIterator iterator = s.iterator();
					
					while(iterator.hasNext())
						domain.add(iterator.next());
				}
				
				break;
			}
		
		return domain;
	}

	@Override
	public String toString()
	{
		String toReturn = "";
		int index = 0;
		
		while(index < size)
		{
			for(int i=0; i<columns.length-1; i++)
			{
				final String columnName = columns[i];
				i++;
				final String type = columns[i];
				
				if(type.equals("sv"))
				{
					for(final Column<Integer> svColumn : singleValueAttributes)
						if(svColumn.getName().equals(columnName))
						{
							toReturn += svColumn.getValue(index) + "; ";
							break;
						}
				}
				else
					for(final Column<TIntHashSet> mvColumn : multiValueAttributes)
						if(mvColumn.getName().equals(columnName))
						{
							toReturn += mvColumn.getValue(index).toString().replace("{","").replace("}", "").replace(",", " ") + "; ";
							break;
						}
					
			}
			
			index++;
			toReturn += "\n";
		}
		
		return toReturn;
	}
	
	
}
