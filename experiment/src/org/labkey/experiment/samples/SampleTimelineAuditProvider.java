package org.labkey.experiment.samples;

import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;

import java.util.ArrayList;
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
    public static final String SAMPLE_ID_COLUMN_NAME = "SampleID"; // ??? LSID instead or in addition ???


    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static {
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_IMPERSONATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_PROJECT_ID));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CONTAINER));
        defaultVisibleColumns.add(FieldKey.fromParts(SAMPLE_TYPE_COLUMN_NAME));
        defaultVisibleColumns.add(FieldKey.fromParts(SAMPLE_NAME_COLUMN_NAME));
        defaultVisibleColumns.add(FieldKey.fromParts(SAMPLE_ID_COLUMN_NAME));
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
                if (SAMPLE_ID_COLUMN_NAME.equalsIgnoreCase(col.getName()))
                {
                    col.setLabel("Sample ID");
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
            }
        };
        appendValueMapColumns(table);
        return table;
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }


    public static class SampleTimelineAuditEvent extends AuditTypeEvent
    {
        private String _sampleId;
        private String _sampleName;
        private String _sampleType;
        private String _sampleTypeId;
        private String _oldRecordMap;
        private String _newRecordMap;

        public String getSampleId()
        {
            return _sampleId;
        }

        public void setSampleId(String sampleId)
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

        public String getSampleTypeId()
        {
            return _sampleTypeId;
        }

        public void setSampleTypeId(String sampleTypeId)
        {
            _sampleTypeId = sampleTypeId;
        }

        public String getOldRecordMap()
        {
            return _oldRecordMap;
        }

        public void setOldRecordMap(String oldRecordMap)
        {
            _oldRecordMap = oldRecordMap;
        }

        public String getNewRecordMap()
        {
            return _newRecordMap;
        }

        public void setNewRecordMap(String newRecordMap)
        {
            _newRecordMap = newRecordMap;
        }

        @Override
        public Map<String, Object> getAuditLogMessageElements()
        {
            Map<String, Object> elements = new LinkedHashMap<>();
            elements.put("sampleId", getSampleId());
            elements.put("sampleName", getSampleName());
            elements.put("sampleType", getSampleType());
            elements.put("sampleTypeId", getSampleTypeId());
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
