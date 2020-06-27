package org.labkey.api.audit;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.query.QueryService;

import java.util.LinkedHashMap;
import java.util.Map;

public class SampleTimelineAuditEvent extends DetailedAuditTypeEvent
{
    public static final String EVENT_TYPE = "SampleTimelineEvent";

    public static final String SAMPLE_TIMELINE_EVENT_TYPE = "SampleTimelineEventType";
    public enum SampleTimelineEventType
    {
        INSERT("Sample was registered.", "Registered"),
        DELETE("Sample was deleted.", "Deleted"),
        TRUNCATE("Sample was deleted.", "Deleted"),
        MERGE("Sample was registered or updated.", "Registered"),
        UPDATE("Sample was updated.", "Updated");

        private String _comment;
        private String _actionLabel;

        SampleTimelineEventType(String comment, String actionLabel)
        {
            _comment = comment;
            _actionLabel = actionLabel;
        }

        public String getComment()
        {
            return _comment;
        }

        public String getActionLabel()
        {
            return _actionLabel;
        }

        public static String getActionCommentDetailed(@NotNull QueryService.AuditAction action)
        {
            SampleTimelineEventType type = getTypeFromAction(action);
            return type == null ? null : type.getComment();
        }

        public static SampleTimelineEventType getTypeFromAction(@NotNull QueryService.AuditAction action)
        {
            return getTypeFromName(action.name());
        }

        public static SampleTimelineEventType getTypeFromName(@Nullable String name)
        {
            if (null == name)
                return null;

            for (SampleTimelineEventType type : SampleTimelineEventType.values())
            {
                if (type.name().equalsIgnoreCase(name))
                {
                    return type;
                }
            }
            return null;
        }

    }

    private String _sampleLsid;
    private int _sampleId;
    private String _sampleName;
    private String _sampleType;
    private int _sampleTypeId;
    private boolean _isLineageUpdate;
    private String _metadata;
    private String _inventoryUpdateType;

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

    public String getInventoryUpdateType()
    {
        return _inventoryUpdateType;
    }

    public void setInventoryUpdateType(String inventoryUpdateType)
    {
        _inventoryUpdateType = inventoryUpdateType;
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
        elements.put("inventoryUpdateType", getInventoryUpdateType());
        elements.putAll(super.getAuditLogMessageElements());
        return elements;
    }
}
