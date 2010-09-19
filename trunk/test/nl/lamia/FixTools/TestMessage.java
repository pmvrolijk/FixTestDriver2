package nl.lamia.FixTools;

import java.text.DecimalFormat;

import junit.framework.Assert;

import quickfix.InvalidMessage;
import quickfix.Message;

public class TestMessage {

    private static final DecimalFormat CHECKSUM_FORMAT = new DecimalFormat("000");
	
	/***
	 * Static helper function that creates a new quickfix message from a pipe separated string
	 * The checksum is calculated after substitutions are made.
	 * @param messageStr
	 * @return
	 */
	public static quickfix.Message fromString(String messageStr) {
		String message=messageStr.replace('|', '\001');
		
		//Calculate checksum
		int to=message.indexOf("\00110=");
        if (to > 1) {
        	message=message.substring(0, to+1);
        }
        message += "10=" + CHECKSUM_FORMAT.format(checksum(message)) + '\001';
		
        //Create and return quickfix message
        quickfix.Message msg=null;
        try {
			msg=new Message(message);
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
