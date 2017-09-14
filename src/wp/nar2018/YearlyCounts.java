package wp.nar2018;

import java.io.File;
import java.io.IOException;
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
import org.bridgedb.DataSource;
import org.bridgedb.IDMapper;
import org.bridgedb.Xref;
import org.bridgedb.bio.DataSourceTxt;
import org.bridgedb.bio.Organism;
import org.pathvisio.core.model.Pathway;
import org.pathvisio.core.view.MIMShapes;
import org.pathvisio.wikipathways.webservice.WSPathwayInfo;
import org.wikipathways.client.WikiPathwaysClient;

public class YearlyCounts {

	private WikiPathwaysClient client;
	private String today;
	
	private static int startYear = 2011;
	private static int endYear = Calendar.getInstance().get(Calendar.YEAR);

	private Organism org = Organism.HomoSapiens;
	// pathways to be included (all for selected species except Tutorial
	// pathways)
	private List<WSPathwayInfo> inclPathways;

	// yearly snapshots -> key = year, value = map (key = pathway id, value =
	// revision)
	private Map<Integer, Map<String, Integer>> snapShots;

	private String geneDb = "C:/Users/martina.kutmon/Data/BridgeDb/Hs_Derby_Ensembl_85.bridge";
	private IDMapper geneMapper;

	private Map<String, Set<Xref>> genes = new HashMap<String, Set<Xref>>();

	private String unifiedGene = "En";		
	private File pathwayFolder = new File("C:/Users/martina.kutmon/owncloud/Data/WikiPathways/pathways-cache/");
	

	public YearlyCounts() throws Exception {
		client = new WikiPathwaysClient(new URL("http://webservice.wikipathways.org"));
		inclPathways = new ArrayList<WSPathwayInfo>();
		snapShots = new HashMap<Integer, Map<String, Integer>>();

		MIMShapes.registerShapes();

		Class.forName("org.bridgedb.rdb.IDMapperRdb");
		DataSourceTxt.init();
		geneMapper = BridgeDb.connect("idmapper-pgdb:" + new File(geneDb).getAbsolutePath());
		
		DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
		Date date = new Date();	
		today = dateFormat.format(date);
	}

	/**
	 * finds all pathways for a specific species ignores tutorial pathways
	 * 
	 * retrieves the yearly history of WikiPathways
	 * always finds the last revision before September of that year
	 * to be included in the yearly snapshot
	 * 
	 * downloads all GPML files for each pathway revision needed only if file
	 * does not yet occur in pathway folder
	 */
	private void retrieveHistory() throws Exception {
		inclPathways = Utils.getPathways(client, org);
		snapShots = Utils.getHistory(today, org, startYear, endYear, inclPathways, client);
		Utils.downloadPathwayFiles(snapShots, pathwayFolder, client);
	}

	private void calculateCounts() throws IOException {
		String error = "";
		Set<String> errorPwy = new HashSet<String>();
		for (int year : snapShots.keySet()) {
			for (String pwId : snapShots.get(year).keySet()) {
				int rev = snapShots.get(year).get(pwId);
				String key = pwId + "-" + rev;
				if (!genes.containsKey(key)) {
					genes.put(key, new HashSet<Xref>());

					Pathway p = new Pathway();
					try {
						p.readFromXml(new File(pathwayFolder, pwId + "_" + rev + ".gpml"), false);
						List<Xref> xrefs = p.getDataNodeXrefs();
						for (Xref x : xrefs) {
							Set<Xref> gRes = geneMapper.mapID(x, DataSource.getExistingBySystemCode(unifiedGene));
							genes.get(key).addAll(gRes);
						}
					} catch (Exception e) {
						error = error + "\n" + key + "\n" + e.getStackTrace().toString() + "\n";
						errorPwy.add(key);
					}
				}
			}
		}
		System.out.println("Year\tpathways\tgenes\tmetabolites");
		for (int year : snapShots.keySet()) {
			Set<Xref> geneCount = new HashSet<Xref>();
			for (String pwId : snapShots.get(year).keySet()) {
				int rev = snapShots.get(year).get(pwId);
				String key = pwId + "-" + rev;
				geneCount.addAll(genes.get(key));
			}
			System.out.println(year + "\t" + snapShots.get(year).size() + "\t" + geneCount.size());
		}
	}

	public static void main(String[] args) throws Exception {
		YearlyCounts yc = new YearlyCounts();
		yc.retrieveHistory();
		yc.calculateCounts();
	}
}
