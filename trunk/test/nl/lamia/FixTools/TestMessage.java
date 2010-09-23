package nl.lamia.FixTools;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.regex.*;

import junit.framework.Assert;

import quickfix.ConfigError;
import quickfix.DataDictionary;
import quickfix.InvalidMessage;
import quickfix.Message;

public class TestMessage {

    private static final DecimalFormat CHECKSUM_FORMAT = new DecimalFormat("000");
    private static final DateFormat DATE_FORMAT= new SimpleDateFormat("yyyyMMdd-HH:mm:ss");
    private static final Pattern DATE = Pattern.compile("<Date(\\+?)([0-9]*)([hd]?)>");
    private static final Pattern CLORDID = Pattern.compile("<Clordid=([^|]*)>");
    private static final Pattern ORIGCLORDID = Pattern.compile("<OrigClordid=([^|]*)>");
    private static final Pattern FIELDPATTERN = Pattern.compile("(\\d+)=([^\\|]+)\\|");
    private static HashSet<String> TIMEFIELDS=null;
    static {
    	TIMEFIELDS=new HashSet<String>();
    	TIMEFIELDS.add("52");
    	TIMEFIELDS.add("60");
    	TIMEFIELDS.add("122");
    	TIMEFIELDS.add("126");
    }
	
    static private HashMap<String,DataDictionary> dataDictionary=null;
    static {
        try {
        	dataDictionary=new HashMap<String,DataDictionary>();
        	dataDictionary.put("FIX.4.0", new DataDictionary("resources/FIX40.xml"));
        	dataDictionary.put("FIX.4.1", new DataDictionary("resources/FIX41.xml"));
        	dataDictionary.put("FIX.4.2", new DataDictionary("resources/FIX42.xml"));
        	dataDictionary.put("FIX.4.3", new DataDictionary("resources/FIX43.xml"));
        	dataDictionary.put("FIX.4.4", new DataDictionary("resources/FIX44.xml"));
        	dataDictionary.put("FIX.5.0", new DataDictionary("resources/FIX50.xml"));
        } catch (ConfigError e) {
        	Assert.fail("Datadictionary failed to load. Doh.");
        }
    }
    
	/***
	 * Static helper function that creates a new quickfix message from a pipe separated string
	 * The checksum is recalculated after substitutions are made.
	 * @param messageStr
	 * @return
	 */
	public static quickfix.Message fromString(String message) {
		
		OrderManager om=OrderManager.get();

		Matcher matcher;
        //Randomize ClientorderID
        matcher=CLORDID.matcher(message);
        if (matcher.find()) {
                String clordid=matcher.group(1);
                int random = (int) (Math.random()*999999);
                String newClordid=clordid+"-"+random;
                message=matcher.replaceFirst(newClordid);
                om.addOrderId(clordid, newClordid);
        }		

        //Retrieve actually used client orderid from order manager
        matcher=ORIGCLORDID.matcher(message);
        if (matcher.find()) {
                String origClordid=matcher.group(1);
                String actOrigClordid=om.getOrigOrderId(origClordid);
                if (actOrigClordid!=null) {
                    message=matcher.replaceFirst(actOrigClordid);
                } else {
                	message=matcher.replaceFirst(origClordid);
                }
        }		

        //Convert Dates
        matcher=DATE.matcher(message);
        String date;
        StringBuffer stringBuffer=new StringBuffer("");
        while (matcher.find()) {
    			if (matcher.group(1).equals("+")) {
					int nr=Integer.parseInt(matcher.group(2));
					String tm=matcher.group(3);
					Date now=new Date();
					Calendar cal = Calendar.getInstance();
					cal.setTime(now);
					if (tm.equals("d")) {
						cal.add(Calendar.DATE, nr);
					} else { 
						cal.add(Calendar.HOUR, nr);
    				}
					Date newDate = cal.getTime();
    				date=DATE_FORMAT.format(newDate);
    				matcher.appendReplacement(stringBuffer, date);
    			} else {
    				//Simpel date replace
    				date=DATE_FORMAT.format(new Date());
    				matcher.appendReplacement(stringBuffer, date);
    			}
        }		
        matcher.appendTail(stringBuffer);
        message=new String(stringBuffer.toString());
        

        //Quickfix SOH separators
        message=message.replace('|', '\001');
        
		//Calculate checksum
		int to=message.indexOf("\00110=");
        if (to > 1) {
        	message=message.substring(0, to+1);
        }
        message += "10=" + CHECKSUM_FORMAT.format(checksum(message)) + '\001';

        //Create and return quickfix message
        //System.out.println("message:" + message);
        quickfix.Message msg=null;
        try {
        	System.out.println();
        	DataDictionary dd=dataDictionary.get(message.substring(2, 9));
        	Assert.assertNotNull("Data dictionary undefined or unknown FIX: "+message.substring(2, 9),dd);
			msg=new Message(message, dd);
		} catch (InvalidMessage e) {
			Assert.fail("Invalid message format:"+message);
		}
		return msg;
	}
	
	/***
	 * Returns the 3 digit checksum of a fix message (\001 separated)
	 * @param message
	 * @return
	 */
    public static int checksum(String message) {
        int sum = 0;
        int fieldSum = 0;
        for (int i = 0; i < message.length(); i++) {
            sum += message.charAt(i);
            fieldSum += message.charAt(i);
            if (message.charAt(i) == '\001') {
                fieldSum = 0;
            }
        }
        return sum % 256;
    }
    
    public static Boolean compare(quickfix.Message QFmessage, String expected) {
    	//First we turn the actual message to string and then a map
    	String message=QFmessage.toString();
    	message=message.replaceAll("\001", "|");
    	HashMap<String,String> actFields=parse(message);
    	
    	//Then we take the expected to a map
    	HashMap<String,String> expFields=parse(expected);
    	
    	//Quick msgtype check
    	Assert.assertEquals("Wrong MsgType received: ",
    			expFields.get("35"), actFields.get("35"));

    	//scan the actual fields and compare to expected
    	//TODO: what to do about repeating groups ? This simple
    	//comparator won't know which goes with which
        Iterator<Map.Entry<String, String>> fieldIterator = actFields.entrySet().iterator();
        while (fieldIterator.hasNext()) {
            Map.Entry<String, String> entry = fieldIterator.next();
            Object key = entry.getKey();
            //System.out.println("key:"+key+",Value:"+entry.getValue());
            if (key.equals("10") || key.equals("9") ||	   //Checksum and length
            	key.equals("45") || key.equals("34") ||    //Sequence numbers
            	key.equals("17") || key.equals("11") ||    //ExecutionID && Clordid, solved later
            	key.equals("37") || key.equals("11")       //Orderid
            	) {   //skip fields
                continue;
            } else if (!expFields.containsKey(key)) {     //Didn't expect that ! 
                Assert.fail("Unexpected field " + key + ",value=" + entry.getValue());
            } else if (TIMEFIELDS.contains(key)) {        //TODO: Check on length only now, format when needed
                Assert.assertEquals("Timefield " + key + " unexpected length.",
                				entry.getValue().length(),expFields.get(key).length());
            } else {
                Assert.assertEquals("field " + key + " not equal: ", expFields.get(key), entry
                        .getValue());
            }
        }
    	System.out.println("Answer matches expectation.");
    	return true;
    }

    public static HashMap<String,String> parse(String message) {
        HashMap<String, String> fields = new HashMap<String, String>();
        Matcher fieldMatcher = FIELDPATTERN.matcher(message);
        while (fieldMatcher.find()) {
            fields.put(fieldMatcher.group(1), fieldMatcher.group(2));
        }
        return fields;
    }
    
}
