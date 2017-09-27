package ca.ubc.cs317.dict.net;

import ca.ubc.cs317.dict.exception.DictConnectionException;
import ca.ubc.cs317.dict.model.Database;
import ca.ubc.cs317.dict.model.Definition;
import ca.ubc.cs317.dict.model.MatchingStrategy;
import ca.ubc.cs317.dict.util.DictStringParser;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.*;

/**
 * Created by Jonatan on 2017-09-09.
 */
public class DictionaryConnection {

    private static final int DEFAULT_PORT = 2628;

    private Socket socket;
    private BufferedReader input;
    private PrintWriter output;

    private Map<String, Database> databaseMap = new LinkedHashMap<String, Database>();



    /** Establishes a new connection with a DICT server using an explicit host and port number, and handles initial
     * welcome messages.
     *
     * @param host Name of the host where the DICT server is running
     * @param port Port number used by the DICT server
     * @throws DictConnectionException If the host does not exist, the connection can't be established, or the messages
     * don't match their expected value.
     */

    public DictionaryConnection(String host, int port) throws DictConnectionException {
        // clientSocket: our client socket
        // output: output stream
        // input: input stream
        // TODO set timeout for socket
        // TODO check different status code 220,530,420,421

        try {
            socket = new Socket(host, port);
            output = new PrintWriter(socket.getOutputStream(), true);
            input = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // set read timeout
            socket.setSoTimeout(2500);

            // Set variables for returned status codes
            int success = 220;
            int tempUnavailable = 420;
            int shutDown = 421;
            int denied = 530;

            // Check status code:
            int statusCode = getReturnStatus();

            if (statusCode == success) {
                return;
            } else if (statusCode == tempUnavailable) {
                throw new DictConnectionException("Server temporarily unavailable");
            } else if (statusCode ==  shutDown) {
                throw new DictConnectionException("Server shutting down at operator request");
            } else if (statusCode == denied) {
                throw new DictConnectionException("Access denied");
            }

        } catch (IOException e) {
            System.err.println("Couldn't get I/O for the connection to:" + host + ":" + port);
        }
    }

    /** Establishes a new connection with a DICT server using an explicit host, with the default DICT port number, and
     * handles initial welcome messages.
     *
     * @param host Name of the host where the DICT server is running
     * @throws DictConnectionException If the host does not exist, the connection can't be established, or the messages
     * don't match their expected value.
     */
    public DictionaryConnection(String host) throws DictConnectionException {
        this(host, DEFAULT_PORT);
    }

    /** Sends the final QUIT message and closes the connection with the server. This function ignores any exception that
     * may happen while sending the message, receiving its reply, or closing the connection.
     *
     */
    public synchronized void close() {
        // close the output stream
        // close the input stream
        // close the socket
        output.println("QUIT");
        //
        try {
            input.close();
            output.close();
            socket.close();
        } catch (IOException e) {
        }
    }

    /** Requests and retrieves all definitions for a specific word.
     *
     * @param word The word who2se definition is to be retrieved.
     * @param database The database to be used to retrieve the definition. A special database may be specified,
     *                 indicating either that all regular databases should be used (database name '*'), or that only
     *                 definitions in the first database that has a definition for the word should be used
     *                 (database '!').
     * @return A collection of Definition objects containing all definitions returned by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Collection<Definition> getDefinitions(String word, Database database) throws DictConnectionException {
        Collection<Definition> set = new ArrayList<>();
        getDatabaseList(); // Ensure the list of databases has been populated

        // simply return if no word entry
        if (word.isEmpty()) return set;

        // Set variables for returned status codes
        int noMatch = 552;
        int success = 150;
        int definitionStart = 151;
        int terminate = 250;
        int invalidDb = 550;

        try {
            // Send Request for definitions
            String databaseName = database.getName();
            output.println("DEFINE" + " " + databaseName + " \"" + word + "\"");

            // Check connection status code
            int statusCode = getReturnStatus();

            if (statusCode == noMatch) {
                return set;
            } else if (statusCode == success) {
                String nextDefinition = input.readLine();
                String [] splitDefinition = DictStringParser.splitAtoms(nextDefinition);

                //new definition line in form: definitionStart "returnedWord" returnedDb
                while (splitDefinition[0].equals(Integer.toString(definitionStart))) {
                    String returnedWord = splitDefinition[1];
                    String returnedDb = splitDefinition[2];

                    // Create definition object, set definition and add to set
                    String nextLine = input.readLine();
                    Database mappedDb = databaseMap.get(returnedDb);
                    Definition def = new Definition(returnedWord, mappedDb);

                    // Append definition together
                    while (!nextLine.equals(".")) {
                        def.appendDefinition(nextLine);
                        nextLine = input.readLine();
                    }
                    set.add(def);

                    nextDefinition = input.readLine();
                    splitDefinition = DictStringParser.splitAtoms(nextDefinition);
                }
                // Check validity of terminating status code
                // terminating line in the form: terminate ok [details]
                if (!splitDefinition[0].equals(Integer.toString(terminate))) {
                    throw new DictConnectionException("Expected termination status for strategy: " + terminate + System.lineSeparator() +
                            "Received termination status: " + splitDefinition[0]);
                }
            } else if (statusCode == invalidDb) {
                throw new DictConnectionException("Invalid database input");
            } else throw new DictConnectionException("Invalid status code received for definition: " + statusCode);
        } catch(IOException e){
            throw new DictConnectionException("Network error when finding definition");
        }
        return set;
    }

    /** Requests and retrieves a list of matches for a specific word pattern.
     *
     * @param word     The word whose definition is to be retrieved.
     * @param strategy The strategy to be used to retrieve the list of matches (e.g., prefix, exact).
     * @param database The database to be used to retrieve the definition. A special database may be specified,
     *                 indicating either that all regular databases should be used (database name '*'), or that only
     *                 matches in the first database that has a match for the word should be used (database '!').
     * @return A set of word matches returned by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Set<String> getMatchList(String word, MatchingStrategy strategy, Database database) throws DictConnectionException {
        Set<String> set = new LinkedHashSet<>();

        int noMatch = 552;
        int success = 152;
        int terminate = 250;
        int invalidDb = 550;
        int invalidStrat = 551;

        // simply return if no word entry
        if (word.isEmpty()) return set;

        try {
            // Send request
            String strategyName = strategy.getName();
            String databaseName = database.getName();
            output.println("MATCH " + databaseName + " " + strategyName + " \"" + word + "\"");

            // Check connection status code
            int statusCode = getReturnStatus();

            if (statusCode == noMatch) {
                return set;
            } else if (statusCode == success) {
                // parse each returned match, put into set
                String nextMatch = input.readLine();
                while (!nextMatch.equals(".")) {
                    // Lines in the form: dictName "matchWord"
                    String[] splitLine = DictStringParser.splitAtoms(nextMatch);
                    String matchWord = splitLine[1];
                    set.add(matchWord);
                    nextMatch = input.readLine();
                }
                // Check validity of terminating status code
                int endStatusCode = getReturnStatus();
                if (endStatusCode != terminate) {
                    throw new DictConnectionException("Expected termination status for matches: " + terminate + System.lineSeparator() +
                            "Received termination status: " + endStatusCode);
                }
            } else if (statusCode == invalidDb) {
                throw new DictConnectionException("Invalid database input");
            } else if (statusCode == invalidStrat) {
                throw new DictConnectionException("Invalid strategy input");
            } else throw new DictConnectionException("Invalid status code received for matches: " + statusCode);
        } catch (IOException e) {
            throw new DictConnectionException("Network error when finding matches");
        }
        return set;
    }

    /** Requests and retrieves a list of all valid databases used in the server. In addition to returning the list, this
     * method also updates the local databaseMap field, which contains a mapping from database name to Database object,
     * to be used by other methods (e.g., getDefinitionMap) to return a Database object based on the name.
     *
     * @return A collection of Database objects supported by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Collection<Database> getDatabaseList() throws DictConnectionException {

        if (!databaseMap.isEmpty()) {
            return databaseMap.values();
        }

        // Set variables for return status codes
        int noMatch = 554;
        int success = 110;
        int terminate = 250;

        try {
            // Send request for list of databases
            output.println("SHOW DATABASES");

            // Check connection code
            int statusCode = getReturnStatus();

            if (statusCode == noMatch) {
                return databaseMap.values();
            } else if (statusCode == success) {
                // Parse each returned database, put into databaseMap
                String nextLine = input.readLine();
                while (!nextLine.equals(".")) {
                    // Lines in the form: dbname "dbDescription"
                    String[] splitLine = DictStringParser.splitAtoms(nextLine);
                    String dbName = splitLine[0];
                    String dbDescription = splitLine[1];
                    databaseMap.put(dbName, new Database(dbName, dbDescription));
                    nextLine = input.readLine();
                }
                // Check validity of terminating status code
                int endStatusCode = getReturnStatus();
                if (endStatusCode != terminate) {
                    throw new DictConnectionException("Expected termination status for dictionary: " + terminate + System.lineSeparator() +
                            "Received termination status: " + endStatusCode);
                }
            } else throw new DictConnectionException("\"Invalid status code received for dictionary: " + statusCode);
        } catch (IOException e) {
            throw new DictConnectionException("Network error when finding databases");
        }
        return databaseMap.values();
    }

    /** Requests and retrieves a list of all valid matching strategies supported by the server.
     *
     * @return A set of MatchingStrategy objects supported by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Set<MatchingStrategy> getStrategyList() throws DictConnectionException {
        Set<MatchingStrategy> set = new LinkedHashSet<>();

        int noMatch = 555;
        int success = 111;
        int terminate = 250;

        try {
            // Send request for list of strategies
            output.println("SHOW STRAT");

            // Check connection status code
            int statusCode = getReturnStatus();

            if (statusCode == noMatch) {
                return set;
            } else if (statusCode == success) {
                // Parse each returned strategy, create MatchingStrategy object, add in set
                String nextLine = input.readLine();
                while (!nextLine.equals(".")) {
                    // Lines in the form: stratName "stratDescription"
                    String[] splitLine = DictStringParser.splitAtoms(nextLine);
                    String stratName = splitLine[0];
                    String stratDescription = splitLine[1];
                    set.add(new MatchingStrategy(stratName, stratDescription));
                    nextLine = input.readLine();
                }
                // Check validity of terminating status code
                int endStatusCode = getReturnStatus();
                if (endStatusCode != terminate) {
                    throw new DictConnectionException("Expected termination status for strategy: " + terminate + System.lineSeparator() +
                            "Received termination status: " + endStatusCode);
                }
            } else throw new DictConnectionException("Invalid status code received for strategy: " + statusCode);
        } catch (IOException e) {
            throw new DictConnectionException("Network error when finding strategies");
        }
        return set;
    }

    private int getReturnStatus() throws DictConnectionException {
        Status status = Status.readStatus(input);
        return status.getStatusCode();
    }
}
