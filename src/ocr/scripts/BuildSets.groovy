package ocr.scripts

import ocr.SimpleTokenizer;
import ocr.util.CommandLineParser;


def clp = new CommandLineParser("-tokensuffix=s", args)
def tokenSuffix = clp.opt("-tokensuffix", ".tokens")
def (inputFilename, prefix, dataDirname) = clp.args() as List
File inputFile = new File(inputFilename)
File dataDir = new File(dataDirname)
def (trainSetFile, devSetFile, testSetFile) = ['train', 'dev', 'test'].collect {
	new File("${prefix}.${it}.set.txt")
}
def (trainFile, devFile, testFile) = ['train', 'dev', 'test'].collect {
	new File("${prefix}.${it}.txt")
}

Set trainSet = [] as Set
Set devSet = """
	astell.marriage
	judson.account
	williams.fallriver
	chandler.essays
	child.appeal
	cushing.saratoga1
	cushing.saratoga2
""".split() as Set
def testSet = """
	astell.proposal
	edgeworth.letters
	gannett.address
	holley.texas
	kilham.memoir
	lee.life
	royall.alabama
	prince.narrativelife
	sanders.aborigine
""".split() as Set

def availableList = []
inputFile.eachLine {
	if (!devSet.contains(it) && !testSet.contains(it))
		availableList.add(it)
}

//int devSetSize = (int) (availableList.size()*0.10)
//int testSetSize = (int) (availableList.size()*0.10)

//Collections.shuffle(availableList, new Random(2))

//devSet.addAll(availableList.take(devSetSize - devSet.size()))
availableList.removeAll(devSet)

//testSet.addAll(availableList.take(testSetSize - testSet.size()))
availableList.removeAll(testSet)

trainSet.addAll(availableList)

[[trainSetFile, trainSet], [devSetFile, devSet], [testSetFile, testSet]].each { File file, set ->
	file.withWriter { out ->
		set.sort().each { out.println(it) }
	}
}

[[trainFile, trainSet], [devFile, devSet], [testFile, testSet]].each { File file, set ->
	file.withWriter('utf-8') { out ->
		set.sort().collect { new File(dataDir, "$it$tokenSuffix") }.each { File dataFile ->
			dataFile.eachLine('utf-8') { line -> out.println(line) }
		}
	}
}

new File("${prefix}.train.deh.txt").withWriter('utf-8') { outDeh ->
	new File("${prefix}.train.deh.lc.txt").withWriter('utf-8') { outLc ->
		trainSet.sort().collect { new File(dataDir, "$it$tokenSuffix") }.each { File dataFile ->
			dataFile.eachLine('utf-8') { line ->
				def dehLine = SimpleTokenizer.dehyphenate(line)
				outDeh.println dehLine
				outLc.println dehLine.toLowerCase()
			}
		}
	}
}











	