package ocr

import ocr.TaggedLattice.Token
import ocr.ConditionalModel.AdditiveConditionalModel
import ocr.ConditionalModel.UnsmoothedConditionalModel;
import ocr.ConditionalModel.WBConditionalModel;
import ocr.ConditionalModel.Observer

class TagDMV implements DMV {

	static final String ROOT = 'ROOT'
	
	private static final boolean DEBUG = false
	
	ConditionalModel<String, ArrayList<String>> stopModel
	ArrayList<ConditionalModel<String, ArrayList<String>>> attachModels
	
	TagDMV(String file, boolean wb, double alpha) {
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
		def p = prob(head, left, stop, hasChild)
		if (DEBUG)
			printf "p_tag(%s | %s/%s, %s, %s) = %f\n",
				stop ? 'STOP' : 'CONT', head, head.getTag(), left, hasChild, p;
		Math.log(p)
	}
	@Override
	double prob(Token head, boolean left, boolean stop, boolean hasChild) {
		def context = [left ? "0" : "1", hasChild ? "1" : "0", head.getTag()]
		stopModel.prob(context, stop ? 'true' : 'false')
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


