gitrecommender
==============

Recommends GIT files based on previous commits of the author 
and the other authors in the repo. (Over time, it might also
recommend files to newcomers casual browsing.)

Case study for http://aprendizajengrande.net

Inspired by Ying (2003) "Predicting software changes by mining 
revision history" http://www.annieying.ca/papers/03thesis-aying.pdf

Current usage
-------------

*you need to have Mahout 1.0 installed from source in your local repo,
configured for Hadoop 2.0, see below*

mvn clean package assembly:single

java -cp j./target/gitrecommender-0.0.1-SNAPSHOT-jar-with-dependencies.jar net.aprendizajengrande.gitrecommender.UpdateLog /path/to/.git /path/to/db

(consumes git history and updates a local DB)

java -cp j./target/gitrecommender-0.0.1-SNAPSHOT-jar-with-dependencies.jar net.aprendizajengrande.gitrecommender.db.DB /path/to/db /path/to/recos/suffix

(creates input files for Mahout itembasedrecommender)

The Hadoop job Recommend will take a DB and produce recommendations using a Hadoop cluster.

Finally, the "Experiment" class allows for single-node experimentation with the non-Hadoop recommenders in Mahout.

Installing Mahout from source
-----------------------------

<pre>
$ git clone https://github.com/apache/mahout.git
$ cd mahout
$ mvn clean package -DskipTests -Drelease -Dmahout.skip.distribution=false -Dhadoop.profile=200 -Dhadoop2.version=2.4.1 -Dhbase.version=0.98.0-hadoop2
</pre>

Sample DB
---------

http://aprendizajengrande.net/clases/material/linux_git_100k_commits.tar.bz2
