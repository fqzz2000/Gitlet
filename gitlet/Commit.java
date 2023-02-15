package gitlet;



import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/** Represents a gitlet commit object.
 *  does at a high level.
 *
 *  @author Quanzhi
 */
public class Commit implements Serializable {
    /**
     *
     *
     * List all instance variables of the Commit class here with a useful
     * comment above them describing what that variable represents and how that
     * variable is used. We've provided one example for `message`.
     */

    /** The message of this Commit. */
    protected String message;
    protected String parent;
    protected String secondParent;
    protected Map<String, String> files;
    protected Date date;
    public Commit() {
    }
    public Commit(String msg, String parent, Date date) {
        this.message = msg;
        this.parent = parent;
        this.secondParent = null;
        this.date = date;
        this.files = new HashMap<>();
    }
    public Commit(String msg, String parent, String secondParent) {
        this.message = msg;
        this.parent = parent;
        this.secondParent = secondParent;
        this.date = new Date();
        this.files = new HashMap<>();
    }

    public Commit(Commit rhs) {
        this.message = rhs.message;
        this.parent = null;
        this.secondParent = null;
        this.files = new HashMap<>(rhs.files);
        this.date = new Date();
    }



    @Override
    public String toString() {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String dateString = df.format(date);
        if (parent == null) {
            return "commit: " + message + " " + dateString + " " + "0".repeat(60) + " ";
        }
        return "commit: " + message + " " + dateString + " " + this.parent + " ";
    }
}
