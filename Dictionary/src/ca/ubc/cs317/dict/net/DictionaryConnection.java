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
        // TODO Add your code here
        try {
            socket = new Socket(host, port);
            output = new PrintWriter(socket.getOutputStream(), true);
            input = new BufferedReader(
                    new InputStreamReader(socket.getInputStream())
            );
            Status.readStatus(input);
        } catch (IOException e) {
            throw new DictConnectionException();
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

        // TODO Add your code here
        output.println("QUIT");
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Requests and retrieves all definitions for a specific word.
     *
     * @param word The word whose definition is to be retrieved.
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

        // TODO Add your code here

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

        // TODO Add your code here

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

        if (!databaseMap.isEmpty()) return databaseMap.values();

        // Send request for list of databases
        output.println("SHOW DATABASES");

        // Ensure valid output
        int[] validStatusCodes = {110};
        validateStatusCodes(validStatusCodes);

        // TODO case handling for 554 return code

        // read all the lines for the good case
        try {
            String nextDatabase = input.readLine();
            while (!nextDatabase.equals(".")) {
                // parse line and put into databaseMap
                String[] parsedDatabase = DictStringParser.splitAtoms(nextDatabase);
                String parsedDatabaseName = parsedDatabase[0];
                String parsedDatabaseDescription = parsedDatabase[1];
                databaseMap.put(parsedDatabaseName, new Database(parsedDatabaseName,parsedDatabaseDescription));
                nextDatabase = input.readLine();
            }

            //confirm final line is status code 250:
            int[] finalCode = {250};
            validateStatusCodes(finalCode);

        } catch (IOException e) {
            throw new DictConnectionException();
        }

        // TODO Add your code here

        return databaseMap.values();
    }

    /** Requests and retrieves a list of all valid matching strategies supported by the server.
     *
     * @return A set of MatchingStrategy objects supported by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Set<MatchingStrategy> getStrategyList() throws DictConnectionException {
        Set<MatchingStrategy> set = new LinkedHashSet<>();

        // TODO Add your code here

        // Send request for list of databases
        output.println("SHOW STRAT");

        // Ensure valid output
        int[] validStatusCodes = {111};
        validateStatusCodes(validStatusCodes);

        // TODO case handling 555 return code

        // read all the lines for the good case
        try {
            String nextStrategy = input.readLine();
            while (!nextStrategy.equals(".")) {
                // parse line and put into databaseMap
                String[] parsedStrategy = DictStringParser.splitAtoms(nextStrategy);
                String parsedStrategyName = parsedStrategy[0];
                String parsedStrategyDescription = parsedStrategy[1];
                set.add(new MatchingStrategy(parsedStrategyName, parsedStrategyDescription));
                nextStrategy = input.readLine();
            }

            //confirm final line is status code 250:
            int[] finalCode = {250};
            validateStatusCodes(finalCode);

        } catch (IOException e) {
            throw new DictConnectionException();
        }
        return set;
    }

    private void validateStatusCodes(int[] validStatusCodes) throws DictConnectionException {
        Status status = Status.readStatus(input);
        int parsedStatus = status.getStatusCode();
        boolean validStatus = false;
        for (int statusCode : validStatusCodes) {
            if (statusCode == parsedStatus) {
                validStatus = true;
                System.out.println("Returned status code: " + parsedStatus + " matches expected SC: " + statusCode);
                break;
            }
        }
        if (validStatus == false) {
            throw new DictConnectionException();
        }
    }
}
