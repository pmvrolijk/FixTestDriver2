/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * MessageEditor.java
 *
 * Created on Oct 5, 2010, 9:13:44 PM
 */

package nl.lamia.FixTools;

import java.util.HashMap;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JDialog;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableModel;
import org.apache.log4j.Logger;
import quickfix.ConfigError;
import quickfix.DataDictionary;

/**
 *
 * @author marcel
 */
public class MessageEditor extends javax.swing.JDialog {

    private static final Pattern FIELDPATTERN = Pattern.compile("(\\d+)=([^\\|]+)");
    private static Logger logger = Logger.getLogger(MainWindow.class);
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
        	logger.error("Datadictionary failed to load. Doh.");
        }
    }


    private String newMessage="";
    private Boolean allowUpdate;
    private String oldMessage;
    private DefaultTableModel tblModel;
    Vector<String> headers;
    Vector<Vector<String>> rows;


    /** Creates new form MessageEditor */
    /***
     * Creates a new message dialog. The old message is set to the text field
     * and split into the components in the table.
     * When a value is pasted into the textfield or it is editted, it should be
     * reflected in the table and vice versa.
     * The buttons should be ok - cancel for updatables, ok for read onlies
     * on pressing ok the textfield is returned to caller.
     * @param parent
     * @param modal
     * @param message
     * @param allowUpdate
     */
    public MessageEditor(java.awt.Frame parent, boolean modal, String message, Boolean allowUpdate) {
        super(parent, "Fixmessage" , modal);
        initComponents();
        this.oldMessage=message;
        this.allowUpdate=allowUpdate;
        jTextFieldMessage.setText(message);

        //Table
        headers=new Vector<String>();
        headers.add("tag");
        headers.add("name");
        headers.add("value");
        headers.add("datatype");
        message2Table(oldMessage);
        jTableMessage.setModel(tblModel);

        if (!allowUpdate) {
            jButtonCancel.setVisible(false);
            jTextFieldMessage.setEditable(false);
            this.setTitle("Show message");
            this.jLabel1.setText("FIX Message:");
            jButtonOK.setText("Close");
        }
    }

    private void message2Table(String msg) {
        DataDictionary dd=dataDictionary.get(msg.substring(2, 9));
        //String[] tags=msg.split("\\|");
        Matcher m=FIELDPATTERN.matcher(msg);
        rows=new Vector<Vector<String>>();
        Vector<String> row;
        String tag,value;
        int tagnr;
        while (m.find()) {
            if (m.groupCount()==2) {
                tag=m.group(1);
                try {tagnr=Integer.parseInt(tag);}
                    catch (NumberFormatException e) {tagnr=0;}
                value=m.group(2);
                row=new Vector<String>();
                row.add(tag);   //Tag
                if (dd!=null) {
                    row.add(dd.getFieldName(tagnr));
                } else {            //Tag name / description
                    row.add("unknown");
                }
                row.add(value); //Value
                if (dd!=null) {
                    row.add(dd.getFieldTypeEnum(tagnr).getName());
                } else {                //Datatype
                    row.add("unknown");
                }
                rows.add(row);
            }
        }
        tblModel=new DefaultTableModel(rows,headers);
        jTableMessage.setModel(tblModel);
        this.jTableMessage.getColumnModel().getColumn(0).setMaxWidth(50);
        this.jTableMessage.getColumnModel().getColumn(1).setWidth(200);
        tblModel.addTableModelListener(new TableModelListener() {
                public void tableChanged(TableModelEvent e) {
                    table2Message((DefaultTableModel)e.getSource());
                }
        });

    }

    private void table2Message(DefaultTableModel tbl) {
        String updMsg="";
        for (int row=0;row<tbl.getRowCount();row++) {
            updMsg+=(String)tbl.getValueAt(row, 0)+"="+(String)tbl.getValueAt(row, 2)+"|";
        }
        jTextFieldMessage.setText(updMsg);
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jButtonOK = new javax.swing.JButton();
        jButtonCancel = new javax.swing.JButton();
        jTextFieldMessage = new javax.swing.JTextField();
        jScrollPane1 = new javax.swing.JScrollPane();
        jTableMessage = jTableMessage=new javax.swing.JTable()
        {
            @Override
            public boolean isCellEditable(int row,int col) {
                if (col==0 || col==1 || col==3) return false;
                if (allowUpdate) return true;
                return false;
            }
        };
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        jButtonOK.setText("Ok");
        jButtonOK.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonOKActionPerformed(evt);
            }
        });

        jButtonCancel.setText("Cancel");
        jButtonCancel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonCancelActionPerformed(evt);
            }
        });

        jTextFieldMessage.setFont(new java.awt.Font("Courier New", 0, 13)); // NOI18N
        jTextFieldMessage.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jTextFieldMessageActionPerformed(evt);
            }
        });
        jTextFieldMessage.addInputMethodListener(new java.awt.event.InputMethodListener() {
            public void inputMethodTextChanged(java.awt.event.InputMethodEvent evt) {
                jTextFieldMessageInputMethodTextChanged(evt);
            }
            public void caretPositionChanged(java.awt.event.InputMethodEvent evt) {
            }
        });

        jTableMessage.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String [] {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        ));
        jScrollPane1.setViewportView(jTableMessage);

        jLabel1.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
        jLabel1.setText("FIX Message string. Hit enter to update.");

        jLabel2.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
        jLabel2.setText("FIX Tags");

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, jTextFieldMessage, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 513, Short.MAX_VALUE)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(jLabel1)
                .addContainerGap(310, Short.MAX_VALUE))
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(jScrollPane1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 473, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(layout.createSequentialGroup()
                        .add(jButtonCancel)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(jButtonOK)))
                .addContainerGap())
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(jLabel2)
                .addContainerGap(463, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .add(jLabel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 10, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jTextFieldMessage, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 11, Short.MAX_VALUE)
                .add(jLabel2)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jScrollPane1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 431, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jButtonOK)
                    .add(jButtonCancel))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void jTextFieldMessageInputMethodTextChanged(java.awt.event.InputMethodEvent evt) {//GEN-FIRST:event_jTextFieldMessageInputMethodTextChanged
         //System.out.println("Changes..");
        newMessage=jTextFieldMessage.getText();
        message2Table(newMessage);
    }//GEN-LAST:event_jTextFieldMessageInputMethodTextChanged

    private void jTextFieldMessageActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jTextFieldMessageActionPerformed
         //System.out.println("Changes.2.");
        newMessage=jTextFieldMessage.getText();
        message2Table(newMessage);
    }//GEN-LAST:event_jTextFieldMessageActionPerformed

    private void jButtonOKActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonOKActionPerformed
        //On an editting dialog return the new message,
        //on a show dialog return null
        if (allowUpdate) {
            newMessage=jTextFieldMessage.getText();
        } else {
            newMessage=null;
        }
        this.setVisible(false);
    }//GEN-LAST:event_jButtonOKActionPerformed

    private void jButtonCancelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonCancelActionPerformed
        //On an editting dialog this cancels the edit
        newMessage=null;
        this.setVisible(false);
    }//GEN-LAST:event_jButtonCancelActionPerformed

    /**
    * @param args the command line arguments
    */
    public static void main(String args[]) {
        /*java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                MessageEditor dialog = new MessageEditor(new javax.swing.JFrame(), true, "8=hui|23=jui|98=jk|99=jk|10=091", true);
                dialog.addWindowListener(new java.awt.event.WindowAdapter() {
                    public void windowClosing(java.awt.event.WindowEvent e) {
                        System.exit(0);
                    }
                });
                System.out.println("Message returned:"+dialog.showDialog());
            }
        });*/
        MessageEditor msgedot=new MessageEditor(null, true, "10=90|89=yu", Boolean.TRUE);
        System.out.println("Message:"+msgedot.showDialog());
    }

    /***
     * Displays the dialog and returns the new message if updatable is true
     * @return new message
     */
    public String showDialog() {
        this.setVisible(true);
        this.dispose();
        return newMessage;
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton jButtonCancel;
    private javax.swing.JButton jButtonOK;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTable jTableMessage;
    private javax.swing.JTextField jTextFieldMessage;
    // End of variables declaration//GEN-END:variables

}
