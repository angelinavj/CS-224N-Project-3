package cs224n.corefsystems;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.*;

import cs224n.coref.ClusteredMention;
import cs224n.coref.Document;
import cs224n.coref.Entity;
import cs224n.coref.Mention;
import cs224n.coref.Name;
import cs224n.coref.Pronoun;
import cs224n.util.Pair;
import cs224n.coref.Util;

public class RuleBased implements CoreferenceSystem {
  private final int NUM_PRIORITY = 6;
  Map<String, Set<String> > trainHeadCoreference;
	@Override
  public void train(Collection<Pair<Document, List<Entity>>> trainingData) {
    trainHeadCoreference = new HashMap<String, Set<String> >(); 

    for(Pair<Document, List<Entity>> pair : trainingData){
      Document doc = pair.getFirst();
      List<Entity> clusters = pair.getSecond();

      //--Iterate Over Coreferent Mention Pairs
      for(Entity e : clusters){
        for(Pair<Mention, Mention> mentionPair : e.orderedMentionPairs()){
            String firstHeadWord = mentionPair.getFirst().headWord();
            String secondHeadWord = mentionPair.getSecond().headWord();

            if ((Pronoun.isSomePronoun(firstHeadWord)) ||
                  (Pronoun.isSomePronoun(secondHeadWord))) {
              continue;
            }

            if (!trainHeadCoreference.containsKey(firstHeadWord)) {
              trainHeadCoreference.put(firstHeadWord, new HashSet<String>());
            }
            trainHeadCoreference.get(firstHeadWord).add(secondHeadWord);
        }
      }
    }
	}

  private boolean similarSubsentence(Mention m1, Mention m2) {
    List<String> words1 = m1.text();
    List<String> words2 = m2.text();
    double threshold = 0.65;

    if (words1.get(0).toLowerCase().equals("the") &&
        words2.get(0).toLowerCase().equals("the")) 
        return false;

    int sameWord = 0;
    for (int i = 0; i < words1.size(); i++) {

      for (int j = 0; j < words2.size(); j++) {
        if (words2.get(j).equals(words1.get(i))) {
          sameWord++;
        }
      }
    }

    return ((sameWord >= threshold * words1.size()) && (sameWord >= threshold * words2.size()));
  }

  private boolean doesPassConstraints(Mention m1, Mention m2) {
    // NER tagging constraints
    if (!m1.headToken().nerTag().equals("O") && !m2.headToken().nerTag().equals("0")) {
      if (!m1.headToken().nerTag().equals(m2.headToken().nerTag())) {
        return false;
      }
    }

    // Gender constraints
    Pair<Boolean, Boolean> genderInfo = Util.haveGenderAndAreSameGender(m1, m2);
    if (genderInfo.getFirst() && !genderInfo.getSecond()) {
      return false;
    }

    // Number constraints
    Pair<Boolean, Boolean> numberInfo = Util.haveNumberAndAreSameNumber(m1, m2);
    if (numberInfo.getFirst() && !numberInfo.getSecond()) {
      return false;
    }
    return true;
  }


  private boolean isAppositive(Mention m1, Mention m2) {

    if (!m1.sentence.equals(m2.sentence)) return false;

    if (!m1.parse.getLabel().equals(m2.parse.getLabel())) return false;
    if (!m1.parse.getLabel().equals("NP")) return false;

    if ((m1.endIndexExclusive == m2.beginIndexInclusive) ||
          (m1.endIndexExclusive + 1 == m2.beginIndexInclusive &&
            m1.sentence.posTags.get(m1.endIndexExclusive).equals("VBD"))) {
      return true;
    }
    return false;
  }

  private boolean isCompatibleModifier(Mention m1, Mention m2) {
    List<String> modifiers1 = m1.getModifiers();
    List<String> modifiers2 = m2.getModifiers();
    for (String mo1 : modifiers1) {
      boolean exists = false;
      for (String mo2 : modifiers2) {
        if (mo1.equals(mo2)) {
          exists = true;
          break;
        }
      }
      if (!exists) {
        return false;
      }
    }
    return true;
  }
  private boolean isMatchWithPriority(Set<Mention> cluster1, Set<Mention> cluster2, int priority) {
    switch (priority) {
      case 1:
              for (Mention m1: cluster1) {
                for (Mention m2: cluster2) {
                  if ((m1.gloss().equals(m2.gloss())) &&
                          (!Pronoun.isSomePronoun(m1.gloss()))){
                    // Exact string matching
                    return true;
                  }
                }
              }

              return false;
      case 2:
              for (Mention m1: cluster1) {
                for (Mention m2: cluster2) {
                  Set<String> coreferenceSet = trainHeadCoreference.get(m1.headWord());
                  if ((coreferenceSet != null) && (coreferenceSet.contains(m2.headWord()))) {
                    return true;
                  }

                }
              }

              return false;
      case 3:
          // Constructs

          for (Mention m1: cluster1) {
            for (Mention m2: cluster2) {
              // Shouldn't have used this line :-( how to fix?
              if (!m1.headWord().equals(m2.headWord())) continue;
              if (isAppositive(m1,m2) && (doesPassConstraints(m1, m2))) {
                return true;
              }

            }
          }
          return false; 
      case 4:
          // Word Inclusion
          for (Mention m1: cluster1) {
            String s1 = m1.gloss();

            if ((Pronoun.isSomePronoun(s1)) || (m1.text().size() < 2))continue;

            for (Mention m2: cluster2) {
              String s2 = m2.gloss();

              if (!m1.headWord().equals(m2.headWord()) ||
                  (Pronoun.isSomePronoun(s2)) || (m2.text().size() < 2))continue;

              if ((s1.indexOf(s2) != -1) && (doesPassConstraints(m1, m2))){

                return true;
              }
            }
          }

          return false; 
      case 5:
        // Compatible modifier
        for (Mention m1: cluster1) {
            String s1 = m1.gloss();

            if (Pronoun.isSomePronoun(s1)) continue;

            for (Mention m2: cluster2) {
              String s2 = m2.gloss();

              if (!m1.headWord().equals(m2.headWord())) continue; 

              if ((isCompatibleModifier(m1, m2)) && (doesPassConstraints(m1, m2))){

                return true;
              }
            }
          }

          return false; 

      default: return false;
    }
  }


	@Override
	public List<ClusteredMention> runCoreference(Document doc) {
    Set<Set<Mention> > clusters = new HashSet<Set<Mention> >();
    List<ClusteredMention> mentions = new ArrayList<ClusteredMention>();

    for (Mention m: doc.getMentions()) {
      Set<Mention> cluster = new HashSet<Mention>();
      cluster.add(m);
      clusters.add(cluster);
    }
    

    for (int i = 1; i <= NUM_PRIORITY; i++) {
      boolean foundMatch = true;

      while (foundMatch) {
        foundMatch = false;
        for (Set<Mention> cluster1: clusters) {

          for (Set<Mention> cluster2: clusters) {
            if (cluster1.equals(cluster2)) continue;

            if (isMatchWithPriority(cluster1, cluster2, i)) {
              cluster1.addAll(cluster2);
              cluster2.removeAll(cluster2);
              foundMatch = true;
              break;
            }
          }
        }
      }

    }

    // Work on pronouns
    for (Set<Mention> cluster1: clusters) {

      Set<Mention> bestSet = null;
      int closestDist = -1;
      for (Mention m1 : cluster1) {
        if (Pronoun.valueOrNull(m1.gloss()) == null) continue;

        for (Set<Mention> cluster2: clusters) {
          if (cluster1.equals(cluster2)) continue;
          for (Mention m2: cluster2) {
            Pronoun p = Pronoun.valueOrNull(m1.gloss());
            Pronoun p2 = Pronoun.valueOrNull(m2.gloss());
            if ((Name.gender(m2.gloss()) == p.gender) &&
                  ((p2 == null) || (p.speaker == p2.speaker))) {
              int dist = Math.abs(doc.indexOfMention(m1) - doc.indexOfMention(m2));
              if ((closestDist == -1) || (dist < closestDist)){
                closestDist = dist;
                bestSet = cluster2;
              }
            }
          }
        }

      }
      
      if (bestSet != null) {
        System.out.println(closestDist + " " + cluster1 + " | " + bestSet);
        bestSet.addAll(cluster1);
        cluster1.removeAll(cluster1);
      }
    }
    for (Set<Mention> cluster: clusters) {
      ClusteredMention curCluster = null;
      // System.out.println("====");
      for (Mention m: cluster) {
        // System.out.println(m + " " + m.headToken().posTag());
        if (curCluster == null) {
          curCluster = m.markSingleton();
          mentions.add(curCluster);
        } else {
          mentions.add(m.markCoreferent(curCluster));
        }
      }
    }
    return mentions;
	}

}
