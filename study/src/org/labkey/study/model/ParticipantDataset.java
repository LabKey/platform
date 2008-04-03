package org.labkey.study.model;

import org.labkey.api.data.Container;

/**
 * User: brittp
 * Date: Feb 1, 2006
 * Time: 5:29:18 PM
 */
public class ParticipantDataset
{
    private String _lsid;
    private Container _container;
    private Double _sequenceNum;
    private Integer _studyDataSetId;
    private String _studyParticipantId;

    public Container getContainer()
    {
        return _container;
    }

    public void setContainer(Container container)
    {
        _container = container;
    }

    public Integer getDataSetId()
    {
        return _studyDataSetId;
    }

    public void setDataSetId(Integer studyDataSetId)
    {
        _studyDataSetId = studyDataSetId;
    }

    public String getParticipantId()
    {
        return _studyParticipantId;
    }

    public void setParticipantId(String studyParticipantId)
    {
        _studyParticipantId = studyParticipantId;
    }

    public String getLsid()
    {
        return _lsid;
    }

    public void setLsid(String lsid)
    {
        _lsid = lsid;
    }

    public Double getSequenceNum()
    {
        return _sequenceNum;
    }

    public void setSequenceNum(Double sequenceNum)
    {
        _sequenceNum = sequenceNum;
    }
}
