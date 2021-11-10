/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package Upload;

import Mysql.DbConnection;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import stal.OddEven_SGDHelper;
import stal.SGD;
import stal.SGD_helper;
import stal.TrainingExample;

@WebServlet("/SearchNews")
public class SearchNews extends HttpServlet {

    // database connection settings
    private SimpleDateFormat format;

    @Override
    protected void doPost(HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {

        try {

            String keyword = request.getParameter("keyword");

            keyword = keyword.toLowerCase();

            String[] keywordToken = keyword.split("\\s");

            Map<Integer, Float> processedWordsIds = DataProcessor.processKeywords(keywordToken);

            Connection conn = null;

            conn = DbConnection.getConnection();

            String sql = "select count(1) as media_df from tweets where post LIKE '%" + keyword + "%'";
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(sql);

            sql = "select count(1) as twitter_df from public_tweets where msg LIKE '%" + keyword + "%'";
            Statement st1 = conn.createStatement();
            ResultSet rs1 = st1.executeQuery(sql);

            int mediaDF = 0;
            if (rs.first()) {
                mediaDF = rs.getInt("media_df");
            }
            int twitterDF = 0;
            if (rs1.first()) {
                twitterDF = rs1.getInt("twitter_df");
            }

            List<TweetData> tweetDataList = new ArrayList<>();

            for (Map.Entry<Integer, Float> entry : processedWordsIds.entrySet()) {
                Integer id = entry.getKey();
                Float percentage = entry.getValue();

                sql = "SELECT * FROM tweets WHERE id = " + id;
                Statement st2 = conn.createStatement();
                ResultSet rs2 = st2.executeQuery(sql);

                rs2.next();

                tweetDataList.add(
                        new TweetData(rs2.getInt("id"), rs2.getString("post"),
                                rs2.getString("img"), rs2.getString("tweet_url"), percentage)
                );

            }

            sql = "select * from public_tweets"
                    + " JOIN user_details ON(uid=email) where msg LIKE '%" + keyword + "%'";
            Statement st3 = conn.createStatement();
            ResultSet rs3 = st3.executeQuery(sql);

            String result = runAlgorithm(keyword);

//            request.setAttribute("mediaDF", mediaDF);
            request.setAttribute("mediaDF", processedWordsIds.size());
            request.setAttribute("twitterDF", twitterDF);
            request.setAttribute("rsMedia", tweetDataList);
            request.setAttribute("rsTwitter", rs3);
            request.setAttribute("algorithmResult", result);

            sql = "select * from tweets where post NOT LIKE '%" + keyword + "%' LIMIT 50";
            Statement st4 = conn.createStatement();
            ResultSet rs4 = st4.executeQuery(sql);

            request.setAttribute("rsOther", rs4);

            if (request.getParameter("userType") != null) {
                RequestDispatcher rd = request.getRequestDispatcher("news_search_admin.jsp");
                rd.forward(request, response);
            } else {
                RequestDispatcher rd = request.getRequestDispatcher("news_search.jsp");
                rd.forward(request, response);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    private String runAlgorithm(String keyword) throws SQLException {
        SGD_helper<String, ArrayList<String>, String> helper = new OddEven_SGDHelper();
        SGD<String, ArrayList<String>, String> sgd = new SGD<>(helper);
        ArrayList<TrainingExample<String, String>> list = new ArrayList<>();

        String sql = "SELECT * FROM tweets";

        Statement stmt1 = DbConnection.getConnection().createStatement();
        ResultSet rsTweets = stmt1.executeQuery(sql);

        while (rsTweets != null && rsTweets.next()) {
            list.add(new TrainingExample<String, String>(rsTweets.getString("post"), "genuine"));
        }

        sql = "SELECT * FROM public_tweets";

        Statement stmt2 = DbConnection.getConnection().createStatement();
        ResultSet rsPulicTweets = stmt2.executeQuery(sql);

        while (rsPulicTweets != null && rsPulicTweets.next()) {
            list.add(new TrainingExample<String, String>(rsPulicTweets.getString("msg"), "fake"));
        }

        HashMap<ArrayList<String>, Float> w = sgd.run(list, 10, (float) 0.1);
        list.clear();
        String result = sgd.predict(keyword);

        System.out.println(result);

        return result;

    }
}
