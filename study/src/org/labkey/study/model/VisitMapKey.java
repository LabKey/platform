package org.labkey.study.model;

/**
 * Created by IntelliJ IDEA.
 * User: Matthew
 * Date: Feb 1, 2006
 * Time: 10:12:34 AM
 */
public class VisitMapKey implements Comparable
{
    public VisitMapKey(Integer datasetId, Integer visitRowId)
    {
        this.datasetId = datasetId == null ? 0 : datasetId;
        this.visitRowId = visitRowId == null ? 0 : visitRowId;
    }

    public VisitMapKey(int datasetId, int visitRowId)
    {
        this.datasetId = datasetId;
        this.visitRowId = visitRowId;
    }

    public boolean equals(Object obj)
    {
        VisitMapKey b = (VisitMapKey)obj;
        return this.datasetId == b.datasetId && this.visitRowId == b.visitRowId;
    }

    public int hashCode()
    {
        return datasetId * 4093 + visitRowId;
    }

    public int compareTo(Object o)
    {
        VisitMapKey k = (VisitMapKey) o;
        return this.visitRowId != k.visitRowId ? this.visitRowId - k.visitRowId :
                this.datasetId - k.datasetId;
    }

    public int datasetId;
    public int visitRowId;
}
