/**
 * @author Antonino Rullo, Domenico Saccà, University of Calabria, 2018.
 * @author Edoardo Serra, Boise State University, 2018
 * @copyright GNU General Public License v3
 * No reproduction in whole or part without maintaining this copyright notice
 * and imposing this condition on any subsequent users.
 */

import java.util.*;

public class Column<T>
{
	private final List<T> values;
	private final String name;
	
	public Column(final List<T> values, final String name)
	{
		this.values  = new ArrayList<T>(values);
		this.name = name;
	}
	
	public T getValue(final int index)
	{
		return values.get(index);
	}
	
	public String getName()
	{
		return name;
	}
	
	public List<T> getValues()
	{
		return values;
	}
}
