package activitystreamer.client;
import java.io.BufferedReader;
import java.net.SocketException;
import activitystreamer.util.InvalidMessageProcessor;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import activitystreamer.util.Settings;

public class MessageListener extends Thread {
    private BufferedReader reader;
    private ClientSkeleton client;
    public MessageListener(BufferedReader reader, ClientSkeleton client ) {
        this.client = client;
        this.reader = reader;
    }


    @Override
    public void run() {

        try {


            JSONParser parser = new JSONParser();
            String msg = null;

            //Read messages from the server while the end of the stream is not reached
            while((msg = reader.readLine()) != null) {
                System.out.println(msg);
                JSONObject clientMsg = (JSONObject) parser.parse(msg);
                //Print the messages to the console

                TextFrame.setOutputText(clientMsg);


                if(!clientMsg.containsKey("command")){
                    this.client.writeMsg(InvalidMessageProcessor.invalidInfo("NO_COMMAND"));
                }

                if(clientMsg.get("command").equals("REGISTER_SUCCESS")){
                    this.client.login(this.client.getUsername(), this.client.getSecret());
                }
                if(clientMsg.get("command").equals("REDIRECT")){
                    Settings.setRemoteHostname((String)clientMsg.get("hostname"));
                    Settings.setRemotePort(new Integer((String) clientMsg.get("port")));
                    this.client.connect();
                    this.client.login(this.client.getUsername(), this.client.getSecret());
                }
                if(clientMsg.get("command").equals("AUTHENTICATION_FAIL")){
                    this.client.disconnect();
                    TextFrame.setSendButtonStatus(false);
                }
            }
        } catch (SocketException e) {
            System.out.println("Socket closed because the user typed exit");
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
