package org.labkey.study.model;

/**
 * Created by IntelliJ IDEA.
 * User: Matthew
 * Date: Feb 1, 2006
 * Time: 10:12:34 AM
 */
public class ParticipantSequenceKey implements Comparable
{
    public ParticipantSequenceKey(String participantId, Double sequenceNum)
    {
        this.participantId = participantId == null ? "" : participantId;
        this.sequenceNum = sequenceNum == null ? 0 : sequenceNum;
    }

    public ParticipantSequenceKey(String participantId, double sequenceNum)
    {
        this.participantId = participantId;
        this.sequenceNum = sequenceNum;
    }


    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ParticipantSequenceKey that = (ParticipantSequenceKey) o;

        if (Double.compare(that.sequenceNum, sequenceNum) != 0) return false;
        if (participantId != null ? !participantId.equals(that.participantId) : that.participantId != null)
            return false;

        return true;
    }


    public int hashCode()
    {
        int result;
        long temp;
        result = (participantId != null ? participantId.hashCode() : 0);
        temp = sequenceNum != +0.0d ? Double.doubleToLongBits(sequenceNum) : 0L;
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }


    public int compareTo(Object o)
    {
        ParticipantSequenceKey k = (ParticipantSequenceKey) o;
        int ptidCompare = this.participantId.compareTo(k.participantId);
        return ptidCompare != 0 ?  ptidCompare : Double.compare(this.sequenceNum, k.sequenceNum);
    }

    public String participantId;
    public double sequenceNum;
}
