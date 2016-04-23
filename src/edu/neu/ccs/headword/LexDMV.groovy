package edu.neu.ccs.headword

import edu.neu.ccs.headword.TaggedLattice.Token
import edu.neu.ccs.headword.ConditionalModel.AdditiveConditionalModel
import edu.neu.ccs.headword.ConditionalModel.UnsmoothedConditionalModel;
import edu.neu.ccs.headword.ConditionalModel.WBConditionalModel;
import edu.neu.ccs.headword.ConditionalModel.Observer

class LexDMV implements DMV {

	private static final boolean DEBUG = false
	
	ArrayList<ConditionalModel<String, ArrayList<String>>> attachModels
	DMV tagModel
	double lambda
	
	LexDMV(
		String file, double alpha, DMV tagModel=null, double lambda, double unkProb
	) {
		this.tagModel = tagModel
		this.lambda = lambda

		CountsFile counts = new CountsFile(file)
		
		attachModels = []
		[0, 1].each { d ->
			if (alpha >= 0D) {
				attachModels << new AdditiveConditionalModel<String, ArrayList<String>>(
						counts.getCounts("attach-$d"), alpha)
			} else {
				attachModels << new WBConditionalModel<String, ArrayList<String>>(
						counts.getCounts("attach-$d"), unkProb);
			}
		}
	}

	@Override
	double prob(Token head, boolean left, boolean stop, boolean hasChild) {
		tagModel.prob(head, left, stop, hasChild)
	}
	@Override
	double logProb(Token head, boolean left, boolean stop, boolean hasChild) {
		tagModel.logProb(head, left, stop, hasChild)
	}
	
	@Override
	double prob(Token head, Token arg, boolean left) {
		if (tagModel == null)
			return attachModels[left ? 0 : 1].prob([head.getString()], arg.getString());
			
		def pTag = tagModel.prob(head, arg, left)*arg.getCondProb()
		if (pTag == 0D) {
			printf("p_tag(%s | %s) = %f * %f\n", arg.getTag(), head.getTag(),
				 tagModel.prob(head, arg, left), arg.getCondProb());
		}

		def p
		if (lambda >= 0D) {
			def pLex = attachModels[left ? 0 : 1].prob([head.getString()], arg.getString())

			p = lambda*pLex + (1D-lambda)*pTag
		
			if (DEBUG)
				printf "p($arg | $head, $left) = %f*%f + %f*(%f = %.9f*%.9f) = %f\n", 
					lambda, pLex,
					1D-lambda, pTag, tagModel.prob(head, arg, left), arg.getCondProb(),
					p
		}
		else {
			// back off to tag model (instead of unigram word model)
			p = attachModels[left ? 0 : 1].prob([head.getString()], arg.getString(), pTag)
		}
			
		return p
	}
	@Override
	double logProb(Token head, Token arg, boolean left) {
		return Math.log(prob(head, arg, left))
	}
	
	static main(args) {
	
	}

}


