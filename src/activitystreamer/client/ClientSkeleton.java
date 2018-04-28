package activitystreamer.client;
import java.io.*;
import java.net.Socket;
import java.util.Random;

import activitystreamer.server.Connection;
import activitystreamer.server.Control;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import activitystreamer.util.Settings;
import activitystreamer.client.MessageListener;

public class ClientSkeleton extends Thread {
	private static final Logger log = LogManager.getLogger();
	private static ClientSkeleton clientSolution;
	private TextFrame textFrame;
    private DataInputStream in;
    private DataOutputStream out;
    private BufferedReader inreader;
    private PrintWriter outwriter;
    private boolean open = false;
    private Socket socket;
    private String username = Settings.getUsername();
    private String secret = Settings.getSecret();

	public static ClientSkeleton getInstance(){
		if(clientSolution==null){
			clientSolution = new ClientSkeleton();
		}
		return clientSolution;
	}
	
	public ClientSkeleton(){

		textFrame = new TextFrame();
		start();
	}

    public boolean writeMsg(String msg)throws IOException {
        if(open){

            outwriter.println(msg);
            outwriter.flush();
            return true;
        }
        return false;
    }





	public boolean connect(){

	    try{
			Socket socket = new Socket(Settings.getRemoteHostname(), Settings.getRemotePort());
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            inreader = new BufferedReader( new InputStreamReader(in));
            outwriter = new PrintWriter(out, true);
            this.socket = socket;
            open = true;
			if(socket.isConnected()){
                log.info("Connection with "+Settings.getRemoteHostname()+":"+Settings.getRemotePort()+" successfully established.");
                MessageListener ml = new MessageListener(inreader, clientSolution);
                ml.start();
            }
            else{
                log.error("Fail to connect to "+Settings.getRemoteHostname()+":"+Settings.getRemotePort()+".");

            }
		}
	    catch(Exception e){
            log.error("Fail to connect to "+Settings.getRemoteHostname()+":"+Settings.getRemotePort()+".");
		}
        return socket.isConnected();



    }

    public void login(String username, String secret){

        JSONObject loginInfo = new JSONObject();
        loginInfo.put("command", "LOGIN");
        loginInfo.put("username", username);
        loginInfo.put("secret", secret);
        String loginText = loginInfo.toJSONString();
        try{
            writeMsg(loginText);
            log.info("Logging in.");
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    public void register(String username, String secret){
        JSONObject registerInfo = new JSONObject();
        registerInfo.put("command", "REGISTER");
        registerInfo.put("username", username);
        registerInfo.put("secret", secret);
        String registerJSON = registerInfo.toJSONString();
        try{
            writeMsg(registerJSON);
            log.info("register for: " + username);
        }catch (IOException e){
            e.printStackTrace();
        }
    }
    
    public void sendActivityObject(JSONObject activityObj){
        JSONObject activity = new JSONObject();
        activity.put("command", "ACTIVITY_MESSAGE");
        activity.put("username", Settings.getUsername());
//        activity.put("username","test");
        activity.put("activity",activityObj.toString());
        String activityJSON = activity.toJSONString();
        try{
//            System.out.println("----------------"+activityJSON);
            writeMsg(activityJSON);
            log.info("message sent from: " + Settings.getUsername());
        }catch (IOException e){
            e.printStackTrace();
        }
    }


	public void disconnect(){
//        Connection.
        log.debug("connection closed to "+Settings.socketAddress(socket));
        JSONObject disconnectOBJ = new JSONObject();
        disconnectOBJ.put("command", "LOGOUT");
        try {
            writeMsg(disconnectOBJ.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }



	}
	public boolean isConnected(){
	    return this.socket.isConnected();
    }
    public String getUsername(){
	    return username;
    }
    public String getSecret(){
        return secret;
    }

	public void run(){

        if(connect()){


            if(username.equals("anonymous") || !secret.equals("")){
                login(username, secret);
            }
            else{
                this.secret =  Settings.nextSecret();
                Settings.setSecret(this.secret);
                System.out.println("Try to login using username: "+username+" and secret: "+secret);
                register(username, secret);

            }

        }





	}

	
}
