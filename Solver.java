/**
 * @author Antonino Rullo, Domenico Saccà, University of Calabria, 2018.
 * @author Edoardo Serra, Boise State University, 2018
 * @copyright GNU General Public License v3
 * No reproduction in whole or part without maintaining this copyright notice
 * and imposing this condition on any subsequent users.
 */

import gnu.trove.iterator.TIntIterator;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.map.hash.TObjectDoubleHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import gnu.trove.set.hash.TIntHashSet;
import ilog.concert.*;
import ilog.cplex.IloCplex;
import ilog.cplex.IloCplex.UnknownObjectException;

import java.io.*;
import java.util.*;


public class Solver
{
	private final Table table;
	private final List<Constraint> frequencyConstraints;
	private final List<Constraint> infrequencyConstraints;
	private IloCplex cplex;
	private IloCplex cplexILP;
	private final Map<String,Constraint> fcConstraints = new HashMap<String,Constraint>();
	private final Map<String,Constraint> icConstraints = new HashMap<String,Constraint>();
	private final Map<String,IloRange> constraints6 = new HashMap<String,IloRange>();//frequency constraints
	private final Map<String,IloRange> constraints7 = new HashMap<String,IloRange>();//frequency constraints
	private final Map<String,IloRange> constraints8 = new HashMap<String,IloRange>();//infrequency constraints
	private final Map<String,IloRange> sizeConstraints = new HashMap<String,IloRange>();
	private final Map<String,TIntObjectHashMap<IloIntVar>> variables = new HashMap<String,TIntObjectHashMap<IloIntVar>>();//key: column name, value: mapping value-->CPLEX variable
	private final Map<IloNumVar,TIntArrayList> transactions = new HashMap<IloNumVar,TIntArrayList>();//key: x; value: itemsets => index=0 --> SV attribute, index>0 --> MV attribute
	private TIntObjectHashMap<TIntArrayList> lastTransactions;
	private int xIndex = 0;
	private IloLinearNumExpr reducedCosts;
	private final double scale_factor;
	private final long start;
	private TObjectDoubleHashMap<TIntArrayList> outputTable = new TObjectDoubleHashMap<TIntArrayList>();
	private IloObjective objectiveILP;
	private IloRange rc;
	private final Set<String> emptySet;
	private final String outputTableName;
	
	public Solver(
			final Table table,
			final List<Constraint> frequencyConstraints, 
			final List<Constraint> infrequencyConstraints,
			final double scaleFactor,
			final long start,
			final Set<String> emptySet,
			final String outputTable
		     )
	{
		this.table = table;
		this.frequencyConstraints = frequencyConstraints;
		this.infrequencyConstraints = infrequencyConstraints;
		scale_factor = scaleFactor;
		this.start = start;
		
		for(final Column<Integer> svColumn : table.get_SV_attributes())
			variables.put(svColumn.getName(), new TIntObjectHashMap<IloIntVar>());
		
		for(final Column<TIntHashSet> mvColumn : table.get_MV_attributes())
			variables.put(mvColumn.getName(), new TIntObjectHashMap<IloIntVar>());
		
		this.emptySet = new HashSet<String>(emptySet);
		
		outputTableName = outputTable;
		
		buildILP();
		buildLP();
	}
	
	
	public Table buildNewTable(final double support, final String[] args)
	{
		Table toReturn = null;
		
		final List<Column<Integer>> singleValueAttributes = new ArrayList<Column<Integer>>();
		final List<Column<TIntHashSet>> multiValueAttributes = new ArrayList<Column<TIntHashSet>>();
		
		for(final Column<Integer> svAttribute : table.get_SV_attributes())
			singleValueAttributes.add(new Column<Integer>(new ArrayList<Integer>(), svAttribute.getName()));
		
		for(final Column<TIntHashSet> mvAttribute : table.get_MV_attributes())
			multiValueAttributes.add(new Column<TIntHashSet>(new ArrayList<TIntHashSet>(), mvAttribute.getName()));
		
		try
		{			
			final File newTable = new File(outputTableName + "_" + support);
			final FileOutputStream os = new FileOutputStream(newTable);
			final PrintStream ps = new PrintStream(os);
			
			for(final IloNumVar x : transactions.keySet())
			{
				final double duplicates = cplex.getValue(x);
				
				if(duplicates > 0)
				{
					final TIntArrayList transaction = transactions.get(x);
					
					for(int i=0; i<Math.round(duplicates); i++)
					{	
						for(final Column<TIntHashSet> column : multiValueAttributes)
							column.getValues().add(new TIntHashSet());

						TIntIterator iterator = transaction.iterator();
						
						while(iterator.hasNext())
						{
							int value = iterator.next();							
							ps.print(value + " ");
							
							boolean isSV = false;
							
							for(final Column<Integer> svAttribute : table.get_SV_attributes())
								if(svAttribute.getValues().contains(value))
								{
									for(final Column<Integer> svAtt : singleValueAttributes)
										if(svAttribute.getName().equals(svAtt.getName()))
										{
											svAtt.getValues().add(value);
											break;
										}
									
									isSV = true;
									break;
								}
							
							if(!isSV)
								for(final Column<TIntHashSet> mvAttribute : table.get_MV_attributes())
									if(table.domainMultiValueAttribute(mvAttribute.getName()).contains(value))
									{
										for(final Column<TIntHashSet> mvAtt : multiValueAttributes)
											if(mvAtt.getName().equals(mvAttribute.getName()))
											{
												final List<TIntHashSet> values = mvAtt.getValues();
												values.get(values.size()-1).add(value);
													
												break;
											}
										
										isSV = true;
										break;
									}

						}
						
						ps.print("\n");
					}
				}
			}
			
			ps.close();
			os.close();

			toReturn = new Table(singleValueAttributes, multiValueAttributes, outputTableName + "_" + support, args);
			
			final File outputTable = new File(outputTableName + "_" + support);
			final FileOutputStream os2 = new FileOutputStream(outputTable);
			final PrintStream ps2 = new PrintStream(os2);
			
			ps2.print(toReturn.toString());
			ps2.close();
			os2.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		
		return toReturn;
	}
	
	public TObjectDoubleHashMap<TIntArrayList> getOutputTable()
	{
		return outputTable;
	}
	
	/**
	 * maps each value to a CPLEX variable and adds an ILP constraint for each attribute to avoid null values in the synthetic table
	 */
	private <T> void mappingValues(final List<Column<T>> columns, final boolean isSingleValue)
	{
		try
		{
			for(final Column<T> column : columns)
			{
				final String columnName = column.getName();
				
				TIntHashSet attributeDomain;
				
				if(isSingleValue)
					attributeDomain = table.domainSingleValueAttribute(columnName);
				else
					attributeDomain = table.domainMultiValueAttribute(columnName);
				
				final IloLinearIntExpr constraint_on_attribute = cplexILP.linearIntExpr();
				
				final TIntIterator iterator = attributeDomain.iterator();
				
				while(iterator.hasNext())
				{
					final int i = iterator.next();
					final IloIntVar var = cplexILP.intVar(0, 1);
					var.setName(columnName + i);
					variables.get(columnName).put(i, var);
					constraint_on_attribute.addTerm(1, var);
				}
				
				if(isSingleValue)
				{
					IloRange addEq = cplexILP.addEq(constraint_on_attribute, 1);//sr1 + sr2 + sr3 = 1
					addEq.setName(columnName);
				}
				else
				{
					if(!emptySet.contains(columnName))
					{
						IloRange addGe = cplexILP.addGe(constraint_on_attribute, 1);//r1 + r2 + r3 >= 1
						addGe.setName(columnName);
					}
					else
					{
						IloRange addGe = cplexILP.addGe(constraint_on_attribute, 0);//r1 + r2 + r3 >= 0
						addGe.setName(columnName);
					}
				}
			}
		}
		catch (IloException e)
		{
			e.printStackTrace();
		}
	}
	
	/**
	 * maps each frequency and infrequency constraint to a CPLEX variable and adds ILP constraints
	 * @param constraints
	 */
	private void mappingConstraints(final List<Constraint> constraints, final IloLinearNumExpr objective)
	{
		try
		{
			for(final Constraint c : constraints)
			{
				final IloIntVar constraintVar = cplexILP.intVar(0, 1);
				
				if(c.getName().startsWith("f"))
				{
					constraintVar.setName("f_" + c.getName());
					fcConstraints.put("f_" + c.getName(), c);
				}
				else
				{
					constraintVar.setName("i_" + c.getName());
					icConstraints.put("i_" + c.getName(), c);
				}
				
				reducedCosts.addTerm(1, constraintVar);
				objective.addTerm(1, constraintVar);
				
				int counter = 0;
				final IloLinearIntExpr linearIntExpr = cplexILP.linearIntExpr();
				final TObjectIntHashMap<String> singleValueAttributeConstraints = c.getSingleValueSttributeConstraints();
				final Map<String,TIntHashSet> multiValueAttributeConstraints = c.getMultiValueSttributeConstraints();
				
				for(final String columnName : singleValueAttributeConstraints.keySet())
				{
					final int value = singleValueAttributeConstraints.get(columnName);
					final IloIntVar var = variables.get(columnName).get(value);
					counter++;
					linearIntExpr.addTerm(1, var);
					
					final IloLinearIntExpr expr = cplexILP.linearIntExpr();
					expr.addTerm(1, var);
					expr.addTerm(-1, constraintVar);
					IloRange addGe = cplexILP.addGe(expr, 0);
					addGe.setName(constraintVar.getName()+"_"+columnName);//sr1 - c1 >= 0
				}
				
				for(final String columnName : multiValueAttributeConstraints.keySet())
				{
					final TIntHashSet values = multiValueAttributeConstraints.get(columnName);					
					final TIntIterator iterator = values.iterator();
					
					while(iterator.hasNext())
					{
						final IloIntVar var = variables.get(columnName).get(iterator.next());
						counter++;
						linearIntExpr.addTerm(1, var);
						
						final IloLinearIntExpr expr = cplexILP.linearIntExpr();
						expr.addTerm(1, var);
						expr.addTerm(-1, constraintVar);
						IloRange addGe = cplexILP.addGe(expr, 0);
						addGe.setName(constraintVar.getName()+"_"+columnName);//ct1 - c1 >= 0
					}
				}
				
				linearIntExpr.addTerm(-1, constraintVar);
				int constant = counter - 1;
				
				IloRange addLe = cplexILP.addLe(linearIntExpr, constant);
				addLe.setName(constraintVar.getName());//sr1 + st1 + ct1 + ct2 + r1 + r2 - c1 <= 5
			}
		}
		catch (IloException e)
		{
			e.printStackTrace();
		}
	}
	
	
	/**
	 * example: 
	 * 
	 * attribute     STARS (SV)  | STATES (SV) |   CATEGORIES (MV)  |    REVIEWERS (MV)
	 * domain          {1,2,3}   |   {4,5,6}   |       {7,8,9}      |      {10,11,12}
	 *  
	 * frequency constraint fc1: [STARS=1, STATES=4, CATEGORIES>={7,8}, REVIEWERS>={10,11}]
	 * 
	 * variables: c1-->fc1
	 * 
	 * 	      sr1-->1
	 *            sr2-->2
	 *            sr3-->3
	 *            
	 *            st1-->4
	 *            st2-->5
	 *            st3-->6
	 *            
	 *            ct1-->7
	 *            ct2-->8
	 *            ct3-->9
	 *            
	 *            r1-->10
	 *            r2-->11
	 *            r3-->12
	 *            
	 * constraint on constraints:	fc1 >= 0.01
	 * constraint on STARS:  	sr1 + sr2 + sr3 = 1
	 * constraint on STATES: 	st1 + st2 + st3 = 1
	 * constaint on CATS: 		ct1 + ct2 + ct3 >= 1
	 * constraint on REVS: 		r1 + r2 + r3 >= 1
	 * 
	 * ILP constraint for fc1:
	 * 							sr1 + st1 + ct1 + ct2 + r1 + r2 - c1 <= 5
	 * 							sr1 - c1 >= 0
	 * 							st1 - c1 >= 0
	 * 							ct1 - c1 >= 0
	 * 							ct2 - c1 >= 0
	 * 							r1 - c1 >= 0
	 * 							r2 - c1 >= 0
	 */
	private void buildILP()
	{
		try
		{
			cplexILP = new IloCplex();
			cplexILP.setParam(IloCplex.IntParam.SolnPoolIntensity, 4);
			cplexILP.setParam(IloCplex.IntParam.SolnPoolCapacity, 30);
			cplexILP.setOut(null);
			cplexILP.setWarning(null);	
			
			reducedCosts = cplexILP.linearNumExpr();
			IloLinearNumExpr objective = cplexILP.linearNumExpr(); 
			
			mappingValues(table.get_SV_attributes(), true);
			mappingValues(table.get_MV_attributes(), false);
			
			mappingConstraints(frequencyConstraints, objetive);
			mappingConstraints(infrequencyConstraints, objetive);
			
			objectiveILP = cplexILP.addMaximize(reducedCosts);
			rc = cplexILP.addGe(reducedCosts, 0.01);
		}
		catch (IloException e)
		{
			e.printStackTrace();
		}
	}

	
	private void buildLP()
	{
		try
		{
			final Map<String,IloLinearNumExpr> fc6Expressions = new HashMap<String,IloLinearNumExpr>();//key: constraint name, value: expression
			final Map<String,IloLinearNumExpr> fc7Expressions = new HashMap<String,IloLinearNumExpr>();
			final Map<String,IloLinearNumExpr> icExpressions = new HashMap<String,IloLinearNumExpr>();
			
			Set<String> nonSatisfiedConstraints = new HashSet<String>();
			
			cplex = new IloCplex();
			cplex.setOut(null);
			cplex.setWarning(null);
			
			final IloLinearNumExpr objective = cplex.linearNumExpr();
			IloLinearNumExpr numExpr6;
			IloLinearNumExpr numExpr7;
			
			for(final Constraint fc : frequencyConstraints)
			{				
				String fcName = fc.getName();
				nonSatisfiedConstraints.add("f_" + fcName);
				
				final IloNumVar w = cplex.numVar(0, Double.POSITIVE_INFINITY);
				w.setName("w_" + fcName);
				
				objective.addTerm(1, w);
				numExpr6 = cplex.linearNumExpr();
				numExpr6.addTerm(1, w);
				
				final IloNumVar w2 = cplex.numVar(0, Double.POSITIVE_INFINITY);
				w2.setName("w2_" + fcName);
				
				objective.addTerm(1, w2);
				numExpr7 = cplex.linearNumExpr();
				numExpr7.addTerm(-1, w2);
				
				fc6Expressions.put("f_" + fcName, numExpr6);
				fc7Expressions.put("f_" + fcName, numExpr7);
			}
			
			for(final Constraint ic : infrequencyConstraints)
			{
				nonSatisfiedConstraints.add("i_" + ic.getName());
				
				icExpressions.put("i_" + ic.getName(), cplex.linearNumExpr());
			}
			
			final IloNumVar w0 = cplex.numVar(0, Double.POSITIVE_INFINITY);
			w0.setName("w0");
			
			objective.addTerm(1, w0);
			IloObjective addMinimize = cplex.addMinimize(objective);
			
			final IloLinearNumExpr numExpr10 = cplex.linearNumExpr();
			numExpr10.addTerm(1, w0);
			
			final IloLinearNumExpr numExpr11 = cplex.linearNumExpr();
			
			final Set<String> constraintsVariables = new HashSet<String>();
			 
			while(nonSatisfiedConstraints.size() > 0)
			{
				runILP(null, null, null, nonSatisfiedConstraints);
				
				final int solnPoolNsolns = cplexILP.getSolnPoolNsolns();
				
				for(int t=0; t<solnPoolNsolns; t++)
				{
					final IloLinearNumExprIterator iterator = reducedCosts.linearIterator();
					
					IloNumVar var;
					String varName;
					
					final IloNumVar x = cplex.numVar(0, Double.POSITIVE_INFINITY);
					x.setName("x" + xIndex);
					xIndex++;
					
					while(iterator.hasNext())
					{
						var = iterator.nextNumVar();
						varName = var.getName();
						
						if(cplexILP.getValue(var,t) > 0)
						{
							nonSatisfiedConstraints.remove(varName);
							constraintsVariables.add(varName);
							
							if(varName.startsWith("f"))
							{
								fc6Expressions.get(varName).addTerm(1, x);//variables and constraints have the same name
								fc7Expressions.get(varName).addTerm(1, x);
							}
							else
								if(varName.startsWith("i"))
									icExpressions.get(varName).addTerm(1, x);
						}
					}

					numExpr10.addTerm(1, x);
					numExpr11.addTerm(1, x);
					
					transactions.put(x, lastTransactions.get(t));
				}
			}
			
			for(final String v : constraintsVariables)
			{
				if(v.startsWith("f"))
				{
					final IloRange c6 = cplex.addGe(fc6Expressions.get(v), (double)fcConstraints.get(v).getLowerBound());
					c6.setName("C6"+v);
					constraints6.put(v, c6);//C6
					
					final IloRange c7 = cplex.addLe(fc7Expressions.get(v), (double)fcConstraints.get(v).getUpperBound());
					c7.setName("C7"+v);
					constraints7.put(v, c7);//C7
				}
				else
					if(v.startsWith("i"))
					{
						final IloRange c8 = cplex.addLe(icExpressions.get(v), (double)icConstraints.get(v).getUpperBound());//C8
						c8.setName("C8"+v);
						constraints8.put(v, c8);
					}
			}
			
			final IloRange c10 = cplex.addGe(numExpr10, table.getSize()*scale_factor);
			c10.setName("C10");
			sizeConstraints.put("C10", c10);//C10
			
			final IloRange c11 = cplex.addLe(numExpr11, table.getSize()*scale_factor);
			c11.setName("C11");
			sizeConstraints.put("C11", c11);//C11
		} 
		catch (IloException e)
		{
			e.printStackTrace();
		}
	}
	

	public void runProgram(final int timeCut)
	{
		try 
		{
			cplex.solve();
			System.out.println("obj: "+cplex.getObjValue());
			boolean timeIsOver = false;
			
			while(runILP(constraints6, constraints7, constraints8, null)>0 && !timeIsOver)
			{
				final double d = cplex.getDual(sizeConstraints.get("C10")) + cplex.getDual(sizeConstraints.get("C11"));
				
				for(int t=0; t<cplexILP.getSolnPoolNsolns(); t++)
				{
					if(cplexILP.getValue(reducedCosts, t) + d > 0)
					{
						final IloLinearNumExprIterator iterator = reducedCosts.linearIterator();

						IloNumVar var = iterator.nextNumVar();
						String varName = var.getName();
						double value = cplexILP.getValue(var,t);
						IloColumn column  = null;
						
						while(value==0 && iterator.hasNext())
						{
							var = iterator.nextNumVar();
							value = cplexILP.getValue(var,t);
							varName = var.getName();
						}

						if(value > 0)
						{
							if(varName.startsWith("f"))//frequency constraints
								column = cplex.column(constraints6.get(varName), 1).and(cplex.column(constraints7.get(varName), 1));
							else
								if(varName.startsWith("i"))//infrequency constraints
									column = cplex.column(constraints8.get(varName), 1);
						}
						
						while(iterator.hasNext())
						{
							var = iterator.nextNumVar();
							value = cplexILP.getValue(var,t);

							if(value > 0)
							{	
								varName = var.getName();
								
								if(varName.startsWith("f"))//frequency constraints
									column = column.and(cplex.column(constraints6.get(varName), 1)).and(cplex.column(constraints7.get(varName), 1));
								else
									if(varName.startsWith("i"))//infrequency constraints	
										column = column.and(cplex.column(constraints8.get(varName), 1));
							}
						}
						
						if(column != null)
							column = column.and(cplex.column(sizeConstraints.get("C10"), 1)).and(cplex.column(sizeConstraints.get("C11"), 1));//C10 and C11	
						else
							column = cplex.column(sizeConstraints.get("C10"), 1).and(cplex.column(sizeConstraints.get("C11"), 1));//C10 and C11;

						final IloNumVar x = cplex.numVar(column, 0, Double.POSITIVE_INFINITY);
						x.setName("x" + xIndex);
						xIndex++;

						transactions.put(x, lastTransactions.get(t));
					}
				}
				
				cplex.solve();
				System.out.println("obj: "+cplex.getObjValue());

				final long currentTimeMillis = System.currentTimeMillis();
				final long pastTime = currentTimeMillis - start;
				timeIsOver = timeCut == 0 ? false : pastTime >= timeCut ? true : false;
				
				System.gc();
			}	
			
			double rows = 0;
			
			for(final IloNumVar x : transactions.keySet())
			{
				final double duplicates = cplex.getValue(x);
				
				if(duplicates > 0.0)
				{
					System.out.println(transactions.get(x).toString() + "\t\t\t--\t" + duplicates);
					rows += duplicates;
					
					outputTable.put(transactions.get(x), duplicates);
				}
			}
			
			System.out.println("\ntime cut: " + timeIsOver);
		}
		catch (IloException e)
		{
			e.printStackTrace();
		}
	}
	
	
	private double runILP(
				final Map<String,IloRange> c6, 
				final Map<String,IloRange> c7, 
				final Map<String,IloRange> c8, 
				final Set<String> nonSatisfiedConstraints
			     )
	{
		try
		{
			cplexILP.remove(rc);
			
			final IloLinearNumExprIterator iterator = reducedCosts.linearIterator();
			
			if(c6!=null && c7!=null && c8!=null)
			{
				while(iterator.hasNext())
				{
					final IloNumVar nextNumVar = iterator.nextNumVar();
					final String name = nextNumVar.getName();
										
					if(name.startsWith("f"))
					{
						double dual1 = cplex.getDual(constraints6.get(name));
						double dual2 = cplex.getDual(constraints7.get(name));
						iterator.setValue(dual1 + dual2);
						cplexILP.setLinearCoef(objectiveILP, dual1 + dual2, nextNumVar);
					}
					else
						if(name.startsWith("i"))
						{
							iterator.setValue(cplex.getDual(constraints8.get(name)));
							cplexILP.setLinearCoef(objectiveILP, cplex.getDual(constraints8.get(name)), nextNumVar);
						}
				}
				
				final double sum = cplex.getDual(sizeConstraints.get("C10")) + cplex.getDual(sizeConstraints.get("C11"));
				final double d = 0.0001-sum;
				rc = cplexILP.addGe(reducedCosts, d);
			}
			else
			{				
				while(iterator.hasNext())
				{
					final IloNumVar v = iterator.nextNumVar();
					final String name = v.getName();
					
					if(nonSatisfiedConstraints.contains(name))
					{
						iterator.setValue(1.0);
						cplexILP.setLinearCoef(objectiveILP, 1.0, v);
					}
					else
					{
						iterator.setValue(0.0);
						cplexILP.setLinearCoef(objectiveILP, 0.0, v);
					}
				}
				
				rc = cplexILP.addGe(reducedCosts, 0.01);
			}
			
			cplexILP.populate();
			
			getTransactions();
			
			final int transactions = cplexILP.getSolnPoolNsolns();

			if(transactions > 0)
				return 1;
		}
		catch (IloException e)
		{
			e.printStackTrace();
		}
		
		return 0;
	}


	private void getTransactions()
	{
		lastTransactions = new TIntObjectHashMap<TIntArrayList>();
		
		try
		{
			final int transactions = cplexILP.getSolnPoolNsolns();

			for(int t=0; t<transactions; t++)
			{
				final TIntArrayList transaction = new TIntArrayList();
				
				for(final String columnName : variables.keySet())
				{
					final TIntIterator iterator = variables.get(columnName).keySet().iterator();
					
					while(iterator.hasNext())
					{
						final int i = iterator.next();						
						final IloIntVar var = variables.get(columnName).get(i);
						
						if(cplexILP.getValue(var,t) > 0)
							transaction.add(i);
					}
				}
				
				lastTransactions.put(t, transaction);
			}
		}
		catch (UnknownObjectException e)
		{
			e.printStackTrace();
		}
		catch (IloException e)
		{
			e.printStackTrace();
		}
	}	

}
