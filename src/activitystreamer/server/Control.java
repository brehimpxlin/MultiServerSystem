package activitystreamer.server;

import java.io.IOException;
import java.io.Serializable;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import activitystreamer.util.Settings;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class Control extends Thread {
	private static final Logger log = LogManager.getLogger();
	private static ArrayList<Connection> connections;
	private static ArrayList<Connection> templist;
	//Registration is a HashMap containing the username and secret of registered clients.
	private static Map registration = new HashMap();
    //ServerLoads is a list of HashMaps containing the serverID, hostname, port and load of every other server in this system.
	private static List<HashMap> serverLoads;
	private static String serverID = "server 0";
	private static boolean term=false;
	private static Listener listener;
	private static ServerConnecter serverConnecter;
	private static int connectedServerCount = 0;
	private static int lockAllowedCount = 0;
	private static Connection registerClient;
	private static String clientUsername;
	private static boolean isRegistering = false;
	private static Connection requestServer;
	//private static String secret = "fmnmpp3ai91qb3gc2bvs14g3ue";
	private static String serverSecret;

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

		// connect to another server when initiate the server
		// start a listener
		try {
			if (connections.isEmpty()) {
                initiateConnection();
                if(!connections.isEmpty()) {
                	connectedServerCount += 1;
                /*
				 * sending authentication here
				 * connections.get(0).writeMsg();
				 */

					String serverSecret = Settings.getSecret();
                	sendAu(connections.get(0), serverSecret);
                	serverID = "server "+ connectedServerCount;
                }
			}
			listener = new Listener();
		} catch (IOException e1) {
			log.fatal("failed to startup a listening thread: "+e1);
			System.exit(-1);
		}
		start();
	}
	
	public void initiateConnection(){
		// make a connection to another server if remote hostname is supplied
		if(Settings.getRemoteHostname()!=null){
			try {
				outgoingConnection(new Socket(Settings.getRemoteHostname(),Settings.getRemotePort()));
			} catch (IOException e) {
				log.error("failed to make connection to "+Settings.getRemoteHostname()+":"+Settings.getRemotePort()+" :"+e);
				System.exit(-1);
			}
		}
	}
	
	/*
	 * Processing incoming messages from the connection.
	 * Return true if the connection should close.
	 */
	public synchronized boolean process(Connection con,String msg){

//		System.out.println(msg);
        JSONParser parser = new JSONParser();
	    try{
            JSONObject clientMsg = (JSONObject) parser.parse(msg);
            String command = (String) clientMsg.get("command");
			String username;
			String secret;
            switch (command){
//				String username = (String)clientMsg.get("username");
//				String secret = (String) clientMsg.get("secret");
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
                            }
                        }
                    }
                    else {
                        con.closeCon();
                        connectionClosed(con);
                    }
                    break;

				case "REGISTER":
                    username = (String)clientMsg.get("username");
                    secret = (String) clientMsg.get("secret");
				    isRegistering = doRegister(con,username,secret);
					log.info("register for " + username);
					break;

                case "AUTHENTICATE":
                    // do authenticate here
					serverSecret = (String)clientMsg.get("secret");
					if(doAu(con,serverSecret)){
                        connectedServerCount += 1;
					}else{
					    con.closeCon();
					}
                    break;

                case "LOCK_REQUEST":
                    username = (String)clientMsg.get("username");
                    secret = (String) clientMsg.get("secret");
                    if (connectedServerCount <= 1 && registration.containsKey(username)) {
						sendLockResult(con, username, secret, false);
						log.info("Lock denied");
					} else if (connectedServerCount <= 1 && !registration.containsKey(username)) {
                    	sendLockResult(con, username, secret, true);
                    	registration.put(username, secret);
                    	log.info("Lock allowed");
                    } else {
                    	log.info(connectedServerCount);
                        requestServer = con;
                        List<Connection> otherServers = connections.subList(0,connectedServerCount);
                        otherServers.remove(con);
                        sendLockRequest(otherServers, username, secret);
                    }
					break;

                case "LOCK_ALLOWED":
                    username = (String)clientMsg.get("username");
                    secret = (String) clientMsg.get("secret");
                    lockAllowedCount += 1;
                    if (isRegistering && lockAllowedCount == connectedServerCount) {
                        registerClient.writeMsg(registerSuccess(clientUsername, true));
                        registration.put(username,secret);
                        lockAllowedCount = 0;
                    } else if (!isRegistering && lockAllowedCount == connectedServerCount - 1){
						sendLockResult(requestServer, username, secret, true);
						registration.put(username,secret);
						lockAllowedCount = 0;
                    }
                    break;

                case "LOCK_DENIED":
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

				case "ACTIVITY_MESSAGE":
//					String activity = (String) clientMsg.get("activity");
//					System.out.println("+++++++"+msg);
					log.info("Activity message received from client.");
					broadcastToClient(connections,msg);
					JSONObject actBroadcast = new JSONObject();
					actBroadcast.put("command","ACTIVITY_BROADCAST");
					actBroadcast.put("activity",msg);
//					broadcastToServer(con,connections, actBroadcast.toString());
					if (connectedServerCount > 0) {
//						log.info(connections.subList(0, connectedServerCount));
						broadcast(connections.subList(0, connectedServerCount), actBroadcast.toJSONString());
					}
					break;

				case "ACTIVITY_BROADCAST":
					log.info("Activity broadcast message received from server." );
					JSONObject  activityBroadcast = new JSONObject();
					String activityMessage = (String)clientMsg.get("activity");
					//JSONObject actObject = (JSONObject) activityBroadcast.get("activity");
					broadcastToServer(con,connections, msg);
//					log.info("!!!!" + activityMessage);
					broadcastToClient(connections,activityMessage);
					break;

				//When a SERVER_ANNOUNCE message is received, update the serverLoads according to the message.
                case "SERVER_ANNOUNCE":
                    try{
                        JSONObject announceInfo = (JSONObject) parser.parse(msg);
                        //Iterate the serverLoads to check if the message is from a known server.
                        //If there was, update the existing one.
                        for(HashMap server: serverLoads){

                            if(server.get("serverID").equals(announceInfo.get("serverID"))){
                                server.put("load", announceInfo.get("load"));
                                break;
                            }
                        }
                        //If no known server was found, add a new HashMap to serverLoads for the new server.
                        HashMap newServer = new HashMap();
                        newServer.put("serverID", announceInfo.get("serverID"));
						newServer.put("hostname", announceInfo.get("hostname"));
						newServer.put("port", announceInfo.get("port"));
						newServer.put("load", announceInfo.get("load"));
						this.serverLoads.add(newServer);
						//Forward this message to other server.
                        List<Connection> forwardServers = connections.subList(0,connectedServerCount);
                        forwardServers.remove(con);
                        broadcast(forwardServers, msg);

                    }
                    catch (Exception e){
                        JSONObject invalidMsg = new JSONObject();
                        invalidMsg.put("command", "INVALID_MESSAGE");
                        con.writeMsg(invalidMsg.toJSONString());
                    }
                    break;


				default:

					break;
            }
        }
        catch (Exception e){
            e.printStackTrace();
        }

		return true;
	}

	/*
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
                loginResult.put("info", "Username and secret do not match.");
                log.info(username+" "+secret);
			}
        }
        else{
            loginResult.put("command", "LOGIN_FAILED");
            loginResult.put("info", "A username must be provided.");
        }
	    return loginResult;
    }
    /*
     * Check load balance and redirect users if there is a need.
     * Iterate the serverLoads to find if there is a server with a load which is at least 2 clients less than the current one.
     * If there was, send a message to the client which tries to connect, to redirect it to the server with a smaller load found.
     */
    public JSONObject redirect(){
        JSONObject redirMsg = new JSONObject();

        for(HashMap server : serverLoads){
            if(this.getConnections().size() - connectedServerCount - serverLoads.size() >= 2){

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


	/*
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
		} else if (connectedServerCount > 0) {
		       sendLockRequest(connections.subList(0, connectedServerCount), username, username);
		       log.info("test: " + connections.subList(0,connectedServerCount));
		       registerClient = con;
		       clientUsername = username;
		       return true;
        } else {
		    con.writeMsg(registerSuccess(username,true));
		    registration.put(username, secret);
		    return false;
		}
	}
	public synchronized void broadcastToClient(ArrayList<Connection> connections, String activityJSON){
		for(int i = connectedServerCount;i<connections.size();i++){
			connections.get(i).writeMsg(activityJSON);
			log.info("Activity message broadcast to client.");
		}
	}

	public synchronized void broadcastToServer(Connection incommingCon,ArrayList<Connection> connections, String activityJSON){
//		List<Connection> templist;
		templist = (ArrayList)connections.clone();
//		templist = connections;
        if(connectedServerCount>1){
//			System.out.println("connections(temp) count (before remove)"+templist.size());
//			System.out.println("connections count (before remove)"+connections.size());
			templist.remove(incommingCon);
//            System.out.println("server count"+connectedServerCount);
//            System.out.println("connections(temp) count (after remove)"+templist.size());
//			System.out.println("connections count (after remove)"+templist.size());
            for(int i = 0;i<connectedServerCount-1;i++){
				templist.get(i).writeMsg(activityJSON);
                log.info("Broadcast message to server.");
            }
		}
	}
	//check whether the username and secret matches the user logged in
	public boolean validUsernameSecret(String username, String secret){
		return (username.equals(Settings.getUsername()) && secret.equals(Settings.getSecret()));
	}


	/*
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

    /*
     * not completed yet
     */
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
		if (connectedServerCount > 1) {
			List<Connection> otherServers = connections.subList(0, connectedServerCount);
			otherServers.remove(fromCon);
			broadcast(otherServers, lockResultJSON);
		}
		return false;
	}

	/*
	 * Broadcast message to certain connections
	 */
	public boolean broadcast(List<Connection> cons, String msg) {
		for (Connection c : cons) {
			c.writeMsg(msg);


		}
		return true;
	}

	public void announce(){
        JSONObject announceInfo = new JSONObject();
        announceInfo.put("command", "SERVER_ANNOUNCE");
        announceInfo.put("id", serverID);
        announceInfo.put("load", connections.size() - connectedServerCount);
        announceInfo.put("hostname", Settings.getLocalHostname());
        announceInfo.put("port", Settings.getLocalPort());
        List<Connection> otherServers = connections.subList(0, connectedServerCount);
        broadcast(otherServers, announceInfo.toJSONString());
    }


	/*
	 * The connection has been closed by the other party.
	 */
	public synchronized void connectionClosed(Connection con){
		if(!term) connections.remove(con);
	}
	
	/*
	 * A new incoming connection has been established, and a reference is returned to it
	 */
	public synchronized Connection incomingConnection(Socket s) throws IOException{
		log.debug("incomming connection: "+Settings.socketAddress(s));
		Connection c = new Connection(s);
		connections.add(c);
		return c;

	}

	/*
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
			if(connectedServerCount >= 1){
//			    announce();
            }
			
		}
		log.info("closing "+connections.size()+" connections");
		// clean up
		for(Connection connection : connections){
			connection.closeCon();
		}
		listener.setTerm(true);
	}

	public void sendAu(Connection con, String secret) {

		JSONObject authenMsg = new JSONObject();
		authenMsg.put("command", "AUTHENTICATE");
		authenMsg.put("secret", secret);
		String authenJSON = authenMsg.toJSONString();
		try {
			con.writeMsg(authenJSON);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}


	public boolean doAu(Connection con, String s) {
		if (s.equals("fmnmpp3ai91qb3gc2bvs14g3ue")) {
			return true;
		} else {
			JSONObject authenfailMsg = new JSONObject();
			authenfailMsg.put("command", "AUTHENTICATION_FAIL");
			authenfailMsg.put("info", "the supplied secret is incorrect: " + serverSecret);
			String authenfailJSON = authenfailMsg.toJSONString();
			try {
				con.writeMsg(authenfailJSON);
			} catch (Exception e) {
				e.printStackTrace();
			}
			return false;
		}
	}
	
	public boolean doActivity(){
		return false;
	}
	
	public final void setTerm(boolean t){
		term=t;
	}
	
	public final ArrayList<Connection> getConnections() {
		return connections;
	}
}
