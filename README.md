# Headword: an OCR error correction system, including a probabilistic dependency parser

This was coded for a research project (my [master's thesis](https://repository.library.northeastern.edu/downloads/neu:349804?datastream_id=content), which explored the utility of structured language models with unsupervised training for noisy-channel error correction), and has not been cleaned up for distribution at this point. It's not bad-quality code, but it is in dire need of documentation.

### The Parser

The component most likely to be of use to others is the parser (`ocr/LatticeParser.java`). Although it's in the `ocr` directory with everything else, it is well-factored and not coupled with the non-parsing-related code.

The Headword parser uses a lattice-generalized version of Eisner and Satta's (1999) parsing algorithm to perform probabilistic dependency parsing on a weighted lattice. Among other things, it can output the highest-likelihood parse among all sentences in the lattice. It parses ordinary token sequences as well, by encoding them as trivial (single-path) lattices.

It uses the Dependency Model with Valence (Klein and Manning, 2004) to estimate probabilities. It can be trained using Klein and Manning's expectation maximization procedure or Smith and Eisner's (2005) contrastive estimation procedure. Like other DMV-based parsers, it relies on part-of-speech tags to reduce data sparsity. Its input can be either 1) part-of-speech tag sequences, or 2) word sequences, plus a file that defines the mapping from words to tags. (The latter are typically automatically induced word clusters, the next-best thing when annotated POS sequences are unavailable.) In addition to using only the tag sequences, it can use a word/tag-interpolated model (see description in thesis), which is useful for language modeling (it out-performed the n-gram model in the OCR correction task).

Empirically, the Headword parser is many times faster than [Dageem](https://github.com/shaybcohen/jdageem) by Shay Cohen, both in training and parsing (I don't know why). Dageem, however, includes support for minimum Bayes risk decoding (mine only does Viterbi) and a more advanced training procedure. Embarrassingly, Headword depends on the Dageem jar because it uses Dageem's implementation of the DMV's "harmonic" initializer, to save me the trouble of duplicating and testing that logic.

## Usage

This is an Eclipse project. I haven't yet set it up to compile with Ant or Gradle or whatever the coolest new build tool is. The required dependencies are in `lib`. Like I said, it's one-off research code.

(*TODO: Update this with examples of training and parsing.*)

### The rest of the OCR error correction system

I don't currently plan to spend time documenting this stuff because:
 1. Honestly, modern OCR software already has decent language modeling for commonly-used languages baked in, so for typical OCR needs in industry, the marginal utility of a post-processing system like this is small.
 2. This particular system is complicated and difficult to use. There are a lot of hyperparameters.

However, if you have text data with OCR errors (and an error-free training corpus that is better-matched to the OCR text than the OCR system's language model training), then maybe Headword will be of use to you. In that case, feel free to get in touch.

### License

Copyright © 2016 Samuel Scarano. See LICENSE.md.

If you use some of this code for research, please cite my master's thesis ("Applying Unsupervised Grammar Induction to Improve OCR Error Correction," Northeastern University, 2015). (As of 4/2016 there is no publication to cite, but I'll update this if it gets accepted to CoNLL 2016....)


### References

Jason Eisner and Giorgio Satta. Efficient parsing for bilexical context-free grammars and head automaton grammars. In *Proceedings of the 37th annual meeting of the Association for Computational Linguistics*, pages 457–464. Association for Computational Linguistics, 1999.

Dan Klein and Christopher D Manning. Corpus-based induction of syntactic structure: Models of dependency and constituency. In *Proceedings of the 42nd Annual Meeting on Association for Computational Linguistics*, page 478. Association for Computational Linguistics, 2004.

Noah A Smith and Jason Eisner. Guiding unsupervised grammar induction using contrastive estimation. In *Proceedings of IJCAI Workshop on Grammatical Inference Applications*, pages 73–82, 2005.
