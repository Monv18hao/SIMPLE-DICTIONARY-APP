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


    /*  TODO: Implement following cases:

             500 Syntax error, command not recognized
             501 Syntax error, illegal parameters
             502 Command not implemented
             503 Command parameter not implemented
             420 Server temporarily unavailable
             421 Server shutting down at operator request

     */

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

            // Check status code:
            Status status = Status.readStatus(input);
            int parsedStatus = status.getStatusCode();

            if (parsedStatus == 220) {
                System.out.print("OK");
            } else if (parsedStatus == 420) {
                throw new DictConnectionException("Server temporarily unavailable");
            } else if (parsedStatus ==  421) {
                throw new DictConnectionException("Server shutting down at operator request");
            } else if (parsedStatus == 530) {
                throw new DictConnectionException("Access denied");
            }

        } catch (IOException e) {
            System.err.println("Couldn't get I/O for the connection to:" + host);
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

        // TODO 550 Invalid database, use "SHOW DB" for list of databases
        // TODO 552 No match
        // TODO 150 n definitions retrieved - definitions follow
        // TODO 151 word database name - text follows
        // TODO 250 ok (optional timing information here)
        if (word.isEmpty() || word.equals("")) {
            return set;
        }
        try {
            // Send Request
            String databaseName = database.getName();
            output.println("DEFINE" + " " + databaseName + " " + word);


            // Check connection code
            Status status = Status.readStatus(input);
            int parsedStatus = status.getStatusCode();

            if (parsedStatus == 550) {
                throw new DictConnectionException("Invalid database, use \"SHOW DB\" for list of databases");
            } else if (parsedStatus == 552) {
                throw new DictConnectionException("No match");
            } else if (parsedStatus == 150) {
                System.out.println(status.getDetails());
                String nextDefinition = input.readLine();
                String [] parsedSummary = DictStringParser.splitAtoms(nextDefinition);
                while (parsedSummary[0].equals("151")) {
                    String parsedWord = parsedSummary[1];
                    String parsedDatabase = parsedSummary[2];

                    String definition = input.readLine();
                    String entireDefinition = "";
                    while (!definition.equals(".")) {
                        System.out.println("Definition start: " + definition);
                        if (definition.startsWith(" ")) {
                            entireDefinition = entireDefinition + "\r\n" + definition;
                        }
                        definition = input.readLine();
                    }
                    Database mappedDb = databaseMap.get(parsedDatabase);
                    Definition def = new Definition(parsedWord, mappedDb);
                    def.setDefinition(entireDefinition.trim());
                    set.add(def);
                    System.out.println("Definition: " + entireDefinition);
                    nextDefinition = input.readLine();
                    parsedSummary = DictStringParser.splitAtoms(nextDefinition);
                }
                if (!parsedSummary[0].equals("250")) {
                    throw new DictConnectionException("Incomplete DefinitionList retrieval");
                }
            } else {
                throw new DictConnectionException("Invalid Code - Definition");
            }
        } catch(IOException e){
            throw new DictConnectionException();
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

        // TODO 550 Invalid database, use "SHOW DB" for list of databases
        // TODO 551 Invalid strategy, use "SHOW STRAT" for a list of strategies
        // TODO 552 No match
        // TODO 152 n matches found - text follows
        // TODO 250 ok (optional timing information here)

        // make sure there's actual input
        if (word.isEmpty()) {
            return set;
        }
        try {
            String strategyName = strategy.getName();
            String databaseName = database.getName();
            // Send Request
            output.println("MATCH " + databaseName + " " + strategyName + " \"" + word + "\"");
            // Check connection code
            Status status = Status.readStatus(input);
            int parsedStatus = status.getStatusCode();

            if (parsedStatus == 550) {
                throw new DictConnectionException("Invalid database, use \"SHOW DB\" for list of databases");
            }
            else if (parsedStatus == 551) {
                throw new DictConnectionException("Invalid strategy, use \"SHOW STRAT\" for a list of strategies");
            }
            else if (parsedStatus == 552) {
                throw new DictConnectionException("No match");
            }
            else if (parsedStatus == 152) {
                String nextMatch = input.readLine();
                while (!nextMatch.equals(".")) {
                    // parse line and put into databaseMap
                    String[] parsedMatch = DictStringParser.splitAtoms(nextMatch);
                    //String parsedMatchDatabase = parsedMatch[0];
                    String parsedMatchWord = parsedMatch[1];
                    set.add(parsedMatchWord);
                    nextMatch = input.readLine();
                }
                Status endStatus = Status.readStatus(input);
                int endStatusCode = endStatus.getStatusCode();

                if (endStatusCode != 250) {
                    throw new DictConnectionException("Incomplete MatchList retrieval");
                }
            } else {
                throw new DictConnectionException("Invalid Code - Matches");
            }
        } catch (IOException e) {
            throw new DictConnectionException();
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


        // Ensure valid output
        // TODO Clean Up
        // StatusCode: 110 n databases present - text follows
        // StatusCode: 554 No databases present

        if (!databaseMap.isEmpty()) {
            return databaseMap.values();
        }

        try {
            // Send request for list of databases
            output.println("SHOW DATABASES");

            // Check connection code
            Status status = Status.readStatus(input);
            int parsedStatus = status.getStatusCode();

            if (parsedStatus == 554) {
                return databaseMap.values();
            }
            else if (parsedStatus == 110) {
                String nextDatabase = input.readLine();
                while (!nextDatabase.equals(".")) {
                    // parse line and put into databaseMap
                    String[] parsedDatabase = DictStringParser.splitAtoms(nextDatabase);
                    String parsedDatabaseName = parsedDatabase[0];
                    String parsedDatabaseDescription = parsedDatabase[1];
                    databaseMap.put(parsedDatabaseName, new Database(parsedDatabaseName, parsedDatabaseDescription));
                    nextDatabase = input.readLine();
                }

                Status endStatus = Status.readStatus(input);
                int endStatusCode = endStatus.getStatusCode();

                if (endStatusCode != 250) {
                    throw new DictConnectionException("Incomplete Dictionary retrieval");
                }
            }
            else {
                throw new DictConnectionException("Invalid Code - Dict");
            }
        } catch (IOException e) {
            throw new DictConnectionException();
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

        try {
            // Send request for list of databases
            output.println("SHOW STRAT");

            // Check connection code.
            Status status = Status.readStatus(input);
            int parsedStatus = status.getStatusCode();

            System.out.println("Parsed Status:" + parsedStatus);
            if (parsedStatus == 555) {
                return set;
            } else if (parsedStatus == 111) {
                String nextStrategy = input.readLine();
                while (!nextStrategy.equals(".")) {
                // parse line and put into databaseMap
                    String[] parsedStrategy = DictStringParser.splitAtoms(nextStrategy);
                    String parsedStrategyName = parsedStrategy[0];
                    String parsedStrategyDescription = parsedStrategy[1];
                    set.add(new MatchingStrategy(parsedStrategyName, parsedStrategyDescription));
                    nextStrategy = input.readLine();
                }

                Status endStatus = Status.readStatus(input);
                int endStatusCode = endStatus.getStatusCode();
                System.out.println("End parsed status: " + endStatusCode);
                if (endStatusCode != 250) {
                    throw new DictConnectionException("Incomplete Strat retrieval");
                }
            }
            else throw new DictConnectionException("Invalid Code - Strat");
        } catch (IOException e) {
            throw new DictConnectionException();
        }
        return set;
    }

}
