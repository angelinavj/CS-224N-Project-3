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


public class BetterBaseline implements CoreferenceSystem {
  Map<String, Set<String> > trainCoreferenceData;

	@Override
  public void train(Collection<Pair<Document, List<Entity>>> trainingData) {
    trainCoreferenceData = new HashMap<String, Set<String> >();
    for(Pair<Document, List<Entity>> pair : trainingData){
      Document doc = pair.getFirst();
      List<Entity> clusters = pair.getSecond();
      List<Mention> mentions = doc.getMentions();

      //--Iterate Over Coreferent Mention Pairs
      for(Entity e : clusters){
        for(Pair<Mention, Mention> mentionPair : e.orderedMentionPairs()){
            String firstHeadWord = mentionPair.getFirst().headWord();
            String secondHeadWord = mentionPair.getSecond().headWord();

            if (!trainCoreferenceData.containsKey(firstHeadWord)) {
              trainCoreferenceData.put(firstHeadWord, new HashSet<String>());
            }
            trainCoreferenceData.get(firstHeadWord).add(secondHeadWord);
        }
      }
    }
    System.out.println(trainCoreferenceData);
	}

	@Override
	public List<ClusteredMention> runCoreference(Document doc) {
    List<ClusteredMention> mentions = new ArrayList<ClusteredMention>();
    Map<String,Entity> clusters = new HashMap<String,Entity>();

    String firstString = null;
    for(Mention m : doc.getMentions()){
      String mentionString = m.gloss();
      // Check if something is exactly the same

      if (clusters.containsKey(mentionString)) {
        mentions.add(m.markCoreferent(clusters.get(mentionString)));
        continue;
      } 

      // If we see this mentionString in the training data
      if (trainCoreferenceData.containsKey(mentionString)) {
        Set<String> coreferents = trainCoreferenceData.get(mentionString);
        boolean foundMatch = false;
        for (String coref: coreferents) {
          if (clusters.containsKey(coref)) {
            mentions.add(m.markCoreferent(clusters.get(coref)));
            foundMatch = true;
            break;
          }
        }
        if (foundMatch) continue;
      }
      // If it's a pronoun, look back for a name / another pronoun of the same type.

      if (Pronoun.isSomePronoun(mentionString)) {
        boolean foundMatch = false;

        for (String key: clusters.keySet()) {
          Pronoun p = Pronoun.valueOrNull(mentionString);
          if ((p != null) && (Name.gender(key) == p.gender)) {
            mentions.add(m.markCoreferent(clusters.get(key)));
            foundMatch = true;
            break;
          }
        }

        if (foundMatch) {
          continue;
        }
      }
      // Else, make a new cluster.
      ClusteredMention newCluster = m.markSingleton();
      mentions.add(newCluster);
      clusters.put(mentionString,newCluster.entity);
 
    }
    return mentions;
	}

}
