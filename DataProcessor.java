package Upload;

import Mysql.DbConnection;
import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import key.Constants;

public class DataProcessor {

    private static final String USER_AGENT = "Mozilla/5.0";

    private static Map<Integer, String> tweetList = new HashMap<>();

    private static void prepareTweetList() throws SQLException {
        String sql = "SELECT * FROM tweets";
        Statement stmt = DbConnection.getConnection().createStatement();
        ResultSet rsData = stmt.executeQuery(sql);

        while (rsData != null && rsData.next()) {
            tweetList.put(rsData.getInt("id"), rsData.getString("post"));
        }
    }

    public static Map<Integer, Float> processKeywords(String[] tokenArray) throws IOException, SQLException, InterruptedException {

        prepareTweetList();

        Map<Integer, Float> percentageMap = new HashMap<>();

        int tokenSize = tokenArray.length;

//        int rowCount = getRowCount();   // 50
        int rowCount = tweetList.size();

        int rowId = 1;

        for (int i = 0; i < rowCount; i++) {    // database row

//            ResultSet rsTweet = getTweet(rowId);
//            rsTweet.next();
//            String tweetBody = rsTweet.getString("post");
            String tweetBody = (String) tweetList.get(rowId);

            tweetBody = null == tweetBody ? "" : tweetBody.toLowerCase();

            int counter = 0;

            for (int j = 0; j < tokenSize; j++) {   // token - outer

                String token = tokenArray[j];

                if (tweetBody.contains(token)) {
                    counter++;
//                    break;
                } else {

                    List<String> synonymsList = getSynonymsList(token);

                    //  synonymsList.add(0, token);
                    for (String word : synonymsList) {  // synonyms - inner

                        if (tweetBody.contains(word)) {
                            counter++;
                            break;
                        }

                    }
                }

                // Thread.sleep(5);    // 5 seconds delay
            }

            float a = (float) counter / tokenSize;

            float percentage = (a * 100);

            if (percentage >= 60) { // genuine
                percentageMap.put(rowId, percentage);
            }
//            updatePercentage(rowId, percentage);

            rowId++;
        }

        return percentageMap;
    }

    private static int getRowCount() throws SQLException {
        String sql = "SELECT COUNT(*) AS row_count FROM tweets";
        Statement stmt = DbConnection.getConnection().createStatement();
        ResultSet rsData = stmt.executeQuery(sql);
        rsData.next();
        return rsData.getInt("row_count");
    }

    private static ResultSet getTweet(int rowId) throws SQLException {
        String sql = "SELECT * FROM tweets"
                + " WHERE id = " + rowId;
        Statement stmt = DbConnection.getConnection().createStatement();
        return stmt.executeQuery(sql);
    }

    private static void updatePercentage(int rowId, float percentage) throws SQLException {
        String sql = "UPDATE tweets SET percentage = " + percentage
                + " WHERE id = " + rowId;
        Statement stmt = DbConnection.getConnection().createStatement();
        stmt.execute(sql);
    }

    private static List<String> getSynonymsList(String token)
            throws ProtocolException, MalformedURLException, IOException {

        URL obj = new URL(Constants.SYNONYMS_URL + token);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();

        // optional default is GET
        con.setRequestMethod("GET");

        //add request header
        con.setRequestProperty("User-Agent", USER_AGENT);

        int responseCode = con.getResponseCode();

        BufferedReader in = new BufferedReader(
                new InputStreamReader(con.getInputStream()));

        String inputLine;

        StringBuilder response = new StringBuilder();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }

        in.close();

        String json = response.toString();

        Synonyms[] synonyms = new Gson().fromJson(json, Synonyms[].class);

        List<Synonyms> dataArray = Arrays.asList(synonyms);

        List<String> synonymsList = new ArrayList<>();

        for (Synonyms syn : dataArray) {
            synonymsList.add(syn.getWord());
        }

        return synonymsList;
    }

}
