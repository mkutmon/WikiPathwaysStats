package wp.nar2018;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.pathvisio.wikipathways.webservice.WSCurationTag;
import org.pathvisio.wikipathways.webservice.WSPathwayInfo;
import org.wikipathways.client.WikiPathwaysClient;


/**
 * retrieve current number of pathways per species
 * excluding test/tutorial pathways
 * @author mkutmon
 *
 */
public class CountPathways {

	public static void main (String [] args) throws Exception {
		WikiPathwaysClient client = new WikiPathwaysClient(new URL("http://webservice.wikipathways.org"));
		WSPathwayInfo [] list = client.listPathways();
		
		Map<String, List<WSPathwayInfo>> map = new HashMap<String, List<WSPathwayInfo>>();
		
		WSCurationTag[] tutorial = client.getCurationTagsByName("Curation:Tutorial");
		Set<String> tutorialPathways = new HashSet<String>();
		for (WSCurationTag t : tutorial) {
			tutorialPathways.add(t.getPathway().getId());
		}
		
		for(WSPathwayInfo i : list) {
			if(!tutorialPathways.contains(i.getId())) {
				if(!map.containsKey(i.getSpecies())) {
					map.put(i.getSpecies(), new ArrayList<WSPathwayInfo>());
				}
				map.get(i.getSpecies()).add(i);
			}
		}
		
		int total = 0;
		for(String species : map.keySet()) {
			System.out.println(species + "\t" + map.get(species).size());
			total = total + map.get(species).size();
		}
		System.out.println("Total: " + total);
	}
}
