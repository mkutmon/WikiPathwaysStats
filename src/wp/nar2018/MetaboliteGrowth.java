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

import org.bridgedb.BridgeDb;
import org.bridgedb.DataSource;
import org.bridgedb.IDMapper;
import org.bridgedb.Xref;
import org.bridgedb.bio.DataSourceTxt;
import org.bridgedb.bio.Organism;
import org.pathvisio.core.model.ObjectType;
import org.pathvisio.core.model.Pathway;
import org.pathvisio.core.model.PathwayElement;
import org.pathvisio.core.view.MIMShapes;
import org.pathvisio.wikipathways.webservice.WSPathway;
import org.pathvisio.wikipathways.webservice.WSPathwayInfo;
import org.wikipathways.client.WikiPathwaysClient;

import com.sun.xml.internal.bind.v2.runtime.unmarshaller.LocatorEx.Snapshot;

import wp.nar2018.Utils;



public class MetaboliteGrowth {
	private WikiPathwaysClient client;
	private String today;
	
	private int startYear = 2011;
	private int endYear = Calendar.getInstance().get(Calendar.YEAR);

	private Organism org = Organism.HomoSapiens;
	// pathways to be included (all for selected species except Tutorial
	// pathways)
	private List<WSPathwayInfo> inclPathways;
	
//	private String metDb = "/home/msk/Data/BridgeDb/metabolites_20170709.bridge";
//	private String metDb = "C:/Users/martina.kutmon/Data/BridgeDb/metabolites_20170709.bridge";
	private String metDb = "C:/Users/martina.kutmon/Data/BridgeDb/metabolites_20170826.bridge";

	private IDMapper metMapper;
	
	// yearly snapshots -> key = year, value = map (key = pathway id, value =
	// revision)
	private Map<Integer, Map<String, Integer>> snapShots;
	
	File pathwayFolder = new File("C:/Users/martina.kutmon/owncloud/Data/WikiPathways/pathways-cache/");
	
	public MetaboliteGrowth() throws Exception {
		client = new WikiPathwaysClient(new URL("http://webservice.wikipathways.org"));
		inclPathways = new ArrayList<WSPathwayInfo>();
		snapShots = new HashMap<Integer, Map<String, Integer>>();
		
		DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
		Date date = new Date();	
		today = dateFormat.format(date);
		
		MIMShapes.registerShapes();

		Class.forName("org.bridgedb.rdb.IDMapperRdb");
		DataSourceTxt.init();
		metMapper = BridgeDb.connect("idmapper-pgdb:" + new File(metDb).getAbsolutePath());
	}
	
	public void run() throws Exception {
		inclPathways = Utils.getPathways(client, org);
		snapShots = Utils.getHistory(today, org, startYear, endYear, inclPathways, client);
		
		pathwayFolder.mkdir();
		Utils.downloadPathwayFiles(snapShots, pathwayFolder, client);
		
		System.out.println("calculate metabolite statistics");
		Map<String, Set<Xref>> xrefs = new HashMap<String, Set<Xref>>();
		for(Integer year : snapShots.keySet()) {	
			for(String pwId : snapShots.get(year).keySet()) {
				Integer rev = snapShots.get(year).get(pwId);
				String key = pwId + "-" + rev;
				if(!xrefs.containsKey(key)) {
					xrefs.put(key, new HashSet<Xref>());
					Pathway p = new Pathway();
					p.readFromXml(new File(pathwayFolder, pwId + "_" + rev + ".gpml"), false);
					xrefs.get(key).addAll(getUniqueXrefs(p));
				}
			}
		}
		
//		printPathwayMetaboliteCounts(xrefs);
		
		Map<Integer, Set<Xref>> ids = new HashMap<Integer, Set<Xref>>();
		for(Integer year : snapShots.keySet()) {
			Set<Xref> uniqueXrefs = new HashSet<Xref>();
			for(String pwId : snapShots.get(year).keySet()) {
				String key = pwId + "-" + snapShots.get(year).get(pwId);
				uniqueXrefs.addAll(xrefs.get(key));
			}
			
			int wikidata = 0;
			int chebi = 0;
			int hmdb = 0;
			int other = 0;
			for(Xref x : uniqueXrefs) {
				if(x.getDataSource().getSystemCode() != null) {
					if(x.getDataSource().getSystemCode().equals("Wd")) {
						wikidata++;
					} else if(x.getDataSource().getSystemCode().equals("Ce")) {
						chebi++;
					} else if(x.getDataSource().getSystemCode().equals("Ch")) {
						hmdb++;
					} else {
						other++;
					} 
				} else {
					other++;
				}
			}
			System.out.println(year + "\t" + uniqueXrefs.size() + "\t" + wikidata + "\t" + chebi + "\t" + hmdb + "\t" + other);
			ids.put(year, uniqueXrefs);
		}
		
		for(int i = startYear; i < endYear; i++) {
			Set<Xref> current = ids.get(i);
			Set<Xref> next = ids.get(i+1);
			
			Set<Xref> changed = new HashSet<Xref>();
			changed.addAll(current);
			changed.removeAll(next);
			
			System.out.println(i + "\t" + (i+1) + "\t" + changed.size() + "\t" + changed);
			
		}
	}
	
	private void printPathwayMetaboliteCounts(Map<String, Set<Xref>> xrefs) {
		for(String id : xrefs.keySet()) {
			System.out.println(id + "\t" + xrefs.get(id).size() + "\t" + xrefs.get(id));
		}
	}
	
	private Set<Xref> getUniqueXrefs(Pathway p) throws Exception {
		Set<Xref> set = new HashSet<Xref>();
		for(PathwayElement e : p.getDataObjects()) {
			if(e.getObjectType().equals(ObjectType.DATANODE)) {
				if(e.getDataNodeType().equals("Metabolite")) {
					if(!e.getXref().getId().equals("") && e.getXref().getDataSource() != null) {
						Set<Xref> res = metMapper.mapID(e.getXref(), DataSource.getExistingBySystemCode("Wd"));
						if(res.size() > 0) {
							set.addAll(res);
						} else {
							Set<Xref> resCe = metMapper.mapID(e.getXref(), DataSource.getExistingBySystemCode("Ce"));
							if(resCe.size() > 0) {
								for(Xref x : resCe) {
									if(x.getId().startsWith("CHEBI")) {
										set.add(x);
									}
								}
							} else {
								Set<Xref> resCh = metMapper.mapID(e.getXref(), DataSource.getExistingBySystemCode("Ch"));
								if(resCh.size() > 0) {
									set.addAll(resCh);
								} else {
									set.add(e.getXref());
								}
							}
						}
					}
				}
			}
		}
		return set;
	}
	
	public static void main(String[] args) throws Exception {
		MetaboliteGrowth mg = new MetaboliteGrowth();
		mg.run();
	}

}
