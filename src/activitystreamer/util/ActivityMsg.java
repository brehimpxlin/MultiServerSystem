package activitystreamer.util;

import java.util.Comparator;

public class ActivityMsg implements Comparable<ActivityMsg> {
    private String id;
    private String activity;
    private String command;
    private String username;
    private long timestamp;
    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getActivity() {
        return activity;
    }

    public void setActivity(String activity) {
        this.activity = activity;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String actCommand) {
        this.command = actCommand;
    }

    public String getUsername() {
        return username;
    }
    public String getId() { return id; }

    public void setId(String id) { this.id = id; }
    public void setUsername(String username) {
        this.username = username;
    }
    public ActivityMsg(String id,String activity,String command,String username,long timestamp){
        this.setId(id);
        this.setUsername(username);
        this.setActivity(activity);
        this.setCommand(command);
        this.setTimestamp(timestamp);

    }

    @Override
    public int compareTo(ActivityMsg o) {
//        return 0;
//        int result = this.getTimestamp()-o.getTimestamp();
        if(this.getTimestamp()-o.getTimestamp()>0){
            return 1;
        }else if (this.getTimestamp()-o.getTimestamp()<0){
            return -1;
        }else{
            return 0;
        }
    }

//    public static class SortByTimeStamp implements Comparator {
//        public int compare(Object o1, Object o2) {
//            ActivityMsg am1 = (ActivityMsg) o1;
//            ActivityMsg am2 = (ActivityMsg) o2;
//            if (am1.getTimestamp() > am2.getTimestamp())
//                return 1;
//            return -1;
//        }
//    }
}
