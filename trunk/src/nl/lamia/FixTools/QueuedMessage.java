/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package nl.lamia.FixTools;

import java.util.Date;
import org.apache.log4j.Logger;
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
    private Message message;
    private Date receivedDate;

    public QueuedMessage(SessionID session, String msgtype, Message message) {
        ConfigurationManager cm=ConfigurationManager.getConfigurationManager();
        stale=Long.parseLong(cm.getProperty("QueuedMessage.staleTime","30000"));
        this.session = session;
        this.msgtype = msgtype;
        this.message = message;
        this.receivedDate = new Date();
    }

    public Message getMessage() {
        return message;
    }

    public String getMsgtype() {
        return msgtype;
    }

    public Date getReceivedDate() {
        return receivedDate;
    }

    public Boolean isStale() {
        long age=new Date().getTime()-receivedDate.getTime();
        logger.info("Message age="+age);
        if (age>stale) return true;
        return false;
    }

    public SessionID getSession() {
        return session;
    }

}
