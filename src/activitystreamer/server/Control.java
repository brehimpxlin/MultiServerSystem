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
	private static Map registration = new HashMap();
	private static Map serverLoads = new HashMap();
	private static boolean term=false;
	private static Listener listener;
	private static ServerConnecter serverConnecter;
	private static int connectedServerCount = 0;
	private static int lockAllowedCount = 0;
	private static Connection registerClient;
	private static String clientUsername;
	private static boolean isRegistering = false;
	private static Connection requestServer;


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
                /*
				 * sending authentication here
				 * connections.get(0).writeMsg();
				 */
                }
			}
			listener = new Listener();
		} catch (IOException e1) {
			log.fatal("failed to startup a listening thread: "+e1);
			System.exit(-1);
		}
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
            //modified to deal with the server broadcast msg.
			String username = null;
			String secret = null;
			if(!command.equals("ACTIVITY_BROADCAST")) {
				username = (String) clientMsg.get("username");
				secret = (String) clientMsg.get("secret");
			}
            switch (command){
//				String username = (String)clientMsg.get("username");
//				String secret = (String) clientMsg.get("secret");
                case "LOGIN":
                    JSONObject loginMsg = login(username, secret);
                    con.writeMsg(loginMsg.toJSONString());
                    if(loginMsg.get("command").equals("LOGIN_SUCCESS")){
                        log.info("A user has logged in: "+username);
                        //Do redirect.
                        JSONObject redirMsg = redirect();
                        if(redirMsg.containsKey("hostname")){
                            con.writeMsg(redirMsg.toJSONString());
                            log.info("Redirect user "+username+" to "+redirMsg.get("hostname")+":"+redirMsg.get("port")+".");
                            con.closeCon();
                        }
                    }
                    else {
                        con.closeCon();
                    }
                    break;

				case "REGISTER":
					isRegistering = doRegister(con,username,secret);
					log.info("register for " + username);
					break;

                case "AUTHENTICATE":
                    // do authenticate here
                    connectedServerCount += 1;
                    break;

                case "LOCK_REQUEST":
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
                    lockAllowedCount += 1;
                    if (isRegistering && lockAllowedCount == connectedServerCount) {
                        registerClient.writeMsg(registerSuccess(clientUsername, true));
                        registration.put(username,secret);
                        lockAllowedCount = 0;
                    } else if (!isRegistering && lockAllowedCount == connectedServerCount - 1){
						requestServer.writeMsg(registerSuccess(clientUsername, true));
						registration.put(username,secret);
                    }
                    break;

                case "LOCK_DENIED":
                    if (isRegistering) {
						registerClient.writeMsg(registerSuccess(clientUsername, false));
						lockAllowedCount = 0;
					} else {
						if (registration.containsKey(username) && registration.containsValue(secret)) {
							registration.remove(username, secret);
						}
						broadcastLockDenied(con, username, secret);
                    	//requestServer.writeMsg(registerSuccess(clientUsername, false));
					}
				case "ACTIVITY_MESSAGE":
//					String activity = (String) clientMsg.get("activity");
//					System.out.println("+++++++"+msg);
					log.info("Received activity message from: " + Settings.getRemoteHostname()+":"+Settings.getRemotePort());
					broadcastToClient(msg);
					JSONObject actBroadcast = new JSONObject();
					actBroadcast.put("command","ACTIVITY_BROADCAST");
					actBroadcast.put("activity",msg);
					broadcastToServer(actBroadcast.toJSONString());
					break;
				case "ACTIVITY_BROADCAST":
					log.info("Received activity broadcast message from server: " + Settings.getRemoteHostname()+":"+Settings.getRemotePort());
					JSONObject  activityBroadcast= new JSONObject();
					JSONObject actObject = (JSONObject) activityBroadcast.get("activity");
					broadcastToClient(actObject.toJSONString());
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
     * The keys of HashMap serverLoads are HashMap type and contain hostname and port number of other servers.
     * The values are the load of each other servers.
     *
     */
    public JSONObject redirect(){
        JSONObject redirMsg = new JSONObject();

        //For testing, need to be deleted later.
        HashMap server1 = new HashMap();
        server1.put("hostname", "localhost");
        server1.put("port", "3781");
        serverLoads.put(server1, 0);
        //

        for(Object key : serverLoads.keySet()){
            if(this.getConnections().size() - (int)serverLoads.get(key) >= 2){
                Map redirSer = (HashMap) key;
                String redirHost = redirSer.get("hostname").toString();
                String redirPort = redirSer.get("port").toString();
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
		       log.info("tset: " + connections.subList(0,connectedServerCount));
		       registerClient = con;
		       clientUsername = username;
		       return true;
        } else {
		    con.writeMsg(registerSuccess(username,true));
		    registration.put(username, secret);
		    return false;
		}
	}
	public synchronized void broadcastToClient(String activityJSON){
		for(int i = connectedServerCount;i<connections.size();i++){
			connections.get(i).writeMsg(activityJSON);
			log.info("Broadcast message to client: " + Settings.getRemoteHostname()+":"+Settings.getRemotePort());
		}
	}public synchronized void broadcastToServer(String activityJSON){
		if(connectedServerCount>0){
			for(int j=0;j<connectedServerCount;j++){
				connections.get(j).writeMsg(activityJSON);
				log.info("Broadcast message to server: " + Settings.getRemoteHostname()+":"+Settings.getRemotePort());
			}
		}else{
			log.info("No server connected! No broadcast sent to server.");
		}
	}
	//check whether the username and secret matches the user logged in
	public boolean validUsernameSecret(String username, String secret){
		return (username.equals(Settings.getUsername()) && secret.equals(Settings.getSecret()));
	}

//	public boolean validMessage(String msg){
//		JSONParser parser = new JSONParser();
//		try {
//			JSONObject obj = (JSONObject)parser.parse(msg);
//			try
////			if ()
//		} catch (ParseException e) {
//			e.printStackTrace();
//		}
//	}

//	public boolean validActivity(Connection con, String username, String secret){
//		if(!(Settings.getUsername().equals("anonymous"))||validUsernameSecret(username,secret)){
//			JSONObject authenticateObj = new JSONObject();
//			authenticateObj.put("command","AUTHENTICATION_FAIL");
//			authenticateObj.put("info","the supplied secret is incorrect: "+secret);
//			con.writeMsg(authenticateObj.toString());
//			return false;
//		}else{
//			JSONObject authenticateObj = new JSONObject();
//			authenticateObj.put("command","AUTHENTICATION_FAIL");
//			authenticateObj.put("info","the supplied secret is incorrect: "+secret);
//			con.writeMsg(authenticateObj.toString());
//			return false;
//		}
//		if()
//
//
//	}

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
				log.debug("doing activity");
				term=doActivity();
			}
			
		}
		log.info("closing "+connections.size()+" connections");
		// clean up
		for(Connection connection : connections){
			connection.closeCon();
		}
		listener.setTerm(true);
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
