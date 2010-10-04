package nl.lamia.FixTools;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import javax.swing.table.DefaultTableModel;

import junit.framework.Assert;
import org.apache.log4j.Logger;

import quickfix.*;
import quickfix.field.MsgType;

/***
 * Class to maintain order id's and status during tests.
 * Singleton
 * @author marcel
 *
 */
public class OrderManager {

        private static Logger logger = Logger.getLogger(OrderManager.class);

	private static OrderManager orderManager=null;
	
	private HashMap<String,String> orderIds;   //Stores issued client order ids for retrieval
	private HashMap<String,ArrayList> messages; //Stores messages per client orderId
	private Connection dbconn;

	public static OrderManager get() {
		if (orderManager==null) {
			orderManager=new OrderManager();
		}
		return orderManager;
	}
	
	public OrderManager() {
		orderIds=new HashMap<String, String>();
		messages=new HashMap<String, ArrayList>();

                //Get a connection to the hsqldb, creating a database if none existed.
                try {
                    Class.forName("org.hsqldb.jdbc.JDBCDriver");
                    dbconn=DriverManager.getConnection("jdbc:hsqldb:file:data/hsqldb/db;shutdown=true","SA","");
                } catch (Exception ex) {
                    logger.error("Exception", ex);
                    logger.error("Cannot load JDBC driver/connect to db");
                    System.exit(1);
                }

                //Database db contains tables:
                //create table orders (clordid varchar(20) primary key, testid varchar(20), insert_time datetime, status varchar(30) )
                //create table messages (clordid varchar(20), insert_time datetime, fix_message varchar(256) )

                //See wheter there is already a table
                try {
                    Statement stmt=dbconn.createStatement();
                    stmt.execute("select * from orders");
                } catch (Exception ex) {
                    logger.error("Exception", ex);
                    logger.error("Cannot check table existence, please initialize tables");
                }

	}
	
	public void addMessage(Message message) {
		//TODO if 35=d then add a key plus new arraylist and first message. If 8 or 9 add new message
                String clordid, origClordid, msgType, orderStatus;
                try {clordid=message.getString(11);} catch (FieldNotFound ex) {clordid="";}
                try {origClordid=message.getString(41);} catch (FieldNotFound ex) {origClordid="";}
                try {msgType=message.getHeader().getString(35);} catch (FieldNotFound ex) {msgType="";}
                try {orderStatus=message.getString(39);} catch (FieldNotFound ex) {orderStatus="";}
                if (!clordid.equals("")) {
                    try {
                        Statement stmt=dbconn.createStatement();
                        stmt.execute("insert into messages values('"+clordid+"',current_timestamp,'"+
                                                message.toString().replace('\001', '|')+"')");
                    } catch (Exception ex) {
                        logger.error("Exception", ex);
                        logger.error("Error inserting new message.");
                    }
                }
                logger.info("Order: "+clordid+" origClordid: "+origClordid+" msgtype: "+msgType+" status: "+orderStatus+".");
                if (msgType.equals("D")) {
                    try {
                        Statement stmt=dbconn.createStatement();
                        stmt.execute("update orders set status='SINGLE' where clordid='"+clordid+"'");
                    } catch (Exception ex) {
                        logger.error("Exception", ex);
                        logger.error("Error updating order.");
                    }
                }
                if (msgType.equals("8") && !orderStatus.equals("")) {
                    try {
                        Statement stmt=dbconn.createStatement();
                        stmt.execute("update orders set status='"+orderStatus+"' where clordid='"+clordid+"'");
                    } catch (Exception ex) {
                        logger.error("Exception", ex);
                        logger.error("Error updating order.");
                    }
                }



	}

	public void addOrderId(String orderid,String newOrderid) {  //Add a new pair; template, actually used
		orderIds.put(orderid, newOrderid);
                //Add the new clordid to the database, with the template used.
                try {
                    Statement stmt=dbconn.createStatement();
                    stmt.execute("insert into orders values('"+newOrderid+"','"+orderid+"',current_timestamp, 'INIT')");
                } catch (Exception ex) {
                    logger.error("Exception", ex);
                    logger.error("Error inserting new order.");
                }
	}
	
	public String getOrigOrderId(String orderid) {  //Get actually used by template
		return orderIds.get(orderid);
	}

        public DefaultTableModel getOrderTable() {
            // TableModel definition
            String[] tableColumnsName = {"clordid","test","timestamp","status"};
            DefaultTableModel orderModel=new DefaultTableModel();
            orderModel.setColumnIdentifiers(tableColumnsName);

            // the query
            ResultSet rs;
            int nrColumns;
            try {
                Statement stmt=dbconn.createStatement();
                rs=stmt.executeQuery("select * from orders");

                // Loop through the ResultSet and transfer in the Model
                java.sql.ResultSetMetaData rsmd = rs.getMetaData();
                nrColumns = rsmd.getColumnCount();
                while(rs.next()){
                    Object[] objects = new Object[nrColumns];
                    for(int i=0;i<nrColumns;i++){
                        objects[i]=rs.getObject(i+1);
                    }
                    orderModel.addRow(objects);
                }
            } catch (SQLException ex) {
                logger.error("Error getting table model", ex);
            }
            return orderModel;
        }

        public DefaultTableModel getMessageTable(String clordid) {
            // TableModel definition
            String[] tableColumnsName = {"clordid","timestamp","message"};
            DefaultTableModel messageModel=new DefaultTableModel();
            messageModel.setColumnIdentifiers(tableColumnsName);

            // the query
            ResultSet rs;
            int nrColumns;
            try {
                Statement stmt=dbconn.createStatement();
                rs=stmt.executeQuery("select * from messages where clordid='"+clordid+"'");

                // Loop through the ResultSet and transfer in the Model
                java.sql.ResultSetMetaData rsmd = rs.getMetaData();
                nrColumns = rsmd.getColumnCount();
                while(rs.next()){
                    Object[] objects = new Object[nrColumns];
                    for(int i=0;i<nrColumns;i++){
                        objects[i]=rs.getObject(i+1);
                    }
                    messageModel.addRow(objects);
                }
            } catch (SQLException ex) {
                logger.error("Error getting table model", ex);
            }
            return messageModel;
        }

        /***
         * Deletes all orders and messages from the database
         */
        public void purgeOrders() {
                try {
                    Statement stmt=dbconn.createStatement();
                    stmt.execute("delete from orders");
                    stmt.execute("delete from messages");
                } catch (Exception ex) {
                    logger.error("Exception", ex);
                    logger.error("Error purging all orders.");
                }
	}

        public void purgeTestCase(String caseId) {
                try {
                    Statement stmt=dbconn.createStatement();
                    stmt.execute("delete from messages where clordid in (select clordid from orders where testid='"+caseId+"') ");
                    stmt.execute("delete from orders where testid='"+caseId+"'");
                } catch (Exception ex) {
                    logger.error("Exception", ex);
                    logger.error("Error deleting testcase "+caseId);
                }
	}



	
}
