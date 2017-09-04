import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bridgedb.bio.Organism;
import org.pathvisio.wikipathways.webservice.WSHistoryRow;
import org.pathvisio.wikipathways.webservice.WSPathwayHistory;
import org.wikipathways.client.WikiPathwaysClient;


public class SnapshotCreator {

	private int startYear;
	private int endYear;
	private Organism org;
	private WikiPathwaysClient client;
	private String today;
	
	public SnapshotCreator(WikiPathwaysClient client, Organism org, int startYear, int endYear, String today) {
		this.startYear = startYear;
		this.endYear = endYear;
		this.org = org;
		this.client = client;
		this.today = today;
	}
	
	public Map<Integer, Map<Integer, Map<String, Integer>>> getQuarterlySnapshots(List<String> pathways) throws Exception {
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
			for (String pwId : pathways) {
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
				writer.write("\n" + year + "\t3\t" + snapShots.get(year).get(3).size() + "\t" + snapShots.get(year).get(3));
				writer.write("\n" + year + "\t6\t" + snapShots.get(year).get(6).size() + "\t" + snapShots.get(year).get(6));
				writer.write("\n" + year + "\t9\t" + snapShots.get(year).get(9).size() + "\t" + snapShots.get(year).get(9));
				writer.write("\n" + year + "\t12\t" + snapShots.get(year).get(12).size() + "\t" + snapShots.get(year).get(12));
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
	
	public Map<Integer, Map<String, Integer>> getYearlySnapShots(List<String> pathways) throws Exception {
		Map<Integer, Map<String, Integer>> snapShots = new HashMap<Integer, Map<String, Integer>>();
		File output = new File("snapshotsY_" + org.shortName() + "_" + today + ".txt");
		if (!output.exists()) {
			Calendar c = Calendar.getInstance();
			c.set(2003, 1, 1, 0, 0, 0);

			for (int i = startYear; i <= endYear; i++) {
				snapShots.put(i, new HashMap<String, Integer>());
			}
			for (String pwId : pathways) {
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
				writer.write("\n" + year + "\t" + snapShots.get(year).size()
						+ "\t" + snapShots.get(year));
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
}
