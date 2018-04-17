package activitystreamer.client;

import java.io.*;
import java.net.Socket;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import activitystreamer.util.Settings;

public class ClientSkeleton extends Thread {
	private static final Logger log = LogManager.getLogger();
	private static ClientSkeleton clientSolution;
	private TextFrame textFrame;
	

	
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
	
	
	
	
	
	
	@SuppressWarnings("unchecked")
	public void sendActivityObject(JSONObject activityObj){
		
	}

	public void connect(){
	    String localHostname = Settings.getLocalHostname();
	    int localPort = Settings.getLocalPort();
	    String serverHostname = "localhost";
	    int serverPort = 3780;
	    String hostName = "localhost";
	    Socket socket = null;
	    try{
			socket = new Socket("localhost", 3780);
		}
	    catch(Exception e){

		}
        System.out.println("Connection established");
	    String username = Settings.getUsername();
	    String secret = Settings.getSecret();
        JSONObject loginInfo = new JSONObject();
        loginInfo.put("username", username);
        loginInfo.put("secret", secret);
//        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        String loginText = loginInfo.toJSONString();
        try{
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
            out.write(loginText);
            out.newLine();
            out.flush();
            System.out.println("Login info sent.");
        }
        catch (IOException e){
            e.printStackTrace();
        }
    }




	public void disconnect(){
		
	}
	
	
	public void run(){

	}

	
}
