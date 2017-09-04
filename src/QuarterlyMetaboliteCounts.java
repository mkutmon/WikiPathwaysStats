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
import org.wikipathways.client.WikiPathwaysClient;

public class QuarterlyMetaboliteCounts {

	private String metDb = "C:/Users/martina.kutmon/Data/BridgeDb/metabolites_20170826.bridge";
	private WikiPathwaysClient client;
	private String today;
	
	private int startYear = 2011;
	private int endYear = Calendar.getInstance().get(Calendar.YEAR);

	private Organism org = Organism.HomoSapiens;
	// pathways to be included (all for selected species except Tutorial
	// pathways)
	private List<String> inclPathways;
	
	private IDMapper metMapper;
	
	// yearly snapshots -> key = year, value = map (key = pathway id, value =
	// revision)
	private Map<Integer, Map<Integer, Map<String, Integer>>> snapShots;
	
	public QuarterlyMetaboliteCounts() throws Exception {
		client = new WikiPathwaysClient(new URL("http://webservice.wikipathways.org"));
		inclPathways = new ArrayList<String>();
		snapShots = new HashMap<Integer, Map<Integer,Map<String, Integer>>>();
		
		DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
		Date date = new Date();	
		today = dateFormat.format(date);
		
		MIMShapes.registerShapes();

		Class.forName("org.bridgedb.rdb.IDMapperRdb");
		DataSourceTxt.init();
		metMapper = BridgeDb.connect("idmapper-pgdb:" + new File(metDb).getAbsolutePath());
	}
	
	public static void main(String[] args) throws Exception {
		QuarterlyMetaboliteCounts mg = new QuarterlyMetaboliteCounts();
		mg.inclPathways = Utils.getPathways(mg.client, mg.org);
		mg.snapShots = Utils.getQuarterlySnapshots(mg.today, mg.org, mg.startYear, mg.endYear, mg.inclPathways, mg.client);
		File pathwayFolder = new File("pathways_" + mg.today);
		pathwayFolder.mkdir();
		Utils.downloadPathwayFilesQuarterly(mg.snapShots, pathwayFolder, mg.client);
		
		System.out.println("calculate metabolite statistics");
		Map<String, Set<Xref>> xrefs = new HashMap<String, Set<Xref>>();
		for(Integer year : mg.snapShots.keySet()) {	
			for(Integer quarter : mg.snapShots.get(year).keySet()) {
				for(String pwId : mg.snapShots.get(year).get(quarter).keySet()) {
					Integer rev = mg.snapShots.get(year).get(quarter).get(pwId);
					String key = pwId + "-" + rev;
					if(!xrefs.containsKey(key)) {
						xrefs.put(key, new HashSet<Xref>());
						Pathway p = new Pathway();
						try {
							p.readFromXml(new File(pathwayFolder, pwId + "_" + rev + ".gpml"), false);
							for(PathwayElement e : p.getDataObjects()) {
								if(e.getObjectType().equals(ObjectType.DATANODE)) {
									if(e.getDataNodeType().equals("Metabolite")) {
										if(!e.getXref().getId().equals("") && e.getXref().getDataSource() != null) {
											Set<Xref> res = mg.metMapper.mapID(e.getXref(), DataSource.getExistingBySystemCode("Wd"));
											if(res.size() > 0) {
												xrefs.get(key).addAll(res);
											} else {
												Set<Xref> resCe = mg.metMapper.mapID(e.getXref(), DataSource.getExistingBySystemCode("Ce"));
												if(resCe.size() > 0) {
													for(Xref x : resCe) {
														if(x.getId().startsWith("CHEBI")) {
															xrefs.get(key).add(x);
														}
													}
												} else {
													Set<Xref> resCh = mg.metMapper.mapID(e.getXref(), DataSource.getExistingBySystemCode("Ch"));
													if(resCh.size() > 0) {
														xrefs.get(key).addAll(resCh);
													} else {
														xrefs.get(key).add(e.getXref());
													}
												}
											}
										}
									}
								}
							}
						} catch(Exception e) {
							
						}
					}
				}
			}
		}
		for(String id : xrefs.keySet()) {
			System.out.println(id + "\t" + xrefs.get(id).size() + "\t" + xrefs.get(id));
		}
		
//		Map<Integer, Set<Xref>> ids = new HashMap<Integer, Set<Xref>>();
		for(Integer year : mg.snapShots.keySet()) {
			for(Integer quarter : mg.snapShots.get(year).keySet()) {
				Set<Xref> uniqueXrefs = new HashSet<Xref>();
				for(String pwId : mg.snapShots.get(year).get(quarter).keySet()) {
					Integer rev = mg.snapShots.get(year).get(quarter).get(pwId);
					String key = pwId + "-" + rev;
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
				System.out.println(year + "\t" + quarter + "\t" + uniqueXrefs.size() + "\t" + wikidata + "\t" + chebi + "\t" + hmdb + "\t" + other);
//				ids.put(year, uniqueXrefs);
			}
		}
		
//		for(int i = mg.startYear; i < mg.endYear; i++) {
//			Set<Xref> current = ids.get(i);
//			Set<Xref> next = ids.get(i+1);
//			
//			Set<Xref> changed = new HashSet<Xref>();
//			changed.addAll(current);
//			changed.removeAll(next);
//			
//			System.out.println(i + "\t" + (i+1) + "\t" + changed.size() + "\t" + changed);
//			
//		}
	}

}
