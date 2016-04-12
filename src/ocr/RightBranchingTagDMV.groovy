package ocr

import ocr.TaggedLattice.Token
import ocr.ConditionalModel.AdditiveConditionalModel
import ocr.ConditionalModel.UnsmoothedConditionalModel;
import ocr.ConditionalModel.WBConditionalModel;
import ocr.ConditionalModel.Observer


/**
 * Bogus. Do not use.
 */
class RightBranchingTagDMV extends TagDMV {

//	static final String ROOT = 'ROOT'
	
	private static final boolean DEBUG = false
	
	RightBranchingTagDMV(String file, boolean wb, double alpha) {
		CountsFile counts = new CountsFile(file)
		
		stopModel = new AdditiveConditionalModel<String, ArrayList<String>>(
			counts.getCounts("stop"), alpha, 2)

		attachModels = [] // new ConditionalModel<String, ArrayList<String>>[2]
		[0, 1].each { d ->
			if (wb)
				throw new Error("not implemented")
//				attachModels << new WBConditionalModel<String, ArrayList<String>>(
//					counts.getCounts("attach-$d"))
			else
				attachModels << new AdditiveConditionalModel<String, ArrayList<String>>(
					counts.getCounts("attach-$d"), alpha)
		}
	}

	@Override
	double logProb(Token head, boolean left, boolean stop, boolean hasChild) {
		left ? Double.NEGATIVE_INFINITY : 0D
	}
	@Override
	double prob(Token head, boolean left, boolean stop, boolean hasChild) {
		left ? 0D : 1D
	}
	
	@Override
	double logProb(Token head, Token arg, boolean left) {
		def p = prob(head, arg, left)
//		if (DEBUG)
//			printf "p_tag($arg/${arg.getTag()} | $head/${head.getTag()}, $left) = %f\n", p
		return Math.log(p)
	}
	@Override
	double prob(Token head, Token arg, boolean left) {
		def p = attachModels[left ? 0 : 1].prob([head.getTag()], arg.getTag())
		if (DEBUG)
			printf "p_tag($arg/${arg.getTag()} | $head/${head.getTag()}, $left) = %f\n", p
		p
	}
	
	static main(args) {
	
	}

}


