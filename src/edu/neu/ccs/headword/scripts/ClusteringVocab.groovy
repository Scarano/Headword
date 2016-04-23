/**
 * Output the tag vocabulary for a cluster (the most common word of each cluster)
 */

package edu.neu.ccs.headword.scripts

import edu.neu.ccs.headword.Clustering
import edu.neu.ccs.headword.util.CommandLineParser

def clp = new CommandLineParser("-add-root", args)
def clusteringFilename = clp.arg(0)
def vocabFilename = clp.arg(1)

def clustering = new Clustering(new File(clusteringFilename), true, true)

clustering.saveVocab(new File(vocabFilename), clp.opt("-add-root"))






