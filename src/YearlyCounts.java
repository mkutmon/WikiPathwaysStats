import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
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
import org.pathvisio.core.model.PathwayElement;
import org.pathvisio.core.view.MIMShapes;
import org.pathvisio.wikipathways.webservice.WSCurationTag;
import org.pathvisio.wikipathways.webservice.WSHistoryRow;
import org.pathvisio.wikipathways.webservice.WSPathway;
import org.pathvisio.wikipathways.webservice.WSPathwayHistory;
import org.pathvisio.wikipathways.webservice.WSPathwayInfo;
import org.wikipathways.client.WikiPathwaysClient;

public class YearlyCounts {

	private WikiPathwaysClient client;
	private Organism org = Organism.HomoSapiens;
	private List<String> inclPathways;
	private static int startYear = 2011;
	private static int endYear = Calendar.getInstance().get(Calendar.YEAR);
	private Map<Integer, Map<String, Integer>> snapShots;
	private String metDb = "C:/Users/MSK/Data/BridgeDb/metabolites_20170709.bridge";
	private String geneDb = "C:/Users/MSK/Data/BridgeDb/Hs_Derby_Ensembl_85.bridge";
	private IDMapper metMapper;
	private IDMapper geneMapper;
	private Map<String, Integer> notAnnotatedMets;
	private Map<String, Set<Xref>> genes = new HashMap<String, Set<Xref>>();
	private Map<String, Set<Xref>> metabolitesWd = new HashMap<String, Set<Xref>>();
	private Map<String, Set<Xref>> metabolitesCe = new HashMap<String, Set<Xref>>();
	
	public YearlyCounts() throws Exception {
		client = new WikiPathwaysClient(new URL("http://webservice.wikipathways.org"));
		inclPathways = new ArrayList<String>();
		snapShots = new HashMap<Integer, Map<String, Integer>>();
		notAnnotatedMets = new HashMap<String, Integer>();
		
		MIMShapes.registerShapes();
		
		Class.forName("org.bridgedb.rdb.IDMapperRdb");  
		DataSourceTxt.init();
		metMapper = BridgeDb.connect("idmapper-pgdb:" + new File(metDb).getAbsolutePath());
		geneMapper = BridgeDb.connect("idmapper-pgdb:" + new File(geneDb).getAbsolutePath());
	}
	
	public void getPathways() throws Exception {
		WSPathwayInfo[] list = client.listPathways(org);
		
		// exclude tutorial pathways
		WSCurationTag [] tutorial = client.getCurationTagsByName("Curation:Tutorial");
		Set<String> tutorialPathways = new HashSet<String>();
		for(WSCurationTag t : tutorial) {
			tutorialPathways.add(t.getPathway().getId());
		}
		for(WSPathwayInfo i : list) {
			if(!tutorialPathways.contains(i.getId())) {
				inclPathways.add(i.getId());
			}
		}
	}
	
	private void downloadPathwayFiles() throws Exception {
		for(Integer year : snapShots.keySet()) {
			for(String id : snapShots.get(year).keySet()) {
				Integer rev = snapShots.get(year).get(id);
				File f = new File("pathways", id + "_" + rev + ".gpml");
				if(!f.exists()) {
					WSPathway p = client.getPathway(id, rev);
					PrintWriter out = new PrintWriter(f);
					out.print(p.getGpml());
					out.close();
				}
			}
		}
	}
	
	private void getHistory() throws Exception {
		File output = new File("snapshots_" + org.shortName() + ".txt");
		if(!output.exists()) {
			Calendar c = Calendar.getInstance();
			c.set(2003, 1, 1, 0, 0, 0);	
			
			for(int i = startYear; i <= endYear; i++) {
				// yearly counts
				snapShots.put(i, new HashMap<String, Integer>());
			}
//			int count = 1;
			for(String pwId : inclPathways) {
//				System.out.println(pwId + "\t" + count + "\t" + pathways.size());
				WSPathwayHistory hist = client.getPathwayHistory(pwId, c.getTime());
				for(WSHistoryRow row : hist.getHistory()) {
					Integer rev = Integer.parseInt(row.getRevision());
					Integer y = Integer.parseInt(row.getTimestamp().substring(0, 4));
					Integer m = Integer.parseInt(row.getTimestamp().substring(4, 6));
					if(y < startYear) {
						for(int i = startYear; i <= endYear; i++) {
							snapShots.get(i).put(pwId, rev);
						}
					} else {
						if(m < 9) {
							for(int i = y; i <= endYear; i++) {
								snapShots.get(i).put(pwId, rev);
							}
						} else {
							int year = y + 1;
							for(int i = year; i <= endYear; i++) {
								snapShots.get(i).put(pwId, rev);
							}
						}
					}
				}
//				count++;
			}
			
			BufferedWriter writer = new BufferedWriter(new FileWriter(output));
			
			writer.write("Year\tCount pathways\tPathway revisions");
			for(Integer year : snapShots.keySet()) {
				writer.write("\n" + year + "\t" + snapShots.get(year).size() + "\t" + snapShots.get(year));
			}
			writer.close();
		} else {
			BufferedReader reader = new BufferedReader(new FileReader(output));
			String line;
			// read header
			reader.readLine();
			while((line = reader.readLine()) != null) {
				String [] buffer = line.split("\t");
				Integer year = Integer.parseInt(buffer[0]);
				snapShots.put(year, new HashMap<String, Integer>());
				String [] buffer2 = buffer[2].replace("{", "").replace("}","").split(", ");
				for(String s : buffer2) {
					String [] buffer3 = s.split("=");
					String wpId = buffer3[0];
					Integer rev = Integer.parseInt(buffer3[1]);
					snapShots.get(year).put(wpId, rev);
				}
			}
			reader.close();
		}
	}
	
	private void calculateCounts() throws IOException {
		String error = "";
		Set<String> errorPwy = new HashSet<String>();
		for(int year : snapShots.keySet()) {
			for(String pwId : snapShots.get(year).keySet()) {
				int rev = snapShots.get(year).get(pwId);
				String key = pwId + "-" + rev;
				if(!genes.containsKey(key)) {
					genes.put(key, new HashSet<Xref>());
					metabolitesWd.put(key, new HashSet<Xref>());
					metabolitesCe.put(key, new HashSet<Xref>());
					
					File f = new File("unifiedSets", key + ".txt");
					if(!f.exists()) {
						Pathway p = new Pathway();
						try {
							p.readFromXml(new File("pathways", pwId + "_" + rev + ".gpml"), false);
							List<Xref> xrefs = p.getDataNodeXrefs();
							for(Xref x : xrefs) {
								Set<Xref> gRes = geneMapper.mapID(x, DataSource.getExistingBySystemCode("En"));
								genes.get(key).addAll(gRes);
								Set<Xref> mRes = metMapper.mapID(x, DataSource.getExistingBySystemCode("Wd"));
								metabolitesWd.get(key).addAll(mRes);
								Set<Xref> mRes2 = metMapper.mapID(x, DataSource.getExistingBySystemCode("Ce"));
								for(Xref xref : mRes2) {
									if(xref.getId().startsWith("CHEBI")) {
										metabolitesCe.get(key).add(xref);
									}
								}
							}
							BufferedWriter writer = new BufferedWriter(new FileWriter(f));
							writer.write("#genes");
							for(Xref x : genes.get(key)) {
								writer.write("\n" + x);
							}
							writer.write("\n#metabolitesWd");
							for(Xref x : metabolitesWd.get(key)) {
								writer.write("\n" + x);
							}
							writer.write("\n#metabolitesCe");
							for(Xref x : metabolitesCe.get(key)) {
								writer.write("\n" + x);
							}
							writer.close();
							
							// count not annotated metabolites
							for(PathwayElement e : p.getDataObjects()) {
								if(e.getXref().getId().isEmpty()) {
									if(e.getObjectType().equals("DataNode")) {
										if(e.getDataNodeType().equals("Metabolite")) {
											if(!notAnnotatedMets.containsKey(key)) {
												notAnnotatedMets.put(key, 1);
											} else {
												int count = notAnnotatedMets.get(key);
												notAnnotatedMets.put(key, (count+1));
											}
										}
									}
								}
							}
						} catch(Exception e) {
							error = error + "\n" + key + "\n" + e.getStackTrace().toString() + "\n";
							e.printStackTrace();
							File f2 = new File("pathways", pwId + "_" + rev + ".gpml");
							f2.deleteOnExit();
							errorPwy.add(key);
						}
					} else {
						BufferedReader reader = new BufferedReader(new FileReader(f));
						String line;
						String current = "";
						while((line = reader.readLine()) != null) {
							if(line.contains("#genes")) {
								current = "gene";
							} else if (line.contains("#metabolitesWd")) {
								current = "wd";
							} else if (line.contains("metabolitesCe")) {
								current = "ce";
							} else {
								String [] buffer = line.split(":");
								String syscode = buffer[0];
								String id = buffer[1];
								Xref x = new Xref(id, DataSource.getExistingBySystemCode(syscode));
								switch (current) {
								case "gene":
									genes.get(key).add(x);
								case "wd":
									metabolitesWd.get(key).add(x);
								case "ce":
									metabolitesCe.get(key).add(x);
								}
							}
							
						}
						reader.close();
					}
				}
			}
		}
//		for(int year : snapShots.keySet()) {
//			int geneCount = 0;
//			for(String pwId : snapShots.get(year).keySet()) {
//				int rev = snapShots.get(year).get(pwId);
//				String key = pwId + "-" + rev;
//				
//			}
//		}
		System.out.println(error);
		System.out.println(errorPwy);
	}
	
	public static void main(String[] args) throws Exception {
		YearlyCounts yc = new YearlyCounts();
		yc.getPathways();
		System.out.println("[INFO]\t" + yc.inclPathways.size() + " pathways included in the analysis.");
		System.out.println("[INFO]\tRetrieving history for included pathways (this might take a while).");
		yc.getHistory();	
		System.out.println("[INFO]\tDownload GPML files for included pathways (this might take a while.");
		yc.downloadPathwayFiles();
		System.out.println("[INFO]\tCreate unified xref sets for all included pathways.");
		yc.calculateCounts();
	}
}
