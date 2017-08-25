import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.bridgedb.BridgeDb;
import org.bridgedb.DataSource;
import org.bridgedb.IDMapper;
import org.bridgedb.Xref;
import org.bridgedb.bio.DataSourceTxt;
import org.bridgedb.bio.Organism;
import org.pathvisio.core.model.Pathway;
import org.pathvisio.wikipathways.webservice.WSPathway;
import org.pathvisio.wikipathways.webservice.WSPathwayInfo;
import org.wikipathways.client.WikiPathwaysClient;


public class ProteinCodingGenes {

	public static void main(String[] args) throws Exception {
		WikiPathwaysClient client = new WikiPathwaysClient(new URL("http://webservice.wikipathways.org"));
		WSPathwayInfo [] list = client.listPathways(Organism.HomoSapiens);
		
		
		Class.forName("org.bridgedb.rdb.IDMapperRdb");
		DataSourceTxt.init();
		IDMapper mapper = BridgeDb.connect("idmapper-pgdb:" + new File("C:/Users/martina.kutmon/Data/BridgeDb/Hs_Derby_Ensembl_85.bridge").getAbsolutePath());
		
		DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
		Date date = new Date();	
		String today = dateFormat.format(date);
		
		File pathwayFolder = new File("pathways_" + today);
		Set<Xref> allEnsemblIds = new HashSet<Xref>();
		for(WSPathwayInfo i : list) {
			File p = new File(pathwayFolder, i.getId() + "_" + i.getRevision() + ".gpml");
			if(!p.exists()) {
				WSPathway wsP = client.getPathway(i.getId(), Integer.parseInt(i.getRevision()));
				PrintWriter out = new PrintWriter(p);
				out.print(wsP.getGpml());
				out.close();
			}
			Pathway pathway = new Pathway();
			pathway.readFromXml(p, false);
			Map<Xref, Set<Xref>> res = mapper.mapID(pathway.getDataNodeXrefs(), DataSource.getExistingBySystemCode("En"));
			for(Xref x : res.keySet()) {
				allEnsemblIds.addAll(res.get(x));
			}
		}
		System.out.println(allEnsemblIds.size());
		
		File f = new File("mart_export.txt");
		BufferedReader reader = new BufferedReader(new FileReader(f));
		String line;
		Set<String> proteinCoding = new HashSet<String>();
		while((line = reader.readLine()) != null) {
			String [] buffer = line.split("\t");
			proteinCoding.add(buffer[0]);
		}
		reader.close();
		System.out.println(proteinCoding.size());
		
		int pc = 0;
		int npc = 0;
		for(Xref x : allEnsemblIds) {
			if(proteinCoding.contains(x.getId())) {
				pc++;
			} else {
				npc++;
			}
		}
		System.out.println(pc + "\t" + npc);
	}
}
