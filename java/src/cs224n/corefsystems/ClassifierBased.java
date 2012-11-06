package cs224n.corefsystems;

import cs224n.coref.*;
import cs224n.util.Pair;
import edu.stanford.nlp.classify.LinearClassifier;
import edu.stanford.nlp.classify.LinearClassifierFactory;
import edu.stanford.nlp.classify.RVFDataset;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Triple;
import edu.stanford.nlp.util.logging.RedwoodConfiguration;
import edu.stanford.nlp.util.logging.StanfordRedwoodConfiguration;

import java.text.DecimalFormat;
import java.util.*;

import static edu.stanford.nlp.util.logging.Redwood.Util.*;

/**
 * @author Gabor Angeli (angeli at cs.stanford)
 */
public class ClassifierBased implements CoreferenceSystem {

	private static <E> Set<E> mkSet(E[] array){
		Set<E> rtn = new HashSet<E>();
		Collections.addAll(rtn, array);
		return rtn;
	}
	
	private static final Set<Object> ACTIVE_FEATURES = mkSet(new Object[]{
			Feature.ExactMatch.class,
			//Feature.FixedIsPronoun.class,
			//Feature.CandIsPronoun.class,
			Feature.WordDist.class,
			//Feature.CandIsName.class,
			//Feature.FixedIsName.class,
			//Feature.FixedIsDef.class,
			//Feature.CandIsDef.class,
			Feature.GenderAgreement.class,
			Feature.HeadMatch.class,
			//Feature.PersonAgreement.class,
			Pair.make(Feature.PersonCand.class, Feature.PersonFixed.class),
			//Pair.make(Feature.CandGender.class, Feature.FixedGender.class),
			Pair.make(Feature.FixedIsPronoun.class, Feature.CandIsPronoun.class),
			//Pair.make(Feature.FixedIsName.class, Feature.CandIsName.class),
			Pair.make(Feature.FixedIsDef.class, Feature.CandIsDef.class),
	});	


	private LinearClassifier<Boolean,Feature> classifier;

	public ClassifierBased(){
		StanfordRedwoodConfiguration.setup();
		RedwoodConfiguration.current().collapseApproximate().apply();
	}

	public FeatureExtractor<Pair<Mention,ClusteredMention>,Feature,Boolean> extractor = new FeatureExtractor<Pair<Mention, ClusteredMention>, Feature, Boolean>() {
		private <E> Feature feature(Class<E> clazz, Pair<Mention,ClusteredMention> input, Option<Double> count){
			
			//--Variables
			Mention onPrix = input.getFirst(); //the first mention (referred to as m_i in the handout)
			Mention candidate = input.getSecond().mention; //the second mention (referred to as m_j in the handout)
			Entity candidateCluster = input.getSecond().entity; //the cluster containing the second mention

			//--Features
			if(clazz.equals(Feature.ExactMatch.class)){
				//(exact string match)
				return new Feature.ExactMatch(onPrix.gloss().equals(candidate.gloss()));
			} else if(clazz.equals(Feature.FixedIsPronoun.class)) {
				return new Feature.FixedIsPronoun(Pronoun.isSomePronoun(onPrix.gloss()));
			} else if(clazz.equals(Feature.CandIsPronoun.class)) {
				return new Feature.CandIsPronoun(Pronoun.isSomePronoun(candidate.gloss()));
			} else if(clazz.equals(Feature.WordDist.class)) {
				int wordDist = Math.abs(onPrix.beginIndexInclusive - candidate.endIndexExclusive);
				if(wordDist > 20) 	return new Feature.WordDist(4);
				if(wordDist > 10) 	return new Feature.WordDist(3);
				if(wordDist > 5) 	return new Feature.WordDist(2);
				if(wordDist > 2) 	return new Feature.WordDist(1);
				return new Feature.WordDist(0);

			} else if(clazz.equals(Feature.CandIsName.class)) {
				return new Feature.CandIsName(Name.isName(candidate.gloss()));
			} else if(clazz.equals(Feature.FixedIsName.class)) {
				return new Feature.FixedIsName(Name.isName(onPrix.gloss()));
			} else if(clazz.equals(Feature.FixedIsDef.class)) {
				return new Feature.FixedIsDef(onPrix.gloss().toLowerCase().startsWith("the "));
			}else if(clazz.equals(Feature.CandIsDef.class)) {
				return new Feature.CandIsDef(candidate.gloss().toLowerCase().startsWith("the "));
			}else if(clazz.equals(Feature.GenderAgreement.class)) {
				Gender candGender = null;
				if(Name.isName(candidate.gloss())) {
					candGender = Name.gender(candidate.gloss());
				} else if(Pronoun.isSomePronoun(candidate.gloss())) {
					Pronoun candPronoun = Pronoun.valueOrNull(candidate.gloss());
					if(candPronoun != null)
						candGender = Pronoun.valueOrNull(candidate.gloss()).gender;
				}
				Gender fixedGender = null;
				if(Name.isName(onPrix.gloss())) {
					fixedGender = Name.gender(onPrix.gloss());
				} else if(Pronoun.isSomePronoun(onPrix.gloss())) {
					Pronoun fixedPronoun = Pronoun.valueOrNull(onPrix.gloss());
					if(fixedPronoun != null)
						fixedGender = Pronoun.valueOrNull(onPrix.gloss()).gender;
				}
				if(candGender==null || fixedGender == null)
					return new Feature.GenderAgreement(true);
				//if(candGender == fixedGender)
				//	System.out.println("Same gender: " + candidate.gloss() + ", " + onPrix.gloss());
				return new Feature.GenderAgreement(candGender != fixedGender);
			} else if(clazz.equals(Feature.PersonAgreement.class)) {
				int person1 = -1;
				int person2 = -1;
				if(Pronoun.isSomePronoun(candidate.gloss())) {
					person1 = Pronoun.person(candidate.gloss());
				}
				if(Pronoun.isSomePronoun(onPrix.gloss())) {
					person2 = Pronoun.person(onPrix.gloss());
				}
				return new Feature.PersonAgreement((person1!=-1 && person2!=-1 && person1==person2));
			} else if(clazz.equals(Feature.PersonCand.class)) {
				return new Feature.PersonCand(Pronoun.person(candidate.gloss()));
			} else if(clazz.equals(Feature.PersonFixed.class)) {
				return new Feature.PersonFixed(Pronoun.person(onPrix.gloss()));
			} else if(clazz.equals(Feature.HeadMatch.class)) {
				return new Feature.HeadMatch(candidate.headWord().equalsIgnoreCase(onPrix.headWord()));
			}
			else {
				throw new IllegalArgumentException("Unregistered feature: " + clazz);
			}
		}

		@SuppressWarnings({"unchecked"})
		@Override
		protected void fillFeatures(Pair<Mention, ClusteredMention> input, Counter<Feature> inFeatures, Boolean output, Counter<Feature> outFeatures) {
			//--Input Features
			for(Object o : ACTIVE_FEATURES){
				if(o instanceof Class){
					//(case: singleton feature)
					Option<Double> count = new Option<Double>(1.0);
					Feature feat = feature((Class) o, input, count);
					if(count.get() > 0.0){
						inFeatures.incrementCount(feat, count.get());
					}
				} else if(o instanceof Pair){
					//(case: pair of features)
					Pair<Class,Class> pair = (Pair<Class,Class>) o;
					Option<Double> countA = new Option<Double>(1.0);
					Option<Double> countB = new Option<Double>(1.0);
					Feature featA = feature(pair.getFirst(), input, countA);
					Feature featB = feature(pair.getSecond(), input, countB);
					if(countA.get() * countB.get() > 0.0){
						inFeatures.incrementCount(new Feature.PairFeature(featA, featB), countA.get() * countB.get());
					}
				}
			}

			//--Output Features
			if(output != null){
				outFeatures.incrementCount(new Feature.CoreferentIndicator(output), 1.0);
			}
		}

		@Override
		protected Feature concat(Feature a, Feature b) {
			return new Feature.PairFeature(a,b);
		}
	};

	public void train(Collection<Pair<Document, List<Entity>>> trainingData) {
		startTrack("Training");
		//--Variables
		RVFDataset<Boolean, Feature> dataset = new RVFDataset<Boolean, Feature>();
		LinearClassifierFactory<Boolean, Feature> fact = new LinearClassifierFactory<Boolean,Feature>();
		//--Feature Extraction
		startTrack("Feature Extraction");
		for(Pair<Document,List<Entity>> datum : trainingData){
			//(document variables)
			Document doc = datum.getFirst();
			List<Entity> goldClusters = datum.getSecond();
			List<Mention> mentions = doc.getMentions();
			Map<Mention,Entity> goldEntities = Entity.mentionToEntityMap(goldClusters);
			startTrack("Document " + doc.id);
			//(for each mention...)
			for(int i=0; i<mentions.size(); i++){
				//(get the mention and its cluster)
				Mention onPrix = mentions.get(i);
				Entity source = goldEntities.get(onPrix);
				if(source == null){ throw new IllegalArgumentException("Mention has no gold entity: " + onPrix); }
				//(for each previous mention...)
				int oldSize = dataset.size();
				for(int j=i-1; j>=0; j--){
					//(get previous mention and its cluster)
					Mention cand = mentions.get(j);
					Entity target = goldEntities.get(cand);
					if(target == null){ throw new IllegalArgumentException("Mention has no gold entity: " + cand); }
					//(extract features)
					Counter<Feature> feats = extractor.extractFeatures(Pair.make(onPrix, cand.markCoreferent(target)));
					//(add datum)
					dataset.add(new RVFDatum<Boolean, Feature>(feats, target == source));
					//(stop if
					if(target == source){ break; }
				}
				//logf("Mention %s (%d datums)", onPrix.toString(), dataset.size() - oldSize);
			}
			endTrack("Document " + doc.id);
		}
		endTrack("Feature Extraction");
		//--Train Classifier
		startTrack("Minimizer");
		this.classifier = fact.trainClassifier(dataset);
		endTrack("Minimizer");
		//--Dump Weights
		startTrack("Features");
		//(get labels to print)
		Set<Boolean> labels = new HashSet<Boolean>();
		labels.add(true);
		//(print features)
		for(Triple<Feature,Boolean,Double> featureInfo : this.classifier.getTopFeatures(labels, 0.0, true, 100, true)){
			Feature feature = featureInfo.first();
			Boolean label = featureInfo.second();
			Double magnitude = featureInfo.third();
			log(FORCE,new DecimalFormat("0.000").format(magnitude) + " [" + label + "] " + feature);
		}
		end_Track("Features");
		endTrack("Training");
	}

	public List<ClusteredMention> runCoreference(Document doc) {
		//--Overhead
		startTrack("Testing " + doc.id);
		//(variables)
		List<ClusteredMention> rtn = new ArrayList<ClusteredMention>(doc.getMentions().size());
		List<Mention> mentions = doc.getMentions();
		int singletons = 0;
		//--Run Classifier
		for(int i=0; i<mentions.size(); i++){
			//(variables)
			Mention onPrix = mentions.get(i);
			int coreferentWith = -1;
			//(get mention it is coreferent with)
			for(int j=i-1; j>=0; j--){
				ClusteredMention cand = rtn.get(j);
				boolean coreferent = classifier.classOf(new RVFDatum<Boolean, Feature>(extractor.extractFeatures(Pair.make(onPrix, cand))));
				if(coreferent){
					coreferentWith = j;
					break;
				}
			}
			//(mark coreference)
			if(coreferentWith < 0){
				singletons += 1;
				rtn.add(onPrix.markSingleton());
			} else {
				//log("Mention " + onPrix + " coreferent with " + mentions.get(coreferentWith));
				rtn.add(onPrix.markCoreferent(rtn.get(coreferentWith)));
			}
		}
		//log("" + singletons + " singletons");
		//--Return
		endTrack("Testing " + doc.id);
		return rtn;
	}

	private class Option<T> {
		private T obj;
		public Option(T obj){ this.obj = obj; }
		public Option(){};
		public T get(){ return obj; }
		public void set(T obj){ this.obj = obj; }
		public boolean exists(){ return obj != null; }
	}
}
