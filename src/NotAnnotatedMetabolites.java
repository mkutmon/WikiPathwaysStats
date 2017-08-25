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

import org.bridgedb.BridgeDb;
import org.bridgedb.IDMapper;
import org.bridgedb.bio.DataSourceTxt;
import org.bridgedb.bio.Organism;
import org.pathvisio.core.model.ObjectType;
import org.pathvisio.core.model.Pathway;
import org.pathvisio.core.model.PathwayElement;
import org.pathvisio.core.view.MIMShapes;
import org.wikipathways.client.WikiPathwaysClient;


public class NotAnnotatedMetabolites {

	public static void main(String[] args) throws Exception {
		int startYear = 2011;
		int endYear = Calendar.getInstance().get(Calendar.YEAR);
		WikiPathwaysClient client = new WikiPathwaysClient(new URL("http://webservice.wikipathways.org"));
		List<String> inclPathways = new ArrayList<String>();
		Map<Integer, Map<String, Integer>> snapShots = new HashMap<Integer, Map<String, Integer>>();
		Organism org = Organism.HomoSapiens;
		
		DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
		Date date = new Date();	
		String today = dateFormat.format(date);
		
		MIMShapes.registerShapes();

		Class.forName("org.bridgedb.rdb.IDMapperRdb");
		DataSourceTxt.init();
		String metDb = "C:/Users/martina.kutmon/Data/BridgeDb/metabolites_20170709.bridge";
		IDMapper metMapper = BridgeDb.connect("idmapper-pgdb:" + new File(metDb).getAbsolutePath());
		
		inclPathways = Utils.getPathways(client, org);
		snapShots = Utils.getHistory(today, org, startYear, endYear, inclPathways, client);
		File pathwayFolder = new File("pathways_" + today);
		pathwayFolder.mkdir();
		Utils.downloadPathwayFiles(snapShots, pathwayFolder, client);
		
		Map<String, Integer> count = new HashMap<String, Integer>();
		for(Integer year : snapShots.keySet()) {	
			for(String pwId : snapShots.get(year).keySet()) {
				Integer rev = snapShots.get(year).get(pwId);
				String key = pwId + "-" + rev;
				if(!count.containsKey(key)) {
					Pathway p = new Pathway();
					try {
						p.readFromXml(new File(pathwayFolder, pwId + "_" + rev + ".gpml"), false);
						int notAnnotated = 0;
						for(PathwayElement e : p.getDataObjects()) {
							if(e.getObjectType().equals(ObjectType.DATANODE)) {
								if(e.getDataNodeType().equals("Metabolite")) {
									if(e.getXref().getId().equals("")) {
										notAnnotated++;
									}
								}
							}
						}
						if(notAnnotated > 0) count.put(key, notAnnotated);
					} catch(Exception e) {
						e.printStackTrace();
					}
				}
			}
		}
		
		for(String pwId : snapShots.get(2017).keySet()) {
			Integer rev = snapShots.get(2017).get(pwId);
			String key = pwId + "-" + rev;
			if(count.containsKey(key)) {
				System.out.println(pwId + "\t" + count.get(key));
			}
		}
		
		for(Integer year : snapShots.keySet()) {	
			int notAnnotated = 0;
			for(String pwId : snapShots.get(year).keySet()) {
				Integer rev = snapShots.get(year).get(pwId);
				String key = pwId + "-" + rev;
				if(count.containsKey(key)) {
					notAnnotated = notAnnotated + count.get(key);
				}
			}
			System.out.println(year + "\t" + notAnnotated);
		}
	}

}
