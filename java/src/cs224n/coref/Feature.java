package cs224n.coref;

import cs224n.util.Pair;

import java.util.Set;

/**
 * @author Gabor Angeli (angeli at cs.stanford)
 */
public interface Feature {

	//-----------------------------------------------------------
	// TEMPLATE FEATURE TEMPLATES
	//-----------------------------------------------------------
	public static class PairFeature implements Feature {
		public final Pair<Feature,Feature> content;
		public PairFeature(Feature a, Feature b){ this.content = Pair.make(a, b); }
		public String toString(){ return content.toString(); }
		public boolean equals(Object o){ return o instanceof PairFeature && ((PairFeature) o).content.equals(content); }
		public int hashCode(){ return content.hashCode(); }
	}

	public static abstract class Indicator implements Feature {
		public final boolean value;
		public Indicator(boolean value){ this.value = value; }
		public boolean equals(Object o){ return o instanceof Indicator && o.getClass().equals(this.getClass()) && ((Indicator) o).value == value; }
		public int hashCode(){ 
			return this.getClass().hashCode() ^ Boolean.valueOf(value).hashCode(); }
		public String toString(){ 
			return this.getClass().getSimpleName() + "(" + value + ")"; }
	}

	public static abstract class IntIndicator implements Feature {
		public final int value;
		public IntIndicator(int value){ this.value = value; }
		public boolean equals(Object o){ return o instanceof IntIndicator && o.getClass().equals(this.getClass()) && ((IntIndicator) o).value == value; }
		public int hashCode(){ 
			return this.getClass().hashCode() ^ value; 
		}
		public String toString(){ return this.getClass().getSimpleName() + "(" + value + ")"; }
	}

	public static abstract class BucketIndicator implements Feature {
		public final int bucket;
		public final int numBuckets;
		public BucketIndicator(int value, int max, int numBuckets){
			this.numBuckets = numBuckets;
			bucket = value * numBuckets / max;
			if(bucket < 0 || bucket >= numBuckets){ throw new IllegalStateException("Bucket out of range: " + value + " max="+max+" numbuckets="+numBuckets); }
		}
		public boolean equals(Object o){ return o instanceof IntIndicator && o.getClass().equals(this.getClass()) && ((IntIndicator) o).value == bucket; }
		public int hashCode(){ return this.getClass().hashCode() ^ bucket; }
		public String toString(){ return this.getClass().getSimpleName() + "(" + bucket + "/" + numBuckets + ")"; }
	}

	public static abstract class Placeholder implements Feature {
		public Placeholder(){ }
		public boolean equals(Object o){ return o instanceof Placeholder && o.getClass().equals(this.getClass()); }
		public int hashCode(){ return this.getClass().hashCode(); }
		public String toString(){ return this.getClass().getSimpleName(); }
	}

	public static abstract class StringIndicator implements Feature {
		public final String str;
		public StringIndicator(String str){ this.str = str; }
		public boolean equals(Object o){ return o instanceof StringIndicator && o.getClass().equals(this.getClass()) && ((StringIndicator) o).str.equals(this.str); }
		public int hashCode(){ return this.getClass().hashCode() ^ str.hashCode(); }
		public String toString(){ return this.getClass().getSimpleName() + "(" + str + ")"; }
	}

	public static abstract class SetIndicator implements Feature {
		public final Set<String> set;
		public SetIndicator(Set<String> set){ this.set = set; }
		public boolean equals(Object o){ return o instanceof SetIndicator && o.getClass().equals(this.getClass()) && ((SetIndicator) o).set.equals(this.set); }
		public int hashCode(){ return this.getClass().hashCode() ^ set.hashCode(); }
		public String toString(){
			StringBuilder b = new StringBuilder();
			b.append(this.getClass().getSimpleName());
			b.append("( ");
			for(String s : set){
				b.append(s).append(" ");
			}
			b.append(")");
			return b.toString();
		}
	}

	/*
	 * TODO: If necessary, add new feature types
	 */

	//-----------------------------------------------------------
	// REAL FEATURE TEMPLATES
	//-----------------------------------------------------------

	public static class CoreferentIndicator extends Indicator {
		public CoreferentIndicator(boolean coreferent){ super(coreferent); }
	}

	public static class ExactMatch extends Indicator {
		public ExactMatch(boolean exactMatch){ super(exactMatch); }
	}

	public static class FixedIsPronoun extends Indicator {
		public FixedIsPronoun(boolean fixedIsPronoun){ super(fixedIsPronoun); }
	}

	public static class CandIsPronoun extends Indicator {
		public CandIsPronoun(boolean candIsPronoun){ super(candIsPronoun); }
	}

	public static class FixedIsName extends Indicator {
		public FixedIsName(boolean featureBool){ super(featureBool); }
	}

	public static class CandIsName extends Indicator {
		public CandIsName(boolean featureBool){ super(featureBool); }
	}

	public static class FixedIsDef extends Indicator {
		public FixedIsDef(boolean featureBool){ super(featureBool); }
	}

	public static class CandIsDef extends Indicator {
		public CandIsDef(boolean featureBool){ super(featureBool); }
	}

	public static class GenderAgreement extends Indicator {
		public GenderAgreement(boolean featureBool){ super(featureBool); }
	}

	public static class WordDist extends IntIndicator {
		public WordDist(int wordDist){ super(wordDist); }
	}

	public static class CandGender extends IntIndicator {
		public CandGender(int featureVal){ super(featureVal); }
	}

	public static class FixedGender extends IntIndicator {
		public FixedGender(int featureVal){ super(featureVal); }
	}

	public static class PersonAgreement extends Indicator {
		public PersonAgreement(boolean featureVal){ super(featureVal); }
	}

	public static class PersonCand extends IntIndicator {
		public PersonCand(int featureVal){ super(featureVal); }
	}
	public static class PersonFixed extends IntIndicator {
		public PersonFixed(int featureVal){ super(featureVal); }
	}

	public static class HeadMatch extends Indicator {
		public HeadMatch(boolean featureVal){ super(featureVal); }
	}
	/*
	 * TODO: Add values to the indicators here.
	 */

}
