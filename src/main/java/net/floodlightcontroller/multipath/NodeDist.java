package net.floodlightcontroller.multipath;

public class NodeDist implements Comparable<NodeDist> {
    private Long node;
    private int dist;
    
    public Long getNode() {
        return node;
    }
 
    public int getDist() {
        return dist;
    }

    public NodeDist(Long node, int dist) {
        this.node = node;
        this.dist = dist;
    }

    public int compareTo(NodeDist o) {
        if (o.dist == this.dist) {
            return (int)(o.node - this.node);
        }
        return o.dist - this.dist;
    }
}
