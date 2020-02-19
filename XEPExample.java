/*
This code shows the example of using XEP and its speed performance in comparison to JDBC connection.
main method shows how it is being used.

The key part is the following line:
// Connect to database using EventPersister, which is based on IRISDataSource
EventPersister xepPersister = PersisterFactory.createPersister();

// Connecting to database
xepPersister.connect(ip, port, namespace, username, password);

//Defining the flat schema to be used as model for data that is being ingested
xepPersister.deleteExtent("model.Trade");   // Remove old test data
xepPersister.importSchema("model.Trade");   // Import flat schema	       

// Create Event
Event xepEvent = xepPersister.getEvent("model.Trade");

now, all you need to do is store your data as an Trade[] array
and then use

xepEvent.store(sampleArray); 

to store it in IRIS as a batch
*/

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.Date;
import java.util.Scanner;
import java.util.HashMap;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import com.intersystems.xep.*;
import model.Trade;

public class XEPExample {
    public static void main(String[] args) throws Exception {
	    // Initialize map to store connection details from config.txt
    	
        HashMap<String, String> map = new HashMap<String, String>();
        try{
            map = getConfig("config.txt");
        }
        catch (IOException e){
            System.out.println(e.getMessage());
        }

        // Retrieve connection information from configuration file
        String ip = map.get("ip");
        int port = Integer.parseInt(map.get("port"));
        String namespace = map.get("namespace");
        String username = map.get("username");
        String password = map.get("password");

	    try {
	    	// Initialize sampleArray to hold Trade items
	    	Trade[] sampleArray = null;
	    	
	    	// Connect to database using EventPersister, which is based on IRISDataSource
	        EventPersister xepPersister = PersisterFactory.createPersister();

	        // Connecting to database
	        xepPersister.connect(ip, port, namespace, username, password);
			System.out.println("Connected to InterSystems IRIS.");

	        xepPersister.deleteExtent("model.Trade");   // Remove old test data
	        xepPersister.importSchema("model.Trade");   // Import flat schema
	       
	        // Create Event
	        Event xepEvent = xepPersister.getEvent("model.Trade");

	        
	        // Starting interactive prompt
			boolean always = true;
			Scanner scanner = new Scanner(System.in);
			while (always) {
				System.out.println("1. Generate and save multiple trades");
				System.out.println("2. Retrieve all trades; show execution statistics");
				System.out.println("3. JDBC Comparison - Create and save multiple trades");
				System.out.println("4. Quit");
				System.out.print("What would you like to do? ");
				
				String option = scanner.next();
				switch (option) {
				case "1":
					System.out.print("How many items do you want to generate? ");	
					int number = scanner.nextInt();
					
					// Get sample generated array to store
					sampleArray = Trade.generateSampleData(number);
					
					// Save generated trades
					Long totalStore = XEPSaveTrades(sampleArray,xepEvent);
					System.out.println("Execution time: " + totalStore + "ms");
					break;
				case "2":
					System.out.println("Fetching all. Please wait...");	
					Long totalFetch = ViewAll(xepEvent);
					System.out.println("Execution time: " + totalFetch + "ms");
					break;
				case "3":
					System.out.print("How many items to generate using JDBC? ");
					int numberJDBC = scanner.nextInt();
					
					// Get sample generated array to store
					sampleArray = Trade.generateSampleData(numberJDBC);
					
					// Save generated trades using JDBC
					Long totalJDBCStore = StoreUsingJDBC(xepPersister,sampleArray);
					System.out.println("Execution time: " + totalJDBCStore + "ms");
					break;
				case "4":
					System.out.println("Exited.");
					always = false;
					break;
				default: 
					System.out.println("Invalid option. Try again!");
					break;
				}
				
			}
			scanner.close();	
	        xepEvent.close();
	        xepPersister.close();
		} catch (XEPException e) { 
			System.out.println("Interactive prompt failed:\n" + e); 
		}
	   } // end main()

	// Save array of trade into database using xepEvent
	public static Long XEPSaveTrades(Trade[] sampleArray,Event xepEvent)
	{
		Long startTime = System.currentTimeMillis(); // To calculate execution time
		xepEvent.store(sampleArray);
		Long totalTime = System.currentTimeMillis() - startTime;
		System.out.println("Saved " + sampleArray.length + " trade(s).");
		return totalTime;
	}

	// Iterate over all trades
	public static Long ViewAll(Event xepEvent)
	{
		//Create and execute query using EventQuery
		String sqlQuery = "SELECT * FROM model.Trade WHERE purchaseprice > ? ORDER BY stockname, purchaseDate"; 
		EventQuery<Trade> xepQuery = xepEvent.createQuery(sqlQuery);
		xepQuery.setParameter(1,"0");    // find stocks purchased > $0/share (all)
		Long startTime = System.currentTimeMillis();
		xepQuery.execute();
		
		Integer cnt=0;
		// Iterate through and write names of stocks using EventQueryIterator
		EventQueryIterator<Trade> xepIter = xepQuery.getIterator();
		while (xepIter.hasNext()) {
		  Trade newSample = xepIter.next();
		  newSample.stockName = "NYSE-" + newSample.stockName;
		  xepIter.set(newSample);
		  cnt++;
		  //System.out.println(newSample.stockName + "\t" + newSample.purchasePrice + "\t" + newSample.purchaseDate);
		}
		System.out.println("Total Amount of transactions read is "+cnt);
		Long totalTime = System.currentTimeMillis() - startTime;
		xepQuery.close();
		return totalTime;
	}

	// Save array of trade into database using JDBC - which is slower than using xepEvent
	public static Long StoreUsingJDBC(EventPersister persist, Trade[] sampleArray)
	{
		Long totalTime = new Long(0);
		
		// Loop through objects to insert
		try {
			String sql = "INSERT INTO model.Trade (purchaseDate,purchaseprice,stockName) VALUES (?,?,?)";
	
			PreparedStatement myStatement = persist.prepareStatement(sql);
			myStatement.setString(1, "2016-08-12");

			// Get current time
			Long startTime = System.currentTimeMillis();

			for (int i=0; i < sampleArray.length; i++)
			{
				myStatement.setBigDecimal(2, sampleArray[i].purchasePrice);
				myStatement.setString(3, sampleArray[i].stockName);
				myStatement.addBatch();
			}
			myStatement.executeBatch();

			// Get time consuming
			totalTime = System.currentTimeMillis() - startTime;	
			System.out.println("Inserted " + sampleArray.length + " item(s) via JDBC successfully.");
			myStatement.close();
		} catch (SQLException e) {
			System.out.println("There was a problem storing items using JDBC");
			e.getMessage();
		}
		return totalTime;
	}

	// Helper method: Get connection details from config file
	public static HashMap<String, String> getConfig(String filename) throws FileNotFoundException, IOException{
        // Initial empty map to store connection details
        HashMap<String, String> map = new HashMap<String, String>();

        String line;

        // Using Buffered Reader to read file
        BufferedReader reader = new BufferedReader(new InputStreamReader(XEPExample.class.getResourceAsStream(filename)));

        while ((line = reader.readLine()) != null)
        {
            // Remove all spaces and split line based on first colon
            String[] parts = line.replaceAll("\\s+","").split(":", 2);

            // Check if line contains enough information
            if (parts.length >= 2)
            {
                String key = parts[0];
                String value = parts[1];
                map.put(key, value);
            } else {
                System.out.println("Ignoring line: " + line);
            }
        }

        reader.close();

        return map;
    }

} 