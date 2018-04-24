package activitystreamer.client;
import java.io.BufferedReader;
import java.net.SocketException;
import activitystreamer.client.TextFrame;
import com.google.gson.JsonParser;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import activitystreamer.client.ClientSkeleton;

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

//            String command = (String) clientMsg.get("command");
            JSONParser parser = new JSONParser();
            String msg = null;
            //Read messages from the server while the end of the stream is not reached
            while((msg = reader.readLine()) != null) {
                JSONObject clientMsg = (JSONObject) parser.parse(msg);
                //Print the messages to the console
                System.out.println(msg);
//                JSONObject activity = new JSONObject;
//                TextFrame tf = new TextFrame();
                TextFrame.setOutputText(clientMsg);
                if(clientMsg.get("command").equals("REGISTER_SUCCESS")){
                    this.client.login(client.getUsername(), client.getSecret());
                }


            }
        } catch (SocketException e) {
            System.out.println("Socket closed because the user typed exit");
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
