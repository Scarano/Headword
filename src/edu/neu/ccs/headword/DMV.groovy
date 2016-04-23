package edu.neu.ccs.headword;

import edu.neu.ccs.headword.TaggedLattice.Token;

public interface DMV {

	public abstract double prob(Token head, boolean left, boolean stop, boolean hasChild);
	public abstract double logProb(Token head, boolean left, boolean stop, boolean hasChild);

	public abstract double prob(Token head, Token arg, boolean left);
	public abstract double logProb(Token head, Token arg, boolean left);
	
}