package nl.lamia.FixTools;

import java.util.Calendar;
import java.util.Date;
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
	
	/***
	 * Static helper function that creates a new quickfix message from a pipe separated string
	 * The checksum is recalculated after substitutions are made.
	 * @param messageStr
	 * @return
	 */
	public static quickfix.Message fromString(String message) {
		

		Matcher matcher;
        //Randomize ClientorderID
        matcher=CLORDID.matcher(message);
        if (matcher.find()) {
                String clordid=matcher.group(1);
                int random = (int) (Math.random()*999999);
                clordid+="-"+random;
                message=matcher.replaceFirst(clordid);
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

        //Load quickfix data dictionary (now only 4.4)
        DataDictionary ddFix44=null;
        try {
        	ddFix44=new DataDictionary("resources/FIX42.xml");
        } catch (ConfigError e) {
        	Assert.fail("Datadictionary failed to load. Doh.");
        }
        
        //Create and return quickfix message
        System.out.println("message:" + message);
        quickfix.Message msg=null;
        try {
			msg=new Message(message, ddFix44);
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

}
