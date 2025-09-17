package org.labkey.assay.plate.audit;

import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.DetailedAuditTypeEvent;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.ObjectFactory;
import org.labkey.assay.plate.PlateImpl;
import org.labkey.assay.plate.model.PlateBean;
import org.labkey.assay.plate.query.PlateTable;

import java.util.Map;
import java.util.Set;

import static org.labkey.assay.plate.audit.PlateAuditProvider.EVENT_NAME;

public class PlateAuditEvent extends DetailedAuditTypeEvent
{
    private String _plateEventType;
    private Long _plateRowId;
    private String _plateName;
    private Long _plateSetRowId;
    private Long _plateTypeRowId;
    private Long _sourcePlateRowId;
    private Long _importRunId;
    private Boolean _reimport;
    private boolean _template;

    public PlateAuditEvent()
    {
        super();
    }

    protected PlateAuditEvent(
        PlateAuditProvider.PlateEventType eventType,
        Container container,
        PlateImpl plate,
        Long transactionAuditId
    )
    {
        super(EVENT_NAME, container, eventType.getComment());
        setPlateEventType(eventType.name());
        setPlateRowId(plate.getRowId());
        setPlateName(plate.getName());
        setPlateSetRowId(plate.getPlateSetId());
        setPlateTypeRowId(plate.getPlateType().getRowId());
        setSourcePlateRowId(plate.getSourcePlateRowId());
        setTemplate(plate.isTemplate());
        setTransactionId(transactionAuditId);
    }

    public String getPlateEventType()
    {
        return _plateEventType;
    }

    public void setPlateEventType(String plateEventType)
    {
        _plateEventType = plateEventType;
    }

    public Long getPlateRowId()
    {
        return _plateRowId;
    }

    public void setPlateRowId(Long plateRowId)
    {
        _plateRowId = plateRowId;
    }

    public String getPlateName()
    {
        return _plateName;
    }

    public void setPlateName(String plateName)
    {
        _plateName = plateName;
    }

    public Long getPlateSetRowId()
    {
        return _plateSetRowId;
    }

    public void setPlateSetRowId(Long plateSetRowId)
    {
        _plateSetRowId = plateSetRowId;
    }

    public Long getPlateTypeRowId()
    {
        return _plateTypeRowId;
    }

    public void setPlateTypeRowId(Long plateTypeRowId)
    {
        _plateTypeRowId = plateTypeRowId;
    }

    public Long getSourcePlateRowId()
    {
        return _sourcePlateRowId;
    }

    public void setSourcePlateRowId(Long sourcePlateRowId)
    {
        _sourcePlateRowId = sourcePlateRowId;
    }

    public boolean isTemplate()
    {
        return _template;
    }

    public void setTemplate(boolean template)
    {
        _template = template;
    }

    public Long getImportRunId()
    {
        return _importRunId;
    }

    public void setImportRunId(Long importRunId)
    {
        _importRunId = importRunId;
    }

    public Boolean isReimport()
    {
        return _reimport;
    }

    public void setReimport(Boolean reimport)
    {
        _reimport = reimport;
    }

    private static final Set<String> EXCLUDED_PROPERTIES = CaseInsensitiveHashSet.of("ContainerId", PlateTable.Column.DataFileId.name(), "EntityId");

    public void setNewRecordMap(Container container, PlateImpl plate)
    {
        Map<String, Object> plateRow = ObjectFactory.Registry.getFactory(PlateBean.class)
                .toMap(PlateBean.from(plate, true), new CaseInsensitiveHashMap<>());
        EXCLUDED_PROPERTIES.forEach(plateRow::remove);

        var newRecordMap = AbstractAuditTypeProvider.encodeForDataMap(plateRow);
        setNewRecordMap(newRecordMap, container);
    }

    public void setOldRecordMap(Container container, PlateImpl plate)
    {
        Map<String, Object> plateRow = ObjectFactory.Registry.getFactory(PlateBean.class)
                .toMap(PlateBean.from(plate, true), new CaseInsensitiveHashMap<>());
        EXCLUDED_PROPERTIES.forEach(plateRow::remove);

        var oldRecordMap = AbstractAuditTypeProvider.encodeForDataMap(plateRow);
        setOldRecordMap(oldRecordMap, container);
    }
}
