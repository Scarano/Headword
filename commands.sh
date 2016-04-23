# This is not actually a shell script! Do not run!
# These are notes I took on the commands I ran to get my results, so that I could reproduce them later.
# These were run interactively in bash, including the for loops.

JAVA=~/bin/runjava.sh
SRILMBIN=/scratch/scarano.s/src/srilm/bin/i686-m64
#SRILMBIN=/Users/sam/src/srilm/bin/macosx
WCLUSTER=echo 'clusterer not specified'; exit;
#WCLUSTER=~/src/brown-cluster-master/wcluster

# -- tesseract (v5) --------------
 
for x in `cat ../archive-names-prose.txt ../archive-names-nonprose.txt`; do
  f=$x.c70
  if [ ! -e $f.started ]; then
    touch $f.started
    echo $f
    convert -density 300  -depth 8 -colorspace gray -contrast-stretch 1x70% $x.pdf $f.png
  fi
done

mkdir png
mv pdf/*.png png/

for x in `cat archive-names-prose.txt archive-names-nonprose.txt`; do
  for p in `seq 1 999`; do
    f=$x.c70-$p
    if [ -e png/$f.png ]; then
      echo $f
      tesseract png/$f.png tesseract-nolm/$f -l eng ../tesseract-testing/lm-none.config
    fi
  done
  cat tesseract-nolm/$x.c70-?.txt tesseract-nolm/$x.c70-??.txt tesseract-nolm/$x.c70-???.txt > tesseract-nolm/$x.c70.txt
done

~/bin/runjava.sh ocr.OCRCorpus -align ocr-corpus/ocr-corpus.txt wwp/text.faithful ocr-corpus/tesseract-nodawg ocr-corpus/channel-training ocr-corpus/alignment-channel-training.txt -ocr-suffix .c70 -channel-set
~/bin/runjava.sh ocr.OCRCorpus -align ocr-corpus/ocr-corpus.txt wwp/text.faithful ocr-corpus/tesseract-nodawg ocr-corpus/aligned-nodawg ocr-corpus/alignment-nodawg.txt -ocr-suffix .c70
~/bin/runjava.sh ocr.OCRCorpus -align ocr-corpus/ocr-corpus.txt wwp/text.faithful ocr-corpus/tesseract-bigram ocr-corpus/aligned-bigram ocr-corpus/alignment-bigram.txt -ocr-suffix .c70


# Run Evaluator
for set in  devset   ; do for rw in 0.0  ; do for sup in 4 5 6 7 8 9; do for plup in 8; do for cup in 6; do for plml in 0.5; do for cn in 100  ; do for pmm in em.har    ; do for lsc in 0; do ~/bin/runjava.sh ocr.scripts.SubmitJob ~/scratch/evaluator-v5-$set.config ~/scratch/output-v5 -overrides run-under-name:[tuning-ngram],lm.sentence-candidates:[$lsc],rescore.weight:[$rw],parser.model-method:[$pmm],clustering.n:[$cn],srilm.unk-prob:[$sup],parser.lex-unk-prob:[1e-$plup],clustering.unk-prob:[1e-$cup],parser.lex-model-lambda:[$plml] -parallel 6 -bsub  ; sleep 1; done; done; done; done; done; done; done; done; done


# -- combined clustering, with tesseract OCR and PTB tokenization (v6) --------------

#java -server -Xmx4G -cp ~/Dropbox/programs/workspace/Thesis/bin:/Users/sam/src/jdageem-1.0/lib/colt.jar:/Users/sam/src/jdageem-1.0/lib/commons-cli-1.2.jar:/Users/sam/src/jdageem-1.0/lib/commons-math-2.2.jar:/Users/sam/src/jdageem-1.0/lib/pcollections-2.1.2.jar:/Users/sam/src/jdageem-1.0/lib/trove.jar:/Applications/eclipse/plugins/org.codehaus.groovy_2.0.7.xx-20130703-1600-e43-RELEASE/lib/groovy-all-2.0.7.jar:/home/sam/thesis/code/gprof-0.3.0-groovy-2.1.jar:/Users/sam/thesis/code/commons-csv-1.1/commons-csv-1.1.jar ocr.scripts.RetokenizePTB train.1to999.ip.parses train.1to999.ip.retokenized

# Replace PTB quotes with UNICODE quote chars
for x in train.1to10 train.1to999 dev.1to10 dev.1to999; do ~/bin/runjava.sh ocr.scripts.RetokenizePTB  $x.ip.parses $x.ip.retok; done

date; for c in 67 100 150 200; do time ~/src/brown-cluster-master/wcluster --text train.1to999.ip.retok.text --min-occur 2 --c $c --rand 0 --threads 1 & done

# Process transcription files, including sentence-breaking, tokenization, and de-hyphenization
cd wwp
for x in text.clean/*.txt; do echo $x; $JAVA ocr.scripts.SentenceFinder $x ${x%.txt}.sentences; done
for x in text.clean/*.sentences; do echo $x; $JAVA -Xmx3g ocr.PTBLikeTokenizer $x ${x%sentences}ptbtokens; done
$JAVA -Xmx2G ocr.scripts.BuildSets -tokensuffix .ptbtokens prose.set.txt prose text.clean
for l in 15 30 50; do $JAVA  ocr.scripts.FilterByLength prose.train.deh.txt prose.train.1to$l.deh.txt 1 $l; done
cd ..

mkdir clustering
cat ptb/train.1to999.ip.retok.text wwp/prose.train.deh.lc.txt > clustering/combined.txt

# Find word clusters (a few minutes)
cd clustering
date; for c in 100 150 200; do
	(
		time $WCLUSTER --text combined.txt --min-occur 2 --c $c --rand 0 --threads 1
		$JAVA -Xmx4G ocr.scripts.ClusteringVocab -add-root combined-c$c-p1.out/paths combined-c$c-p1.out/vocab.txt
	)&
done
wait
cd ..

# generate versions of corpus with clusters substituted for words
for c in 100 150 200; do $JAVA -Xmx4G ocr.Clustering clustering/combined-c$c-p1.out/paths wwp/prose.train.deh.lc.txt clustering/prose.train.4to10.c$c.txt 4 10 -cl; done

# Train DMV structured tag language models with EM (several minutes)
# (Note that PTB-trained models are already trained by ptb/train.sh.)
mkdir grammar
for c in 100 150 200; do for min in 1 4; do d=wwp.${min}to10.cc$c.em.har; $JAVA ocr.DMVEM wwp/prose.train.1to15.deh.txt -min-length $min -max-length 10 -input-model harmonic grammar/$d. 1 999 -epsilon 1e-5 -clustering clustering/combined-c$c-p1.out/paths & done; done
# I decided to make symlinks for the PTB-trained models:
cd grammar
ln -s ../ptb/models/train.1to*cc*.em*.cnt  .
cd ..

# Create right-branching-only DMV:
for c in 100 150 200; do m=train.1to10.ip.retok.cc$c.rb; o=$m.cnt;  $JAVA ocr.LatticeParser -string -right-branching -counts ptb/models/train.1to10.ip.retok.cc$c.em.har.cnt  wwp/prose.train.1to15.deh.txt -min-length 1 -max-length 10 -tag-smoothing 1 -clustering clustering/combined-c150-p1.out/paths  -reestimate grammar/$o   -quiet & done
for c in 100 150 200 ; do for l in 50; do m=train.1to10.ip.retok.cc$c.rb; o=$m.1to$l.tagalpha1.v.lcnt;  $JAVA ocr.LatticeParser -string -right-branching -counts grammar/$m.cnt  wwp/prose.train.1to$l.deh.txt  -tag-smoothing 1 -clustering clustering/combined-c$c-p1.out/paths   -estimate-lex grammar/$o -viterbi  -quiet & done; done
#oops, i put those in the wrong dir, created symlinks to fix:
cd ptb/models; ln -s ../../grammar/train.1to*cnt .

# Train supervised model (also done by ptb/train.sh)
for c in 100 150 200; do for l in 10 999; do  $JAVA ocr.LatticeParser  -supervised-training train.1to999.ip.retok.parses -clustering ../clustering/combined-c$c-p1.out/paths -max-length $l  -reestimate models/train.1to$l.ip.retok.cc$c.em.super.cnt; done; done

# Count lexical dependencies using parses from DMV/EM on PTB
for c in 100 150 200; do
  for l in 50; do
    m=train.1to10.ip.retok.cc$c.em.har;
    o=$m.1to$l.tagalpha1.v.lcnt;
    bsub -n 1  -q  ser-par-10g -J $o -o grammar/$o.log $JAVA ocr.LatticeParser -string -counts ptb/models/$m.cnt  wwp/prose.train.1to$l.deh.txt  -tag-smoothing 1 -clustering clustering/combined-c$c-p1.out/paths   -estimate-lex grammar/$o -viterbi  -quiet;
  done;
done
# Count lexical dependencies using parses from supervised parser
for c in 100 150 200; do
  for cl in 10 999; do
    l=50
    m=train.1to$cl.ip.retok.cc$c.em.super;
    o=$m.1to$l.tagalpha1.v.lcnt;
    bsub -n 1  -q  ser-par-10g -J $o -o grammar/$o.log $JAVA ocr.LatticeParser -string -counts ptb/models/$m.cnt  wwp/prose.train.1to$l.deh.txt  -tag-smoothing 1 -clustering clustering/combined-c$c-p1.out/paths   -estimate-lex grammar/$o -viterbi  -quiet;
  done;
done
# Count lexical dependencies using parses from DMV/EM on WWP
for c in 100 150 200; do
  for l in 50; do
    for min in 1 4; do
      m=wwp.${min}to10.cc$c.em.har;
      o=$m.1to$l.tagalpha1.v.lcnt;
#      bsub -n 1  -q  ser-par-10g -J $o -o grammar/$o.log  \
      $JAVA ocr.LatticeParser -string -counts grammar/$m.cnt  wwp/prose.train.1to$l.deh.txt  -tag-smoothing 1 -clustering clustering/combined-c$c-p1.out/paths   -estimate-lex grammar/$o -viterbi  -quiet;
    done
  done;
done

# -- combined clustering, with ABBYY OCR and PTB tokenization (v8) --------------

# Start by copying v7/clustering

mkdir grammar

# CE training with combined clustering
for c in 100 150 200; do for s in 4to10; do for n in error trans; do f=model.prose.$s.c$c.ce$n.unif; date; time $JAVA edu.neu.ccs.headword.DMVCE clustering/prose.train.$s.c$c.txt -vocab clustering/combined-c$c-p1.out/vocab.txt -hood $n -parallel 2 grammar/$f. 0 800  >grammar/$f.log ; done ; done; done; date
for c in 100 150 200; do for s in 4to10; do for n in error trans; do f=model.prose.$s.c$c.ce$n.har; date; time $JAVA edu.neu.ccs.headword.DMVCE clustering/prose.train.$s.c$c.txt -vocab clustering/combined-c$c-p1.out/vocab.txt -hood $n -parallel 2 grammar/$f. -input-model harmonic 0 800  >grammar/$f.log ; done ; done; done; date


####
####

# Train DMV structured tag language models with EM (several minutes)
# (Note that PTB-trained models are already trained by ptb/train.sh.)
mkdir grammar
for c in 100 150 200; do for min in 1 4; do d=wwp.${min}to10.cc$c.em.har; $JAVA ocr.DMVEM wwp/prose.train.1to15.deh.txt -min-length $min -max-length 10 -input-model harmonic grammar/$d. 1 999 -epsilon 1e-5 -clustering clustering/combined-c$c-p1.out/paths & done; done
# I decided to make symlinks for the PTB-trained models:
cd grammar
ln -s ../ptb/models/train.1to*cc*.em*.cnt  .
cd ..

# Create right-branching-only DMV:
for c in 100 150 200; do m=train.1to10.ip.retok.cc$c.rb; o=$m.cnt;  $JAVA ocr.LatticeParser -string -right-branching -counts ptb/models/train.1to10.ip.retok.cc$c.em.har.cnt  wwp/prose.train.1to15.deh.txt -min-length 1 -max-length 10 -tag-smoothing 1 -clustering clustering/combined-c150-p1.out/paths  -reestimate grammar/$o   -quiet & done
for c in 100 150 200 ; do for l in 50; do m=train.1to10.ip.retok.cc$c.rb; o=$m.1to$l.tagalpha1.v.lcnt;  $JAVA ocr.LatticeParser -string -right-branching -counts grammar/$m.cnt  wwp/prose.train.1to$l.deh.txt  -tag-smoothing 1 -clustering clustering/combined-c$c-p1.out/paths   -estimate-lex grammar/$o -viterbi  -quiet & done; done
#oops, i put those in the wrong dir, created symlinks to fix:
cd ptb/models; ln -s ../../grammar/train.1to*cnt .

# Train supervised model (also done by ptb/train.sh)
for c in 100 150 200; do for l in 10 999; do  $JAVA ocr.LatticeParser  -supervised-training train.1to999.ip.retok.parses -clustering ../clustering/combined-c$c-p1.out/paths -max-length $l  -reestimate models/train.1to$l.ip.retok.cc$c.em.super.cnt; done; done

# Count lexical dependencies using parses from DMV/EM on PTB
for c in 100 150 200; do
  for l in 50; do
    m=train.1to10.ip.retok.cc$c.em.har;
    o=$m.1to$l.tagalpha1.v.lcnt;
    bsub -n 1  -q  ser-par-10g -J $o -o grammar/$o.log $JAVA ocr.LatticeParser -string -counts ptb/models/$m.cnt  wwp/prose.train.1to$l.deh.txt  -tag-smoothing 1 -clustering clustering/combined-c$c-p1.out/paths   -estimate-lex grammar/$o -viterbi  -quiet;
  done;
done
# Count lexical dependencies using parses from supervised parser
for c in 100 150 200; do
  for cl in 10 999; do
    l=50
    m=train.1to$cl.ip.retok.cc$c.em.super;
    o=$m.1to$l.tagalpha1.v.lcnt;
    bsub -n 1  -q  ser-par-10g -J $o -o grammar/$o.log $JAVA ocr.LatticeParser -string -counts ptb/models/$m.cnt  wwp/prose.train.1to$l.deh.txt  -tag-smoothing 1 -clustering clustering/combined-c$c-p1.out/paths   -estimate-lex grammar/$o -viterbi  -quiet;
  done;
done
# Count lexical dependencies using parses from DMV/EM on WWP
for c in 100 150 200; do
  for l in 50; do
    for min in 1 4; do
      m=wwp.${min}to10.cc$c.em.har;
      o=$m.1to$l.tagalpha1.v.lcnt;
#      bsub -n 1  -q  ser-par-10g -J $o -o grammar/$o.log  \
      $JAVA ocr.LatticeParser -string -counts grammar/$m.cnt  wwp/prose.train.1to$l.deh.txt  -tag-smoothing 1 -clustering clustering/combined-c$c-p1.out/paths   -estimate-lex grammar/$o -viterbi  -quiet;
    done
  done;
done

# -- version 3 --------------

#JAVA='java -server -cp /Users/sam/Dropbox/programs/workspace/Thesis/bin:/Users/sam/src/jdageem-1.0/lib/colt.jar:/Users/sam/src/jdageem-1.0/lib/commons-cli-1.2.jar:/Users/sam/src/jdageem-1.0/lib/commons-math-2.2.jar:/Users/sam/src/jdageem-1.0/lib/pcollections-2.1.2.jar:/Users/sam/src/jdageem-1.0/lib/trove.jar:/Applications/eclipse/plugins/org.codehaus.groovy_2.0.7.xx-20130703-1600-e43-RELEASE/lib/groovy-all-2.0.7.jar:/Users/sam/thesis/code/gprof-0.3.0-groovy-2.1.jar'
#SRILMBIN=/Users/sam/src/srilm/bin/macosx
#WCLUSTER=~/src/brown-cluster-master/wcluster


# Extract text from WWP TEI files
cd wwp/snapshot
mkdir ../text.clean
for x in *.xml; do echo $x; $JAVA -Xmx3g -cp ~/thesis/code/bin ocr.WWPDocument $x ../text.clean/${x%.xml}.txt -clean; done
mkdir ../text.faithful
for x in *.xml; do echo $x; $JAVA -Xmx3g -cp ~/thesis/code/bin ocr.WWPDocument $x ../text.faithful/${x%.xml}.txt ; done
cd ../..

# Align WWP transcription text with OCR to form aligned OCR corpus (one of prose, for testing; and one of verse and plays, for channel model training)
mkdir ocr-corpus/aligned ocr-corpus/channel-training
$JAVA -Xmx20G ocr.OCRCorpus -align ocr-corpus/ocr-corpus.txt wwp/text.clean ocr-corpus/djvu ocr-corpus/aligned ocr-corpus/alignment.txt
$JAVA -Xmx20G ocr.OCRCorpus -align ocr-corpus/ocr-corpus.txt wwp/text.faithful ocr-corpus/djvu ocr-corpus/channel-training ocr-corpus/alignment-channel-training.txt -channel-set

# Train channel model (several minutes)
mkdir channelmodel
cat ocr-corpus/channel-training/*.align > channelmodel/channel-training.align
time $JAVA -Xmx10G ocr.SegmentAligner channelmodel/channel-training.align channelmodel/model 99 

# Process transcription files, including sentence-breaking, tokenization, and de-hyphenization
cd wwp
for x in text.clean/*.txt; do echo $x; $JAVA ocr.scripts.SentenceFinder $x ${x%.txt}.sentences; done
for x in text.clean/*.sentences; do echo $x; $JAVA -Xmx3g ocr.SimpleTokenizer $x ${x%sentences}tokens; done
$JAVA -Xmx2G ocr.scripts.BuildSets prose.set.txt prose text.clean
for l in 15 30 50; do $JAVA  ocr.scripts.FilterByLength prose.train.deh.txt prose.train.1to$l.deh.txt 1 $l; done
cd ..

# Find word clusters (a few minutes)
mkdir clustering
cd clustering
date; for c in 100 150 200; do
	(
		time $WCLUSTER --text ../wwp/prose.train.deh.lc.txt --min-occur 2 --c $c --rand 0 --threads 1
		$JAVA -Xmx4G ocr.scripts.ClusteringVocab -add-root prose.train.deh.lc-c$c-p1.out/paths prose.train.deh.lc-c$c-p1.out/vocab.txt
	)&
done
wait
cd ..

# generate versions of corpus with clusters substituted for words
for c in 100 150 200; do $JAVA -Xmx4G ocr.Clustering clustering/prose.train.deh.lc-c$c-p1.out/paths wwp/prose.train.deh.lc.txt clustering/prose.train.4to10.c$c.txt 4 10 -cl; done

# Train SRILM n-gram language models (under a minute, I think)
mkdir srilm
for s in 1to15 1to30 1to50; do $SRILMBIN/ngram-count -kndiscount -interpolate -order 4 -unk -text wwp/prose.train.$s.deh.txt -lm srilm/prose.$s.lm.4kni.txt; done
# Create modified versions of those language models with specific probabilities assigned to <unk>
for l in 15 30 50; do for unkprob in 08 09 10 11 12 13 14 15; do cat srilm/prose.1to$l.lm.4kni.txt | sed -e "s/.*<unk>/-$unkprob"$'\t<unk>/' > srilm/prose.1to$l.lm.4kni-unk$unkprob.txt ; done; done

# Train DMV structured tag language models with EM (several minutes)
mkdir grammar
for c in 100 150 200; do for l in 10; do d=model.prose.4to$l.c$c.em.har; bsub -n 2 -q ser-par-10g -J DMVEM_$d -o grammar/$d.log $JAVA ocr.DMVEM wwp/prose.train.1to15.deh.txt -min-length 4 -max-length $l -input-model harmonic grammar/$d. 1 999 -epsilon 1e-5 -clustering clustering/prose.train.deh.lc-c$c-p1.out/paths; done; done
# Train DMV structured tag language models with CE (about an hour or so at -parallel 8)
for c in 100 150 200; do for s in 4to10; do for n in error trans; do f=model.prose.$s.c$c.ce$n.unif; bsub  -n 12 -q  ser-par-10g -J $f -o grammar/$f.log $JAVA ocr.DMVCE clustering/prose.train.$s.c$c.txt -vocab clustering/prose.train.deh.lc-c$c-p1.out/vocab.txt -hood $n -parallel 12 grammar/$f. 0 800   ; done ; done; done
for c in 100 150 200; do for s in 4to10; do for n in error trans; do f=model.prose.$s.c$c.ce$n.har; bsub  -n 12 -q  ser-par-10g -J $f -o grammar/$f.log $JAVA ocr.DMVCE clustering/prose.train.$s.c$c.txt -vocab clustering/prose.train.deh.lc-c$c-p1.out/vocab.txt -hood $n -parallel 12 grammar/$f. -input-model harmonic 0 800   ; done ; done; done
# Note that currently (corpus v4) the above fails for trans/100 and trans/150, and must be re-run using the last model output before failure as the initial model.

# Create right-branching-only DMV:
for c in 100 150 200; do for s in 4to10; do m=model.prose.$s.c$c.rb; o=$m.cnt;  $JAVA ocr.LatticeParser -string -right-branching -counts grammar/model.prose.4to10.c$c.em.har.cnt  wwp/prose.train.1to15.deh.txt -min-length 4 -max-length 10 -tag-smoothing 1 -clustering clustering/prose.train.deh.lc-c$c-p1.out/paths   -reestimate grammar/$o   -quiet & done; done
for c in 100 150 200 ; do for s in 4to10; do for l in 50; do m=model.prose.$s.c$c.rb; o=$m.1to$l.tagalpha1.v.lcnt;  $JAVA ocr.LatticeParser -string -right-branching -counts grammar/$m.cnt  wwp/prose.train.1to$l.deh.txt  -tag-smoothing 1 -clustering clustering/prose.train.deh.lc-c$c-p1.out/paths   -estimate-lex grammar/$o -viterbi  -quiet & done; done; done

# Re-estimate (tag) counts from CE models
for c in 100 150 200; do for s in 4to10 ; do for n in cetrans.unif cetrans.har ceerror.unif ceerror.har; do m=model.prose.$s.c$c.$n; o=$m.cnt; echo $o; $JAVA ocr.LatticeParser -string -model grammar/$m.dmv  wwp/prose.train.deh.txt  -min-length 4 -max-length 10 -clustering clustering/prose.train.deh.lc-c$c-p1.out/paths -reestimate grammar/$o -quiet ; done; done; done

# Count lexical dependencies using parses from DMV/EM
for c in 100 150 200; do for s in 4to10; do for l in 15 30 50; do m=model.prose.$s.c$c.em.har; o=$m.1to$l.tagalpha1.v.lcnt; bsub -n 2  -q  ser-par-10g -J $o -o grammar/$o.log $JAVA ocr.LatticeParser -string -counts grammar/$m.cnt  wwp/prose.train.1to$l.deh.txt  -tag-smoothing 1 -clustering clustering/prose.train.deh.lc-c$c-p1.out/paths   -estimate-lex grammar/$o -viterbi  -quiet; done; done; done
# Count lexical dependencies using parses from DMV/CE
for c in 100 150 200; do for s in 4to10; do for l in 15 30 50; do for n in trans.unif trans.har error.unif error.har; do m=model.prose.$s.c$c.ce$n; o=$m.1to$l.tagalpha1.v.lcnt; bsub -n 2  -q  ser-par-10g -J $o -o grammar/$o.log $JAVA ocr.LatticeParser -string -counts grammar/$m.cnt  wwp/prose.train.1to$l.deh.txt  -tag-smoothing 1 -clustering clustering/prose.train.deh.lc-c$c-p1.out/paths   -estimate-lex grammar/$o -viterbi  -quiet; done; done; done ; done


# Get stats on percentage of left & right "neighbor" arcs
cd ~/thesis-data/v4/ocr-corpus/aligned
$ for m in em.har ceerror.unif ceerror.har cetrans.unif cetrans.har; do echo $m; java -server -Xmx4G -cp ~/Dropbox/programs/workspace/Thesis/bin:/Users/sam/src/jdageem-1.0/lib/colt.jar:/Users/sam/src/jdageem-1.0/lib/commons-cli-1.2.jar:/Users/sam/src/jdageem-1.0/lib/commons-math-2.2.jar:/Users/sam/src/jdageem-1.0/lib/pcollections-2.1.2.jar:/Users/sam/src/jdageem-1.0/lib/trove.jar:/Applications/eclipse/plugins/org.codehaus.groovy_2.0.7.xx-20130703-1600-e43-RELEASE/lib/groovy-all-2.0.7.jar:/home/sam/thesis/code/gprof-0.3.0-groovy-2.1.jar ocr.scripts.ParseTranscription -clustering ~/thesis-data/v4/clustering/prose.train.deh.lc-c150-p1.out/paths -counts ~/thesis-data/v4/grammar/model.prose.4to10.c150.$m.cnt -tag-smoothing 1 -lex-counts ~/thesis-data/v4/grammar/model.prose.4to10.c150.$m.1to50.tagalpha1.v.lcnt  reflectionsuponm00aste.salign -min-length 1 -max-length 30; done
$ for m in em.har ceerror.unif ceerror.har cetrans.unif cetrans.har; do echo $m; java -server -Xmx4G -cp ~/Dropbox/programs/workspace/Thesis/bin:/Users/sam/src/jdageem-1.0/lib/colt.jar:/Users/sam/src/jdageem-1.0/lib/commons-cli-1.2.jar:/Users/sam/src/jdageem-1.0/lib/commons-math-2.2.jar:/Users/sam/src/jdageem-1.0/lib/pcollections-2.1.2.jar:/Users/sam/src/jdageem-1.0/lib/trove.jar:/Applications/eclipse/plugins/org.codehaus.groovy_2.0.7.xx-20130703-1600-e43-RELEASE/lib/groovy-all-2.0.7.jar:/home/sam/thesis/code/gprof-0.3.0-groovy-2.1.jar ocr.scripts.ParseTranscription -clustering ~/thesis-data/v4/clustering/prose.train.deh.lc-c150-p1.out/paths -counts ~/thesis-data/v4/grammar/model.prose.4to10.c150.$m.cnt -tag-smoothing 1   reflectionsuponm00aste.salign -min-length 1 -max-length 30; done

# -- version 2 --------------

# Set corpus version
CV=3

# Extract text from WWP TEI files
cd wwp/snapshot
for x in *.xml; do echo $x; java -Xmx3g -cp ~/thesis/code/bin ocr.WWPDocument $x ../text.clean${CV}/${x%.xml}.txt -clean; done
for x in *.xml; do echo $x; java -Xmx3g -cp ~/thesis/code/bin ocr.WWPDocument $x ../text.faithful${CV}/${x%.xml}.txt ; done
cd ../..

# Align WWP transcription text with OCR to form aligned OCR corpus (one of prose, for testing; and one of verse and plays, for channel model training)
~/bin/runjava.sh ocr.OCRCorpus -align ocr-corpus/ocr-corpus.txt wwp/text.clean$CV ocr-corpus/djvu ocr-corpus/aligned ocr-corpus/alignment.txt
~/bin/runjava.sh ocr.OCRCorpus -align ocr-corpus/ocr-corpus.txt wwp/text.faithful$CV ocr-corpus/djvu ocr-corpus/channel-training ocr-corpus/alignment-channel-training.txt -channel-set

# Train channel model
cat ocr-corpus/channel-training/*.align > channelmodel/channel-training-$CV.align
~/bin/runjava.sh ocr.SegmentAligner channelmodel/channel-training-$CV.align channelmodel/channel-training-$CV 99 

# Process transcription files, including sentence-breaking, tokenization, and de-hyphenization
cd wwp
for x in text.clean$CV/*.txt; do echo $x; groovy -cp ~/Dropbox/programs/workspace/Thesis/bin  ~/Dropbox/programs/workspace/Thesis/src/ocr/scripts/SentenceFinder $x ${x%.txt}.sentences; done
for x in text.clean$CV/*.sentences; do echo $x; java -Xmx3g -cp ~/Dropbox/programs/workspace/Thesis/bin ocr.SimpleTokenizer $x ${x%sentences}tokens; done
java -server -Xmx2G -cp ~/Dropbox/programs/workspace/Thesis/bin:/Applications/eclipse/plugins/org.codehaus.groovy_2.0.7.xx-20130703-1600-e43-RELEASE/lib/groovy-all-2.0.7.jar ocr.scripts.BuildSets prose$CV.set.txt prose3 text.clean3
for l in 15 30 50; do java -cp ~/thesis/code/bin:/Applications/eclipse/plugins/org.codehaus.groovy_2.0.7.xx-20130703-1600-e43-RELEASE/lib/groovy-all-2.0.7.jar  ocr.scripts.FilterByLength prose$CV.train.deh.txt prose$CV.train.1to$l.deh.txt 1 $l; done
cd ..

# Find word clusters (a few minutes)
cd clustering
date; for c in 100 150 200; do time ~/src/brown-cluster-master/wcluster --text ../wwp/prose$CV.train.deh.lc.txt --min-occur 2 --c $c --rand 0 --threads 1 & done
for c in 150 100 200 ; do java -Xmx4G -cp ~/Dropbox/programs/workspace/Thesis/bin:/Applications/eclipse/plugins/org.codehaus.groovy_2.0.7.xx-20130703-1600-e43-RELEASE/lib/groovy-all-2.0.7.jar ocr.scripts.ClusteringVocab -add-root prose$CV.train.deh.lc-c$c-p1.out/paths prose$CV.train.deh.lc-c$c-p1.out/vocab.txt; done
cd ..

# generate versions of corpus with clusters substituted for words
for c in 100 150 200; do  java -Xmx4G -cp ~/Dropbox/programs/workspace/Thesis/bin:/Applications/eclipse/plugins/org.codehaus.groovy_2.0.7.xx-20130703-1600-e43-RELEASE/lib/groovy-all-2.0.7.jar:/home/sam/thesis/code/gprof-0.3.0-groovy-2.1.jar ocr.Clustering clustering/prose$CV.train.deh.lc-c$c-p1.out/paths wwp/prose$CV.train.deh.lc.txt clustering/prose$c.train.4to10.c$c.txt 4 10 -cl; done

# Train SRILM n-gram language models
for s in 1to15 1to30 1to50; do ~/src/srilm/bin/macosx/ngram-count -kndiscount -interpolate -order 4 -unk -text wwp/prose$CV.train.$s.deh.txt -lm srilm/prose$CV.$s.lm.4kni.txt; done

# Train DMV structured tag language models with EM (several minutes)
for c in 100 150 200; do for l in 10; do d=model.prose$CV.4to$l.c$c.em.har; bsub -n 2 -q ser-par-10g -J DMVEM_$d -o /scratch/scarano.s/data/grammar/$d.log ~/bin/runjava.sh ocr.DMVEM /scratch/scarano.s/data/wwp/prose$CV.train.1to15.deh.txt -min-length 4 -max-length $l -input-model harmonic /scratch/scarano.s/data/grammar/$d. 1 $c -epsilon 1e-5 -clustering /scratch/scarano.s/data/clustering/prose$CV.train.deh.lc-c$c-p1.out/paths; done; done
# Train DMV structured tag language models with CE (less than an hour at -parallel 8)
for c in 100 150 200; do for s in 4to10; do for n in error trans; do f=model.prose$CV.$s.c$c.ce$n.unif; bsub  -n 14 -q  ser-par-10g -J $f -o /scratch/scarano.s/data/grammar/$f.log ~/bin/runjava.sh ocr.DMVCE ~/scratch/data/clustering/prose$CV.train.$s.c$c.txt -vocab ~/scratch/data/clustering/prose$CV.train.deh.lc-c$c-p1.out/vocab.txt -hood $n -parallel 8 /scratch/scarano.s/data/grammar/$f. 0 800   ; done ; done; done

# Count lexical dependencies using parses from DMV/EM
for c in 100 150 200; do for s in 4to10; do for l in 15 30 50;  do m=model.prose$CV.$s.c$c.em.har; o=$m.1to$l.tagalpha1.v.lcnt; bsub -n 2  -q  ser-par-10g -J $o -o /scratch/scarano.s/data/grammar/$o.log ~/bin/runjava.sh ocr.LatticeParser -string -counts /scratch/scarano.s/data/grammar/$m.cnt  /scratch/scarano.s/data/wwp/prose$CV.train.1to$l.deh.txt  -tag-smoothing 1 -clustering /scratch/scarano.s/data/clustering/prose$CV.train.deh.lc-c$c-p1.out/paths   -estimate-lex /scratch/scarano.s/data/grammar/$o -viterbi  -quiet; done; done; done


# -------------------------- old ---------------------------

#time ~/thesis-data/srilm/bin/macosx/ngram-count -text ~/thesis-data/wwp/prose.train.txt -order 3 -lm ~/thesis-data/wwp/prose.lm.3kni.txt  -kndiscount2 -interpolate -unk
~/src/srilm/bin/macosx/ngram-count -kndiscount -interpolate -order 4  -unk -text prose.train.txt -lm prose.lm.4kni.v2.txt
time ~/thesis-data/srilm/bin/macosx/ngram -lm prose.lm.3kni.txt -ppl prose.dev.txt 
~/thesis-data/srilm/bin/macosx/ngram -lm prose.lm.3kni.txt -server-port 10000 -unk

cat prose.train.clean1.txt | perl -npe 's/\s\s*/\n/g' | sort | uniq > prose.train.vocab.clean1.txt

(for x in reflectionsuponm00aste; do java -Xmx3g -cp ~/Dropbox/programs/Goose/Thesis/bin ocr.SegmentAligner $x.align $x 2 fallriverauthent01will.align | tee $x.out; done)  |les

groovy ~/Dropbox/programs/workspace/Thesis/src/ocr/scripts/BuildSets.groovy set-prose.txt lm tokens
for x in *.txt; do echo $x; groovy -cp ~/Dropbox/programs/workspace/Thesis/bin  ~/Dropbox/programs/workspace/Thesis/src/ocr/scripts/SentenceFinder $x ${x%.txt}.sentences; done
for x in *.sentences; do echo $x; java -Xmx3g -cp ~/Dropbox/programs/workspace/Thesis/bin ocr.SimpleTokenizer $x ${x%sentences}tokens; done
time java -Xmx3g -cp ~/Dropbox/programs/workspace/Thesis/bin ocr.Evaluator ../wwp/prose.train.vocab.txt anaccountameric01judsgoog.train.1.javaobj fallriverauthent01will.5000.align fallriverauthent01will.5000.corr.srilm1
time java -Xmx2G -cp ~/thesis/code/bin:/Applications/eclipse/plugins/org.codehaus.groovy_2.0.7.xx-20130703-1600-e43-RELEASE/lib/groovy-all-2.0.7.jar:/home/sam/thesis/code/gprof-0.3.0-groovy-2.1.jar  ocr.SentenceAlignment reflectionsuponm00aste_djvu.txt reflectionsuponm00aste.truth reflectionsuponm00aste.salign

# Seems to require JDK 1.6 for acceptable performance on the judson doc:
time /System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Home/bin/java  -Xmx3G -cp ~/thesis/code/bin:/Applications/eclipse/plugins/org.codehaus.groovy_2.0.7.xx-20130703-1600-e43-RELEASE/lib/groovy-all-2.0.7.jar:/home/sam/thesis/code/gprof-0.3.0-groovy-2.1.jar  ocr.SentenceAlignment anaccountameric01judsgoog_djvu.txt anaccountameric01judsgoog.truth anaccountameric01judsgoog.salign

time java -Xmx3g -cp ~/thesis/code/bin:/Applications/eclipse/plugins/org.codehaus.groovy_2.0.7.xx-20130703-1600-e43-RELEASE/lib/groovy-all-2.0.7.jar:/home/sam/thesis/code/gprof-0.3.0-groovy-2.1.jar ocr.Evaluator ../wwp/prose.train.vocab.txt ../channelmodel/anaccountameric01judsgoog.train.1.javaobj reflectionsuponm00aste.salign reflectionsuponm00aste.corr

date; ~/src/brown-cluster-master/wcluster --text prose.train.txt --c 200 --rand 0 --threads 4 &
date; ~/src/brown-cluster-master/wcluster --text ../wwp/prose.train.txt --min-occur 2 --c 100 --rand 0 --threads 4

# note - this gave error, so actually used WB
for c in 50 100 150; do ~/src/srilm/bin/macosx/ngram-count -kndiscount -interpolate -order 7  -text ~/thesis-data/tagngram/prose.train.clean1.c$c.cl.ip.txt -lm ~/thesis-data/tagngram/prose.train.clean1.c$c.cl.ip.lm.7kni.txt; done

time java -Xmx4G -cp ~/Dropbox/programs/workspace/Thesis/bin:/Applications/eclipse/plugins/org.codehaus.groovy_2.0.7.xx-20130703-1600-e43-RELEASE/lib/groovy-all-2.0.7.jar:/home/sam/thesis/code/gprof-0.3.0-groovy-2.1.jar ocr.Clustering prose.train-c200-p1.out/paths prose.train.shuffled.txt prose.train.max10.c200.txt 10
time java -Xmx4G -cp ~/Dropbox/programs/workspace/Thesis/bin:/Applications/eclipse/plugins/org.codehaus.groovy_2.0.7.xx-20130703-1600-e43-RELEASE/lib/groovy-all-2.0.7.jar:/home/sam/thesis/code/gprof-0.3.0-groovy-2.1.jar ocr.Clustering prose.train.lc-c100-p1.out/paths prose.train.shuffled.txt prose.train.4to10.c100cl.txt 4 10 -cl


head -n 5000 prose.train.max10.c200.txt >prose.train.max10.c200.5k.txt 
date; time /System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Home/bin/java -Xmx6000m -cp /Users/sam/src/jdageem-1.0/lib/colt.jar:/Users/sam/src/jdageem-1.0/lib/commons-cli-1.2.jar:/Users/sam/src/jdageem-1.0/lib/commons-math-2.2.jar:/Users/sam/src/jdageem-1.0/lib/pcollections-2.1.2.jar:/Users/sam/src/jdageem-1.0/lib/concurrent.jar:/Users/sam/src/jdageem-1.0/lib/jdageem.jar:/Users/sam/src/jdageem-1.0/lib/trove.jar edu.cmu.cs.lti.ark.dageem.Dageem  -em -emdropiter 1 -emiter 100 -initmodel @harmonic@ -modeloutputprefix prose.train.max10.c100.5k.model -traininput prose.train.max10.c100.5k.txt 
date; time java -Xmx6000m -agentpath:/Applications/YourKit_Java_Profiler_2013_build_13076.app/bin/mac/libyjpagent.jnilib -cp /Users/sam/src/jdageem-1.0/lib/colt.jar:/Users/sam/src/jdageem-1.0/lib/commons-cli-1.2.jar:/Users/sam/src/jdageem-1.0/lib/commons-math-2.2.jar:/Users/sam/src/jdageem-1.0/lib/pcollections-2.1.2.jar:/Users/sam/src/jdageem-1.0/lib/jdageem.jar:/Users/sam/src/jdageem-1.0/lib/trove.jar edu.cmu.cs.lti.ark.dageem.Dageem  -em -emdropiter 1 -emiter 2 -initmodel @harmonic@ -modeloutputprefix perftest -traininput prose.train.4to15.c200mcw.100.txt 


# DMV EM on PTB
date; time java -server -Xmx3G -cp ~/Dropbox/programs/workspace/Thesis/bin:/Users/sam/src/jdageem-1.0/lib/colt.jar:/Users/sam/src/jdageem-1.0/lib/commons-cli-1.2.jar:/Users/sam/src/jdageem-1.0/lib/commons-math-2.2.jar:/Users/sam/src/jdageem-1.0/lib/pcollections-2.1.2.jar:/Users/sam/src/jdageem-1.0/lib/trove.jar:/Applications/eclipse/plugins/org.codehaus.groovy_2.0.7.xx-20130703-1600-e43-RELEASE/lib/groovy-all-2.0.7.jar:/home/sam/thesis/code/gprof-0.3.0-groovy-2.1.jar ocr.DMVEM train.1to12.xp.tags -input-model harmonic model.train.1to12.xp.em/model. 1 200 -epsilon 1e-5

# DMV CE on PTB
for x in model.train.1to12.xp.error.unif; do date; mkdir $x; time java -server -Xmx3G -cp ~/Dropbox/programs/workspace/Thesis/bin:/Users/sam/src/jdageem-1.0/lib/colt.jar:/Users/sam/src/jdageem-1.0/lib/commons-cli-1.2.jar:/Users/sam/src/jdageem-1.0/lib/commons-math-2.2.jar:/Users/sam/src/jdageem-1.0/lib/pcollections-2.1.2.jar:/Users/sam/src/jdageem-1.0/lib/trove.jar:/Applications/eclipse/plugins/org.codehaus.groovy_2.0.7.xx-20130703-1600-e43-RELEASE/lib/groovy-all-2.0.7.jar:/home/sam/thesis/code/gprof-0.3.0-groovy-2.1.jar:/Users/sam/thesis/code/stanford-corenlp-3.2.0.jar ocr.DMVCE -vocab xp.vocab.txt  ../train.1to12.xp.tags $x/model. 0 -hood error -weighted; done

#for c in 100 ; do d=model.clean1.4to10.c$c.em ; echo $d; time java -server -Xmx4G -cp ~/Dropbox/programs/workspace/Thesis/bin:/Users/sam/src/jdageem-1.0/lib/colt.jar:/Users/sam/src/jdageem-1.0/lib/commons-cli-1.2.jar:/Users/sam/src/jdageem-1.0/lib/commons-math-2.2.jar:/Users/sam/src/jdageem-1.0/lib/pcollections-2.1.2.jar:/Users/sam/src/jdageem-1.0/lib/trove.jar:/Applications/eclipse/plugins/org.codehaus.groovy_2.0.7.xx-20130703-1600-e43-RELEASE/lib/groovy-all-2.0.7.jar:/home/sam/thesis/code/gprof-0.3.0-groovy-2.1.jar ocr.LatticeParser -string -model $d/model.last.dmv  ../wwp/prose.train.clean1.txt -min-length 4 -max-length 10 -tag-smoothing 1 -clustering ../clustering/prose.train.clean1.cl.ip-c$c-p1.out/paths -reestimate $d/model.last.tagalpha1.cnt  -estimate-lex $d/model.last.tagalpha1.lcnt > temp; done

# Estimation of lexical model from EM-trained DMV
for c in 100 150 200; do for s in 4to10 4to15; do for l in 15 30 50;  do m=model.prose2.$s.c$c.em.har; o=$m.1to$l.tagalpha1.lcnt; bsub -n 1  -q  ser-par-10g -J $o -o /scratch/scarano.s/data/grammar/$o.log ~/bin/runjava.sh ocr.LatticeParser -string -counts $m.cnt  ../wwp/prose2.train.1to$l.txt  -tag-smoothing 1 -clustering ~/scratch/data/clustering/prose.train.clean1.cl.ip-c$c-p1.out/paths   -estimate-lex $o  -quiet; done; done; done

# Re-estimate (tag) counts from CE models
for c in 100 150 200; do for s in 4to10 ; do for n in cetrans.har cetrans.unif ceerror.har ceerror.unif; do m=model.prose2.$s.c$c.$n; o=$m.cnt; bsub -n 1  -q  ser-par-10g -J $o -o /scratch/scarano.s/data/grammar/$o.log ~/bin/runjava.sh ocr.LatticeParser -string -model $m.dmv  ../wwp/prose2.train.txt  -min-length 4 -max-length 10 -clustering ~/scratch/data/clustering/prose.train.clean1.cl.ip-c$c-p1.out/paths   -reestimate $o  -quiet ; done; done; done

# Estimate lexical counts from CE-trained tag counts (from preceding command)
for c in 100 150 200; do for s in 4to10; do for n in cetrans.har cetrans.unif ceerror.har ceerror.unif; do for l in 15 30 50; do m=model.prose2.$s.c$c.$n; o=$m.1to$l.tagalpha1.lcnt; bsub -n 1  -q  ser-par-10g -J $o -o /scratch/scarano.s/data/grammar/$o.log ~/bin/runjava.sh ocr.LatticeParser -string -counts $m.cnt  ../wwp/prose2.train.1to$l.txt  -tag-smoothing 1 -clustering ~/scratch/data/clustering/prose.train.clean1.cl.ip-c$c-p1.out/paths   -estimate-lex $o  -quiet; done; done; done; done


