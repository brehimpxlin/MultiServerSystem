package activitystreamer.util;
import org.json.simple.JSONObject;

public class InvalidMessageProcessor {

    public static String invalidInfo(String error){
        JSONObject errorObj = new JSONObject();
        errorObj.put("command", "INVALID_MESSAGE");
        switch(error){
            case "NO_COMMAND":
                errorObj.put("info", "The received message did not contain a command.");
                break;

            case "UNKNOWN_COMMAND":
                errorObj.put("info", "Unknown command.");
                break;

            case "ALREADY_AUTHENTICATED":
                errorObj.put("info", "The received message did not contain a command.");
                break;

            case "REGISTER":
                errorObj.put("info", "Client has already logged in.");
                break;

            case "SERVER":
                errorObj.put("info", "Not authenticated server.");
                break;

            default:
                errorObj.put("info", "JSON parse error while parsing message");
                break;
        }
        return errorObj.toJSONString();
    }
}
