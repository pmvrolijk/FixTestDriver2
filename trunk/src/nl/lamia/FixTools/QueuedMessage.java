/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package nl.lamia.FixTools;

import java.util.Date;
import org.apache.log4j.Logger;
import quickfix.FieldNotFound;
import quickfix.Message;
import quickfix.SessionID;

/**
 *
 * @author marcel
 */
public class QueuedMessage {

    private long stale=30000; //after 30s cleanup

    private static Logger logger = Logger.getLogger(QueuedMessage.class);

    private SessionID session;
    private String msgtype;
    private String clordid;
    private Message message;
    private Date receivedDate;

    public QueuedMessage(SessionID session, String msgtype, Message message) {
        ConfigurationManager cm=ConfigurationManager.getConfigurationManager();
        stale=Long.parseLong(cm.getProperty("QueuedMessage.staleTime","30000"));
        this.session = session;
        this.msgtype = msgtype;
        this.message = message;
        this.receivedDate = new Date();
        //Is there a client order id ?
        try {
            clordid=message.getString(11);
        } catch (quickfix.FieldNotFound e) {
            clordid=null;
        }
    }

    public Message getMessage() {
        return message;
    }

    public String getMsgtype() {
        return msgtype;
    }

    public String getClordid() {
        return clordid;
    }

    public Date getReceivedDate() {
        return receivedDate;
    }

    public Boolean isStale() {
        long age=new Date().getTime()-receivedDate.getTime();
        logger.debug("Message age="+age);
        if (age>stale) {
            logger.info("Message ("+msgtype+") is stale, can be deleted.");
            return true;
        }
        return false;
    }

    public SessionID getSession() {
        return session;
    }

}
