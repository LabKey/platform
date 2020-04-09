package org.labkey.experiment.samples;

import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.DetailedAuditTypeEvent;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SampleTimelineAuditProvider extends AbstractAuditTypeProvider
{
    public static final String EVENT_TYPE = "SampleTimelineEvent";

    public static final String SAMPLE_TYPE_COLUMN_NAME = "SampleType";
    public static final String SAMPLE_TYPE_ID_COLUMN_NAME = "SampleTypeID";
    public static final String SAMPLE_NAME_COLUMN_NAME = "SampleName";
    public static final String SAMPLE_LSID_COLUMN_NAME = "SampleLSID"; // ??? TODO replace with id once we are generating ids
    public static final String IS_LINEAGE_UPDATE_COLUMN_NAME = "IsLineageUpdate";


    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static {
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_IMPERSONATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CONTAINER));
        defaultVisibleColumns.add(FieldKey.fromParts(SAMPLE_TYPE_COLUMN_NAME));
        defaultVisibleColumns.add(FieldKey.fromParts(SAMPLE_TYPE_ID_COLUMN_NAME));
        defaultVisibleColumns.add(FieldKey.fromParts(SAMPLE_NAME_COLUMN_NAME));
        defaultVisibleColumns.add(FieldKey.fromParts(SAMPLE_LSID_COLUMN_NAME));
        defaultVisibleColumns.add(FieldKey.fromParts(IS_LINEAGE_UPDATE_COLUMN_NAME));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_COMMENT));
    }

    @Override
    protected AbstractAuditDomainKind getDomainKind()
    {
        return new SampleTimelineAuditDomainKind();
    }

    @Override
    public String getEventName()
    {
        return EVENT_TYPE;
    }

    @Override
    public String getLabel()
    {
        return "Sample timeline events";
    }

    @Override
    public String getDescription()
    {
        return "Data about events for individual samples";
    }

    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>) SampleTimelineAuditEvent.class;
    }

    @Override
    public TableInfo createTableInfo(UserSchema userSchema, ContainerFilter cf)
    {
        DefaultAuditTypeTable table = new DefaultAuditTypeTable(this, createStorageTableInfo(), userSchema, cf, getDefaultVisibleColumns())
        {
            @Override
            protected void initColumn(BaseColumnInfo col)
            {
                if (SAMPLE_LSID_COLUMN_NAME.equalsIgnoreCase(col.getName()))
                {
                    col.setLabel("Sample LSID");
                }
                else if (SAMPLE_NAME_COLUMN_NAME.equalsIgnoreCase(col.getName()))
                {
                    col.setLabel("Sample Name");
                }
                else if (SAMPLE_TYPE_COLUMN_NAME.equalsIgnoreCase(col.getName()))
                {
                    col.setLabel("Sample Type");
                }
                else if (SAMPLE_TYPE_ID_COLUMN_NAME.equalsIgnoreCase(col.getName()))
                {
                    col.setLabel("Sample Type ID");
                }
                else if (IS_LINEAGE_UPDATE_COLUMN_NAME.equalsIgnoreCase(col.getName()))
                {
                    col.setLabel("Lineage Update?");
                }
            }
        };
        table.setTitleColumn(SAMPLE_NAME_COLUMN_NAME);
        appendValueMapColumns(table);

        DetailsURL url = DetailsURL.fromString("audit-detailedAuditChanges.view?auditRowId=${rowId}&auditEventType=" + EVENT_TYPE);
        url.setStrictContainerContextEval(true);
        table.setDetailsURL(url);
        return table;
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }


    public static class SampleTimelineAuditEvent extends DetailedAuditTypeEvent
    {
        private String _sampleLsid;
        private String _sampleName;
        private String _sampleType;
        private int _sampleTypeId;
        private boolean _isLineageUpdate;

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

        @Override
        public Map<String, Object> getAuditLogMessageElements()
        {
            Map<String, Object> elements = new LinkedHashMap<>();
            elements.put("sampleId", getSampleLsid());
            elements.put("sampleName", getSampleName());
            elements.put("sampleType", getSampleType());
            elements.put("sampleTypeId", getSampleTypeId());
            elements.put("isLineageUpdate", getIsLineageUpdate());
            elements.putAll(super.getAuditLogMessageElements());
            return elements;
        }
    }

    public static class SampleTimelineAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "SampleTimelineAuditDomain";
        public static final String NAMESPACE_PREFIX = "Audit-" + NAME;

        private static Set<PropertyDescriptor> _fields = new LinkedHashSet<>();

        public SampleTimelineAuditDomainKind()
        {
            super(EVENT_TYPE);

            Set<PropertyDescriptor> fields = new LinkedHashSet<>();
            fields.add(createPropertyDescriptor(SAMPLE_TYPE_COLUMN_NAME, PropertyType.STRING));
            fields.add(createPropertyDescriptor(SAMPLE_TYPE_ID_COLUMN_NAME, PropertyType.INTEGER));
            fields.add(createPropertyDescriptor(SAMPLE_NAME_COLUMN_NAME, PropertyType.STRING));
            fields.add(createPropertyDescriptor(SAMPLE_LSID_COLUMN_NAME, PropertyType.STRING));
            fields.add(createPropertyDescriptor(IS_LINEAGE_UPDATE_COLUMN_NAME, PropertyType.BOOLEAN));
            fields.add(createPropertyDescriptor(OLD_RECORD_PROP_NAME, PropertyType.STRING, -1));        // varchar max
            fields.add(createPropertyDescriptor(NEW_RECORD_PROP_NAME, PropertyType.STRING, -1));        // varchar max
            _fields = Collections.unmodifiableSet(fields);
        }

        @Override
        protected String getNamespacePrefix()
        {
            return NAMESPACE_PREFIX;
        }

        @Override
        public Set<PropertyDescriptor> getProperties()
        {
            return _fields;
        }

        @Override
        public String getKindName()
        {
            return NAME;
        }
    }
}
