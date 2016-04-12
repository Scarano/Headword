/**
 * Output the tag vocabulary for a cluster (the most common word of each cluster)
 */

package ocr.scripts

import ocr.Clustering
import ocr.util.CommandLineParser

def clp = new CommandLineParser("-add-root", args)
def clusteringFilename = clp.arg(0)
def vocabFilename = clp.arg(1)

def clustering = new Clustering(new File(clusteringFilename), true, true)

clustering.saveVocab(new File(vocabFilename), clp.opt("-add-root"))






