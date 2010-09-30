package nl.lamia.FixTools;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Vector;
import javax.swing.table.DefaultTableModel;

import org.apache.log4j.Logger;

public class Dictionary {

	private static Logger logger = Logger.getLogger(Dictionary.class);
	
	static final String FILENAME="config/products.def";
	static private Dictionary productDictionary=null;
	
	private HashMap<String,HashMap<String,String>> dictionary=null;
        private Vector<String> headers=null;
        private Vector<Vector<String>> rows=null;

        public static void main(String args[]) {
            Dictionary d=Dictionary.getDict();
            System.out.println(d.toString());
        }
        
	public static Dictionary getDict() {
		if (productDictionary==null) {
			productDictionary=new Dictionary();
			productDictionary.loadDictionary(FILENAME);
		}
		return productDictionary;
	}

	private void loadDictionary(String file) {
		this.dictionary=new HashMap<String, HashMap<String,String>>();
		BufferedReader in = null;
        try {
            in = new BufferedReader(new FileReader(file));
            String line = in.readLine();
            //First line is header
            String[] header=line.split(",");
            this.headers=new Vector<String>();  //Jtable Model
            for (int i=0;i<header.length;i++) this.headers.add(header[i]);
            Vector<String> row;
            this.rows=new Vector<Vector<String>>();
            line = in.readLine();
            while (line != null) {
                        row=new Vector<String>();   //JTable model
            		String[] fields=line.split(",");
            		//First column is product ID
            		if (fields.length>1) {
            			dictionary.put(fields[0], new HashMap<String, String>());
                                row.add(fields[0]);
            		}
            		for (int i=1;i<fields.length;i++) {
            			if (i<header.length) {
                                    dictionary.get(fields[0]).put(header[i], fields[i]);
                                    row.add(fields[i]);
                                }
            		}
                        rows.add(row);
                        line=in.readLine();
            	}
            } catch (IOException ex) {
            	logger.error("Error: can't find dictionary file: "+file);
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        }
	
	public String getProductProp(String product, String prop) {
		if (dictionary.get(product)!=null) {
				return dictionary.get(product).get(prop);
			} else {
				return null;
			}
	}

	public HashMap<String,String> getProduct(String product) {
		return dictionary.get(product);
	}

        public DefaultTableModel getTableModel () {
            DefaultTableModel tm=new DefaultTableModel(rows, headers);
            return tm;
        }
}
	

