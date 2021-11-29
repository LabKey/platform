package org.labkey.api.audit;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.qc.DataState;
import org.labkey.api.qc.DataStateManager;
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
        UPDATE("Sample was updated.", "Updated"),
        PUBLISH("Sample was linked to a study", "Linked To Study"),
        RECALL("Sample was recalled from a study", "Recalled From Study");

        private final String _comment;
        private final String _actionLabel;

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

        public static String getActionCommentDetailed(@NotNull QueryService.AuditAction action, boolean isUpdate)
        {
            SampleTimelineEventType type;
            if (action == QueryService.AuditAction.MERGE)
                type = getTypeFromAction(isUpdate ? QueryService.AuditAction.UPDATE : QueryService.AuditAction.INSERT);
            else
                type = getTypeFromAction(action);
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
    private Long _transactionId;

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

    public Long getTransactionId()
    {
        return _transactionId;
    }

    public void setTransactionId(Long transactionId)
    {
        _transactionId = transactionId;
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
        elements.put("transactionId", getTransactionId());
        elements.put("metadata", getMetadata());
        elements.putAll(super.getAuditLogMessageElements());
        return elements;
    }

    /**
     * If the sample state changed, explicitly add in the Status Label value to the map so that it will render in the
     * audit log timeline event even if the DataState row is later deleted.
     */
    @Override
    public void setOldRecordMap(String oldRecordMap, Container container)
    {
        if (oldRecordMap != null)
        {
            Map<String, String> row = new CaseInsensitiveHashMap<>(AbstractAuditTypeProvider.decodeFromDataMap(oldRecordMap));
            String label = getStatusLabel(row, container);
            if (label != null)
            {
                row.put("samplestatelabel", label);
                oldRecordMap = AbstractAuditTypeProvider.encodeForDataMap(container, row);
            }
        }
        super.setOldRecordMap(oldRecordMap);
    }

    /**
     * If the sample state changed, explicitly add in the Status Label value to the map so that it will render in the
     * audit log timeline event even if the DataState row is later deleted.
     */
    @Override
    public void setNewRecordMap(String newRecordMap, Container container)
    {
        if (newRecordMap != null)
        {
            Map<String, String> row = new CaseInsensitiveHashMap<>(AbstractAuditTypeProvider.decodeFromDataMap(newRecordMap));
            String label = getStatusLabel(row, container);
            if (label != null)
            {
                row.put("samplestatelabel", label);
                newRecordMap = AbstractAuditTypeProvider.encodeForDataMap(container, row);
            }
        }
        super.setNewRecordMap(newRecordMap, container);
    }

    private String getStatusLabel(Map<String, String> row, Container container)
    {
        if (row.get("samplestate") != null && !StringUtils.isBlank(row.get("samplestate")))
        {
            DataState status = DataStateManager.getInstance().getStateForRowId(container, Integer.parseInt(row.get("samplestate")));
            if (status != null)
                return status.getLabel();
        }
        return null;
    }
}
