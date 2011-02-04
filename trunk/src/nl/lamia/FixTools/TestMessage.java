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
import java.util.Arrays;
import java.util.List;
import java.util.regex.*;

import org.apache.log4j.Logger;

import junit.framework.Assert;

import quickfix.ConfigError;
import quickfix.DataDictionary;
import quickfix.Field;
import quickfix.FieldMap;
import quickfix.FieldNotFound;
import quickfix.FieldType;
import quickfix.Group;
import quickfix.InvalidMessage;
import quickfix.Message;
import quickfix.field.MsgType;

public class TestMessage {

	private static Logger logger = Logger.getLogger(TestMessage.class);

        //TODO: Make all this threadsafer !
        private static String output;
	
    private static final DecimalFormat CHECKSUM_FORMAT = new DecimalFormat("000");
    private static final DateFormat DATE_FORMAT= new SimpleDateFormat("yyyyMMdd-HH:mm:ss");
    private static final Pattern DATE = Pattern.compile("<Date(\\+?)([0-9]*)([hd]?)[,]?([^>|]*)>");
    private static final Pattern PRODUCT = Pattern.compile("<Product=([^,|]*),([^|]*)>");
    private static final Pattern CLORDID = Pattern.compile("<Clordid=([^|]*)>");
    private static final Pattern ORIGCLORDID = Pattern.compile("<OrigClordid=([^|]*)>");
    private static final Pattern FIELDPATTERN = Pattern.compile("(\\d+)=([^\\|]+)\\|");
    private static final Pattern REGEXEXPECT = Pattern.compile("<([^>]*)>");
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
        String date="";
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
					if (!matcher.group(4).equals("")) {
						try {
							date=new SimpleDateFormat(matcher.group(4)).format(newDate);
						} catch (IllegalArgumentException ex ) { 
								Assert.fail("Illegal date format: "+matcher.group(4)); 
						}
					} else {
						date=DATE_FORMAT.format(newDate);
					}
    				matcher.appendReplacement(stringBuffer, date);
    			} else {
    				//Simpel date replace
    				date=DATE_FORMAT.format(new Date());
    				matcher.appendReplacement(stringBuffer, date);
    			}
        }		
        matcher.appendTail(stringBuffer);
        message=new String(stringBuffer.toString());
        
        //Replace Product IDs
        matcher=PRODUCT.matcher(message);
        stringBuffer=new StringBuffer("");
        String product,property,value;
        Dictionary dict=Dictionary.getDict();
        while (matcher.find()) {
        		product=matcher.group(1);
        		property=matcher.group(2);
    			if (product.length()>0 && property.length()>0) {
    				//System.out.println("Getting "+product+" 's "+property);
    				value=dict.getProductProp(product, property);
    				if (value!=null) matcher.appendReplacement(stringBuffer, value);
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
        //logger.info("message:" + message);
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

    public static Boolean dateValidator(quickfix.Message QFmessage) {
        //First we turn the actual message to string and then a map
    	String message=QFmessage.toString();
    	message=message.replaceAll("\001", "|");
        DataDictionary dd=dataDictionary.get(message.substring(2, 9));
        Assert.assertNotNull("Data dictionary undefined or unknown FIX: "+message.substring(2, 9),dd);
        HashMap<String,String> actFields=parse(message);
        Iterator<Map.Entry<String, String>> fieldIterator = actFields.entrySet().iterator();
        String key,value;
        while (fieldIterator.hasNext()) {
            Map.Entry<String, String> entry = fieldIterator.next();
            key = entry.getKey();
            value = entry.getValue();
            FieldType ft=dd.getFieldTypeEnum(Integer.parseInt(key));
            if (ft!=null && ft.getName().equals("UTCTIMESTAMP")) {
                logger.info("Checking tag "+key+"="+value+" for valid UTC timestamp.");
                Assert.assertTrue("Invalid UTC Timestamp for tag "+key+"("+value+")",
                        value.matches("[0-9]{8}-[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]*"));
            }
        }
        return true;
    }

    public static Boolean compare(quickfix.Message QFmessage, String expected) {
        //Get the handle on the configuration manager
        //TestMessage.ignoreUnexpectedTags=true
        //TestMessage.checkDateTags=true
        //TestMessage.skipTags=10,9,45,34,17,37
        ConfigurationManager cm=ConfigurationManager.getConfigurationManager();
        Boolean ignoreUnexpectedTags=false;
        Boolean checkDateTags=false;
        List skipTags;
        if (cm.getProperty("TestMessage.ignoreUnexpectedTags","false").equalsIgnoreCase("true"))
                ignoreUnexpectedTags=true;
        if (cm.getProperty("TestMessage.checkDateTags","false").equalsIgnoreCase("true"))
                checkDateTags=true;
        String tags=cm.getProperty("TestMessage.skipTags","");
        String tag[]=tags.split(",");
        skipTags=Arrays.asList(tag);

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
            String key = entry.getKey();
            //System.out.println("key:"+key+",Value:"+entry.getValue());
            if (skipTags.contains(key)) {   //skip fields
                continue;
            } else if (!expFields.containsKey(key) && !ignoreUnexpectedTags ) {     //Didn't expect that !
                Assert.fail("Unexpected field " + key + ",value=" + entry.getValue());
            } else if (TIMEFIELDS.contains(key) && expFields.containsKey(key)) {
                //TODO: Check on length only now, format when needed
                Assert.assertEquals("Timefield " + key + " unexpected length.",
                				entry.getValue().length(),expFields.get(key).length());
            } else if (expFields.containsKey(key) && REGEXEXPECT.matcher(expFields.get(key)).find()) {
            	//Regular expression
            	logger.debug("We are doing a regex matching: "+expFields.get(key)+" vs "+entry.getValue());
            	Matcher m=REGEXEXPECT.matcher(expFields.get(key));
            	m.find();
            	String expPattern=m.group(1);
            	Assert.assertTrue("Tag "+key+" value "+entry.getValue()+" doesn't match regex: "+expPattern,
            			entry.getValue().matches(expPattern));
            } else if (expFields.containsKey(key)) {
                Assert.assertEquals("field " + key + " not equal: ", expFields.get(key), entry.getValue());
            }
        }
    	logger.info("Answer matches expectation.");
    	return true;
    }

    /***
     * Helper function to do a compare of two fields, 
     *  implements  - a straight compare
     *              - a regex compare
     * @param soll     left, expected value (can contain regex contained in <>)
     * @param ist      right, actual value (literal string only)
     * @return          true if equal
     */
    public static Boolean fieldCompare(String soll, String ist) {
            if (ist==null && soll==null) return true;  //null=null
            if (ist==null || soll==null) return false; //null!=!null
            if (REGEXEXPECT.matcher(soll).find()) {
            	//Regular expression
            	logger.debug("We are doing a regex matching: "+soll+" vs "+ist);
            	Matcher m=REGEXEXPECT.matcher(soll);
            	m.find();
            	String expPattern=m.group(1);
                //TODO use assert on field compare
            	//Assert.assertTrue("Tag "+key+" value "+entry.getValue()+" doesn't match regex: "+expPattern,
            	//		entry.getValue().matches(expPattern));
                if (ist.matches(expPattern)) return true;
            } else {
                //Literal compare
                logger.debug("We are doing a literal match: "+soll+" vs "+ist);
                //Assert.assertEquals("field " + key + " not equal: ", expFields.get(key), entry.getValue());
                if (ist.equals(soll)) return true;
            }
        return false;
    }

    public static HashMap<String,String> parse(String message) {
        HashMap<String, String> fields = new HashMap<String, String>();
        Matcher fieldMatcher = FIELDPATTERN.matcher(message);
        while (fieldMatcher.find()) {
            fields.put(fieldMatcher.group(1), fieldMatcher.group(2));
        }
        return fields;
    }

    /***
     * Fix message pretty print function based on http://www.quickfixj.org/confluence/display/qfj/Using+Message+Metadata
     * @param dd
     * @param message
     * @throws FieldNotFound
     */
    public static String print(Message message) throws FieldNotFound {
        output="";   //TODO not nice threadsafe, etc.
        String msgType = message.getHeader().getString(MsgType.FIELD);
        String protocol=message.getHeader().getString(8);
        DataDictionary dd=dataDictionary.get(protocol);
        if (dd==null) {logger.error("Unknown protocol: "+protocol); return null;}
        printFieldMap("", dd, msgType, message.getHeader());
        printFieldMap("", dd, msgType, message);
        printFieldMap("", dd, msgType, message.getTrailer());
        return output;
    }

    private static void printFieldMap(String prefix, DataDictionary dd, String msgType, FieldMap fieldMap)
            throws FieldNotFound {

        Iterator fieldIterator = fieldMap.iterator();
        while (fieldIterator.hasNext()) {
            Field field = (Field) fieldIterator.next();
            if (!isGroupCountField(dd, field)) {
                String value = fieldMap.getString(field.getTag());
                if (dd.hasFieldValue(field.getTag())) {
                    value = dd.getValueName(field.getTag(), fieldMap.getString(field.getTag())) + " (" + value + ")";
                }
                //System.out.println(prefix + dd.getFieldName(field.getTag()) + ": " + value);
                output+=prefix + dd.getFieldName(field.getTag()) + ": " + value+"\n";
            }
        }

        Iterator groupsKeys = fieldMap.groupKeyIterator();
        while (groupsKeys.hasNext()) {
            int groupCountTag = ((Integer) groupsKeys.next()).intValue();
            //System.out.println(prefix + dd.getFieldName(groupCountTag) + ": count = "
            //        + fieldMap.getInt(groupCountTag));
            output+=prefix+dd.getFieldName(groupCountTag)+": count = "+fieldMap.getInt(groupCountTag)+"\n";
            Group g = new Group(groupCountTag, 0);
            int i = 1;
            while (fieldMap.hasGroup(i, groupCountTag)) {
                if (i > 1) {
                    //System.out.println(prefix + "  ----");
                    output+=prefix + "  ----\n";
                }
                fieldMap.getGroup(i, g);
                printFieldMap(prefix + "  ", dd, msgType, g);
                i++;
            }
        }
    }

    private static boolean isGroupCountField(DataDictionary dd, Field field) {
        return dd.getFieldTypeEnum(field.getTag()) == FieldType.NumInGroup;
    }

    
}
