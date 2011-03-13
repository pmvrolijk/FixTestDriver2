/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package nl.lamia.FixTools;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Set;
import java.util.TimerTask;
import java.util.Timer;
import java.util.Vector;
import org.apache.log4j.Logger;
import quickfix.FieldNotFound;
import quickfix.Message;
import quickfix.SessionID;

/**
 *
 * @author marcel
 */
public class QueueManager extends Thread {

    static QueueManager qm=null;

    private static Logger logger = Logger.getLogger(QueueManager.class);

    //The incoming Queue and message queue
    private Vector<QueuedMessage> msgQueue;
    private Vector<QueuedMessage> incomingQueue;

    //Subscribers
    private Set<ExpectMessageStep> expectations;

    //Order manager is also interested party
    private OrderManager om;

    QueueManager() {
        om=OrderManager.get();
        expectations=Collections.synchronizedSet(new HashSet<ExpectMessageStep>());
        msgQueue=new Vector<QueuedMessage>();
        incomingQueue=new Vector<QueuedMessage>();
    }

    static QueueManager get() {
        if (qm==null) {
            qm=new QueueManager();
            qm.start();
        }
        return qm;
    }

    public void run() {
        //We start a callback timer to deliver every 300ms
        //Timer for queue
        Timer t=new Timer();
        t.schedule(new TimerTask() {
            public void run() {
                deliver();
            }
        }, 0, 100);

        //Timer t=new Timer(300, new ActionListener() {
        //    public void actionPerformed(ActionEvent e) {
        //        deliver();
        //    }
        //});
        //t.start();
    }

    public void addMessage(Message message, SessionID sessionId) {
        String msgType;
        try {
            msgType=message.getHeader().getString(35);
        } catch (FieldNotFound ex) {
            logger.error("Message doesn't have a msgType... Skip");
            return;
        }
        logger.info("Queuing: type:"+msgType+" "+message.toString().replace('\001', '|'));
        //om.addMessage(message);
        //We quickly write it into the incoming queue
        //and later process everything. Return to the waiting FixEngine Thread
        //synchronized (incomingQueue) {
            try {
                incomingQueue.add(new QueuedMessage(sessionId, msgType, message));
        //}
            } catch (ConcurrentModificationException ex) {
                logger.error("Concurrent modification.");
            }
    }

    /*
     * Allows interested parties to subscribe
     */
    public Boolean expectMessage(ExpectMessageStep caller) {
	expectations.add(caller);
	return true;
    }

    /*
     * Clean the queue completely
     */
    public void cleanQueue() {
        logger.info("Clearing message queue");
        msgQueue.clear();
    }

    private void deliver() {
        //Process new arrivals
        QueuedMessage msg;
        try {
            //synchronized (incomingQueue) {
                for (int i=0;i<incomingQueue.size();i++) {
                    msg=incomingQueue.get(i);
                    om.addMessage(msg.getMessage());
                    msgQueue.add(msg);
                    incomingQueue.remove(i);
                    logger.info("Added new incoming msg to queue.");
             //   }
            }
        } catch (ConcurrentModificationException ex) {
            logger.warn("Concurrent modification.");
        }
        //Process the queue
        logger.debug("Number of waiting msgs: "+msgQueue.size());
        String msgtype,clordid;
        try {
            for (int i=0;i<msgQueue.size();i++) {
                msg=msgQueue.get(i);
                msgtype=msg.getMsgtype();
                clordid=msg.getClordid();
                for (ExpectMessageStep caller: expectations) {
                    logger.debug("Queued msg: "+msg.getMsgtype()+" for "+msg.getSession().toString());
                    logger.debug("Caller msg: "+caller.getMsgType()+" for "+caller.getSession().toString());
                    if (caller.wants(msgtype,clordid) &&
                        caller.getSession().equals(msg.getSession()) ) {
                            //try to send
                            synchronized (caller) {
                                caller.receive(msg.getMessage());     //wake up sleepy head
                                caller.notifyAll();
                            }
                            expectations.remove(caller); //only one message per expect
                            msgQueue.remove(i);
                    }
                }
                if (msg.isStale()) msgQueue.remove(i);
            }
        } catch (ConcurrentModificationException ex) {
             logger.error("Concurrent modification occured. Fix this.");
        }
    }

}
