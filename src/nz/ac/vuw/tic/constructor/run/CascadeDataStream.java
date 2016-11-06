package nz.ac.vuw.tic.constructor.run;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;

import org.apache.commons.csv.*;
import org.jgrapht.DirectedGraph;
import org.jgrapht.alg.BellmanFordShortestPath;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

public class CascadeDataStream {
	
	static String dbHost;
	static String dbName;
	static String dbUser;
	static String dbPass;
	
	static String sourceFile;
	
	private static final String DB_PATH = "resources/tmp-neo4j-db";
	
	/*
	 * command line use:
	 * java -jar CascadeDataStream.jar -i -p -c -s [source 1] [source 2] ... [source n] -m [matcher 1] [matcher 2] ... [matcher n] -db [host] [database] [username] [password]
	 */
	public static void main(String[] args) throws IOException, SQLException {
		// without any arguments or missing data source -> break
		List<String> argsList = Arrays.asList(args);
		
		if(!argsList.contains("-i") && !argsList.contains("-p") && !argsList.contains("-c")){
			System.out.println("You did not specify any actions. Please specify -i (import nodes), -p (process nodes), and/or -c (construct cascades)");
			System.exit(0);
		} else if(!argsList.contains("-db")){
			System.out.println("You did not specify any database. All actions will need access to a named postgres database.");
			System.exit(0);
		}
		
		dbHost = args[argsList.indexOf("-db")+1];
		dbName = args[argsList.indexOf("-db")+2];
		dbUser = args[argsList.indexOf("-db")+3];
		dbPass = args[argsList.indexOf("-db")+4];
		
		sourceFile = args[argsList.indexOf("-s")+1];
		
		if(argsList.contains("-i")){
			if(argsList.indexOf("-s") < 0){
				System.out.println("Data source for node import missing. Please use command in the following way: java -jar CascadeDataStream.jar -i -p -c -s [source 1] [source 2] ... [source n] -m [matcher 1] [matcher 2] ... [matcher n] -db [host] [database] [username] [password]");
				System.out.println("Example data: java -jar CascadeDataStream.jar -i -p -c -s resources/example-sources/crisis.csv -db localhost crisis_cascades postgres potgres");
				System.exit(0);
			} else{
				importNodes(sourceFile, dbHost, dbName, dbUser, dbPass);
			}
		}
		
		if(argsList.contains("-p")){
			processNodes(dbHost, dbName, dbUser, dbPass);
		}
		
		if(argsList.contains("-c")){
			constructCascades(dbHost, dbName, dbUser, dbPass);
		}
		
	}
	
	private static void importNodes(String fileName, String dbHost, String dbName, String dbUser, String dbPass) throws IOException, SQLException{
		String sourceDataFile = fileName;
		File csvData = new File(sourceDataFile);
		
		CSVParser parser = CSVParser.parse(csvData, Charset.defaultCharset(), CSVFormat.newFormat(';'));
		
		Iterator<CSVRecord> iter = parser.iterator();
		
		// insert all raw nodes in a stupid and sequential way - replace with parallel once fixed!!!
		while(iter.hasNext()){
			CSVRecord next = iter.next();
			insertStmt(dbHost, dbName, dbUser, dbPass, next);
		}
		/* PARALLEL DOES NOT WORK ON AZURE ATM - FIX!
		List<CSVRecord> values = parser.getRecords();
		ForkJoinPool forkJoinPool = new ForkJoinPool(3);
		forkJoinPool.submit(() -> {
			values.parallelStream().forEach((o) -> {
				try {
					insertStmt(dbHost, dbName, dbUser, dbPass, o);
				} catch (IOException | SQLException e) { e.printStackTrace();}
			});
		});*/
		// garbage
		iter = null;
		parser = null;
	}
	
	private static void insertStmt(String dbHost, String dbName, String dbUser, String dbPass, CSVRecord obj) throws IOException, SQLException{
		String url = "jdbc:postgresql://"+dbHost+"/"+dbName+"?user="+dbUser+"&password="+dbPass;
		Connection conn = DriverManager.getConnection(url);
		Statement st = conn.createStatement();
		boolean suc = st.execute("INSERT INTO nodes (uri, dpub, interactions) VALUES('"+URLEncoder.encode(obj.get(0)+obj.get(1))+"', to_timestamp('"+obj.get(1).replace("T", " ").replace("+00:00", "").replace("\"", "")+"','YYYY-MM-DD HH24:MI:SS'),'"+obj.get(2)+"');");
		conn.close();
	}
	
	private static void processNodes(String dbHost, String dbName, String dbUser, String dbPass) throws SQLException{
		String url = "jdbc:postgresql://"+dbHost+"/"+dbName+"?user="+dbUser+"&password="+dbPass;
		Connection conn = DriverManager.getConnection(url);
		Statement st = conn.createStatement();
		
		// iterate all nodes and create links etc.
		Map<String,String> lastNodeForIdentifier = new HashMap<String,String>();
		ResultSet rs = st.executeQuery("SELECT id, uri, interactions, dpub FROM nodes WHERE interactions <> '' ORDER BY dpub ASC;");
		boolean suc;
		while(rs.next()){
			String tagString = rs.getString("interactions");
			int nodeId = rs.getInt("id");
			if(tagString.length()>0){
				Statement st1 = conn.createStatement();
				ResultSet tmp1 = st1.executeQuery("SELECT id from identifier_sets where set ='"+tagString+"';");
				int identSetId;
				if(tmp1.next()){
					identSetId = tmp1.getInt(1);
				} else {
					Statement st2 = conn.createStatement();
					ResultSet tmp2 = st2.executeQuery("SELECT count(*) from identifier_sets;");
					tmp2.next();
					System.out.println(tmp2.getInt(1));
					Statement st3 = conn.createStatement();
					suc = st3.execute("INSERT INTO identifier_sets (set, identifier_set_index_global) VALUES('"+tagString+"','"+tmp2.getInt(1)+"');");
					tmp2.close();
					Statement st4 = conn.createStatement();
					ResultSet tmp3 = st4.executeQuery("SELECT id from identifier_sets where set ='"+tagString+"';");
					tmp3.next();
					identSetId = tmp3.getInt(1);
					tmp3.close();
				}
				tmp1.close();
				Statement st5 = conn.createStatement();
				ResultSet tmp4 = st5.executeQuery("SELECT count(*) from nodes_identifier_sets where identifier_set_id ='"+identSetId+"';");
				tmp4.next();
				int idx = tmp4.getInt(1);
				Statement st6 = conn.createStatement();
				suc = st6.execute("INSERT INTO nodes_identifier_sets (node_id, identifier_set_id, identifier_set_index) VALUES("+nodeId+","+identSetId+","+idx+");");
				
				String[] tags = tagString.split(",");
				int targetNodeUri = rs.getInt("id");
				Arrays.sort(tags);
				if(tags[0].length() > 0){
					for(int i = 0; i < tags.length; i++){
						String currentTag = tags[i];
						Statement st7 = conn.createStatement();
						if(lastNodeForIdentifier.get(currentTag)!=null){
							int sourceNodeUri = Integer.parseInt(lastNodeForIdentifier.get(currentTag));
							suc = st7.execute("INSERT INTO links (source_node, target_node, interaction) VALUES("+sourceNodeUri+","+targetNodeUri+",'"+currentTag+"');");
						} else{
							suc = st7.execute("INSERT INTO roots (root_node_uri, interaction) VALUES('"+targetNodeUri+"','"+currentTag+"');");
						}
						lastNodeForIdentifier.put(currentTag, Integer.toString(targetNodeUri));
					}
				}
			}
		}
	}
	
    private static enum RelTypes implements RelationshipType
    {
        FOLLOWS
    }
	
	private static void constructCascades(String dbHost, String dbName, String dbUser, String dbPass) throws SQLException{
		System.out.println("foo");
		DirectedGraph<String, DefaultEdge> g =
	            new DefaultDirectedGraph<String, DefaultEdge>
	            (DefaultEdge.class);
		String url = "jdbc:postgresql://"+dbHost+"/"+dbName+"?user="+dbUser+"&password="+dbPass;
		Connection conn = DriverManager.getConnection(url);
		Statement st = conn.createStatement();
		ResultSet rs = st.executeQuery("SELECT DISTINCT source_node_uri, target_node_uri, interaction FROM links;");
		
		while(rs.next()){
			String source = rs.getString(1);
			String target = rs.getString(2);
			g.addVertex(source);
			g.addVertex(target);
			g.addEdge(source, target);
			BellmanFordShortestPath path = new BellmanFordShortestPath(g, null);
			List edges = path.findPathBetween(g, source, target);
			System.out.println(edges.size());
		}
		
		/*GraphDatabaseService graphDb;
	    Node firstNode;
	    Node secondNode;
	    Relationship relationship;
		
		graphDb = new GraphDatabaseFactory().newEmbeddedDatabase( DB_PATH );
		registerShutdownHook( graphDb );
		
		try ( Transaction tx = graphDb.beginTx() )
        {
			while(rs.next()){
				String source = rs.getString(1);
				String target = rs.getString(2);
				String interaction = rs.getString(3);
				firstNode = graphDb.createNode();
	            firstNode.setProperty("uri", source);
	            secondNode = graphDb.createNode();
	            secondNode.setProperty("uri", target);
	
	            relationship = firstNode.createRelationshipTo( secondNode, RelTypes.FOLLOWS );
	            relationship.setProperty("label", interaction);
	        }
			tx.success();
        }*/
	}
	
	private static void registerShutdownHook( final GraphDatabaseService graphDb )
    {
        // Registers a shutdown hook for the Neo4j instance so that it
        // shuts down nicely when the VM exits (even if you "Ctrl-C" the
        // running application).
        Runtime.getRuntime().addShutdownHook( new Thread()
        {
            @Override
            public void run()
            {
                graphDb.shutdown();
            }
        } );
    }
}
