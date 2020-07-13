package servletspackage;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.mysql.cj.conf.ConnectionUrlParser.Pair;

import indexerpackage.DBManager;

public class Ranker {
	// Use PS varible to assign then check on phrase searching
	public boolean PS ;
	// max link id got fron database
	public int max_id ; 
	// my ranked list and its rank
	public ArrayList<Pair<String,Double>> ranked;
	//the query that we search for 
	public String query = SearchEngine.q; 
	DBManager dbMan ;
	// my final result of links sorted
	public   List<String> url=SearchEngine.url;
	
	public Ranker() throws ClassNotFoundException {
		// intialize database connection 
		Class.forName("com.mysql.cj.jdbc.Driver");  
		 dbMan = new DBManager(); //connected
		
		PS = false;
		
		
	}
	// this function takes some links and for each link search for a specific string 
	// input is ( links , string I search for)
	// output is (links which cotain the specific string)
	public static ArrayList<String> phraseFilter(ArrayList<String> links, String phrase) {
		ArrayList<String> filteredLinks = new ArrayList<String>();
		Document doc = null;
		
		for (String link : links) {
			try {
				doc = Jsoup.connect(link).get();
			} catch (IOException e) {
				System.out.println("Failed to connect to " + link);
				e.printStackTrace();
			}
			// for each link I sepreate text of tages
			Elements headers1El = doc.select("h1, h2");
			Elements headers2El = doc.select("h3, h4, h5, h6");
			Elements paragraphsEl = doc.select("p");
			Elements boldEl = doc.select("b");
			Elements urlsEl = doc.select("a[href]");
			Elements imgs = doc.select("img");
			
			boolean added = false;
			// searching for the string in H1 and H2 strings 
			for (Element e : headers1El)
				if (e.ownText().contains(phrase) && !added) {
					filteredLinks.add(link);
					added = true;
					break;
				}
			// if I found it then I will go to the following link 
			if (added) continue;
			// if not found  I exam the rest tags

			for (Element e : headers2El) 
				if (e.ownText().contains(phrase) && !added) {
					filteredLinks.add(link);
					added = true;
					break;
				}
			
			if (added) continue;
			
			for (Element e : paragraphsEl) 
				if (e.ownText().contains(phrase) && !added) {
					filteredLinks.add(link);
					added = true;
					break;
				}
			
			if (added) continue;
			
			for (Element e : boldEl)
				if (e.ownText().contains(phrase) && !added) {
					filteredLinks.add(link);
					added = true;
					break;
				}
			
			if (added) continue;
			
			for (Element e : urlsEl)
				if (e.ownText().contains(phrase) && !added) {
					filteredLinks.add(link);
					added = true;
					break;
				}
			
			if (added) continue;
			
			for (Element e : imgs)
				if (e.attr("alt").contains(phrase) && !added) {
					filteredLinks.add(link);
					added = true;
					break;
				}
			
		}
		
		return filteredLinks;
	}
	
	// this function takes a link and count from database column number of times that user enter this link
	// input is ( link )
	// output is (number of links that from the same website)
	public int VisitedBefore (String url) throws SQLException { 
		String query= "SELECT COUNT(Clicked_links) from links WHERE Clicked_links LIKE '" + url.substring(0,url.length() /2)+"%' \n";
			
		System.out.println(query);
		ResultSet res = dbMan.execute(query);
		int link_num=0;
		while(res.next()) {
			link_num=res.getInt(1);
			
		}
		return link_num;
		
		
	}

	// this function takes some links and calculate points for each one then order them 
	// input is ( links reslting from each searching database query for each word in user search phrase , country (place )of the user , total number of links in database )
	// output is (ordered links)
	public void Ranking (ArrayList<List<SearchResultInfo>> QueryResults , String country , int Total) throws SQLException {
		max_id=Total;
		// make a simple Hash table depends on link id (unique) to know that it is calculated before.
		ranked = new ArrayList<Pair<String,Double>> (Collections.nCopies(max_id, new Pair <String,Double>("" , 0.0)) ) ;
		Double  Rank;
		
		// for each url calculate its rank depending on some factors
		for (int i = 0 ; i < QueryResults.size();i++) {
			for (int j = 0 ; j < QueryResults.get(i).size(); j++) {
				
				
				//it is first time to get this url 
				// I use it to consider some factors 
				if ((ranked.get(QueryResults.get(i).get(j).url_ID).left.equals(""))) {
				    Rank = 0.0 ; 
					// if country of website is the country of user increase rank by 50 point
					if (QueryResults.get(i).get(j).country.equals(country))
							Rank += 50 ;
					// quick caluclation of page rank multiply by factor depends on only number of links in it and number of refrenced in the other pages (not use probablity to faster preformance with good asumption ) 
					Rank += 50*( Double.valueOf (QueryResults.get(i).get(j).Ref) /QueryResults.get(i).get(j).PointTo);
					// give recently created pages higher rank multiply by factor
					Rank += 50 * ((QueryResults.get(i).get(j).publish_date.getYear() + QueryResults.get(i).get(j).publish_date.getMonth() )/ 2020.0) ;
					// increase rank of link depending on user historical choices 
					Rank += 2 * VisitedBefore(QueryResults.get(i).get(j).url);
					// add TF * IDF multiply by big factor to make it effective strongly 
					Rank += 1000 * Math.log10( Total/ QueryResults.get(i).size()) * QueryResults.get(i).get(j).TF ;
					//System.out.println("TF :"+ Math.log10( Total/ QueryResults.get(i).size())+ "list size : "+QueryResults.get(i).size());
					// increase rank with different factors depends on the importace of word place (tag) 
					Rank += 10*QueryResults.get(i).get(j).h12_freq + 5*QueryResults.get(i).get(j).h3456_freq + QueryResults.get(i).get(j).parag_freq + 20* QueryResults.get(i).get(j).title_freq + 2*QueryResults.get(i).get(j).bold_freq + QueryResults.get(i).get(j).alt_text_freq + QueryResults.get(i).get(j).link_freq;
				}
				
				else 
					continue ; 
				
				// search for the same link and find if it contains more than one word So, I increase his rank to make results more relevant 
				for (int k = i+1 ; k <QueryResults.size() ; k++) {
					for (int l = 0 ; l< QueryResults.get(k).size() ; l++) {
						// continue ranking 
						// increase rank as making above 
						if (QueryResults.get(k).get(l).url_ID == QueryResults.get(i).get(j).url_ID) {
							Rank += 1000 * Math.log10( Total/ QueryResults.get(k).size()) * QueryResults.get(k).get(l).TF ; 
							System.out.println("TF 1:"+ Math.log10( Total/ QueryResults.get(k).size())+ "list size : "+QueryResults.get(k).size()+QueryResults.get(k).get(l).url);
							Rank += 10*QueryResults.get(k).get(l).h12_freq + 5*QueryResults.get(k).get(l).h3456_freq + QueryResults.get(k).get(l).parag_freq + 20* QueryResults.get(k).get(l).title_freq + 2*QueryResults.get(k).get(l).bold_freq + QueryResults.get(k).get(l).alt_text_freq + QueryResults.get(k).get(l).link_freq;
							// for optimization I know for every word the link appears only one time so I break after found it.
							break;
						}
						// for optimization I retrive urls from database sorted by ID , So if I break when I exceed url ID because it won't be found
						if (QueryResults.get(k).get(l).url_ID > QueryResults.get(i).get(j).url_ID) {
							break;
						}
					}
					
				}
				// set rankvalue in hash table 
				ranked.set(QueryResults.get(i).get(j).url_ID , new Pair <String , Double> (QueryResults.get(i).get(j).url , Rank));	
				
			}
			// check phrase searching after frist list and break if it is true because this links will aleardy has the complete phrase or not.
			if (PS) 
				break ; 
		}
		// sorting urls depending on each url rank 
		Collections.sort(ranked, Comparator.comparing(p -> -p.right));
		
		
		
		// keep urls only after sorting 
		for (int a = 0 ; a<ranked.size();a++) {
		//	System.out.println("rank left "+ranked.get(a).left);
			if (! ranked.get(a).left.equals("")) {
				url.add(ranked.get(a).left);
				//System.out.println("url :"+url.get(a) +" rank :"+ ranked.get(a).right);
			}
		}
		return ; 
	}

	// this function takes user search phrase and set flag true if it contains ""
	// input is (  user search phrase )
	// output is (set phrase searching flag)
	public void Check_PS(String query) {
		if ((query.charAt(0) == '"' ||query.charAt(1) == '"' )&& (query.charAt(query.length()-1) == '"' || query.charAt(query.length()-2) == '"' ) )
			PS = true ; 
	}

}
