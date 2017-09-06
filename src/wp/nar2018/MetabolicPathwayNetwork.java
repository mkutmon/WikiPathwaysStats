package wp.nar2018;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
import org.pathvisio.wikipathways.webservice.WSPathwayInfo;
import org.wikipathways.client.WikiPathwaysClient;

public class MetabolicPathwayNetwork {

	private String metDb = "C:/Users/MSK/Data/BridgeDb/metabolites_20170826.bridge";
	private String commonMetFile = "common-metabolites.txt";
	
	private WikiPathwaysClient client;
	private String today;
	
	private Organism org = Organism.HomoSapiens;
	private List<WSPathwayInfo> inclPathways;
	
	private IDMapper metMapper;
	
	private File pathwayFolder = new File("pathways");
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
		inclPathways = Utils.getPathways(client, org);
		readCommonMetabolites();
		HashMap<String, Set<Xref>> xrefs = new HashMap<String, Set<Xref>>();
		
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
								xrefs.get(key).addAll(res);
							} else {
								Set<Xref> resCe = metMapper.mapID(e.getXref(), DataSource.getExistingBySystemCode("Ce"));
								if(resCe.size() > 0) {
									for(Xref x : resCe) {
										if(x.getId().startsWith("CHEBI")) {
											xrefs.get(key).add(x);
										}
									}
								} else {
									Set<Xref> resCh = metMapper.mapID(e.getXref(), DataSource.getExistingBySystemCode("Ch"));
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
		}
		
		for(String key : xrefs.keySet()) {
			System.out.println(key + "\t" + xrefs.get(key).size() + "\t" + xrefs.get(key));
		}
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
