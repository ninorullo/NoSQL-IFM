/**
 * @author Antonino Rullo, Domenico Saccà, University of Calabria, 2018.
 * @author Edoardo Serra, Boise State University, 2018
 * @copyright GNU General Public License v3
 * No reproduction in whole or part without maintaining this copyright notice
 * and imposing this condition on any subsequent users.
 */

import gnu.trove.iterator.TIntIterator;
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
	private Map<String,Constraint> fcConstraints = new HashMap<String,Constraint>();
	private Map<String,Constraint> icConstraints = new HashMap<String,Constraint>();
	private Map<String,IloRange> constraints6 = new HashMap<String,IloRange>();//frequency constraints
	private Map<String,IloRange> constraints7 = new HashMap<String,IloRange>();//frequency constraints
	private Map<String,IloRange> constraints8 = new HashMap<String,IloRange>();//infrequency constraints
	private Map<String,IloRange> sizeConstraints = new HashMap<String,IloRange>();
	private Map<String,Map<Integer,IloIntVar>> variables = new HashMap<String,Map<Integer,IloIntVar>>();//key: column name, value: mapping value-->CPLEX variable
	private Map<IloNumVar,List<Integer>> transactions = new HashMap<IloNumVar,List<Integer>>();//key: x; value: itemsets => index=0 --> SV attribute, index>0 --> MV attribute
	private Set<List<IloIntVar>> lastTransactionsVars = new HashSet<List<IloIntVar>>();
	private Map<Integer,List<Integer>> lastTransactions;
	private int xIndex = 0;
	private IloLinearNumExpr constraintOnConstraints;
	
	public Solver(
					final Table table,
					final List<Constraint> frequencyConstraints, 
					final List<Constraint> infrequencyConstraints
				  )
	{
		this.table = table;
		this.frequencyConstraints = frequencyConstraints;
		this.infrequencyConstraints = infrequencyConstraints;
		
		for(final Column<Integer> svColumn : table.get_SV_attributes())
			variables.put(svColumn.getName(), new HashMap<Integer,IloIntVar>());
		
		for(final Column<TIntHashSet> mvColumn : table.get_MV_attributes())
			variables.put(mvColumn.getName(), new HashMap<Integer,IloIntVar>());
		
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
			final File newTable = new File("new_" + table.getName() + "_" + support);
			final FileOutputStream os = new FileOutputStream(newTable);
			final PrintStream ps = new PrintStream(os);
			
			for(final IloNumVar x : transactions.keySet())
			{
				final double duplicates = cplex.getValue(x);
				
				if(duplicates > 0)
				{
					final List<Integer> transaction = transactions.get(x);
					
					for(int i=0; i<(int)duplicates; i++)
					{	
						for(final Column<TIntHashSet> column : multiValueAttributes)
							column.getValues().add(new TIntHashSet());

						for(final int value : transaction)
						{
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

			toReturn = new Table(singleValueAttributes, multiValueAttributes, "new_" + table.getName() + "_" + support, args);
			
			final File outputTable = new File("outputTable" + "_" + support);
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
	
	/**
	 * maps each value to a CPLEX variable and adds an ILP constraint for each attribute to avoid null values in the synthetic table
	 */
	private <T> void mappingValues(final List<Column<T>> columns, final boolean isSingleValue)
	{
		try
		{
			for(final Column<T> sv_column : columns)
			{
				final String columnName = sv_column.getName();
				
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
					cplexILP.addEq(constraint_on_attribute, 1).setName(columnName);//sr1 + sr2 + sr3 = 1
				else
					cplexILP.addGe(constraint_on_attribute, 1).setName(columnName);//sr1 + sr2 + sr3 >= 1
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
	private void mappingConstraints(final List<Constraint> constraints)
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
				
				constraintOnConstraints.addTerm(1, constraintVar);
				
				int counter = 0;
				final IloLinearIntExpr linearIntExpr = cplexILP.linearIntExpr();
				final Map<String,Integer> singleValueSttributeConstraints = c.getSingleValueSttributeConstraints();
				final Map<String,TIntHashSet> multiValueSttributeConstraints = c.getMultiValueSttributeConstraints();
				
				for(final String columnName : singleValueSttributeConstraints.keySet())
				{
					final int value = singleValueSttributeConstraints.get(columnName);
					final IloIntVar var = variables.get(columnName).get(value);
					counter++;
					linearIntExpr.addTerm(1, var);
					
					final IloLinearIntExpr expr = cplexILP.linearIntExpr();
					expr.addTerm(1, var);
					expr.addTerm(-1, constraintVar);
					cplexILP.addGe(expr, 0).setName(constraintVar.getName()+"_"+columnName);//sr1 - c1 >= 0
				}
				
				for(final String columnName : multiValueSttributeConstraints.keySet())
				{
					final TIntHashSet values = multiValueSttributeConstraints.get(columnName);
					
					final TIntIterator iterator = values.iterator();
					
					while(iterator.hasNext())
					{
						final IloIntVar var = variables.get(columnName).get(iterator.next());
						counter++;
						linearIntExpr.addTerm(1, var);
						
						final IloLinearIntExpr expr = cplexILP.linearIntExpr();
						expr.addTerm(1, var);
						expr.addTerm(-1, constraintVar);
						cplexILP.addGe(expr, 0).setName(constraintVar.getName()+"_"+columnName);//ct1 - c1 >= 0
					}
				}
				
				linearIntExpr.addTerm(-1, constraintVar);
				int constant = counter - 1;
				
				cplexILP.addLe(linearIntExpr, constant).setName(constraintVar.getName());//sr1 + st1 + ct1 + ct2 + r1 + r2 - c1 <= 5
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
	 * attribute     STARS (SV)  | STATES (SV) |   CATS (MV)  |    REVS (MV)
	 * domain          {1,2,3}   |   {4,5,6}   |    {7,8,9}   |   {10,11,12}
	 *  
	 * frequency constraint fc1: [STARS=1, STATES=4, CATS>={7,8}, REVS>={10,11}]
	 * 
	 * variables: c1-->fc1
	 * 
	 * 			  sr1-->1
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
	 * constraint on constraints:	fc1 >= 0.00001
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
			cplexILP.setParam(IloCplex.IntParam.SolnPoolIntensity, 1);
			cplexILP.setParam(IloCplex.DoubleParam.TimeLimit,10*60);
			
			constraintOnConstraints = cplexILP.linearNumExpr();
			
			mappingValues(table.get_SV_attributes(), true);
			mappingValues(table.get_MV_attributes(), false);
			
			mappingConstraints(frequencyConstraints);
			mappingConstraints(infrequencyConstraints);
			
			cplexILP.addGe(constraintOnConstraints, 0.00001).setName("CoC");
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
				
				numExpr7 = cplex.linearNumExpr();
				
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
			cplex.addMinimize(objective);
			
			final IloLinearNumExpr numExpr10 = cplex.linearNumExpr();
			numExpr10.addTerm(1, w0);
			
			final IloLinearNumExpr numExpr11 = cplex.linearNumExpr();
			
			final Set<String> constraintsVariables = new HashSet<String>();
			 
			while(nonSatisfiedConstraints.size() > 0)
			{
				runILP(null, null, null, lastTransactionsVars, nonSatisfiedConstraints);
				
				lastTransactionsVars = new HashSet<List<IloIntVar>>();
				final int solnPoolNsolns = cplexILP.getSolnPoolNsolns();
				
				for(int t=0; t<solnPoolNsolns && nonSatisfiedConstraints.size()>0; t++)
				{
					final IloLinearNumExprIterator iterator = constraintOnConstraints.linearIterator();
					
					IloNumVar var;
					String varName;
					
					final IloNumVar x = cplex.numVar(0, Double.POSITIVE_INFINITY);
					x.setName("x" + xIndex);
					xIndex++;
										
					while(iterator.hasNext() && nonSatisfiedConstraints.size()>0)
					{
						var = iterator.nextNumVar();
						varName = var.getName();
						
						if(cplexILP.getValue(var,t) > 0)
							if(nonSatisfiedConstraints.remove(varName))
							{
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
					
					final List<Integer> one_Transaction = lastTransactions.get(t);
					transactions.put(x, one_Transaction);
					
					final List<IloIntVar> transVars = new ArrayList<IloIntVar>();
					
					for(final int i : one_Transaction)
					{
						for(final String columnName : variables.keySet())
							if(variables.get(columnName).containsKey(i))
							{
								transVars.add(variables.get(columnName).get(i));
								break;
							}
					}
					
					lastTransactionsVars.add(transVars);
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
			
			final IloRange c10 = cplex.addGe(numExpr10, table.getSize());
			c10.setName("C10");
			sizeConstraints.put("C10", c10);//C10
			
			final IloRange c11 = cplex.addLe(numExpr11, table.getSize());
			c11.setName("C11");
			sizeConstraints.put("C11", c11);//C11
		} 
		catch (IloException e)
		{
			e.printStackTrace();
		}
	}
	

	public void runProgram()
	{
		try 
		{
			cplex.solve();

			if(runILP(constraints6, constraints7, constraints8, lastTransactionsVars, null) > 0)
			{
				lastTransactionsVars = new HashSet<List<IloIntVar>>();
				int solutions = cplexILP.getSolnPoolNsolns();
				
				for(int t=0; t<solutions; t++)
				{
					final IloLinearNumExprIterator iterator = constraintOnConstraints.linearIterator();
				
					IloNumVar var = iterator.nextNumVar();
					String varName = var.getName();
					double value = cplexILP.getValue(var,t);
					IloColumn column = null;
					
					while(value==0 && iterator.hasNext())
					{
						var = iterator.nextNumVar();
						value = cplexILP.getValue(var,t);
						varName = var.getName();
					}
					
					if(varName.startsWith("f"))//frequency constraints
						column = cplex.column(constraints6.get(varName), 1).and(cplex.column(constraints7.get(varName), 1));
					else
						if(varName.startsWith("i"))//infrequency constraints
							column = cplex.column(constraints8.get(varName), 1);			
					
					while(iterator.hasNext())
					{
						var = iterator.nextNumVar();
						varName = var.getName();
						value = cplexILP.getValue(var,t);
						
						if(value > 0)
						{							
							if(varName.startsWith("f"))//frequency constraints
									column = column.and(cplex.column(constraints6.get(varName), 1)).and(cplex.column(constraints7.get(varName), 1));
							else
								if(varName.startsWith("i"))//infrequency constraints	
									column = column.and(cplex.column(constraints8.get(varName), 1));
						}
					}
					
					column = column.and(cplex.column(sizeConstraints.get("C10"), 1)).and(cplex.column(sizeConstraints.get("C11"), 1));//C10 and C11	
					
					final IloNumVar x = cplex.numVar(column, 0, Double.POSITIVE_INFINITY);
					x.setName("x" + xIndex);
					xIndex++;
					
					final List<Integer> one_Transaction = lastTransactions.get(t);
					transactions.put(x, one_Transaction);
					
					final List<IloIntVar> transVars = new ArrayList<IloIntVar>();
					
					for(final int i : one_Transaction)
					{
						for(final String columnName : variables.keySet())
							if(variables.get(columnName).containsKey(i))
							{
								transVars.add(variables.get(columnName).get(i));
								break;
							}
					}
					
					lastTransactionsVars.add(transVars);
				}
				
				runProgram();
			}
			else
			{				
				double rows = 0;
				
				for(final IloNumVar x : transactions.keySet())
				{
					final double duplicates = cplex.getValue(x);
					
					if(duplicates > 0.0)
					{
						System.out.println(x.getName() + " " + transactions.get(x).toString() + "\t\t\t--\t" + duplicates);
						rows += duplicates;
					}
				}
				
				System.out.println("\nROWS: " + (int)rows);
			}
		}
		catch (IloException e)
		{
			e.printStackTrace();
		}
	}
	
	
//	private List<Constraint> DCsImpliedBy(final List<Integer> itemset)
//	{
//		final List<Constraint> toReturn = new ArrayList<Constraint>();
//		
//		for(final Constraint dc : duplicateConstraints)
//		{
//			final Map<String,Integer> vincoliSV = dc.getVincoliSV();
//			final Map<String,Set<Integer>> vincoliMV = dc.getVincoliMV();
//			
//			boolean s = false; //true if SV attribute value satisfies the current constraint
//			
//			if(vincoliSV.size() > 0)
//			{						
//				if((vincoliSV.values().contains(itemset.get(0))))
//					s = true;
//			}
//			else
//				s = true;
//			
//			boolean m = false; //true if MV attribute value satisfies the current constraint
//			
//			if(vincoliMV.size() > 0)
//			{
//				final Map<String,String> operators = dc.getOperators();
//				final String operator = operators.get(dc.getName());
//				final Set<Integer> constraintSet = vincoliMV.get("");//aggiungere il nome della colonna MV
//				
//				if(operator.equals("equals"))
//				{
//					if(
//						constraintSet.size() == itemset.size() &&
//						constraintSet.containsAll(itemset)
//					  )
//						m = true;
//				}
//				else 
//					if(operator.equals("contains"))
//					{
//						if(itemset.containsAll(constraintSet))
//							m = true;
//					}
//					else
//					{
//						System.out.println("syntax error: operator \"" + operator + "\" not defined!");
//						System.exit(0);
//					}
//			}
//			else
//				m = true;
//			
//			if(s && m)
//				toReturn.add(dc);
//		}
//		
//		return toReturn;
//	}


	
	private double runILP(
							final Map<String,IloRange> c6, 
							final Map<String,IloRange> c7, 
							final Map<String,IloRange> c8, 
							final Set<List<IloIntVar>> transactionsVars,
							final Set<String> nonSatisfiedConstraints
						 )
	{
		try
		{
//			final IloLinearNumExpr objective = (IloLinearNumExpr) cplexILP.getObjective().getExpr();
			final IloLinearNumExprIterator iterator = constraintOnConstraints.linearIterator();
			
			if(c6!=null && c7!=null && c8!=null)
			{
				while(iterator.hasNext())
				{
					final String name = iterator.nextNumVar().getName();
										
					if(name.startsWith("f"))
						iterator.setValue(cplex.getDual(constraints6.get(name)) + cplex.getDual(constraints7.get(name)));
					else
						if(name.startsWith("i"))
							iterator.setValue(cplex.getDual(constraints8.get(name)));
				}
				
				cplexILP.addGe(constraintOnConstraints, 0.000001);
			}
			else
			{
				while(iterator.hasNext())
				{
					if(nonSatisfiedConstraints.contains(iterator.nextNumVar().getName()))
						iterator.setValue(1);
					else
						iterator.setValue(0);
				}
				
				cplexILP.addGe(constraintOnConstraints, 0.000001);
			}

			if(transactionsVars.size() > 0)
				for(final List<IloIntVar> transactionVars : transactionsVars)
				{
					final int constant = transactionVars.size() - 1;
					final IloLinearIntExpr intExpr = cplexILP.linearIntExpr();
					
					final Collection<IloIntVar> multiValuesAttributesVariables = new HashSet<IloIntVar>();
					
					for(final Column<TIntHashSet> mvColumn : table.get_MV_attributes())
						multiValuesAttributesVariables.addAll(variables.get(mvColumn.getName()).values());
					
					for(final IloIntVar var : transactionVars)
					{
						intExpr.addTerm(1, var);
						
						multiValuesAttributesVariables.remove(var);
					}
					
					for(final IloIntVar var : multiValuesAttributesVariables)
						intExpr.addTerm(-1, var);
					
					cplexILP.addLe(intExpr, constant);
				}
			
			cplexILP.setOut(null);
			cplexILP.setWarning(null);	
			cplexILP.populate();
			
			getTransactions();
			
			final int transactions = cplexILP.getSolnPoolNsolns();

			double max = 0;
			
			for(int t=0; t<transactions; t++)
				max = Math.max(max, cplexILP.getValue(constraintOnConstraints,t));

			return max;
		}
		catch (IloException e)
		{
			e.printStackTrace();
		}
		
		return 0;
	}


	private void getTransactions()
	{
		lastTransactions = new HashMap<Integer,List<Integer>>();
		
		try
		{
			final int transactions = cplexILP.getSolnPoolNsolns();

			for(int t=0; t<transactions; t++)
			{
				final List<Integer> transaction = new ArrayList<Integer>();
				
				for(final String columnName : variables.keySet())
					for(final int i : variables.get(columnName).keySet())
					{
						final IloIntVar var = variables.get(columnName).get(i);
						
						if(cplexILP.getValue(var,t) > 0)
							transaction.add(i);
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
