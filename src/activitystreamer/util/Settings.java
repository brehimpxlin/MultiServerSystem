package activitystreamer.util;

import java.math.BigInteger;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Settings {
	private static final Logger log = LogManager.getLogger();
	private static SecureRandom random = new SecureRandom();
	private static int localPort = 3780;
	private static  String localHostname = "localhost";
	private static String remoteHostname = null;
	private static int remotePort = 3780;
	private static int activityInterval = 1000; // milliseconds
	private static String secret = "";
	private static String username = "anonymous";
	private static Boolean isRootServer = true;

	private static int crashedPort = 0;
	private static String crashedHostname = localHostname;

	public static int getLocalPort() {
		return localPort;
	}

	public static void setLocalPort(int localPort) {
		if(localPort<0 || localPort>65535){
			log.error("supplied port "+localPort+" is out of range, using "+getLocalPort());
		} else {
			Settings.localPort = localPort;
		}
	}
	
	public static int getRemotePort() {
		return remotePort;
	}

	public static void setRemotePort(int remotePort) {
		if(remotePort<0 || remotePort>65535){
			log.error("supplied port "+remotePort+" is out of range, using "+getRemotePort());
		} else {
			Settings.remotePort = remotePort;
		}
	}
	
	public static String getRemoteHostname() {
		return remoteHostname;
	}

	public static void setRemoteHostname(String remoteHostname) {
		Settings.remoteHostname = remoteHostname;
	}
	
	public static int getActivityInterval() {
		return activityInterval;
	}

	public static void setActivityInterval(int activityInterval) {
		Settings.activityInterval = activityInterval;
	}
	
	public static String getSecret() {
		return secret;
	}

	public static void setSecret(String s) {
		secret = s;
	}
	
	public static String getUsername() {
		return username;
	}

	public static void setUsername(String username) {
		Settings.username = username;
	}
	
	public static String getLocalHostname() {
		return localHostname;
	}

	public static void setLocalHostname(String localHostname) {
		Settings.localHostname = localHostname;
	}

	public static String getCrashedHostname() {
	    return crashedHostname;
    }

    public static void setCrashedHostname(String crashedHostname) {
        Settings.crashedHostname = crashedHostname;
    }

    public static int getCrashedPort() {
	    return crashedPort;
    }

    public static void setCrashedPort(int crashedPort) {
	    Settings.crashedPort = crashedPort;
    }
	/*
	 * some general helper functions
	 */

	public static Boolean getIsRootServer() {
	    return isRootServer;
    }

	public static void setIsRootServer(Boolean isRootServer) {
	    Settings.isRootServer = isRootServer;
    }
	
	public static String socketAddress(Socket socket){
		return socket.getInetAddress()+":"+socket.getPort();
	}


	public static String nextSecret() {
	    return new BigInteger(130, random).toString(32);
	 }



	
}
