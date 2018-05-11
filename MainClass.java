/**
 * @author Antonino Rullo, Domenico Saccà, University of Calabria, 2018.
 * @author Edoardo Serra, Boise State University, 2018
 * @copyright GNU General Public License v3
 * No reproduction in whole or part without maintaining this copyright notice
 * and imposing this condition on any subsequent users.
 */

import gnu.trove.iterator.TIntIterator;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.*;
import gnu.trove.set.hash.TIntHashSet;

import java.io.*;
import java.util.*;

public class MainClass
{	
	private static String inputTableName;
	private static String outputTableName;
	private static double minimumSupport;
	private static double scaleFactor;
	private static int timeCut;
	private static String[] attributes;
	private static Set<String> emptySet = new HashSet<String>();
	private static String problem;
	private static boolean showFrequentItemsets = false;
	private static boolean showFrequencyConstraints = false;
	
	private static Table buildTable()
	{		
		final Map<Integer,Column<Integer>> sv_columns = new TreeMap<Integer,Column<Integer>>();
		final Map<Integer,Column<TIntHashSet>> mv_columns = new TreeMap<Integer,Column<TIntHashSet>>();
		
		int cIndex = 0;
		
		for(int i=0; i<attributes.length-1; i++)
		{
			final String columnName = attributes[i];
			i++;
			final boolean isSingleValue = attributes[i].equals("sv") ? true : false;
			
			if(isSingleValue)
				sv_columns.put(cIndex, new Column<Integer>(new ArrayList<Integer>(), columnName));
			else
				mv_columns.put(cIndex, new Column<TIntHashSet>(new ArrayList<TIntHashSet>(), columnName));
			
			cIndex++;
		}
		
		try
		{
			final BufferedReader reader = new BufferedReader(new FileReader(inputTableName));
			final File table = new File("transactional_" + inputTableName);
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
		
		Table table = new Table(sv_list, mv_list, inputTableName, attributes);
		return table;
	}
	
	
	private static List<Itemset> computeFrequentItemsets(final double support, final String tableName)
	{
		final String[] parameters = {"transactional_" + tableName, support+""};//transactions file, support
		Apriori apriori = null;
	
		try
		{
			apriori = new Apriori(parameters, showFrequentItemsets);
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
				final TObjectIntHashMap<String> singleValueAttributeConstraint = new TObjectIntHashMap<String>();
				final Map<String,TIntHashSet> multiValueAttributeConstraint = new HashMap<String,TIntHashSet>();
				
				final List<Column<Integer>> sv_attributes = table.get_SV_attributes();
				final List<Column<TIntHashSet>> mv_attributes = table.get_MV_attributes();

				final TIntHashSet items = itemset.getElements();
				
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
		
		for(final Column<Integer> svAtt : sv_attributes)
			frontier.addAll(computeFrontierSvAttribute(frequentItemsets, table, s, svAtt));

		for(final Column<TIntHashSet> mvAtt : mv_attributes)
			frontier.addAll(computeFrontierMvAttribute(frequentItemsets, table, s, mvAtt));
		
		return frontier;
	}
	
	
	private static Set<TIntHashSet> computeFrontierMvAttribute(final List<Itemset> frequentItemsets, final Table table, final double s, final Column<TIntHashSet> mvAtt)
	{
		final Set<TIntHashSet> frontier = new HashSet<TIntHashSet>();

		for(final Itemset itemset : frequentItemsets)
		{
			if((double)itemset.getSupport()/(double)table.getSize() >= s)
			{
				final TIntHashSet setOfItems = itemset.getElements();
				
				final TIntHashSet domainMultiValueAttribute = new TIntHashSet(table.domainMultiValueAttribute(mvAtt.getName()));
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

		TIntHashSet singleton;
		final TIntIterator iterator = table.domainMultiValueAttribute(mvAtt.getName()).iterator();
		
		while(iterator.hasNext())
		{
			singleton = new TIntHashSet();
			singleton.add(iterator.next());
			frontier.add(singleton);
		}		
				
		Set<TIntHashSet> copyOfFrontier = new HashSet<TIntHashSet>(frontier);
		
		for(final TIntHashSet itemset : copyOfFrontier)
			for(final Itemset i : frequentItemsets)
				if((double)i.getSupport()/(double)table.getSize()>=s && itemset.equals(new TIntHashSet(i.getElements())))
				{
					frontier.remove(itemset);
					break;
				}
		
		TIntHashSet[] array = frontier.toArray(new TIntHashSet[0]);
		
		for(int i=0; i<array.length; i++)
		{
			boolean isMinimal = true;

			for(int j=0; j<array.length; j++)
				if(i != j)
				{
					if(ca(array[i],array[j]))
					{
						isMinimal = false;
						break;
					}
				}
			
			if(!isMinimal)
				frontier.remove(array[i]);
		}
		
		return frontier;
	} 
	
	private static Set<TIntHashSet> computeFrontierSvAttribute(final List<Itemset> frequentItemsets, final Table table, final double s, final Column<Integer> svAtt)
	{
		final Set<TIntHashSet> frontier = new HashSet<TIntHashSet>();
		
		for(final Itemset itemset : frequentItemsets)
		{
			if((double)itemset.getSupport()/(double)table.getSize() >= s)
			{
				final TIntHashSet setOfItems = itemset.getElements();	
				
				final TIntHashSet domainSingleValueAttribute = new TIntHashSet(table.domainSingleValueAttribute(svAtt.getName()));
				
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
		}

		TIntHashSet singleton;
		final TIntIterator iterator = table.domainSingleValueAttribute(svAtt.getName()).iterator();
		
		while(iterator.hasNext())
		{
			singleton = new TIntHashSet();
			singleton.add(iterator.next());
			frontier.add(singleton);
		}
		
		Set<TIntHashSet> copyOfFrontier = new HashSet<TIntHashSet>(frontier);
		
		for(final TIntHashSet itemset : copyOfFrontier)
			for(final Itemset i : frequentItemsets)
				if((double)i.getSupport()/(double)table.getSize()>=s && itemset.equals(new TIntHashSet(i.getElements())))
				{
					frontier.remove(itemset);
					break;
				}
		
		TIntHashSet[] array = frontier.toArray(new TIntHashSet[0]);
		
		for(int i=0; i<array.length; i++)
		{
			boolean isMinimal = true;

			for(int j=0; j<array.length; j++)
				if(i != j)
				{
					if(ca(array[i],array[j]))
					{
						isMinimal = false;
						break;
					}
				}
			
			if(!isMinimal)
				frontier.remove(array[i]);
		}
		
		return frontier;  
	}
	
	
	private static boolean ca(TIntHashSet s1, TIntHashSet s2)
	{
		if(s1.size() < s2.size())
			return false;
		else
			return s1.containsAll(s2);
	}
	
	
	private static List<Constraint> computeIC(final Set<TIntHashSet> frontier, final Table table, final double threshold, final double scale_factor)
	{
		final List<Constraint> infrequencyConstraints = new ArrayList<Constraint>();
		int icIndex = 0;
	
		for(final TIntHashSet minimalInfrequentItemset : frontier)
		{		
			final TObjectIntHashMap<String> singleValueAttributeConstraint = new TObjectIntHashMap<String>();
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


	private static void configure()
	{
		try
		{
			final BufferedReader reader = new BufferedReader(new FileReader("CONF"));
			
			String line;			

			while((line = reader.readLine()) != null)
			{				
				if(line.startsWith("#"))
				{
					StringTokenizer st = new StringTokenizer(line, ": ");

					final String parameter = st.nextToken();
					
					if(parameter.equals("#INPUT_TABLE_NAME"))
						inputTableName = st.nextToken();
					else
						if(parameter.equals("#INPUT_TABLE_ATTRIBUTES"))
						{
							final List<String> att = new ArrayList<String>();
							
							while(st.hasMoreTokens())
							{
								att.add(st.nextToken());
								att.add(st.nextToken());
							}
							
							attributes = att.toArray(new String[0]);
						}
						else
							if(parameter.equals("#EMPTY_SET"))
							{								
								while(st.hasMoreTokens())
									emptySet.add(st.nextToken());
							}
							else
								if(parameter.equals("#MINIMUM_SUPPORT"))
									minimumSupport = Double.parseDouble(st.nextToken());
								else
									if(parameter.equals("#OUTPUT_TABLE_NAME"))
										outputTableName = st.nextToken();
									else
										if(parameter.equals("#PROBLEM"))
											problem = st.nextToken();
										else
											if(parameter.equals("#SCALE_FACTOR"))
												scaleFactor = Double.parseDouble(st.nextToken());
											else
												if(parameter.equals("#TIME_CUT"))
													timeCut = Integer.parseInt(st.nextToken()) * 60 * 1000;
												else
													if(parameter.equals("#FREQUENT_ITEMSETS"))
													{
														if(st.nextToken().equals("yes"))
															showFrequentItemsets = true;
													}
													else
														if(parameter.equals("#FREQUENCY_CONSTRAINTS"))
														{
															if(st.nextToken().equals("yes"))
																showFrequencyConstraints = true;
														}
				}
			}
			
			reader.close();
		}
		catch (FileNotFoundException e)
		{
			e.printStackTrace();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}


	public static void main(String[] args) throws Exception
	{	
		configure();

		System.out.println("input table: "+inputTableName+"\n"+
						   "output table: "+outputTableName+"\n"+
						   "min support: "+minimumSupport+"\n"+
						   "scale factor: "+scaleFactor+"\n"+
						   "time cut: "+timeCut+"\n"+
						   "problem: "+problem);

		
		final Table table = buildTable();
		
		System.out.print("\nrunning APRIORI algorithm on '" + inputTableName + "' with suppport " + minimumSupport + "...");
		final List<Itemset> frequentItemsets = computeFrequentItemsets(minimumSupport, inputTableName);
		System.out.println(" done.");
		
		final List<Constraint> frequencyConstraints = computeFC(frequentItemsets, table, minimumSupport, scaleFactor);
		System.out.println("#frequency constraints: " + frequencyConstraints.size());

		if(frequencyConstraints.size() > 0)
		{
			List<Constraint> infrequencyConstraints = new ArrayList<Constraint>();
			
			if(problem.equals("IFM_I"))
			{
				final Set<TIntHashSet> frontier = computeFrontier(frequentItemsets, table, minimumSupport);
				infrequencyConstraints = computeIC(frontier, table, minimumSupport, scaleFactor);
				System.out.println("#infrequency constraints: " + infrequencyConstraints.size());
			}
			else
				infrequencyConstraints = new ArrayList<Constraint>();
			
			if(showFrequencyConstraints)
			{
				for(Constraint c : frequencyConstraints)
				System.out.println(c.toString());
			
				System.out.println();
				for(Constraint c : infrequencyConstraints)
					System.out.println(c.toString());
			}
			
			System.out.println("running ...");
			long start = System.currentTimeMillis();
			final Solver solver = new Solver(table, frequencyConstraints, infrequencyConstraints, scaleFactor, start, emptySet, outputTableName);
			solver.runProgram(timeCut);
			final long end = System.currentTimeMillis();
			
			System.out.println("done in " + (end-start) + " ms");
			
			final TObjectDoubleHashMap<TIntArrayList> outputTable = solver.getOutputTable();
			
			final File output = new File(outputTableName + "_" + minimumSupport);
			final FileOutputStream os2 = new FileOutputStream(output);
			final PrintStream ps2 = new PrintStream(os2);
			
			ps2.print(outputTable.toString());
			ps2.close();
			os2.close();
		}
	}

}
