package nl.lamia.FixTools;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import junit.framework.Assert;

import quickfix.SessionID;

public class ExpectMessageStep implements TestStep {

	private static Logger logger = Logger.getLogger(ExpectMessageStep.class);
	private static final Pattern MSGTYPEPATTERN = Pattern.compile("35=([^\\|]+)\\|");
	private static final Pattern CLORDIDPATTERN = Pattern.compile("11=([^\\|]+)\\|");

	private int timeout=10000;
	
	private FixEngine engine;
        private QueueManager qm;
        private ConfigurationManager cm;
	private SessionID session;
	private quickfix.Message answer;
	private String expect;
	private Boolean received;
        private Boolean wantMsgType,wantClordid; //what are we waiting for.
                      /*This should be expect step specific for possible later enhancements,
                       * like having each step define its own behaviour.
                       */
        private String msgtype;  //for matching incoming, <unknown> if unspecified
        private String clordid;  //for matching incoming, <unknown> if not present

	@Override
	public Boolean run() throws Exception {
		//engine.expectMessage(this);
                qm.expectMessage(this);  //Let's listen to the QueueManager better
		logger.info("Waiting for answer...");
		Boolean timeout=false;
		synchronized (this) {
			while (!received && !timeout) {
				try {
					this.notifyAll();
					wait(this.timeout);
					logger.info("Woke up!");
					if (!received) timeout=true;
					//System.out.println("got something ??"+received);
				} catch (Exception e) {
					Assert.fail("Waiting interrupted before "+this.timeout/1000+" sec.");
				}
			}
		}
		Assert.assertTrue("Expect: No message returned in "+this.timeout/1000+" sec.", received);    //No answer is fail
		//Compare the message to the expected:
		Assert.assertTrue("Answer doesn't match expected message.",TestMessage.compare(answer, expect));
                Boolean checkDateTags=false;
                if (cm.getProperty("TestMessage.checkDateTags","false").equalsIgnoreCase("true")) checkDateTags=true;
                if (checkDateTags)
              		Assert.assertTrue("Message contains invalid dateformats.",TestMessage.dateValidator(answer));
		return true;
	}
	
	synchronized public void receive(quickfix.Message answer) {
		logger.info("We were Called ! "+answer);
		this.answer=answer;
		this.received=true;
	}

	public ExpectMessageStep(String expectStr, FixEngine engine, String sessionStr) {
		this.engine=engine;
		this.session=new SessionID(sessionStr);
		this.received=false;
		this.expect = expectStr.substring(1, expectStr.length()); //cut off the E
                this.cm=ConfigurationManager.getConfigurationManager();
                this.qm=QueueManager.get();
                String toVal=cm.getProperty("ExpectMessageStep.timeout", "10000");
                this.timeout=Integer.parseInt(toVal);
                if (timeout==0) timeout=10000;
                logger.info("Setting timeout on messages to "+timeout+" ms.");
                Matcher m=MSGTYPEPATTERN.matcher(expectStr);
                if (m.find()) {
                    msgtype=m.group(1);
                } else {
                    msgtype="<unknown>";
                }
                m=CLORDIDPATTERN.matcher(expectStr);
                if (m.find()) {
                    clordid=m.group(1);
                } else {
                    clordid="<unknown>";
                }
                //Do we want to match msgtype and/or clordid ?
                wantMsgType=(cm.getProperty("TestMessage.matchMsgType","true").equalsIgnoreCase("true"))? true:false;
                wantClordid=(cm.getProperty("TestMessage.matchClordid","true").equalsIgnoreCase("true"))? true:false;
                if (wantMsgType) logger.info("Waiting for msgtype:"+msgtype);
                if (wantClordid) logger.info("Waiting for clordid:"+clordid);
	}

        /***
         * Function can be called on Expect step to check if the message is desired.
         * Each expect step can filter for msgtype, clientorder, both or not at all.
         * @param type      Message type (tag 35)
         * @param orderid   Client orderid (tag 11)
         * @return
         */
        public Boolean wants(String type, String orderid) {
            if (wantMsgType) logger.debug("Do we want 35="+type+" ?");
            if (wantClordid) logger.debug("Do we want 11="+orderid+" ?");
            if (type!=null && wantMsgType && !this.msgtype.equals(type)) return false;
            if (orderid!=null && wantClordid && !TestMessage.fieldCompare(this.clordid, orderid)) return false;
            logger.debug("yes we do !");
            return true;
        }

        public SessionID getSession() {
            return session;
        }

        public String getMsgType() {
            return msgtype;
        }
	
        public String getClordid() {
            return clordid;
        }

}
