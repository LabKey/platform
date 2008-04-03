package org.labkey.api.study.assay;

import java.util.Date;

/**
 * User: brittp
 * Date: Jul 30, 2007
 * Time: 4:29:10 PM
 */
public class AssayPublishKey
{
    private String _participantId;
    private float _visitId;
    private Date _date;
    private Object _dataId;

    public AssayPublishKey(String participantId, float visitId, Object dataId)
    {
        _participantId = participantId;
        _visitId = visitId;
        _dataId = dataId;
    }

    public AssayPublishKey(String participantId, Date date, Object dataId)
    {
        _participantId = participantId;
        _date = date;
        _dataId = dataId;
    }

    public String getParticipantId()
    {
        return _participantId;
    }

    public float getVisitId()
    {
        return _visitId;
    }

    public Object getDataId()
    {
        return _dataId;
    }

    public Date getDate()
    {
        return _date;
    }
}
