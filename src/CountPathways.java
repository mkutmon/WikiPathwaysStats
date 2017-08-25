import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.pathvisio.wikipathways.webservice.WSPathwayInfo;
import org.wikipathways.client.WikiPathwaysClient;


public class CountPathways {

	public static void main (String [] args) throws Exception {
		WikiPathwaysClient client = new WikiPathwaysClient(new URL("http://webservice.wikipathways.org"));
		WSPathwayInfo [] list = client.listPathways();
		Map<String, List<WSPathwayInfo>> map = new HashMap<String, List<WSPathwayInfo>>();
		
		for(WSPathwayInfo i : list) {
			if(!map.containsKey(i.getSpecies())) {
				map.put(i.getSpecies(), new ArrayList<WSPathwayInfo>());
			}
			map.get(i.getSpecies()).add(i);
		}
		
		int total = 0;
		for(String s : map.keySet()) {
			System.out.println(s + "\t" + map.get(s).size());
			total = total + map.get(s).size();
		}
		System.out.println("Total: " + total);
	}
}
