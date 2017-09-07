package wp.other;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jdk.jfr.events.FileWriteEvent;

import org.bridgedb.BridgeDb;
import org.bridgedb.DataSource;
import org.bridgedb.IDMapper;
import org.bridgedb.Xref;
import org.bridgedb.bio.DataSourceTxt;
import org.bridgedb.bio.Organism;
import org.pathvisio.core.model.ObjectType;
import org.pathvisio.core.model.Pathway;
import org.pathvisio.core.model.PathwayElement;
import org.pathvisio.wikipathways.webservice.WSPathwayInfo;
import org.wikipathways.client.WikiPathwaysClient;

import wp.nar2018.Utils;

public class MetabolicPathwayNetwork {

//	private String metDb = "C:/Users/MSK/Data/BridgeDb/metabolites_20170826.bridge";
	private String metDb = "C:/Users/martina.kutmon/Data/BridgeDb/metabolites_20170826.bridge";
	private String commonMetFile = "common-metabolites.txt";
	
	private WikiPathwaysClient client;
	private String today;
	
	private Organism org = Organism.HomoSapiens;
	private List<WSPathwayInfo> inclPathways;
	
	private IDMapper metMapper;
	
	private File pathwayFolder = new File("C:/Users/martina.kutmon/owncloud/Data/WikiPathways/pathways-cache/");
	private Set<String> commonMetabolites;
	
	public MetabolicPathwayNetwork() throws Exception {
		client = new WikiPathwaysClient(new URL("http://webservice.wikipathways.org"));
		inclPathways = new ArrayList<WSPathwayInfo>();
		commonMetabolites = new HashSet<String>();
		
		DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
		Date date = new Date();	
		today = dateFormat.format(date);
		
		Class.forName("org.bridgedb.rdb.IDMapperRdb");
		DataSourceTxt.init();
		metMapper = BridgeDb.connect("idmapper-pgdb:" + new File(metDb).getAbsolutePath());
	}
	
	public void generateNetwork(File outputFile) throws Exception {
		inclPathways = Utils.getCuratedPathways(client, org);
		readCommonMetabolites();
		HashMap<String, Set<Xref>> xrefs = new HashMap<String, Set<Xref>>();
		HashMap<Xref, Set<String>> occurences = new HashMap<Xref, Set<String>>();
		
		BufferedWriter writer = new BufferedWriter(new FileWriter(new File("nodes.txt")));
		writer.write("Key\tPathwayName\tPathwayID\tMet\n");
		for(WSPathwayInfo i : inclPathways) {
			String pwId = i.getId();
			String rev = i.getRevision();
			String key = pwId + "_" + rev;
			Utils.downloadPathway(pwId, Integer.parseInt(rev), client, pathwayFolder);
			File f = new File(pathwayFolder, key + ".gpml");
			Pathway p = new Pathway();
			p.readFromXml(f, false);	
			xrefs.put(key, new HashSet<Xref>());
			for(PathwayElement e : p.getDataObjects()) {
				if(e.getObjectType().equals(ObjectType.DATANODE)) {
					if(e.getDataNodeType().equals("Metabolite")) {
						if(!e.getXref().getId().equals("") && e.getXref().getDataSource() != null) {
							Set<Xref> res = metMapper.mapID(e.getXref(), DataSource.getExistingBySystemCode("Wd"));
							if(res.size() > 0) {
								for(Xref x : res) {
									if(!commonMetabolites.contains(x.getId())) {
										if(!occurences.containsKey(x)) occurences.put(x, new HashSet<String>());
										occurences.get(x).add(pwId);
										xrefs.get(key).add(x);
									}
								}
							} else {
								Set<Xref> resCe = metMapper.mapID(e.getXref(), DataSource.getExistingBySystemCode("Ce"));
								if(resCe.size() > 0) {
									for(Xref x : resCe) {
										if(x.getId().startsWith("CHEBI")) {
											if(!occurences.containsKey(x)) occurences.put(x, new HashSet<String>());
											occurences.get(x).add(pwId);
											xrefs.get(key).add(x);
										}
									}
								} else {
									Set<Xref> resCh = metMapper.mapID(e.getXref(), DataSource.getExistingBySystemCode("Ch"));
									if(resCh.size() > 0) {
										for(Xref x : resCh) {
											xrefs.get(key).add(x);
											if(!occurences.containsKey(x)) occurences.put(x, new HashSet<String>());
											occurences.get(x).add(pwId);
										}
									} else {
										if(!occurences.containsKey(e.getXref())) occurences.put(e.getXref(), new HashSet<String>());
										occurences.get(e.getXref()).add(pwId);
										xrefs.get(key).add(e.getXref());
									}
								}
							}
						}
					}
				}
			}
			if(xrefs.get(key).size() > 0) writer.write(key + "\t" + i.getName() + "\t" + i.getId() + "\t" + xrefs.get(key).size() + "\n");
		}
		writer.close();
		
		for(Xref x : occurences.keySet()) {
			System.out.println(x + "\t" + occurences.get(x).size());
		}
 		
		BufferedWriter writer2 = new BufferedWriter(new FileWriter(new File("edges.txt")));
		writer2.write("Source\tTarget\tSharedMet\tList\n");
		Map<String, Set<String>> map = new HashMap<String, Set<String>>();
		for(String key : xrefs.keySet()) {
			for(String key2 : xrefs.keySet()) {
				if(!key.equals(key2)) {
					if(map.containsKey(key) && map.get(key).contains(key2)) { 
					} else if(map.containsKey(key2) && map.get(key2).contains(key)) {
					} else {
						if(!map.containsKey(key)) map.put(key, new HashSet<String>());
						if(!map.containsKey(key2)) map.put(key2, new HashSet<String>());
						map.get(key).add(key2);
						map.get(key2).add(key);
						if(xrefs.get(key).size() > 0 && xrefs.get(key2).size() > 0) {
							Set<Xref> common = new HashSet<Xref>(xrefs.get(key));
							common.retainAll(xrefs.get(key2));
							if(common.size() > 0) {
								writer2.write(key + "\t" + key2 + "\t" + common.size() + "\t" + common + "\n");
							}
						}
					}
				}
			}
		}
		writer2.close();
	}
	
	private void readCommonMetabolites() throws Exception {
		File f = new File(commonMetFile);
		BufferedReader reader = new BufferedReader(new FileReader(f));
		String line;
		while((line = reader.readLine()) != null) {
			commonMetabolites.add(line);
		}
		reader.close();
	}
	
	public static void main(String[] args) throws Exception {
		MetabolicPathwayNetwork mpn = new MetabolicPathwayNetwork();
		mpn.generateNetwork(new File("network.txt"));
	}

}
