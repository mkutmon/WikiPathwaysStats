package wp.nar2018;

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

/**
 * retrieve number of unique genes
 * calculate how many protein coding genes are present in pathways
 * calculate how many non-coding genes are present in pathways
 * 
 * @author mkutmon
 *
 */
public class ProteinCodingGenes { 
	
	public static void main(String[] args) throws Exception {
		// change variables accordingly
		String bridgeDbMappingFile = "C:/Users/martina.kutmon/Data/BridgeDb/Hs_Derby_Ensembl_85.bridge";
		File pathwayFolder = new File("pathways");
		Organism org = Organism.HomoSapiens;
		
		// retrieve pathways for selected species
		WikiPathwaysClient client = new WikiPathwaysClient(new URL("http://webservice.wikipathways.org"));
		WSPathwayInfo [] list = client.listPathways(org);
		
		// set up bridgedb
		Class.forName("org.bridgedb.rdb.IDMapperRdb");
		DataSourceTxt.init();
		IDMapper mapper = BridgeDb.connect("idmapper-pgdb:" + new File(bridgeDbMappingFile).getAbsolutePath());
		
		// retrieve unique genes in pathways
		Set<Xref> uniqueGenes = new HashSet<Xref>();
		pathwayFolder.mkdir();
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
				uniqueGenes.addAll(res.get(x));
			}
		}
		System.out.println("Number of unique genes in WikiPathways (" + org + "): " + uniqueGenes.size());
		
		// read file with protein coding genes retrieved from Ensembl BioMART
		File f = new File("mart_export.txt");
		BufferedReader reader = new BufferedReader(new FileReader(f));
		String line;
		Set<String> proteinCoding = new HashSet<String>();
		while((line = reader.readLine()) != null) {
			String [] buffer = line.split("\t");
			proteinCoding.add(buffer[0]);
		}
		reader.close();
		System.out.println("Number of all protein coding genes in Ensembl: " + proteinCoding.size());
		
		int pc = 0;
		int npc = 0;
		for(Xref x : uniqueGenes) {
			if(proteinCoding.contains(x.getId())) {
				pc++;
			} else {
				npc++;
			}
		}
		System.out.println("Number of protein coding genes in WikiPathways: " + pc + "\n Number of non-coding genes in WikiPathways" + npc);
	}
}
