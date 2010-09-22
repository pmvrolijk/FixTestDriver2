package nl.lamia.FixTools;

import java.util.ArrayList;
import java.util.HashMap;

import junit.framework.Assert;

import quickfix.*;
import quickfix.field.MsgType;

/***
 * Class to maintain order id's and status during tests.
 * Singleton
 * @author marcel
 *
 */
public class OrderManager {

	private static OrderManager orderManager=null;
	
	private HashMap<String,String> orderIds;   //Stores issued client order ids for retrieval
	private HashMap<String,ArrayList> messages; //Stores messages per client orderId
	
	public static OrderManager get() {
		if (orderManager==null) {
			orderManager=new OrderManager();
		}
		return orderManager;
	}
	
	public OrderManager() {
		orderIds=new HashMap<String, String>();
		messages=new HashMap<String, ArrayList>();
	}
	
	public void addMessage(Message message) {
		//TODO if 35=d then add a key plus new arraylist and first message. If 8 or 9 add new message
	}

	public void addOrderId(String orderid,String newOrderid) {  //Add a new pair; template, actually used
		orderIds.put(orderid, newOrderid);
	}
	
	public String getOrigOrderId(String orderid) {  //Get actually used by template
		return orderIds.get(orderid);
	}

	
}
