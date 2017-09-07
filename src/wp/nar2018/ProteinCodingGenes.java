package wp.nar2018;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
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
		File pathwayFolder = new File("C:/Users/martina.kutmon/owncloud/Data/WikiPathways/pathways-cache/");
		Organism org = Organism.HomoSapiens;
		
		// set up bridgedb
		Class.forName("org.bridgedb.rdb.IDMapperRdb");
		DataSourceTxt.init();
		IDMapper mapper = BridgeDb.connect("idmapper-pgdb:" + new File(bridgeDbMappingFile).getAbsolutePath());
		
		// read file with protein coding genes retrieved from Ensembl BioMART
		File f = new File("mart_export.txt");
		Set<String> proteinCoding = getProteinCodingGenes(f);
			
		// retrieve disease-gene associations
		Set<String> diseaseGenes = getDiseaseGenes();
		
		// read GO metabolic process genes
		Set<String> metProcessGenes = getGOMetGenes(new File("data/metabolic_process_term.txt"), mapper);
		
		// retrieve unique genes in pathways
		Set<String> uniqueGenes = getUniqueGenesFromPathways(pathwayFolder, org, mapper);
		
		// calc protein coding and non-protein coding genes
		Set<String> common = new HashSet<String>(proteinCoding);
		common.retainAll(uniqueGenes);
		int pc = common.size();
		int npc = uniqueGenes.size()-common.size();
		

		Set<String> commonPCDisease = new HashSet<String>(proteinCoding);
		commonPCDisease.retainAll(diseaseGenes);
		Set<String> commonPwyDisease = new HashSet<String>(uniqueGenes);
		commonPwyDisease.retainAll(diseaseGenes);
		
		Set<String> commonPCGO = new HashSet<String>(proteinCoding);
		commonPCGO.retainAll(metProcessGenes);
		Set<String> commonPwyGO = new HashSet<String>(uniqueGenes);
		commonPwyGO.retainAll(metProcessGenes);
		
		System.out.println("Number of all protein coding genes in Ensembl: " + proteinCoding.size());
		System.out.println("Number of disease genes: " + diseaseGenes.size() + " (" + commonPCDisease.size() + " protein coding, " + commonPwyDisease.size() + " in pathways)");
		System.out.println("Number of GO met process genes: " + metProcessGenes.size() + " (" + commonPCGO.size() + " protein coding, " + commonPwyGO.size() + " in pathways)");

		System.out.println("Number of unique genes in WikiPathways (" + org + "): " + uniqueGenes.size());
		System.out.println("Number of protein coding genes in WikiPathways: " + pc + "\nNumber of non-coding genes in WikiPathways: " + npc);
	}
	
	private static Set<String> getGOMetGenes(File f, IDMapper mapper) throws Exception {
		Set<String> metProcessGenes = new HashSet<String>();
		BufferedReader reader = new BufferedReader(new FileReader(f));
		String line;
		while((line = reader.readLine()) != null) {
			String [] buffer = line.split("\t");
			Set<Xref> res = mapper.mapID(new Xref(buffer[1], DataSource.getExistingBySystemCode("H")), DataSource.getExistingBySystemCode("En"));
			for(Xref x : res) {
				metProcessGenes.add(x.getId());
			}
		}
		reader.close();
		return metProcessGenes;
	}
	
	private static Set<String> getDiseaseGenes() throws Exception {
		Set<String> diseaseGenes = new HashSet<String>();
		URL url = new URL("https://omim.org/static/omim/data/mim2gene.txt");
        BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));

        String line;
        while ((line = in.readLine()) != null) {
        	if(!line.startsWith("#")) {
        		String [] buffer = line.split("\t");
        		if(buffer.length > 4) {
        			diseaseGenes.add(buffer[4]);
        		}
        	}
        }
        in.close();
        return diseaseGenes;
	}

	private static Set<String> getUniqueGenesFromPathways(File pathwayFolder, Organism org, IDMapper mapper) throws Exception {
		// retrieve pathways for selected species
		WikiPathwaysClient client = new WikiPathwaysClient(new URL("http://webservice.wikipathways.org"));
		WSPathwayInfo [] list = client.listPathways(org);
		Set<String> uniqueGenes = new HashSet<String>();
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
				for(Xref xr : res.get(x)) {
					uniqueGenes.add(xr.getId());
				}
			}
		}
		return uniqueGenes;
	}
	
	/**
	 * reads file from BioMART (first column containing ENSG identifier for protein-coding genes)
	 */
	private static Set<String> getProteinCodingGenes(File f) throws Exception {
		BufferedReader reader = new BufferedReader(new FileReader(f));
		String line;
		Set<String> proteinCoding = new HashSet<String>();
		while((line = reader.readLine()) != null) {
			String [] buffer = line.split("\t");
			proteinCoding.add(buffer[0]);
		}
		reader.close();
		return proteinCoding;
	}
}
