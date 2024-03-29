package wp.nar2018;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bridgedb.bio.Organism;
import org.pathvisio.wikipathways.webservice.WSCurationTag;
import org.pathvisio.wikipathways.webservice.WSHistoryRow;
import org.pathvisio.wikipathways.webservice.WSPathway;
import org.pathvisio.wikipathways.webservice.WSPathwayHistory;
import org.pathvisio.wikipathways.webservice.WSPathwayInfo;
import org.wikipathways.client.WikiPathwaysClient;


public class Utils {

	/**
	 * finds all pathways for a specific species ignores tutorial pathways
	 */
	public static List<WSPathwayInfo> getPathways(WikiPathwaysClient client, Organism org) throws Exception {
		System.out.println("get pathway list");
		WSPathwayInfo[] list = client.listPathways(org);

		List<WSPathwayInfo> inclPathways = new ArrayList<WSPathwayInfo>();
		// exclude tutorial pathways
		WSCurationTag[] tutorial = client.getCurationTagsByName("Curation:Tutorial");
		Set<String> tutorialPathways = new HashSet<String>();
		for (WSCurationTag t : tutorial) {
			tutorialPathways.add(t.getPathway().getId());
		}
		for (WSPathwayInfo i : list) {
			if (!tutorialPathways.contains(i.getId())) {
				inclPathways.add(i);
			}
		}
		return inclPathways;
	}
	
	public static List<WSPathwayInfo> getCuratedPathways(WikiPathwaysClient client, Organism org) throws Exception {
		WSCurationTag [] list = client.getCurationTagsByName("Curation:AnalysisCollection");
		List<WSPathwayInfo> inclPathways = new ArrayList<WSPathwayInfo>();
		
		for(WSCurationTag t : list) {
			System.out.println(t.getPathway().getSpecies() + "\t" + org.latinName());
			if(t.getPathway().getSpecies().equals(org.latinName())) {
				inclPathways.add(t.getPathway());
			}
		}
		
		return inclPathways;
	}
	
	/**
	 * downloads all GPML files for each pathway revision needed only if file
	 * does not yet occur in pathway folder
	 */
	public static void downloadPathwayFiles(Map<Integer, Map<String, Integer>> snapShots, File pathwayFolder, WikiPathwaysClient client) throws Exception {
		System.out.println("downloading pathway files");
		for (Integer year : snapShots.keySet()) {
			for (String id : snapShots.get(year).keySet()) {
				Integer rev = snapShots.get(year).get(id);
				File f = new File(pathwayFolder, id + "_" + rev + ".gpml");
				if (!f.exists()) {
					WSPathway p = client.getPathway(id, rev);
					PrintWriter out = new PrintWriter(f);
					out.print(p.getGpml());
					out.close();
				}
			}
		}
	}
	
	public static void downloadPathway(String id, int rev, WikiPathwaysClient client, File pathwayFolder) throws Exception {
		File f = new File(pathwayFolder, id + "_" + rev + ".gpml");
		if (!f.exists()) {
			WSPathway p = client.getPathway(id, rev);
			PrintWriter out = new PrintWriter(f);
			out.print(p.getGpml());
			out.close();
		}
	}
	
	/**
	 * downloads all GPML files for each pathway revision needed only if file
	 * does not yet occur in pathway folder
	 */
	public static void downloadPathwayFilesQuarterly(Map<Integer, Map<Integer, Map<String, Integer>>> snapShots, File pathwayFolder, WikiPathwaysClient client) throws Exception {
		System.out.println("downloading pathway files");
		for (Integer year : snapShots.keySet()) {
			for(Integer quarter : snapShots.get(year).keySet()) {
				for (String id : snapShots.get(year).get(quarter).keySet()) {
					Integer rev = snapShots.get(year).get(quarter).get(id);
					File f = new File(pathwayFolder, id + "_" + rev + ".gpml");
					if (!f.exists()) {
						WSPathway p = client.getPathway(id, rev);
						PrintWriter out = new PrintWriter(f);
						out.print(p.getGpml());
						out.close();
					}
				}
			}
		}
	}
	
	public static Map<Integer, Map<String, Integer>> getHistory(String today, Organism org, int startYear, int endYear, List<WSPathwayInfo> pathways, WikiPathwaysClient client) throws Exception {
		System.out.println("retrieving pathway history");
		Map<Integer, Map<String, Integer>> snapShots = new HashMap<Integer, Map<String, Integer>>();
		File output = new File("snapshots_" + org.shortName() + "_" + today + ".txt");
		if (!output.exists()) {
			Calendar c = Calendar.getInstance();
			c.set(2003, 1, 1, 0, 0, 0);

			for (int i = startYear; i <= endYear; i++) {
				snapShots.put(i, new HashMap<String, Integer>());
			}
			for (WSPathwayInfo info : pathways) {
				String pwId = info.getId();
				WSPathwayHistory hist = client.getPathwayHistory(pwId, c.getTime());
				for (WSHistoryRow row : hist.getHistory()) {
					// rows always come in order (oldest revision first)
					Integer rev = Integer.parseInt(row.getRevision());
					Integer y = Integer.parseInt(row.getTimestamp().substring(0, 4));
					Integer m = Integer.parseInt(row.getTimestamp().substring(4, 6));
					
					if (y < startYear) {
						for (int i = startYear; i <= endYear; i++) {
							snapShots.get(i).put(pwId, rev);
						}
					} else {
						if (m <= 9) {
							for (int i = y; i <= endYear; i++) {
								snapShots.get(i).put(pwId, rev);
							}
						} else {
							int year = y + 1;
							for (int i = year; i <= endYear; i++) {
								snapShots.get(i).put(pwId, rev);
							}
						}
					}
				}
			}
			
			// write snapshot which can be reused when running the script again
			BufferedWriter writer = new BufferedWriter(new FileWriter(output));
			writer.write("Year\tCount pathways\tPathway revisions");
			for (Integer year : snapShots.keySet()) {
				writer.write("\n" + year + "\t" + snapShots.get(year).size() + "\t" + snapShots.get(year));
			}
			writer.close();
		} else {
			BufferedReader reader = new BufferedReader(new FileReader(output));
			String line;
			// read header
			reader.readLine();
			while ((line = reader.readLine()) != null) {
				String[] buffer = line.split("\t");
				Integer year = Integer.parseInt(buffer[0]);
				snapShots.put(year, new HashMap<String, Integer>());
				String[] buffer2 = buffer[2].replace("{", "").replace("}", "")
						.split(", ");
				for (String s : buffer2) {
					String[] buffer3 = s.split("=");
					String wpId = buffer3[0];
					Integer rev = Integer.parseInt(buffer3[1]);
					snapShots.get(year).put(wpId, rev);
				}
			}
			reader.close();
		}
		return snapShots;
	}
	
	public static Map<Integer, Map<Integer, Map<String, Integer>>> getQuarterlySnapshots(String today, Organism org, int startYear, int endYear, List<WSPathwayInfo> pathways, WikiPathwaysClient client) throws Exception {
		Map<Integer, Map<Integer, Map<String, Integer>>> snapShots = new HashMap<Integer, Map<Integer, Map<String, Integer>>>();
		File output = new File("snapshotsQ_" + org.shortName() + "_" + today + ".txt");
		if (!output.exists()) {
			Calendar c = Calendar.getInstance();
			c.set(2003, 1, 1, 0, 0, 0);

			for (int i = startYear; i <= endYear; i++) {
				Map<Integer, Map<String, Integer>> quarters = new HashMap<Integer, Map<String, Integer>>();
				quarters.put(3, new HashMap<>());
				quarters.put(6, new HashMap<>());
				quarters.put(9, new HashMap<>());
				quarters.put(12, new HashMap<>());
				snapShots.put(i, quarters);
			}
			for (WSPathwayInfo info : pathways) {
				String pwId = info.getId();
				WSPathwayHistory hist = client.getPathwayHistory(pwId, c.getTime());
				for (WSHistoryRow row : hist.getHistory()) {
					// rows always come in order (oldest revision first)
					Integer rev = Integer.parseInt(row.getRevision());
					Integer y = Integer.parseInt(row.getTimestamp().substring(0, 4));
					Integer m = Integer.parseInt(row.getTimestamp().substring(4, 6));
					
					if (y < startYear) {
						for (int i = startYear; i <= endYear; i++) {
							for(Integer month : snapShots.get(i).keySet()) {
								snapShots.get(i).get(month).put(pwId, rev);
							}
						}
					} else {
						if(m <= 3) {
							for (int i = y; i <= endYear; i++) {
								snapShots.get(i).get(3).put(pwId, rev);
								snapShots.get(i).get(6).put(pwId, rev);
								snapShots.get(i).get(9).put(pwId, rev);
								snapShots.get(i).get(12).put(pwId, rev);
							}
						} else if(m <= 6) {
							snapShots.get(y).get(6).put(pwId, rev);
							snapShots.get(y).get(9).put(pwId, rev);
							snapShots.get(y).get(12).put(pwId, rev);
							for (int i = (y+1); i <= endYear; i++) {
								snapShots.get(i).get(3).put(pwId, rev);
								snapShots.get(i).get(6).put(pwId, rev);
								snapShots.get(i).get(9).put(pwId, rev);
								snapShots.get(i).get(12).put(pwId, rev);
							}
						} else if(m <= 9) {
							snapShots.get(y).get(9).put(pwId, rev);
							snapShots.get(y).get(12).put(pwId, rev);
							for (int i = (y+1); i <= endYear; i++) {
								snapShots.get(i).get(3).put(pwId, rev);
								snapShots.get(i).get(6).put(pwId, rev);
								snapShots.get(i).get(9).put(pwId, rev);
								snapShots.get(i).get(12).put(pwId, rev);
							}
						} else if (m <= 12) {
							snapShots.get(y).get(12).put(pwId, rev);
							for (int i = (y+1); i <= endYear; i++) {
								snapShots.get(i).get(3).put(pwId, rev);
								snapShots.get(i).get(6).put(pwId, rev);
								snapShots.get(i).get(9).put(pwId, rev);
								snapShots.get(i).get(12).put(pwId, rev);
							}
						}
					}
				}
			}
			
			// write snapshot which can be reused when running the script again
			BufferedWriter writer = new BufferedWriter(new FileWriter(output));
			writer.write("Year\tQuarter\tCount pathways\tPathway revisions");
			for (Integer year : snapShots.keySet()) {
				writer.write("\n" + year + "\t3" + snapShots.get(year).get(3).size() + "\t" + snapShots.get(year).get(3));
				writer.write("\n" + year + "\t6" + snapShots.get(year).get(6).size() + "\t" + snapShots.get(year).get(6));
				writer.write("\n" + year + "\t9" + snapShots.get(year).get(9).size() + "\t" + snapShots.get(year).get(9));
				writer.write("\n" + year + "\t12" + snapShots.get(year).get(12).size() + "\t" + snapShots.get(year).get(12));
			}
			writer.close();
		} else {
			BufferedReader reader = new BufferedReader(new FileReader(output));
			String line;
			// read header
			reader.readLine();
			while ((line = reader.readLine()) != null) {
				String[] buffer = line.split("\t");
				Integer year = Integer.parseInt(buffer[0]);
				Integer quarter = Integer.parseInt(buffer[1]);
				Map<Integer, Map<String, Integer>> quarters = new HashMap<Integer, Map<String, Integer>>();
				quarters.put(3, new HashMap<>());
				quarters.put(6, new HashMap<>());
				quarters.put(9, new HashMap<>());
				quarters.put(12, new HashMap<>());
				snapShots.put(year, quarters);
				String[] buffer2 = buffer[3].replace("{", "").replace("}", "").split(", ");
				for (String s : buffer2) {
					String[] buffer3 = s.split("=");
					String wpId = buffer3[0];
					Integer rev = Integer.parseInt(buffer3[1]);
					snapShots.get(year).get(quarter).put(wpId, rev);
				}
			}
			reader.close();
		}
		return snapShots;
	}
}
