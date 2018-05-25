package activitystreamer.server;


import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import activitystreamer.util.Settings;


public class Connection extends Thread {
	private static final Logger log = LogManager.getLogger();
	private DataInputStream in;
	private DataOutputStream out;
	private BufferedReader inreader;
	private PrintWriter outwriter;
	private boolean open = false;
	private Socket socket;
	private boolean term=false;
    private String sender;
    private String receiver;

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receive) {
        this.receiver = receive;
    }



//	public String getRemoteServerID() {
//		return remoteServerID;
//	}
//
//	public void setRemoteServerID(String remoteServerID) {
//		this.remoteServerID = remoteServerID;
//	}

	Connection(Socket socket) throws IOException{
		in = new DataInputStream(socket.getInputStream());
	    out = new DataOutputStream(socket.getOutputStream());
	    inreader = new BufferedReader( new InputStreamReader(in));
	    outwriter = new PrintWriter(out, true);
	    this.socket = socket;
	    open = true;
	    start();
	}
	
	/*
	 * returns true if the message was written, otherwise false
	 */
	public boolean writeMsg(String msg) {
		if(open){
			outwriter.println(msg);
			outwriter.flush();
			return true;
		}
		return false;
	}


	public void closeCon(){
		if(open){
			log.info("closing connection "+Settings.socketAddress(socket));
			try {
				term=true;
				inreader.close();
				out.close();
				this.socket.close();
			} catch (IOException e) {
				// already closed?
				log.error("received exception closing the connection "+Settings.socketAddress(socket)+": "+e);
			}
		}
	}
	
	
	public void run(){
		try {
			String data;

			 /*
              * !!!!! logic for while loop has been changed here, may be wrong
              */
			while((data = inreader.readLine())!=null || !term){
				log.info("Receive message: " + data);
				term=Control.getInstance().process(this,data);
			}

			log.debug("connection closed to "+Settings.socketAddress(socket));

			String crashServer;
			if(Control.getInstance().getServerID().equals(getSender())){
                crashServer = getReceiver();
            }else{
                crashServer = getSender();
            }
			Control.getInstance().initCrashServer(crashServer);

			/*
			 * reconnect when connection closed accidentally
			 */
			reconnect();

			Control.getInstance().connectionClosed(this);
			in.close();
		} catch (IOException e) {
			log.error("connection "+Settings.socketAddress(socket)+" closed with exception: "+e);
			Control.getInstance().connectionClosed(this);
		}
		open=false;
	}
	
	public Socket getSocket() {
		return socket;
	}
	
	public boolean isOpen() {
		return open;
	}

    public synchronized void reconnect() {
        if (this == Control.getInstance().getConnections().get(0)
                && Settings.getLocalPort() != Settings.getRemotePort()) {
            log.info("tring to reconnect to the crashed server ...");
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    log.info("reconnecting ...");
                    Boolean isSuccess = Control.getInstance().initiateConnection(true);
                    if (isSuccess) {
                        Control.getInstance().sendAu(Control.getInstance().getConnections()
                                .get(Control.getInstance().getConnections().size()-1), Settings.getSecret());
                        Control.getInstance().SyncRegistration(Control.getInstance().getConnections()
                                        .get(Control.getInstance().getConnections().size()-1));
                        timer.cancel();
                    }
                }
            }, 0, 5000);
        }
    }
}
