package net.aprendizajengrande.gitrecommender;

import java.io.File;

import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.eval.RecommenderBuilder;
import org.apache.mahout.cf.taste.eval.RecommenderEvaluator;
import org.apache.mahout.cf.taste.impl.eval.RMSRecommenderEvaluator;
import org.apache.mahout.cf.taste.impl.model.file.FileDataModel;
import org.apache.mahout.cf.taste.impl.neighborhood.NearestNUserNeighborhood;
import org.apache.mahout.cf.taste.impl.recommender.GenericItemBasedRecommender;
import org.apache.mahout.cf.taste.impl.recommender.GenericUserBasedRecommender;
import org.apache.mahout.cf.taste.impl.similarity.EuclideanDistanceSimilarity;
import org.apache.mahout.cf.taste.impl.similarity.PearsonCorrelationSimilarity;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.neighborhood.UserNeighborhood;
import org.apache.mahout.cf.taste.recommender.Recommender;
import org.apache.mahout.cf.taste.similarity.ItemSimilarity;
import org.apache.mahout.cf.taste.similarity.UserSimilarity;

public class Experiment {

	public static void main(String[] args) throws Exception {

		DataModel model = new FileDataModel(new File(args[0]));

		System.out.println("For repo: " + args[0]);
		System.out.println("User-based:");

		RecommenderEvaluator evaluator = new RMSRecommenderEvaluator();
		RecommenderBuilder recommenderBuilder = new RecommenderBuilder() {
			@Override
			public Recommender buildRecommender(DataModel model)
					throws TasteException {
				UserSimilarity similarity = new PearsonCorrelationSimilarity(
						model);
				UserNeighborhood neighborhood = new NearestNUserNeighborhood(2,
						similarity, model);
				return new GenericUserBasedRecommender(model, neighborhood,
						similarity);
			}
		};
		// Use 90% of the data to train; test using the other 10%.
		double score = evaluator.evaluate(recommenderBuilder, null, model, 0.9,
				1.0);
		System.out.println(score);

		recommenderBuilder = new RecommenderBuilder() {
			@Override
			public Recommender buildRecommender(DataModel model)
					throws TasteException {
				ItemSimilarity similarity =
						new EuclideanDistanceSimilarity(model);
						//new LogLikelihoodSimilarity(model);
				return new GenericItemBasedRecommender(model, similarity);
			}
		};
		System.out.println("Item-based:");
		score = evaluator.evaluate(recommenderBuilder, null, model, 0.9, 1.0);
		System.out.println(score);
	}
}
