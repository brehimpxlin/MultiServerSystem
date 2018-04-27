package activitystreamer.util;

import activitystreamer.server.Connection;
import com.oracle.javafx.jmx.json.JSONException;
import com.sun.tools.internal.xjc.reader.gbind.ConnectedComponent;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;


public class FailureController {

    public static void sendInvalidInfoObj(Connection con, String errStr){
        JSONParser parser = new JSONParser();
        JSONObject errorObj = new JSONObject();

        if(errStr.equals("NO_COMMAND")) {
            errorObj.put("command", "INVALID_MESSAGE");
            errorObj.put("info", "the received message did not contain a command");

        }else{
            errorObj.put("command", "INVALID_MESSAGE");
            errorObj.put("info", "JSON parse error while parsing message");
        }
        try {
            con.writeMsg(errorObj.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
