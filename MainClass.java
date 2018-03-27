/**
 * @author Antonino Rullo, Domenico Saccà, University of Calabria, 2018.
 * @author Edoardo Serra, Boise State University, 2018
 * @copyright GNU General Public License v3
 * No reproduction in whole or part without maintaining this copyright notice
 * and imposing this condition on any subsequent users.
 */

import gnu.trove.iterator.TIntIterator;
import gnu.trove.set.hash.TIntHashSet;

import java.io.*;
import java.util.*;

public class MainClass
{		
	private static Table buildTable(final String[] args)
	{		
		final Map<Integer,Column<Integer>> sv_columns = new TreeMap<Integer,Column<Integer>>();
		final Map<Integer,Column<TIntHashSet>> mv_columns = new TreeMap<Integer,Column<TIntHashSet>>();
		
		int cIndex = 0;
		
		for(int i=3; i<args.length-1; i++)
		{
			final String columnName = args[i];
			i++;
			final boolean isSingleValue = args[i].equals("sv") ? true : false;
			
			if(isSingleValue)
				sv_columns.put(cIndex, new Column<Integer>(new ArrayList<Integer>(), columnName));
			else
				mv_columns.put(cIndex, new Column<TIntHashSet>(new ArrayList<TIntHashSet>(), columnName));
			
			cIndex++;
		}
		
		try
		{
			final BufferedReader reader = new BufferedReader(new FileReader(args[0]));
			final File table = new File("transactional_" + args[0]);
			final FileOutputStream os = new FileOutputStream(table);
			final PrintStream ps = new PrintStream(os);
			
			String line;			

			while((line = reader.readLine()) != null)
			{
				final StringTokenizer st = new StringTokenizer(line, ";");
				int columnIndex = 0;	
				
				while(st.hasMoreTokens())
				{
					final String attributeValue = st.nextToken().trim();
					
					if(sv_columns.containsKey(columnIndex))
						sv_columns.get(columnIndex).getValues().add(Integer.parseInt(attributeValue));
					else
					{
						final TIntHashSet set = new TIntHashSet();
						final StringTokenizer st2 = new StringTokenizer(attributeValue, " ");
						
						while(st2.hasMoreTokens())
							set.add(Integer.parseInt(st2.nextToken()));
						
						mv_columns.get(columnIndex).getValues().add(set);
					}
					
					columnIndex++;
				}
				
				ps.println(line.replace(";", " "));
			}
			
			reader.close();
			ps.close();
			os.close();
		}
		catch (FileNotFoundException e)
		{
			e.printStackTrace();
		}
		catch (IOException e) 
		{
			e.printStackTrace();
		}
		
		final List<Column<Integer>> sv_list = new ArrayList<Column<Integer>>();
		final List<Column<TIntHashSet>> mv_list = new ArrayList<Column<TIntHashSet>>();
		
		for(final int i : sv_columns.keySet())
			sv_list.add(sv_columns.get(i));
		
		for(final int i : mv_columns.keySet())
			mv_list.add(mv_columns.get(i));
		
		Table table = new Table(sv_list, mv_list, "transactional", args);
		return table;
	}
	
	
	private static List<Itemset> computeFrequentItemsets(final double support, final String tableName)
	{
		final String[] parameters = {"transactional_" + tableName, support+""};//transactions file, support
		Apriori apriori = null;
	
		try
		{
			apriori = new Apriori(parameters);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		
		return apriori.getItemsets();
	}
	

	private static List<Constraint> computeFC(final List<Itemset> frequentItemsets, final Table table, final double threshold, final double scale_factor)
	{
		final List<Constraint> frequencyConstraints = new ArrayList<Constraint>();
		int fcIndex = 0;
		
		for(final Itemset itemset : frequentItemsets)
		{
			final double support = (double)itemset.getSupport()/(double)table.getSize();
			
			if(support >= threshold)
			{
				final Map<String,Integer> singleValueAttributeConstraint = new HashMap<String,Integer>();
				final Map<String,TIntHashSet> multiValueAttributeConstraint = new HashMap<String,TIntHashSet>();
				
				final List<Column<Integer>> sv_attributes = table.get_SV_attributes();
				final List<Column<TIntHashSet>> mv_attributes = table.get_MV_attributes();
				
				final TIntHashSet items = itemset.getSetOfItems();
				
				final TIntIterator iterator = items.iterator();
				
				while(iterator.hasNext())
				{
					final int i = iterator.next();
					boolean isMV = true;
					
					for(final Column<Integer> column : sv_attributes)
						if(table.domainSingleValueAttribute(column.getName()).contains(i))
						{
							singleValueAttributeConstraint.put(column.getName(), i);
							isMV = false;
							
							break;
						}
					
					if(isMV)
						for(final Column<TIntHashSet> column : mv_attributes)
							if(table.domainMultiValueAttribute(column.getName()).contains(i))
							{
								if(multiValueAttributeConstraint.containsKey(column.getName()))
									multiValueAttributeConstraint.get(column.getName()).add(i);
								else
								{
									final TIntHashSet set = new TIntHashSet();
									set.add(i);
									multiValueAttributeConstraint.put(column.getName(), set);
								}
								
								break;
							}
				}
			
				final int bound = (int)((int)(itemset.getSupport())*scale_factor);
				
				frequencyConstraints.add(new Constraint("fc"+fcIndex, bound, bound, singleValueAttributeConstraint, multiValueAttributeConstraint));
				fcIndex++;
			}
		}
		
		return frequencyConstraints;
	}

	
	private static Set<TIntHashSet> computeFrontier(final List<Itemset> frequentItemsets, final Table table, final double s)
	{
		final Set<TIntHashSet> frontier = new HashSet<TIntHashSet>();
		
		final List<Column<Integer>> sv_attributes = table.get_SV_attributes();
		final List<Column<TIntHashSet>> mv_attributes = table.get_MV_attributes();

		for(final Itemset itemset : frequentItemsets)
		{
			if((double)itemset.getSupport()/(double)table.getSize() >= s)
			{
				final TIntHashSet setOfItems = itemset.getSetOfItems();
						
				for(final Column<Integer> column : sv_attributes)
				{
					final TIntHashSet domainSingleValueAttribute = new TIntHashSet(table.domainSingleValueAttribute(column.getName()));
					
					if(!domainSingleValueAttribute.removeAll(setOfItems))
					{
						final TIntIterator iterator = domainSingleValueAttribute.iterator();

						while(iterator.hasNext())
						{
							final TIntHashSet newItemset = new TIntHashSet(setOfItems);
							newItemset.add(iterator.next());
							
							frontier.add(newItemset);
						}
					}
				}
				
				for(final Column<TIntHashSet> column : mv_attributes)
				{
					final TIntHashSet domainMultiValueAttribute = new TIntHashSet(table.domainMultiValueAttribute(column.getName()));
					domainMultiValueAttribute.removeAll(setOfItems);
					
					final TIntIterator iterator = domainMultiValueAttribute.iterator();
					
					while(iterator.hasNext())
					{
						final TIntHashSet newItemset = new TIntHashSet(setOfItems);
						newItemset.add(iterator.next());
						
						frontier.add(newItemset);
					}
				}
			}
		}

		TIntHashSet singleton;
		
		for(final Column<Integer> column : sv_attributes)
		{
			final TIntIterator iterator = table.domainSingleValueAttribute(column.getName()).iterator();
			
			while(iterator.hasNext())
			{
				singleton = new TIntHashSet();
				singleton.add(iterator.next());
				frontier.add(singleton);
			}
		}
		
		for(final Column<TIntHashSet> column : mv_attributes)
		{
			final TIntIterator iterator = table.domainMultiValueAttribute(column.getName()).iterator();
			
			while(iterator.hasNext())
			{
				singleton = new TIntHashSet();
				singleton.add(iterator.next());
				frontier.add(singleton);
			}		
		}
		
		Set<TIntHashSet> copyOfFrontier = new HashSet<TIntHashSet>(frontier);
		
		for(final TIntHashSet itemset : copyOfFrontier)
			for(final Itemset i : frequentItemsets)
				if((double)i.getSupport()/(double)table.getSize()>=s && itemset.equals(new TIntHashSet(i.getSetOfItems())))
				{
					frontier.remove(itemset);
					break;
				}
		
		copyOfFrontier = new HashSet<TIntHashSet>(frontier);
		
		for(final TIntHashSet i : copyOfFrontier)
		{
			boolean isMinimal = true;

			for(final TIntHashSet j : copyOfFrontier)
				if(!i.equals(j))
				{
					if(i.containsAll(j))
					{
						isMinimal = false;
						break;
					}
				}
			
			if(!isMinimal)
				frontier.remove(i);
		}
		
		return frontier;
	}

	private static List<Constraint> computeIC(final Set<TIntHashSet> frontier, final Table table, final double threshold, final double scale_factor)
	{
		final List<Constraint> infrequencyConstraints = new ArrayList<Constraint>();
		int icIndex = 0;
	
		for(final TIntHashSet minimalInfrequentItemset : frontier)
		{		
			final Map<String,Integer> singleValueAttributeConstraint = new HashMap<String,Integer>();
			final Map<String,TIntHashSet> multiValueAttributeConstraint = new HashMap<String,TIntHashSet>();
			
			final List<Column<Integer>> sv_attributes = table.get_SV_attributes();
			final List<Column<TIntHashSet>> mv_attributes = table.get_MV_attributes();
			
			final TIntIterator iterator = minimalInfrequentItemset.iterator();
			
			while(iterator.hasNext())
			{
				final int i = iterator.next();
				boolean isMV = true;
				
				for(final Column<Integer> column : sv_attributes)
					if(table.domainSingleValueAttribute(column.getName()).contains(i))
					{
						singleValueAttributeConstraint.put(column.getName(), i);
						isMV = false;
						
						break;
					}
				
				if(isMV)
					for(final Column<TIntHashSet> column : mv_attributes)
						if(table.domainMultiValueAttribute(column.getName()).contains(i))
						{
							if(multiValueAttributeConstraint.containsKey(column.getName()))
								multiValueAttributeConstraint.get(column.getName()).add(i);
							else
							{
								final TIntHashSet set = new TIntHashSet();
								set.add(i);
								multiValueAttributeConstraint.put(column.getName(), set);
							}
							
							break;
						}
			}
			
			int upperBound = (int)(((int)(threshold*table.getSize())) - 1);
			
			if(upperBound < 0)
				upperBound = 0;
			
			infrequencyConstraints.add(new Constraint("ic"+icIndex, (int)(upperBound*scale_factor), 0, singleValueAttributeConstraint, multiValueAttributeConstraint));
			icIndex++;
		}
		
		return infrequencyConstraints;
	}

	public static void main(String[] args) throws Exception
	{	
		final long solverStart = System.currentTimeMillis();
		
		final Table table = buildTable(args);
		final String tableName = args[0];
		final double threshold = Double.parseDouble(args[1]);
		final double scale_factor = Double.parseDouble(args[2]);
		
		System.out.println("\nrunning APRIORI on " + tableName + " ...");
		final List<Itemset> frequentItemsets = computeFrequentItemsets(threshold, tableName);
			
		final List<Constraint> frequencyConstraints = computeFC(frequentItemsets, table, threshold, scale_factor);

		if(frequencyConstraints.size() > 0)
		{
			final Set<TIntHashSet> frontier = computeFrontier(frequentItemsets, table, threshold);
			List<Constraint> infrequencyConstraints = computeIC(frontier, table, threshold, scale_factor);
			
			System.out.println("frequency constraints: " + frequencyConstraints.size() + "\ninfrequency constraints: " + infrequencyConstraints.size());

//				for(Constraint c : frequencyConstraints)
//					System.out.println(c.toString());
//				
//				System.out.println();
//				for(Constraint c : infrequencyConstraints)
//					System.out.println(c.toString());
			
			System.out.println("computing table ...");
			
			final Solver solver = new Solver(table, frequencyConstraints, infrequencyConstraints, scale_factor);
			solver.runProgram();
			final long solverEnd = System.currentTimeMillis();
			
			solver.buildNewTable(threshold, args);
			System.out.println("done in " + (solverEnd-solverStart) + " ms");
		}
	}

}
