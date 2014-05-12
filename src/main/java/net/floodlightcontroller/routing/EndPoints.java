package net.floodlightcontroller.routing;

import org.openflow.util.HexString;

/**
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public class EndPoints implements Cloneable, Comparable<EndPoints> {
	/** The source end point, i.e. the source switch Id. */
	protected Long srcSwitchId;
	/** The destination end point, i.e. the destination switch Id. */
    protected Long dstSwitchId;
    
    /**
     * Constructor.
     * 
     * @param srcSwitchId
     * @param dstSwitchId
     */
    public EndPoints(long srcSwitchId, long dstSwitchId) {
        super();
        this.srcSwitchId = srcSwitchId;
        this.dstSwitchId = dstSwitchId;
    }
    
    /**
     * Getter for the source end point, i.e. the source switch ID.
     * 
     * @return <b>Long</b> The switch ID of the source switch.
     */
    public Long getSrc() {
        return srcSwitchId;
    }

    /**
     * Setter for the source end point, i.e. the source switch ID.
     * 
     * @param src The ID of the source switch.
     */
    public void setSrc(Long src) {
        this.srcSwitchId = src;
    }

    /**
     * Getter for the destination end point, i.e. the destination switch ID.
     * 
     * @return <b>Long</b> The switch ID of the destination switch.
     */
    public Long getDst() {
        return dstSwitchId;
    }

    /**
     * Setter for the destination end point, i.e. the source switch ID.
     * 
     * @param src The ID of the source switch.
     */
    public void setDst(Long dst) {
        this.dstSwitchId = dst;
    }
    
    @Override
    public int hashCode() {
        final int prime = 2417;
        Long result = 1L;
        result = prime * result + ((dstSwitchId == null) ? 0 : dstSwitchId.hashCode());
        result = prime * result + ((srcSwitchId == null) ? 0 : srcSwitchId.hashCode());
        // To cope with long cookie, use Long to compute hash then use Long's 
        // built-in hash to produce int hash code
        return result.hashCode(); 
    }
    
    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        EndPoints other = (EndPoints) obj;
        if (dstSwitchId == null) {
            if (other.getDst() != null)
                return false;
        } else if (!dstSwitchId.equals(other.getDst()))
            return false;
        if (srcSwitchId == null) {
            if (other.getSrc() != null)
                return false;
        } else if (!srcSwitchId.equals(other.getSrc()))
            return false;
        return true;
    }
    
    @Override
    public String toString() {
        return "Endpoints [src=" + HexString.toHexString(this.srcSwitchId) + " dst=" + HexString.toHexString(this.dstSwitchId) + "]";
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    @Override
    public int compareTo(EndPoints o) {
        int result = srcSwitchId.compareTo(o.getSrc());
        if (result != 0)
            return result;
        return dstSwitchId.compareTo(o.getDst());
    }
}