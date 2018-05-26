package activitystreamer.server;

import activitystreamer.util.InvalidMessageProcessor;
import activitystreamer.util.ActivityMsg;
import activitystreamer.util.Settings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.net.*;
import java.util.Collections;
import java.util.*;

public class Control extends Thread {
	private static final Logger log = LogManager.getLogger();
	private static ArrayList<Connection> connections;
	private static LinkedList<Connection> templist;
	//Registration is a HashMap containing the username and secret of registered clients.
	private static Map registration = new HashMap();
    //ServerLoads is a list of HashMaps containing the serverID, hostname, port and load of every other server in this system.
	private static List<HashMap<String, Integer>> serverLoads = new LinkedList<>();
//	private static LinkedList<SocketAddress> serverList = new LinkedList<>();
    private static LinkedList<SocketAddress> clientList = new LinkedList<>();
    private static HashMap serverMap = new HashMap();
    private static String serverID = Settings.getLocalHostname()+":"+Settings.getLocalPort();
    public static String getServerID() {
        return serverID;
    }
	private static boolean term=false;
	private static Listener listener;
    private static int connectedServerCount = 0;
	private static int lockAllowedCount = 0;
	private static Connection registerClient;
	private static String clientUsername;
	private static boolean isRegistering = false;
	private static Connection requestServer;
//    private static boolean existTimeStamp = false;
//    private static long startTime;
    private static List<ActivityMsg> al = new ArrayList<ActivityMsg>();
    //Use a HashMap to storage the id of the crush server, and unsuccessfully broadcast message in its value.
    public static Map<String, ArrayList<String>> undeliveredBoradcastMsg = new HashMap<>();

    /**
     * otherServer list is restore the connection of the servers connected to this server
     * use getOtherServers() method to get the update otherServer
     */
    private static LinkedList<Connection> otherServers;

	protected static Control control = null;

	public static Control getInstance() {
		if(control==null){
			control=new Control();
		}
		return control;
	}

	public Control() {
		// initialize the connections array
		connections = new ArrayList<Connection>();
        if(Settings.getSecret().equals("")){
            // setting a random secret for server
            // Settings.setSecret(Settings.nextSecret());
            Settings.setSecret("gd8a67ieqvspmor4rt3mp43hlb");
            log.info("Using server secret: "+Settings.getSecret());
        }
		// connect to another server when initiate the server
		// start a listener
		try {
            initiateConnection(false );
			listener = new Listener();
		} catch (IOException e1) {
			log.fatal("failed to startup a listening thread: "+e1);
			System.exit(-1);
		}
		start();
	}

	/**
	 * getOtherServers() return the connected server connection list
	 */
	public static LinkedList<Connection> getOtherServers() {
	    LinkedList<Connection> otherServers = new LinkedList<>();
        for(Connection con: connections){
            if(serverMap.values().contains(con.getSocket().getRemoteSocketAddress())){
                otherServers.push(con);
            }
        }
        log.info(otherServers.size());
        return otherServers;
    }

	public static LinkedList<SocketAddress> getServerList() {
	    return (LinkedList<SocketAddress>) serverMap.values();
    }

	public boolean initiateConnection(boolean isReconnection){
		// make a connection to another server if remote hostname is supplied
		if(Settings.getRemoteHostname()!=null){
			try {
//
                Socket socket = new Socket(Settings.getRemoteHostname(),Settings.getRemotePort());
                outgoingConnection(socket);
            } catch (IOException e) {
				log.error("failed to make connection to "+Settings.getRemoteHostname()+":"+Settings.getRemotePort()+" :"+e);
                String crashServer = Settings.getRemoteHostname()+":"+Settings.getRemotePort();
//                log.info("Contains current crash server? "+(undeliveredBoradcastMsg.containsKey(crashServer)));
                if(!undeliveredBoradcastMsg.containsKey(crashServer)) {
                    initCrashServer(crashServer);
                }
				if (!isReconnection)
				System.exit(-1);
				return false;
			}
		}
		return true;
	}

	public void restoreConnection(String crashedHostName, int crashPort){
	    if(Settings.getCrashedHostname() != null && Settings.getCrashedPort() != 0){
	        try {
	            outgoingConnection(new Socket(Settings.getCrashedHostname(), Settings.getCrashedPort()));
            } catch (IOException e) {
	            log.error("failed to make connection to crashed server: "+
                        Settings.getCrashedHostname()+":"+Settings.getCrashedPort()+", try again in 10s");
            }
        }
    }
    

    public boolean checkCon(Connection con, String type){
        switch (type){
            case "SERVER":
                for(Object address: serverMap.values()){
                    if(address.equals(con.getSocket().getRemoteSocketAddress())){
                        return true;
                    }
                }
                break;

            case "CLIENT":
                for(SocketAddress client: clientList){
                    if(client.equals(con.getSocket().getRemoteSocketAddress())){
                        return true;
                    }
                }
                break;
        }
        return false;
    }


	/*
	 * Processing incoming messages from the connection.
	 * Return true if the connection should close.
	 */
	public boolean process(Connection con,String msg){
	    
        JSONParser parser = new JSONParser();
	    try{
	        //SyncRegistration(con);
            JSONObject clientMsg = (JSONObject) parser.parse(msg);
            if(!clientMsg.containsKey("command")){
                con.writeMsg(InvalidMessageProcessor.invalidInfo("NO_COMMAND"));
                return false;
            }
            String command = (String) clientMsg.get("command");
			String username;
			String secret;
            switch (command){
                case "LOGIN":
					username = (String)clientMsg.get("username");
					secret = (String)clientMsg.get("secret");
                    JSONObject loginMsg = login(username, secret);
                    con.writeMsg(loginMsg.toJSONString());
                    if(loginMsg.get("command").equals("LOGIN_SUCCESS")){
                        log.info("A user has logged in: "+username);
                        //Do redirect.

                        if(serverLoads != null && serverLoads.size() > 0){

                            JSONObject redirMsg = redirect();
                            if(redirMsg.containsKey("hostname")){
                                con.writeMsg(redirMsg.toJSONString());
                                log.info("Redirect user "+username+" to "+redirMsg.get("hostname")+":"+redirMsg.get("port")+".");
                                con.closeCon();
                                connectionClosed(con);
                                break;
                            }
                        }
                        clientList.add(con.getSocket().getRemoteSocketAddress());
                    }
                    else {
                        con.closeCon();
                        connectionClosed(con);
                    }
                    break;

				case "REGISTER":
				    if(checkCon(con, "CLIENT")){
                        con.writeMsg(InvalidMessageProcessor.invalidInfo("REGISTER"));
                        con.closeCon();
                        connectionClosed(con);
                        break;
                    }
				    username = (String)clientMsg.get("username");
                    secret = (String) clientMsg.get("secret");
				    isRegistering = doRegister(con,username,secret);
					log.info("register for " + username);
					break;

                case "AUTHENTICATE":
					if(doAu(con,(String)clientMsg.get("secret"))){
					    serverMap.put(clientMsg.get("id"), con.getSocket().getRemoteSocketAddress());

                        //If the connected server is authenticated, send registration info to it.
//                        reBoradcastMsgToCrashServer(con);
                        if (registration.size() != 0) {
                            SyncRegistration(con);
                        }

                        con.setSender((String)clientMsg.get("id"));
                        con.setReceiver(serverID);
                        reBoradcastMsgToCrashServer(con,(String)clientMsg.get("id"));
					}else{
					    con.closeCon();
					    connectionClosed(con);
					}
                    break;

                case "LOCK_REQUEST":
                    if(!checkCon(con, "SERVER")){
                        con.writeMsg(InvalidMessageProcessor.invalidInfo("SERVER"));
                        con.closeCon();
                        connectionClosed(con);
                        break;
                    }
                    username = (String)clientMsg.get("username");
                    secret = (String)clientMsg.get("secret");
                    if (serverMap.size() <= 1 && registration.containsKey(username)) {
						sendLockResult(con, username, secret, false);
						log.info("Lock denied");
					} else if (serverMap.size() <= 1 && !registration.containsKey(username)) {
                    	sendLockResult(con, username, secret, true);
                    	registration.put(username, secret);
                    	log.info("Lock allowed");
                    } else {
                    	log.info(serverMap.size());
                        requestServer = con;
                        //List<Connection> otherServers = new ArrayList<>(connections.subList(0,connectedServerCount));
                        LinkedList<Connection> otherServers = new LinkedList<>();
                        for(Object server: serverMap.values()){
                            for(Connection cons: connections){
                                if(cons.getSocket().getRemoteSocketAddress().equals(server)){
                                    otherServers.push(cons);
                                }
                            }
                        }
                        otherServers.remove(con);
                        sendLockRequest(otherServers, username, secret);
                    }
					break;

                case "LOCK_ALLOWED":
                    if(!checkCon(con, "SERVER")){
                        con.writeMsg(InvalidMessageProcessor.invalidInfo("SERVER"));
                        con.closeCon();
                        connectionClosed(con);
                        break;
                    }
                    username = (String)clientMsg.get("username");
                    secret = (String) clientMsg.get("secret");
                    lockAllowedCount += 1;
                    if (isRegistering && lockAllowedCount == serverMap.size()) {
                        registerClient.writeMsg(registerSuccess(clientUsername, true));
                        registration.put(username,secret);
                        lockAllowedCount = 0;
                    } else if (!isRegistering && lockAllowedCount == serverMap.size() - 1){
						sendLockResult(requestServer, username, secret, true);
						registration.put(username,secret);
						lockAllowedCount = 0;
                    }
                    break;

                case "LOCK_DENIED":
                    if(!checkCon(con, "SERVER")){
                        con.writeMsg(InvalidMessageProcessor.invalidInfo("SERVER"));
                        con.closeCon();
                        connectionClosed(con);
                        break;
                    }
                    username = (String)clientMsg.get("username");
                    secret = (String) clientMsg.get("secret");
                    if (isRegistering) {
						registerClient.writeMsg(registerSuccess(clientUsername, false));
						lockAllowedCount = 0;
					} else {
						if (registration.containsKey(username) && registration.containsValue(secret)) {
							registration.remove(username, secret);
							lockAllowedCount = 0;
						}
						broadcastLockDenied(con, username, secret);
                    	//requestServer.writeMsg(registerSuccess(clientUsername, false));
					}
					break;

				case "ACTIVITY_MESSAGE":
					log.info("Activity message received from client.");
					username = (String)clientMsg.get("username");
					secret = (String)clientMsg.get("secret");
                    if((username.equals("anonymous")||checkCon(con,"CLIENT"))){
                        JSONObject actBroadcast = new JSONObject();

                        JSONParser parser2 = new JSONParser();
                        JSONObject msgFromClient = (JSONObject) parser2.parse(msg);
                        msgFromClient.put("timestamp",System.currentTimeMillis());
                        String msgString = msgFromClient.toString();
                        actBroadcast.put("command","ACTIVITY_BROADCAST");
                        actBroadcast.put("activity",msgString);

//                        if (serverMap.size() > 0) {
//                            broadcast(connections.subList(0, serverMap.size()), actBroadcast.toJSONString());
//                        }
                        LinkedList<Connection> tempOtherServers = new LinkedList<>();
                        for(Connection cons: connections){
                            if(serverMap.values().contains(cons.getSocket().getRemoteSocketAddress())){
                                tempOtherServers.push(cons);
                            }
                        }
                        tempOtherServers.remove(con);
                        broadcast(tempOtherServers, actBroadcast.toJSONString());

                        if(undeliveredBoradcastMsg.size()>0){
                            storageUndeliveredMsg(actBroadcast.toJSONString());
                        }


//                        broadcastToServer(con,msg);
                        broadcastToClient(msg);

					}else{
                        JSONObject authenticationFail = new JSONObject();
                        authenticationFail.put("command","AUTHENTICATION_FAIL");
                        authenticationFail.put("info","invalid username or secret. ");
                        con.writeMsg(authenticationFail.toString());
					}
                    break;

				case "ACTIVITY_BROADCAST":

                    if(!checkCon(con, "SERVER")){
                        con.writeMsg(InvalidMessageProcessor.invalidInfo("SERVER"));
                        con.closeCon();
                        connectionClosed(con);
                        break;
                    }
				    log.info("Activity broadcast message received from server." );
//					JSONObject  activityBroadcast = new JSONObject();
                    JSONParser parser3 = new JSONParser();
                    JSONObject msgFromServer = (JSONObject) parser3.parse(msg);
                    String msgString = (String)msgFromServer.get("activity");
                    JSONObject actMsg = (JSONObject) parser3.parse(msgString);
                    String msg_activity = (String)actMsg.get("activity");
                    String msg_command = (String)actMsg.get("command");
                    String msg_username = (String)actMsg.get("username");
                    long msg_timestamp = (long)actMsg.get("timestamp");
                    ActivityMsg temp = new ActivityMsg(msg_activity,msg_command,msg_username,msg_timestamp);
                    al.add(temp);
					broadcastToServer(con,msg);
					if(undeliveredBoradcastMsg.size()>0){
                        storageUndeliveredMsg(msg);
                    }
//					broadcastToClient(connections,modifiedMsg);
					break;

                case "SYNCHRONIZE":
                    if(!checkCon(con, "SERVER")){
                        con.writeMsg(InvalidMessageProcessor.invalidInfo("SERVER"));
                        con.closeCon();
                        connectionClosed(con);
                        break;
                    }

                    registration.putAll((HashMap)clientMsg.get("registration"));
                    break;

				//When a SERVER_ANNOUNCE message is received, update the serverLoads according to the message.
                case "SERVER_ANNOUNCE":
                    if(!checkCon(con, "SERVER")){
                        con.writeMsg(InvalidMessageProcessor.invalidInfo("SERVER"));
                        con.closeCon();
                        connectionClosed(con);
                        break;
                    }
                    try{
                        JSONObject announceInfo = (JSONObject) parser.parse(msg);
                        //Iterate the serverLoads to check if the message is from a known server.
                        //If there was, update the existing one.
                        boolean isNewServer = true;
                        if(serverLoads.size() > 0){
                            for(HashMap server: serverLoads){
                                if(server.get("id").equals(announceInfo.get("id"))){
                                    server.put("load", announceInfo.get("load"));
                                    isNewServer = false;
                                    break;
                                }
                            }

                        }

                        //If no known server was found, add a new HashMap to serverLoads for the new server.
                        if(isNewServer == true){
                            HashMap newServer = new HashMap();
                            newServer.put("id", announceInfo.get("id"));
                            newServer.put("hostname", announceInfo.get("hostname"));
                            newServer.put("port", announceInfo.get("port"));
                            newServer.put("load", announceInfo.get("load"));
                            this.serverLoads.add(newServer);

                        }
                        //Forward this message to other server.
                        LinkedList<Connection> forwardServers = new LinkedList<>();
                        for(Connection cons: connections){
                            if(serverMap.values().contains(cons.getSocket().getRemoteSocketAddress())){
                                forwardServers.push(cons);

                            }
                        }
                        forwardServers.remove(con);

                        broadcast(forwardServers, msg);

                    } catch (Exception e){
                        e.printStackTrace();

//                        con.writeMsg(InvalidMessageProcessor.invalidInfo("JSON_PARSE_ERROR"));
//                        return false;
                    }

                    if(serverLoads != null && serverLoads.size() > 0){

                        JSONObject redirMsg = redirect();
                        if(redirMsg.containsKey("hostname")){
                            SocketAddress client = clientList.pop();
                            Connection conToClose = null;
                            for(Connection cons: connections){
                                if(cons.getSocket().getRemoteSocketAddress().equals(client)){
                                    cons.writeMsg(redirMsg.toJSONString());
                                    log.info("Redirect user "+client+" to "+redirMsg.get("hostname")+":"+redirMsg.get("port")+".");

                                    conToClose = cons;


                                }
                            }
                            if(conToClose != null){
                                connectionClosed(conToClose);

                            }



                            break;
                        }
                    }

                    break;


                case "AUTHENTICATION_FAIL":

                    con.closeCon();
                    connectionClosed(con);
                    this.setTerm(true);
                    System.exit(0);
                    break;

                case "INVALID_MESSAGE":
                    break;

				case "LOGOUT":
					clientList.remove(con.getSocket().getRemoteSocketAddress());
				    connections.remove(con);
					break;

				default:
                    con.writeMsg(InvalidMessageProcessor.invalidInfo("UNKNOWN_COMMAND"));
					break;
            }
        }
        catch (ClassCastException | ParseException e) {
            con.writeMsg(InvalidMessageProcessor.invalidInfo("JSON_PARSE_ERROR"));
        }
        catch (NullPointerException e) {
	        e.printStackTrace();
//	        con.writeMsg(InvalidMessageProcessor.invalidInfo("NULL MESSAGE"));
        }
        catch (Exception e){
            e.printStackTrace();
        }

		return true;
	}

	/**
	 * Compare the input username and secret with the ones in (HashMap)registration.
	 * Return a JSONObject.
	 */
	public synchronized JSONObject login(String username, String secret){

        JSONObject loginResult = new JSONObject();
        if(username != null && username != "" ){
	        if(secret != null && registration.containsKey(username) && registration.get(username).equals(secret)
                    || username.equals("anonymous")){

	            loginResult.put("command", "LOGIN_SUCCESS");
                loginResult.put("info", "Logged in as user: "+username);
            }
            else {
                loginResult.put("command", "LOGIN_FAILED");
                loginResult.put("info", "Username and secret do not match. secret: "+secret);
                log.info(username+" "+secret);
			}
        }
        else{
            loginResult.put("command", "LOGIN_FAILED");
            loginResult.put("info", "A username must be provided.");
        }
	    return loginResult;
    }

    /**
     * Check load balance and redirect users if there is a need.
     * Iterate the serverLoads to find if there is a server with a load which is at least 2 clients less than the current one.
     * If there was, send a message to the client which tries to connect, to redirect it to the server with a smaller load found.
     */
    public JSONObject redirect(){
        JSONObject redirMsg = new JSONObject();

        for(HashMap server : serverLoads){
            if(clientList.size() - (long)server.get("load") >= 2){
//                log.info(this.getConnections().size()+", "+ serverMap.size());
                String redirHost = server.get("hostname").toString();
                String redirPort = server.get("port").toString();
                redirMsg.put("command", "REDIRECT");
                redirMsg.put("hostname", redirHost);
                redirMsg.put("port", redirPort);
                break;
            }
        }

        return redirMsg;
    }


	/**
     * REGISTER
     * - first check the local storage if the incoming username exits.
     * - return `REGISTER_FAIL` if it does, Else check the other servers.
     * - return `REGISTER_FAIL` and disconnect or return REGISTER_SUCCESS and store it.
     *
     * Only support one server now, without checking the other server;
     */
	public boolean doRegister(Connection con, String username, String secret) {
		if (registration.containsKey(username)) {
			con.writeMsg(registerSuccess(username,false));
            return false;
		} else if (serverMap.size() > 0) {

		       sendLockRequest(getOtherServers(), username, secret);

		       registerClient = con;
		       clientUsername = username;
		       return true;
        } else {
		    con.writeMsg(registerSuccess(username,true));
		    registration.put(username, secret);
		    return false;
		}
	}

    public synchronized void processActivityToClient(){


        LinkedList<Connection> tempClientList = new LinkedList<>();

        for (Connection cons : connections) {
            if (clientList.contains(cons.getSocket().getRemoteSocketAddress())) {
                tempClientList.push(cons);
//                cons.writeMsg(activityJSON);
//                log.info("Activity message broadcast to client.");
            }
        }

	    if(al.size()>0) {
            Collections.sort(al);
            for (int i = 0; i < tempClientList.size(); i++) {
                for (int j = 0; j < al.size(); j++) {
                    String msg_act = al.get(j).getActivity();
                    String msg_cmd = al.get(j).getCommand();
                    String msg_uname = al.get(j).getUsername();
//                long msg_time = al.get(j).getTimestamp();
                    JSONObject msgToClient = new JSONObject();
                    msgToClient.put("activity", msg_act);
                    msgToClient.put("command", msg_cmd);
                    msgToClient.put("username", msg_uname);
//                msgToClient.put("timestamp",msg_time);
                    tempClientList.get(i).writeMsg(msgToClient.toString());
                }
                log.info("Activity message broadcast to client.");
            }
            al.clear();
            log.info("Activity ArrayList had been cleared.");
        }else{
            log.info("No activity exist in ArrayList.");
        }
    }

    public synchronized void broadcastToClient(String activityJSON) {

        for (Connection cons : connections) {
            if (clientList.contains(cons.getSocket().getRemoteSocketAddress())) {
                //            tempClientList.push(cons);
                cons.writeMsg(activityJSON);
                log.info("Activity message broadcast to client.");
            }
        }
    }

    public synchronized void broadcastToServer(Connection incommingCon, String activityJSON) {

        LinkedList<Connection> otherServers = new LinkedList<>();
        for (Connection cons : connections) {
            if (serverMap.values().contains(cons.getSocket().getRemoteSocketAddress())) {
                otherServers.push(cons);
            }
        }
        otherServers.remove(incommingCon);
        broadcast(otherServers, activityJSON);
        log.info("Broadcast message to other server.");

    }


    public synchronized void storageUndeliveredMsg(String newMsg) {
        if (undeliveredBoradcastMsg.size() > 0) {
            Iterator it = undeliveredBoradcastMsg.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry entry = (Map.Entry) it.next();
                String key = (String) entry.getKey();
                log.info("Storage undelivered msg for "+key+".");
                log.info("Msg :"+newMsg);
                ArrayList<String> value = (ArrayList<String>) entry.getValue();
                value.add(newMsg);
                undeliveredBoradcastMsg.put(key, value);
            }
        }
    }

    public synchronized void initCrashServer(String crashServer) {
        ArrayList<String> tempAl = new ArrayList<>();
        undeliveredBoradcastMsg.put(crashServer, tempAl);
    }

    public synchronized void showUndeliveredMsg(){
        if(undeliveredBoradcastMsg.size()>0) {
            Iterator iter = undeliveredBoradcastMsg.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry entry = (Map.Entry) iter.next();
                String key = (String) entry.getKey();
                log.info("Host id: " + key);
                ArrayList<String> val = (ArrayList<String>) entry.getValue();
                if(val.size()>0) {
                    for (int i = 0; i < val.size(); i++) {
                        log.info("Message (" + (i + 1) + ") th :" + val.get(i));
                    }
                }else{
                    log.info("No undelivered msg for host: " + key);
                }
            }
        }else{
            log.info("No undelivered msg in ArrayList");
        }
    }

    public synchronized void reBoradcastMsgToCrashServer(Connection currentCon,String targetServer) {
	    if(undeliveredBoradcastMsg.size()>0) {
            log.info("Preparing to deliver msg to server "+targetServer);
            boolean crashed = undeliveredBoradcastMsg.containsKey(targetServer);
            if (crashed) {
                if (undeliveredBoradcastMsg.get(targetServer).size() > 0) {
                    for (int i = 0; i < undeliveredBoradcastMsg.get(targetServer).size(); i++) {
                        currentCon.writeMsg(undeliveredBoradcastMsg.get(targetServer).get(i));
                    }
                }else{
                    log.info("No msg in buffer.");
                }
            }
            undeliveredBoradcastMsg.remove(targetServer);
        }
    }

	/**
	 * Return REGISTER_SUCCESS or REGISTER_FAIL
	 */
	public String registerSuccess(String userName, boolean result) {
		JSONObject resultJSON = new JSONObject();
		if (result) {
			resultJSON.put("command", "REGISTER_SUCCESS");
			resultJSON.put("info", "register success for " + userName);
		} else {
			resultJSON.put("command","REGISTER_FAIL");
			resultJSON.put("info", userName + " is already registered with the system");
		}

		return resultJSON.toJSONString();
	}

	public boolean sendLockRequest(List<Connection> cons, String username, String secret) {
		JSONObject lockInfo = new JSONObject();
		lockInfo.put("command", "LOCK_REQUEST");
		lockInfo.put("username", username);
		lockInfo.put("secret", secret);
		String lockRequestJSON = lockInfo.toJSONString();
        broadcast(cons, lockRequestJSON);
        log.info("send lock request for: " + username + "to " + connections.get(0));
		return true;
	}

	public boolean sendLockResult(Connection con, String username, String secret, boolean isAllowed) {
	    JSONObject lockResult = new JSONObject();
	    if (isAllowed) {
			lockResult.put("command", "LOCK_ALLOWED");
			registration.put(username, secret);
		} else {
			lockResult.put("command", "LOCK_DENIED");
		}
		lockResult.put("username", username);
		lockResult.put("secret", secret);
		String lockResultJSON = lockResult.toJSONString();
		con.writeMsg(lockResultJSON);
        return true;
    }

	public boolean broadcastLockDenied(Connection fromCon, String username, String secret) {
		JSONObject lockResult = new JSONObject();
		lockResult.put("command", "LOCK_DENIED");
		lockResult.put("username", username);
		lockResult.put("secret", secret);
		String lockResultJSON = lockResult.toJSONString();
		if (serverMap.size() > 1) {

			//List<Connection> otherServers = connections.subList(0, connectedServerCount);
			getOtherServers().remove(fromCon);
			broadcast(otherServers, lockResultJSON);
		}
		return false;
	}

	/**
	 * Broadcast message to certain connections
	 */
	public boolean broadcast(List<Connection> cons, String msg) {
		for (Connection c : cons) {
			c.writeMsg(msg);
		}
		return true;
	}

	public boolean SyncRegistration(Connection con) {
	    JSONObject registrationObj = new JSONObject();
	    registrationObj.put("command", "SYNCHRONIZE");
	    registrationObj.put("registration", registration);
        String registrationJSON = registrationObj.toJSONString();
        if (registration.size() != 0) {
            log.info("sending registration information: " + registrationJSON);
            con.writeMsg(registrationJSON);
        }
        return true;
    }

	public void announce(){
        JSONObject announceInfo = new JSONObject();
        announceInfo.put("command", "SERVER_ANNOUNCE");
        announceInfo.put("id", serverID);
        announceInfo.put("load", clientList.size());
        announceInfo.put("hostname", Settings.getLocalHostname());
        announceInfo.put("port", Settings.getLocalPort());
        LinkedList<Connection> otherServers = new LinkedList<>();
        for(Connection cons: connections){
            if(serverMap.values().contains(cons.getSocket().getRemoteSocketAddress())){
                otherServers.push(cons);
            }
        }
        broadcast(otherServers, announceInfo.toJSONString());
    }

    public void sendAu(Connection con, String secret) {

        JSONObject authenMsg = new JSONObject();
        authenMsg.put("command", "AUTHENTICATE");
        authenMsg.put("secret", secret);
        authenMsg.put("id",serverID);
        String authenJSON = authenMsg.toJSONString();
        log.info("Authenticate send to: "+con.getSocket().getRemoteSocketAddress());
        serverMap.put(Settings.getRemoteHostname()+":"+Settings.getRemotePort(), con.getSocket().getRemoteSocketAddress());

        try {
            con.writeMsg(authenJSON);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean doAu(Connection con, String s) {
        if (s.equals(Settings.getSecret())) {
            return true;
        } else {

            JSONObject authenfailMsg = new JSONObject();
            authenfailMsg.put("command", "AUTHENTICATION_FAIL");
            authenfailMsg.put("info", "the supplied secret is incorrect: " + s);

            String authenfailJSON = authenfailMsg.toJSONString();
            try {
                con.writeMsg(authenfailJSON);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        }
    }

	/**
	 * The connection has been closed by the other party.
	 */
	public synchronized void connectionClosed(Connection con){
//		if(!term)
		    connections.remove(con);
	}

	/**
	 * A new incoming connection has been established, and a reference is returned to it
	 */
	public synchronized Connection incomingConnection(Socket s) throws IOException{
		log.debug("incomming connection: "+Settings.socketAddress(s));
		Connection c = new Connection(s);
		connections.add(c);
		return c;
	}

	/**
	 * A new outgoing connection has been established, and a reference is returned to it
	 */
	public synchronized Connection outgoingConnection(Socket s) throws IOException{
		log.debug("outgoing connection: "+Settings.socketAddress(s));
		Connection c = new Connection(s);
		connections.add(c);

		return c;
	}



    @Override
	public void run(){
        if(Settings.getRemoteHostname() != null){
            String serverSecret = Settings.getSecret();
            sendAu(connections.get(0), serverSecret);
        }
	    log.info("using activity interval of "+Settings.getActivityInterval()+" milliseconds");
		while(!term){
			// do something with 5 second intervals in between
			try {
				Thread.sleep(Settings.getActivityInterval());
			} catch (InterruptedException e) {
				log.info("received an interrupt, system is shutting down");
				break;
			}


			if(!term){
//				log.debug("doing activity");
//				term=doActivity();

			}
			if(serverMap.size() >= 1){

			    try{

                    for(Object value:serverMap.values()){
                        log.info(value);
                    }
			        announce();
                    showUndeliveredMsg();
                    processActivityToClient();
                }
                catch (Exception e){
			        log.error("A server has quited accidentally. System failed.");
			        //System.exit(-1);
                }
            }

		}
		log.info("closing "+connections.size()+" connections");
		// clean up
		for(Connection connection : connections){
			connection.closeCon();
		}
		listener.setTerm(true);

	}

	public final void setTerm(boolean t){
		term=t;
	}

	public final ArrayList<Connection> getConnections() {
		return connections;
	}
}
