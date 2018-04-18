package activitystreamer.server;

import activitystreamer.util.Settings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ServerConnecter extends Thread{
    private static final Logger log = LogManager.getLogger();

    public ServerConnecter() throws IOException {
        start();
    }

    @Override
    public void run() {
        Control.getInstance().initiateConnection();
    }
}
