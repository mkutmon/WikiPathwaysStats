package wp.nar2018;

import java.io.File;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bridgedb.bio.Organism;
import org.pathvisio.core.model.ObjectType;
import org.pathvisio.core.model.Pathway;
import org.pathvisio.core.model.PathwayElement;
import org.pathvisio.core.view.MIMShapes;
import org.pathvisio.wikipathways.webservice.WSPathwayInfo;
import org.wikipathways.client.WikiPathwaysClient;

/**
 * calculates how many not annotated metabolites are present in pathways
 * 
 * @author mkutmon
 *
 */
public class NotAnnotatedMetabolites {

	public static void main(String[] args) throws Exception {
		int startYear = 2011;
		int endYear = Calendar.getInstance().get(Calendar.YEAR);
		
		WikiPathwaysClient client = new WikiPathwaysClient(new URL("http://webservice.wikipathways.org"));
		List<WSPathwayInfo> inclPathways = new ArrayList<WSPathwayInfo>();
		Map<Integer, Map<String, Integer>> snapShots = new HashMap<Integer, Map<String, Integer>>();
		Organism org = Organism.HomoSapiens;
		
		DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
		Date date = new Date();	
		String today = dateFormat.format(date);
		
		MIMShapes.registerShapes();
		
		inclPathways = Utils.getPathways(client, org);
		snapShots = Utils.getHistory(today, org, startYear, endYear, inclPathways, client);
		File pathwayFolder = new File("pathways");
		pathwayFolder.mkdir();
		Utils.downloadPathwayFiles(snapShots, pathwayFolder, client);
		
		Map<String, Integer> countTotal = new HashMap<String, Integer>();
		Map<String, Integer> count = new HashMap<String, Integer>();
		Set<String> errors = new HashSet<>();
		for(Integer year : snapShots.keySet()) {	
			for(String pwId : snapShots.get(year).keySet()) {
				Integer rev = snapShots.get(year).get(pwId);
				String key = pwId + "-" + rev;
				if(!count.containsKey(key)) {
					File f = new File(pathwayFolder, pwId + "_" + rev + ".gpml");
					Pathway p = new Pathway();
					try {
						p.readFromXml(f, false);
						int notAnnotated = 0;
						int total = 0;
						for(PathwayElement e : p.getDataObjects()) {
							if(e.getObjectType().equals(ObjectType.DATANODE)) {
								if(e.getDataNodeType().equals("Metabolite")) {
									total++;
									if(e.getXref().getId().equals("")) {
										notAnnotated++;
									}
								}
							}
						}
						if(notAnnotated > 0) count.put(key, notAnnotated);
						if(total > 0) countTotal.put(key, total);
					} catch(Exception e) {
						errors.add(pwId + "\t" + rev);
						f.deleteOnExit();
						e.printStackTrace();
					}
				}
			}
		}
		
		System.out.println("Errors " + errors.size());
		
		System.out.println("Year\tTotal metabolite nodes\tNot annotated metabolites");
		for(Integer year : snapShots.keySet()) {	
			int notAnnotated = 0;
			int total = 0;
			for(String pwId : snapShots.get(year).keySet()) {
				Integer rev = snapShots.get(year).get(pwId);
				String key = pwId + "-" + rev;
				if(count.containsKey(key)) {
					notAnnotated = notAnnotated + count.get(key);
				}
				if(countTotal.containsKey(key)) {
					total = total + countTotal.get(key);
				}
			}
			System.out.println(year + "\t" + total + "\t" + notAnnotated);
		}
	}

}
