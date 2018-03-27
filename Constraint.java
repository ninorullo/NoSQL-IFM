/**
 * @author Antonino Rullo, Domenico Saccà, University of Calabria, 2018.
 * @author Edoardo Serra, Boise State University, 2018
 * @copyright GNU General Public License v3
 * No reproduction in whole or part without maintaining this copyright notice
 * and imposing this condition on any subsequent users.
 */

import gnu.trove.set.hash.TIntHashSet;

import java.util.*;

public class Constraint 
{
	private final String name;
	private final int upperBound;
	private final int lowerBound;
	private final Map<String,Integer> singleValueAttributesConstraint;//key: column name; value: value
	private final Map<String,TIntHashSet> multiValueAttributesConstraint;//key: column name; value: values
	
	public Constraint(
						final String name, 
						final int upperBound, 
						final int lowerBound, 
						final Map<String,Integer> svConstraints,
						final Map<String,TIntHashSet> mvConstraints
					  )
	{
		this.name = name;
		this.upperBound = upperBound;
		this.lowerBound = lowerBound;
		singleValueAttributesConstraint = svConstraints;
		multiValueAttributesConstraint = mvConstraints;
	}
	
	public Map<String,Integer> getSingleValueSttributeConstraints()
	{
		return singleValueAttributesConstraint;
	}
	
	public Map<String,TIntHashSet> getMultiValueSttributeConstraints()
	{
		return multiValueAttributesConstraint;
	}
	
	public int getLowerBound()
	{
		return lowerBound;
	}
	
	public int getUpperBound()
	{
		return upperBound;				
	}
	
	public String getName()
	{
		return name;
	}
	
	public String toString()
	{
		String toReturn = name + ": " ;
		
		for(final String s : singleValueAttributesConstraint.keySet())
			toReturn += s + "=" + singleValueAttributesConstraint.get(s) + ", ";

		for(final String s : multiValueAttributesConstraint.keySet())
			toReturn += s + ">=" + multiValueAttributesConstraint.get(s).toString() + ", ";
		
		toReturn += lowerBound + ", " + upperBound;
		
		return toReturn;
	}
}
