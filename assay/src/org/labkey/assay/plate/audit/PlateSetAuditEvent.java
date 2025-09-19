package org.labkey.assay.plate.audit;

import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.DetailedAuditTypeEvent;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.ObjectFactory;
import org.labkey.assay.plate.PlateSetImpl;

import java.util.Map;
import java.util.Set;

import static org.labkey.assay.plate.audit.PlateSetAuditProvider.EVENT_NAME;

public class PlateSetAuditEvent extends DetailedAuditTypeEvent
{
    private Boolean _archived;
    private String _plateSetEventType;
    private String _plateSetName;
    private Long _plateSetRowId;
    private String _plateSetType;
    private Long _parentPlateSetRowId;
    private Long _primaryPlateSetRowId;
    private Long _rootPlateSetRowId;

    public PlateSetAuditEvent()
    {
        super();
    }

    public PlateSetAuditEvent(
        PlateSetAuditProvider.PlateSetEventType eventType,
        Container container,
        PlateSetImpl plateSet,
        Long transactionAuditId
    )
    {
        super(EVENT_NAME, container, eventType.getComment());
        setArchived(plateSet.isArchived());
        setPlateSetEventType(eventType.name());
        setPlateSetName(plateSet.getName());
        setPlateSetRowId(plateSet.getRowId());
        setPlateSetType(plateSet.getType().name());
        setPrimaryPlateSetRowId(plateSet.getPrimaryPlateSetId());
        setParentPlateSetRowId(plateSet.getParentPlateSetId());
        setRootPlateSetRowId(plateSet.getRootPlateSetId());
        setTransactionId(transactionAuditId);
    }

    public Boolean getArchived()
    {
        return _archived;
    }

    public void setArchived(Boolean archived)
    {
        _archived = archived;
    }

    public String getPlateSetEventType()
    {
        return _plateSetEventType;
    }

    public void setPlateSetEventType(String plateSetEventType)
    {
        _plateSetEventType = plateSetEventType;
    }

    public String getPlateSetName()
    {
        return _plateSetName;
    }

    public void setPlateSetName(String plateSetName)
    {
        _plateSetName = plateSetName;
    }

    public Long getPlateSetRowId()
    {
        return _plateSetRowId;
    }

    public void setPlateSetRowId(Long plateSetRowId)
    {
        _plateSetRowId = plateSetRowId;
    }

    public String getPlateSetType()
    {
        return _plateSetType;
    }

    public void setPlateSetType(String plateSetType)
    {
        _plateSetType = plateSetType;
    }

    public Long getParentPlateSetRowId()
    {
        return _parentPlateSetRowId;
    }

    public void setParentPlateSetRowId(Long parentPlateSetRowId)
    {
        _parentPlateSetRowId = parentPlateSetRowId;
    }

    public Long getPrimaryPlateSetRowId()
    {
        return _primaryPlateSetRowId;
    }

    public void setPrimaryPlateSetRowId(Long primaryPlateSetRowId)
    {
        _primaryPlateSetRowId = primaryPlateSetRowId;
    }

    public Long getRootPlateSetRowId()
    {
        return _rootPlateSetRowId;
    }

    public void setRootPlateSetRowId(Long rootPlateSetRowId)
    {
        _rootPlateSetRowId = rootPlateSetRowId;
    }

    private static final Set<String> EXCLUDED_PROPERTIES = CaseInsensitiveHashSet.of(
        "Assay", "Container", "ContainerId", "ContainerName", "ExpObject",
            "Folder", "Full", "LSIDNamespacePrefix", "New", "ParentPlateSetId", "PlateCount",
            "Plates", "Primary", "QueryRowReference", "Standalone");

    public void setNewRecordMap(Container container, PlateSetImpl plateSet)
    {
        Map<String, Object> plateSetRow = ObjectFactory.Registry.getFactory(PlateSetImpl.class).toMap(plateSet, new CaseInsensitiveHashMap<>());
        EXCLUDED_PROPERTIES.forEach(plateSetRow::remove);

        var newRecordMap = AbstractAuditTypeProvider.encodeForDataMap(plateSetRow);
        setNewRecordMap(newRecordMap, container);
    }
}
