package org.labkey.api.audit;

import java.util.LinkedHashMap;
import java.util.Map;

public class SampleTimelineAuditEvent extends DetailedAuditTypeEvent
{
    public static final String EVENT_TYPE = "SampleTimelineEvent";

    private String _sampleLsid;
    private int _sampleId;
    private String _sampleName;
    private String _sampleType;
    private int _sampleTypeId;
    private boolean _isLineageUpdate;
    private String _metadata;

    public SampleTimelineAuditEvent()
    {
        super();
    }

    public SampleTimelineAuditEvent(String container, String comment)
    {
        super(EVENT_TYPE, container, comment);
    }

    public String getSampleLsid()
    {
        return _sampleLsid;
    }

    public void setSampleLsid(String sampleLsid)
    {
        _sampleLsid = sampleLsid;
    }

    public int getSampleId()
    {
        return _sampleId;
    }

    public void setSampleId(int sampleId)
    {
        _sampleId = sampleId;
    }

    public String getSampleName()
    {
        return _sampleName;
    }

    public void setSampleName(String sampleName)
    {
        _sampleName = sampleName;
    }

    public String getSampleType()
    {
        return _sampleType;
    }

    public void setSampleType(String sampleType)
    {
        _sampleType = sampleType;
    }

    public int getSampleTypeId()
    {
        return _sampleTypeId;
    }

    public void setSampleTypeId(int sampleTypeId)
    {
        _sampleTypeId = sampleTypeId;
    }

    public boolean getIsLineageUpdate()
    {
        return _isLineageUpdate;
    }

    public void setLineageUpdate(boolean lineageUpdate)
    {
        _isLineageUpdate = lineageUpdate;
    }

    public String getMetadata()
    {
        return _metadata;
    }

    public void setMetadata(String metadata)
    {
        _metadata = metadata;
    }

    @Override
    public Map<String, Object> getAuditLogMessageElements()
    {
        Map<String, Object> elements = new LinkedHashMap<>();
        elements.put("sampleLsid", getSampleLsid());
        elements.put("sampleId", getSampleId());
        elements.put("sampleName", getSampleName());
        elements.put("sampleType", getSampleType());
        elements.put("sampleTypeId", getSampleTypeId());
        elements.put("isLineageUpdate", getIsLineageUpdate());
        elements.putAll(super.getAuditLogMessageElements());
        return elements;
    }
}
