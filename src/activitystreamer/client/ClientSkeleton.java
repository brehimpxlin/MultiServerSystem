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



    public void readMsg(String msg){
        System.out.println(msg);
    }


	public void sendActivityObject(JSONObject activityObj){
		
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
        String loginText = loginInfo.toJSONString()+"\n";
        try{
            writeMsg(loginText);
            log.info("Logging in.");
        }
        catch (IOException e){
            e.printStackTrace();
        }
    }



	public void disconnect(){
		
	}
	public boolean isConnected(){
	    return this.socket.isConnected();
    }


	public void run(){

        connect();
        String command = "LOGIN";

        if(connect()){
            //login(Settings.getUsername(), Settings.getSecret());
        }



        try {
            String data;
            while(socket.isConnected() && (data = inreader.readLine())!=null){
                readMsg(data);
            }

        } catch (IOException e) {
            log.error("connection "+Settings.socketAddress(socket)+" closed with exception: "+e);

        }

	}

	
}
