package cs224n.corefsystems;

import java.util.Collection;
import java.util.List;

import cs224n.coref.ClusteredMention;
import cs224n.coref.Document;
import cs224n.coref.Mention;
import cs224n.coref.Document;
import cs224n.coref.Entity;
import cs224n.util.Pair;

import java.util.ArrayList;
import java.util.*;

public class AllSingleton implements CoreferenceSystem {

	@Override
	public void train(Collection<Pair<Document, List<Entity>>> trainingData) {
    // Does not have to do anything
    return;
	}

	@Override
	public List<ClusteredMention> runCoreference(Document doc) {
    List<ClusteredMention> mentions = new ArrayList <ClusteredMention>();
    Map<String, Entity> clusters = new HashMap<String, Entity>();

    for (Mention m: doc.getMentions()) {
      String mentionString = m.gloss();
      ClusteredMention newCluster = m.markSingleton();
      mentions.add(newCluster);
      clusters.put(mentionString, newCluster.entity);
    }
		return mentions;
	}

}
