/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package nl.lamia.FixTools;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.Serializable;
import java.util.Date;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Transient;

/**
 *
 * @author marcel
 */
@Entity
@Table(name = "ORDERS", catalog = "PUBLIC", schema = "PUBLIC")
@NamedQueries({
    @NamedQuery(name = "Orders.findAll", query = "SELECT o FROM Orders o"),
    @NamedQuery(name = "Orders.findByClordid", query = "SELECT o FROM Orders o WHERE o.clordid = :clordid"),
    @NamedQuery(name = "Orders.findByTestid", query = "SELECT o FROM Orders o WHERE o.testid = :testid"),
    @NamedQuery(name = "Orders.findByInsertTime", query = "SELECT o FROM Orders o WHERE o.insertTime = :insertTime"),
    @NamedQuery(name = "Orders.findByStatus", query = "SELECT o FROM Orders o WHERE o.status = :status")})
public class Orders implements Serializable {
    @Transient
    private PropertyChangeSupport changeSupport = new PropertyChangeSupport(this);
    private static final long serialVersionUID = 1L;
    @Id
    @Basic(optional = false)
    @Column(name = "CLORDID")
    private String clordid;
    @Column(name = "TESTID")
    private String testid;
    @Column(name = "INSERT_TIME")
    @Temporal(TemporalType.TIMESTAMP)
    private Date insertTime;
    @Column(name = "STATUS")
    private String status;

    public Orders() {
    }

    public Orders(String clordid) {
        this.clordid = clordid;
    }

    public String getClordid() {
        return clordid;
    }

    public void setClordid(String clordid) {
        String oldClordid = this.clordid;
        this.clordid = clordid;
        changeSupport.firePropertyChange("clordid", oldClordid, clordid);
    }

    public String getTestid() {
        return testid;
    }

    public void setTestid(String testid) {
        String oldTestid = this.testid;
        this.testid = testid;
        changeSupport.firePropertyChange("testid", oldTestid, testid);
    }

    public Date getInsertTime() {
        return insertTime;
    }

    public void setInsertTime(Date insertTime) {
        Date oldInsertTime = this.insertTime;
        this.insertTime = insertTime;
        changeSupport.firePropertyChange("insertTime", oldInsertTime, insertTime);
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        String oldStatus = this.status;
        this.status = status;
        changeSupport.firePropertyChange("status", oldStatus, status);
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash += (clordid != null ? clordid.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        // TODO: Warning - this method won't work in the case the id fields are not set
        if (!(object instanceof Orders)) {
            return false;
        }
        Orders other = (Orders) object;
        if ((this.clordid == null && other.clordid != null) || (this.clordid != null && !this.clordid.equals(other.clordid))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "nl.lamia.FixTools.Orders[clordid=" + clordid + "]";
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        changeSupport.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        changeSupport.removePropertyChangeListener(listener);
    }

}
