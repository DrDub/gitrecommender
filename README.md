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
configured for Hadoop 2.0*

mvn clean package assembly:single

java -cp j./target/gitrecommender-0.0.1-SNAPSHOT-jar-with-dependencies.jar net.aprendizajengrande.gitrecommender.UpdateLog /path/to/.git /path/to/db

(consumes git history and updates a local DB)

java -cp j./target/gitrecommender-0.0.1-SNAPSHOT-jar-with-dependencies.jar net.aprendizajengrande.gitrecommender.db.DB /path/to/db /path/to/recos/suffix

(creates input files for Mahout itembasedrecommender)

The Hadoop job Recommend will take a DB and produce recommendations using a Hadoop cluster.

Finally, the "Experiment" class allows for single-node experimentation with the non-Hadoop recommenders in Mahout.

   