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
    private DataInputStream in;
    private DataOutputStream out;
    private BufferedReader inreader;
    private PrintWriter outwriter;
    private boolean open = false;
    private Socket socket;

	
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


	@SuppressWarnings("unchecked")
	public void sendActivityObject(JSONObject activityObj){
		
	}

	public boolean connect(){
	    String localHostname = Settings.getLocalHostname();
	    int localPort = Settings.getLocalPort();
	    String serverHostname = "localhost";
	    int serverPort = 3781;
	    String hostName = "localhost";
	    Socket socket = null;

	    try{
			socket = new Socket(serverHostname, serverPort);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            inreader = new BufferedReader( new InputStreamReader(in));
            outwriter = new PrintWriter(out, true);
            this.socket = socket;
            open = true;
			if(socket.isConnected()){
                System.out.println("Connection with "+serverHostname+":"+serverPort+" successfully established.");
            }
            else{
                System.out.println("Fail to connect to "+serverHostname+":"+serverPort+".");
            }
		}
	    catch(Exception e){
            System.out.println("Fail to connect to "+serverHostname+":"+serverPort+".");
		}
        return socket.isConnected();



    }

    public void login(String username, String secret){

        JSONObject loginInfo = new JSONObject();
        loginInfo.put("command", "LOGIN");
        loginInfo.put("username", username);
        loginInfo.put("secret", secret);
        String loginText = loginInfo.toJSONString()+"\n";
        try{
            writeMsg(loginText);
            System.out.println("Login info sent.");
        }
        catch (IOException e){
            e.printStackTrace();
        }
    }



	public void disconnect(){
		
	}
	
	
	public void run(){
        connect();
        String command = "LOGIN";
//        if(command.equals("LOGIN")){
//            login(Settings.getUsername(), Settings.getSecret());
//        }

	}

	
}
