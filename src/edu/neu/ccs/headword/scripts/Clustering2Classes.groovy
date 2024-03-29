/**
 * Read "paths" file from wcluster, and output an SRILM classes file.
 * Clusters are identified by their most commonly occurring word.
 */

package edu.neu.ccs.headword.scripts

import edu.neu.ccs.headword.Clustering

def (clusteringFilename, classesFilename) = args
def caseless = args.length < 3 ? false : (args[2] == "-cl")

def clustering = new Clustering(new File(clusteringFilename), caseless, true)

clustering.saveSRILMClasses(new File(classesFilename))






