package cs224n.corefsystems;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.*;

import cs224n.coref.ClusteredMention;
import cs224n.coref.Document;
import cs224n.coref.Entity;
import cs224n.coref.Mention;
import cs224n.util.Pair;

public class OneCluster implements CoreferenceSystem {

	@Override
	public void train(Collection<Pair<Document, List<Entity>>> trainingData) {
    // No need to do anything
    return;
	}

	@Override
	public List<ClusteredMention> runCoreference(Document doc) {
    List<ClusteredMention> mentions = new ArrayList<ClusteredMention>();
    Map<String,Entity> clusters = new HashMap<String,Entity>();

    String firstString = null;
    for(Mention m : doc.getMentions()){
      String mentionString = m.gloss();
      if (firstString != null) {
        mentions.add(m.markCoreferent(clusters.get(firstString)));
      } else {
        ClusteredMention newCluster = m.markSingleton();
        mentions.add(newCluster);
        clusters.put(mentionString,newCluster.entity);
        firstString = mentionString;
      }
    }
    return mentions;
  }

}
