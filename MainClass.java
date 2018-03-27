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
	final static File output = new File("output");
	static FileOutputStream os;
	static PrintStream ps;
	
	
	private static Table buildTable(final String[] args)
	{		
		final Map<Integer,Column<Integer>> sv_columns = new TreeMap<Integer,Column<Integer>>();
		final Map<Integer,Column<TIntHashSet>> mv_columns = new TreeMap<Integer,Column<TIntHashSet>>();
		
		int cIndex = 0;
		
		for(int i=1; i<args.length-1; i++)
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
	

	private static List<Constraint> computeFC(final List<Itemset> frequentItemsets, final Table table, final double s)
	{
		final List<Constraint> frequencyConstraints = new ArrayList<Constraint>();
		int fcIndex = 0;
		
		for(final Itemset itemset : frequentItemsets)
		{
			final double support = (double)itemset.getSupport()/(double)table.getSize();
			
			if(support >= s)
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
			
				frequencyConstraints.add(new Constraint("fc"+fcIndex, (int)(itemset.getSupport()), (int)(itemset.getSupport()), singleValueAttributeConstraint, multiValueAttributeConstraint));
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
	
	
//	private static List<Constraint> computeIC(final Set<Set<Integer>> frontier, final Table table, final double s)
//	{
//		final List<Constraint> infrequencyConstraints = new ArrayList<Constraint>();
//		int icIndex = 0;
//		
//		final Set<Integer> domainStarsAttribute = new HashSet<Integer>(table.domainStarsAttribute());
//		final Set<Integer> domainStatesAttribute = new HashSet<Integer>(table.domainStatesAttribute());
//		final Set<Integer> domainCategoriesAttribute = new HashSet<Integer>(table.domainCategoriesAttribute());
//			
//		for(final Set<Integer> minimalInfrequentItemset : frontier)
//		{		
//			final Map<String,Integer> vincoliStarsAtt = new HashMap<String,Integer>();
//			final Map<String,Integer> vincoliStatesAtt = new HashMap<String,Integer>();
//			final Map<String,Set<Integer>> vincoliReviewersAtt = new HashMap<String,Set<Integer>>();
//			final Map<String,Set<Integer>> vincoliCategoriesAtt = new HashMap<String,Set<Integer>>();
//			
//			final Set<Integer> reviewers = new HashSet<Integer>();
//			final Set<Integer> categories = new HashSet<Integer>();
//			
//			for(final int i : minimalInfrequentItemset)
//			{
//				if(domainStarsAttribute.contains(i))
//					vincoliStarsAtt.put(table.getStarsAttribute().getName(), i);
//				else
//					if(domainStatesAttribute.contains(i))
//						vincoliStatesAtt.put(table.getStatesAttribute().getName(), i);
//					else 
//						if(domainCategoriesAttribute.contains(i))
//							categories.add(i);
//						else
//							reviewers.add(i);
//			}
//
//			if(categories.size() > 0)
//				vincoliCategoriesAtt.put(table.getCategoriesAttribute().getName(), categories);
//			
//			if(reviewers.size() > 0)
//				vincoliReviewersAtt.put(table.getReviewersAttribute().getName(), reviewers);
//			
////			final int upperBound = (int)(((int)(s*table.getSize())) - 1 - 0.04 * table.getSize());
//			final int upperBound = (int)(((int)(s*table.getSize())) - 1);
//			
//			infrequencyConstraints.add(new Constraint("ic"+icIndex, upperBound, 0, vincoliStarsAtt, vincoliStatesAtt, vincoliCategoriesAtt, vincoliReviewersAtt));
//			icIndex++;
//		}
//		
//		return infrequencyConstraints;
//	}
	
	
	private static List<Constraint> computeIC(final Set<TIntHashSet> frontier, final Table table, final double s)
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
			
			int upperBound = (int)(((int)(s*table.getSize())) - 1);
			
			if(upperBound < 0)
				upperBound = 0;
			
			infrequencyConstraints.add(new Constraint("ic"+icIndex, upperBound, 0, singleValueAttributeConstraint, multiValueAttributeConstraint));
			icIndex++;
		}
		
		return infrequencyConstraints;
	}


	private static double compare(final List<Itemset> frequentItemsets, final double support, final Table table, final Table newTable) throws Exception
	{
		final String[] parameters = {"new_" + table.getName() + "_" + support, support+""};
		
		final Apriori apriori = new Apriori(parameters);
		final List<Itemset> newFrequentItemsets = apriori.getItemsets();
		
		int counter = 0;
		double sum = 0;
		
		for(final Itemset i : frequentItemsets)
		{
			int support_D = i.getSupport();
			int support_Dp;
			
			if((double)support_D/(double)table.getSize() >= support)
			{
				boolean inCommon = false;

				for(final Itemset j : newFrequentItemsets)
					if(i.getSetOfItems().equals(j.getSetOfItems()))
					{	
						support_Dp = j.getSupport();
						
						int min = Math.min(support_D, support_Dp);
						int max = Math.max(support_D, support_Dp);
						
						sum += (double)min/(double)max;
						inCommon = true;
						counter++;
						
						newFrequentItemsets.remove(newFrequentItemsets.indexOf(j));
						
						break;
					}
				
				if(!inCommon)
				{
					support_Dp = computeSupport(i.getSetOfItems(), newTable);
					
					int min = Math.min(support_D, support_Dp);
					int max = Math.max(support_D, support_Dp);
					
					sum += (double)min/(double)max;
					counter++;
				}
			}
		}
		
		for(final Itemset i : newFrequentItemsets)
		{
			int support_D = computeSupport(i.getSetOfItems(), table);
			int support_Dp = i.getSupport();
			
			int min = Math.min(support_D, support_Dp);
			int max = Math.max(support_D, support_Dp);
			
			sum += (double)min/(double)max;
			counter++;
		}

		final double accuracy = 1.0/counter * sum;
		
		return accuracy;
	}
	
	
	private static int computeSupport(final TIntHashSet setOfItems, final Table table)
	{
		int support = 0;
		TIntHashSet transaction;
		
		for(int i=0; i<table.getSize(); i++)
		{
			transaction = new TIntHashSet();
			
			for(final Column<Integer> svColumn : table.get_SV_attributes())
				transaction.add(svColumn.getValue(i));
			
			for(final Column<TIntHashSet> mvColumn : table.get_MV_attributes())
				transaction.addAll(mvColumn.getValue(i));
			
			if(transaction.containsAll(setOfItems))
				support++;
		}
		
		return support;
	}


	public static void main(String[] args) throws Exception
	{		
//		Test test = new Test(25000, 8);
//		Table table = test.getTable();
//		Test.transactionalTable(table);
		
		os = new FileOutputStream(output);
		ps = new PrintStream(os);
		
		final Table table = buildTable(args);
		
//		System.out.println("\nrunning APRIORI...");
		
		ps.print("TABLE ROWS: " + table.getSize() + "\n");
		
		final List<Itemset> frequentItemsets = computeFrequentItemsets(0.1, args[0]);

//		final double[] thresholds = {0.01, 0.02, 0.03, 0.04, 0.05, 0.06, 0.07, 0.08, 0.09, 0.1};
		final double[] thresholds = {0.1,0.1,0.1,0.1};
		
		for(int i=0; i<thresholds.length; i++)
		{			
			final List<Constraint> frequencyConstraints = computeFC(frequentItemsets, table, thresholds[i]);

			if(frequencyConstraints.size() > 0)
			{
				final Set<TIntHashSet> frontier = computeFrontier(frequentItemsets, table, thresholds[i]);
				List<Constraint> infrequencyConstraints = computeIC(frontier, table, thresholds[i]);
				
				System.out.println("\ns = " + thresholds[i] + ",  m = " + frequencyConstraints.size() + ",  m' = " + infrequencyConstraints.size());
				ps.print("\ns = " + thresholds[i] + ",  m = " + frequencyConstraints.size() + ",  m' = " + infrequencyConstraints.size() + "\n");

//				for(Constraint c : frequencyConstraints)
//					System.out.println(c.toString());
//				
//				System.out.println();
//				for(Constraint c : infrequencyConstraints)
//					System.out.println(c.toString());
							
				long solverStart = System.currentTimeMillis();
				Solver solver = new Solver(table, frequencyConstraints, infrequencyConstraints);
				solver.runProgram();
				long solverEnd = System.currentTimeMillis();
				
				System.out.println("done in " + (solverEnd-solverStart) + " ms");
				ps.print("done in " + (solverEnd-solverStart) + " ms\n");
				
				final Table newTable = solver.buildNewTable(thresholds[i], args);
				double accuracy = compare(frequentItemsets, thresholds[i], table, newTable);
				
				System.out.println("accuracy " + accuracy);
				ps.print("accuracy " + accuracy + "\n");
				
				System.out.println("----------------------------------------------------");
				ps.print("----------------------------------------------------\n");
			}
		}
		
		ps.close();
		os.close();
	}

}
