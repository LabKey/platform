package org.labkey.study.model;

/**
 * Created by IntelliJ IDEA.
 * User: Matthew
 * Date: Feb 1, 2006
 * Time: 10:12:34 AM
 */
public class ParticipantDataMapKey implements Comparable
{
    public int datasetId;
    public double sequenceNum;
//    public int objectPropertyId;

    public ParticipantDataMapKey(int datasetId, double sequenceNum) // , int objectPropertyId)
    {
//        this.objectPropertyId = objectPropertyId;
        this.sequenceNum = sequenceNum;
        this.datasetId = datasetId;
    }


    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ParticipantDataMapKey that = (ParticipantDataMapKey) o;

        if (datasetId != that.datasetId) return false;
//        if (objectPropertyId != that.objectPropertyId) return false;
        if (Double.compare(that.sequenceNum, sequenceNum) != 0) return false;

        return true;
    }


    public int hashCode()
    {
        int result;
        long temp;
        result = datasetId;
        temp = sequenceNum != +0.0d ? Double.doubleToLongBits(sequenceNum) : 0L;
        result = 31 * result + (int) (temp ^ (temp >>> 32));
//        result = 31 * result + objectPropertyId;
        return result;
    }


    public int compareTo(Object o)
    {
        ParticipantDataMapKey k = (ParticipantDataMapKey) o;
        if (this.sequenceNum != k.sequenceNum)
            return Double.compare(this.sequenceNum, k.sequenceNum);
//        if (this.datasetId != k.datasetId)
            return this.datasetId - k.datasetId;
//        return this.objectPropertyId - k.objectPropertyId;
    }

    
    @Override
    public String toString()
    {
        return "ParticipantDataMapKey(" + datasetId + "," + sequenceNum + ")";
    }
}
