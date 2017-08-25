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


public class YearlySnapshots {

	private int startYear;
	private int endYear;
	private Organism org;
	private WikiPathwaysClient client;
	private String today;
	
	public YearlySnapshots(WikiPathwaysClient client, Organism org, int startYear, int endYear, String today) {
		this.startYear = startYear;
		this.endYear = endYear;
		this.org = org;
		this.client = client;
		this.today = today;
	}
	
	public Map<Integer, Map<String, Integer>> getYearlySnapShots(List<String> pathways) throws Exception {
		Map<Integer, Map<String, Integer>> snapShots = new HashMap<Integer, Map<String, Integer>>();
		File output = new File("snapshots_" + org.shortName() + "_" + today + ".txt");
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
