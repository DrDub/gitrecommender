gitrecommender
==============

Recommends GIT files based on previous commits of the author 
and the other authors in the repo. Contain also a simple recommender 
of files to newcomers for casual browsing.

Case study for http://aprendizajengrande.net (see tag famaf2014 for the
version used in class)

Inspired by Ying (2003) "Predicting software changes by mining 
revision history" http://www.annieying.ca/papers/03thesis-aying.pdf

Current usage
-------------

mvn clean package assembly:single

java -cp ./target/gitrecommender-0.0.3-SNAPSHOT-jar-with-dependencies.jar net.aprendizajengrande.gitrecommender.UpdateLog /path/to/.git /path/to/db

(consumes git history and updates a local DB)

java -cp ./target/gitrecommender-0.0.3-SNAPSHOT-jar-with-dependencies.jar net.aprendizajengrande.gitrecommender.db.DB /path/to/db /path/to/recos/suffix

(creates input files for Mahout itembasedrecommender)

The Hadoop job Recommend will take a DB and produce recommendations using a Hadoop cluster.

Finally, the "Experiment" class allows for single-node experimentation with the non-Hadoop recommenders in Mahout.

Alternatively, the "Recommend" main will use a hadoop cluster with the Mahout itembasedrecommender:

java -cp ./target/gitrecommender-0.0.3-SNAPSHOT-jar-with-dependencies.jar net.aprendizajengrande.gitrecommender.Recommend /path/to/db /hdfs/folder/for/input /hdfs/folder/for/output /path/to/output/file

Sample DB
---------

http://aprendizajengrande.net/clases/material/linux_git_100k_commits.tar.bz2

API Demo Server
---------------

A very inefficient, very slow demo API server is provided, follow the instructions about to obtain a fat jar, then launch a server:

java -cp ./target/gitrecommender-0.0.3-SNAPSHOT-jar-with-dependencies.jar net.aprendizajengrande.gitrecommender.api.GitRecommenderServer 8088

This will launch the server listening to the port 8088

To test it, create a file with a task and post it to http://localhost:8088/recommend, for example using curl:


curl -Xpost --data @task2.json http://localhost:8088/recommend
{"recommendation":[
{"file":"examples/src/main/resources/donut.csv","score":10000},
{"file":"math/src/main/java/org/apache/mahout/math/random/Sampler.java","score":10000},
{"file":"conf/cat.props","score":10000},
{"file":"math/src/main/java/org/apache/mahout/math/jet/random/ExponentialPower.java","score":10000},
{"file":"math/src/main/java/org/apache/mahout/math/jet/random/sampling/RandomSampler.java","score":10000},
{"file":"math/src/test/java/org/apache/mahout/math/ssvd/SequentialBigSvdTest.java","score":10000},
{"file":"core/src/main/java/org/apache/mahout/classifier/sgd/PriorFunction.java","score":10000},
{"file":"math/src/main/java/org/apache/mahout/math/jet/random/Logarithmic.java","score":10000},
{"file":"core/src/main/java/org/apache/mahout/classifier/sgd/AdaptiveWordValueEncoder.java","score":10000},
{"file":"core/src/main/java/org/apache/mahout/ep/package.html","score":10000}]}

(Actual output for the second task below)

Sample tasks to try:

{ "repository": "https://github.com/fatiherikli/fil.git", "files" : [
 "master/examples/hello.py", "gh-pages/index.html", "master/images/logo.png",
 "master/workers/opal.js" ] }

{"repository":"https://github.com/apache/mahout.git",
"files":["master/math/src/main/java/org/apache/mahout/math/flavor/MatrixFlavor.java",
"master/math/src/main/java/org/apache/mahout/math/random/IndianBuffet.java",
"master/math/src/main/java/org/apache/mahout/math/random/PoissonSampler.java"]}

See GitRecommenderServer javadocs for details. 

Limitations: single user, very slow, produces plenty of temporary files and doesn't use branches at the moment.  

