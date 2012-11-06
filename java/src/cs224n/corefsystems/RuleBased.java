package cs224n.corefsystems;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.*;

import cs224n.ling.Tree;

import cs224n.coref.ClusteredMention;
import cs224n.coref.Document;
import cs224n.coref.Entity;
import cs224n.coref.Sentence;
import cs224n.coref.Mention;
import cs224n.coref.Name;
import cs224n.coref.Pronoun;
import cs224n.util.Pair;
import cs224n.coref.Util;

public class RuleBased implements CoreferenceSystem {
  private final int NUM_PRIORITY = 6;
  Map<String, Set<String> > trainHeadCoreference;
  Map<Tree<String>, Mention> parseToMentionMap;

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


  private boolean isHighestSInSentence(Tree<String> tree,
                  Map<Tree<String>, Tree<String> > parentInfo) {
    if (!tree.getLabel().equals("S")) {
      return false;
    }
    Tree<String> curTree = parentInfo.get(tree);
    while (curTree != null) {
      if (curTree.getLabel().equals("S")) {
        return false;
      }
      curTree = parentInfo.get(curTree);
    }
    return true;
  }

  private Tree<String> closestNPOrSAncestor(Tree<String> subtree,
                    Map<Tree<String>, Tree<String> > parentInfo,
                    List<Tree<String> > topDownPath) {


    Tree<String> currentTree = subtree;
    Tree<String> resultTree = null;


    if (topDownPath != null) {
      topDownPath.removeAll(topDownPath);
      topDownPath.add(currentTree);
    }

    currentTree = parentInfo.get(currentTree);

    while (currentTree != null) {
      if (topDownPath != null) 
        topDownPath.add(currentTree);

      if (currentTree.getLabel().equals("NP") ||
          currentTree.getLabel().equals("S")) {
        resultTree = currentTree = currentTree;
      }

      currentTree = parentInfo.get(currentTree);
    }
    if (topDownPath != null)
      Collections.reverse(topDownPath);
    return resultTree;
  }

  
  private Mention getHobbsMatch (Document doc, Mention m) {
    Map<Tree<String>, Tree<String> > parentInfo = new HashMap<Tree<String>, Tree<String> > ();
    Tree.extractParentRelationship(m.sentence.parse, parentInfo);

    // 1. Begin at NP
    Tree<String> dominatingNP = m.parse;
    if (!m.parse.getLabel().equals("NP")) {
      dominatingNP = parentInfo.get(parentInfo.get(m.parse));
    }

    // 2. Go up tree to first NP or S.

    List<Tree<String> > pathP = new ArrayList<Tree<String> > ();
    Tree<String> XTree = closestNPOrSAncestor(dominatingNP,
                                              parentInfo,
                                              pathP);

    if (XTree == null) return null;
    Tree<String> currentTree = null;

    // 3. Traverse all branches below X to the left of p, left-to-right,
    // breadth-first.
    // Propose as antecedent any NP that has a NP or S between it and X.
    boolean found = false;

    for (Tree<String> tree: Tree.getBFSTraversalWithRightBoundary(XTree, pathP)) {
      
      if (tree.getLabel().equals("NP")) {
        // traverse up to X and check whether it has NP or S
        currentTree = parentInfo.get(tree);
        
        while ((currentTree != null) && (!currentTree.equals(XTree))){
          if (currentTree.getLabel().equals("NP") ||
            currentTree.getLabel().equals("S")) {
            Mention match = parseToMentionMap.get(tree);
            if ((match != null) && (doesPassConstraints(m, match))) {
              return parseToMentionMap.get(tree);
            }
            break;
          }
          currentTree = parentInfo.get(currentTree);
        }
      }
    }
    //4. If X is the highest S in the sentence:  
    if (isHighestSInSentence(XTree, parentInfo)) {
      // Traverse parse trees of previous sentences in order of recency.
      for (int i = doc.indexOfSentence(m.sentence) - 1; i >= 0; i--) {
        Sentence curSentence = doc.sentences.get(i);
        for (Tree<String> tree: Tree.getBFSTraversalWithRightBoundary(curSentence.parse, null)) {
          if (tree.getLabel().equals("NP")) {
            Mention match = parseToMentionMap.get(tree);
            if ((match != null) && (doesPassConstraints(m, match))) {
              return parseToMentionMap.get(tree);
            }
          }
        }
      }
    } else {

      // 6. From node X, go up the three to the first NP or S.
      XTree = closestNPOrSAncestor(XTree, parentInfo, pathP);


    }
    return m;
  }



	@Override
	public List<ClusteredMention> runCoreference(Document doc) {
    Set<Set<Mention> > clusters = new HashSet<Set<Mention> >();
    List<ClusteredMention> mentions = new ArrayList<ClusteredMention>();
    parseToMentionMap = new HashMap<Tree<String>, Mention>();

    for (Mention m: doc.getMentions()) {
      Set<Mention> cluster = new HashSet<Mention>();
      cluster.add(m);
      clusters.add(cluster);
      parseToMentionMap.put(m.parse, m);
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

    for (Set<Mention> cluster1: clusters) {
      boolean foundMatch = false;
      for (Mention m1: cluster1) {
        if (Pronoun.isSomePronoun(m1.gloss())) {
          Mention match = getHobbsMatch(doc, m1);

          for (Set<Mention> cluster2: clusters) {
            if (cluster1.equals(cluster2)) continue;

            for (Mention m2: cluster2) {
              if (m2.equals(match)) {
                foundMatch = true;
                break;
              }
            }
            if (foundMatch) {
              cluster1.addAll(cluster2);
              cluster2.removeAll(cluster2);
              break;
            }
          }
        }
        if (foundMatch) {
          break;
        }
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
