package org.labkey.experiment.samples;

import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.SampleTimelineAuditEvent;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SampleTimelineAuditProvider extends AbstractAuditTypeProvider
{
    public static final String SAMPLE_TYPE_COLUMN_NAME = "SampleType";
    public static final String SAMPLE_TYPE_ID_COLUMN_NAME = "SampleTypeID";
    public static final String SAMPLE_NAME_COLUMN_NAME = "SampleName";
    public static final String SAMPLE_LSID_COLUMN_NAME = "SampleLSID";
    public static final String SAMPLE_ID_COLUMN_NAME = "SampleID";
    public static final String METADATA_COLUMN_NAME = "Metadata";
    public static final String IS_LINEAGE_UPDATE_COLUMN_NAME = "IsLineageUpdate";
    public static final String INVENTORY_UPDATE_TYPE_COLUMN_NAME = "InventoryUpdateType";


    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static {
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_IMPERSONATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CONTAINER));
        defaultVisibleColumns.add(FieldKey.fromParts(SAMPLE_TYPE_COLUMN_NAME));
        defaultVisibleColumns.add(FieldKey.fromParts(SAMPLE_TYPE_ID_COLUMN_NAME));
        defaultVisibleColumns.add(FieldKey.fromParts(SAMPLE_NAME_COLUMN_NAME));
        defaultVisibleColumns.add(FieldKey.fromParts(SAMPLE_ID_COLUMN_NAME));
        defaultVisibleColumns.add(FieldKey.fromParts(IS_LINEAGE_UPDATE_COLUMN_NAME));
        defaultVisibleColumns.add(FieldKey.fromParts(INVENTORY_UPDATE_TYPE_COLUMN_NAME));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_COMMENT));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_USER_COMMENT));
    }

    public SampleTimelineAuditProvider()
    {
        super(new SampleTimelineAuditDomainKind());
    }

    @Override
    public String getEventName()
    {
        return SampleTimelineAuditEvent.EVENT_TYPE;
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
            protected void initColumn(MutableColumnInfo col)
            {
                if (SAMPLE_LSID_COLUMN_NAME.equalsIgnoreCase(col.getName()))
                {
                    col.setLabel("Sample LSID");
                }
                else if (SAMPLE_ID_COLUMN_NAME.equalsIgnoreCase(col.getName()))
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
                else if (IS_LINEAGE_UPDATE_COLUMN_NAME.equalsIgnoreCase(col.getName()))
                {
                    col.setLabel("Lineage Update?");
                }
                else if (INVENTORY_UPDATE_TYPE_COLUMN_NAME.equalsIgnoreCase(col.getName()))
                {
                    col.setLabel("Inventory Update Type");
                }
                else if (COLUMN_NAME_TRANSACTION_ID.equalsIgnoreCase(col.getName()))
                {
                    col.setLabel("Transaction ID");
                }
                else if (COLUMN_NAME_USER_COMMENT.equalsIgnoreCase(col.getName()))
                {
                    col.setLabel("Reason");
                }
            }
        };
        table.setTitleColumn(SAMPLE_NAME_COLUMN_NAME);
        appendValueMapColumns(table);

        DetailsURL url = DetailsURL.fromString("audit-detailedAuditChanges.view?auditRowId=${rowId}&auditEventType=" + SampleTimelineAuditEvent.EVENT_TYPE);
        url.setStrictContainerContextEval(true);
        table.setDetailsURL(url);
        return table;
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }

    @Override
    public boolean canDeleteOldRows()
    {
        return false;
    }

    public int moveEvents(Container targetContainer, Collection<Integer> sampleIds)
    {
        return moveEvents(targetContainer, SAMPLE_ID_COLUMN_NAME, sampleIds);
    }

    public static class SampleTimelineAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "SampleTimelineAuditDomain";
        public static final String NAMESPACE_PREFIX = "Audit-" + NAME;

        private static Set<PropertyDescriptor> _fields = new LinkedHashSet<>();

        public SampleTimelineAuditDomainKind()
        {
            super(SampleTimelineAuditEvent.EVENT_TYPE);

            Set<PropertyDescriptor> fields = new LinkedHashSet<>();
            fields.add(createPropertyDescriptor(SAMPLE_TYPE_COLUMN_NAME, PropertyType.STRING, 100));
            fields.add(createPropertyDescriptor(SAMPLE_TYPE_ID_COLUMN_NAME, PropertyType.INTEGER));
            fields.add(createPropertyDescriptor(SAMPLE_NAME_COLUMN_NAME, PropertyType.STRING, 200));
            fields.add(createPropertyDescriptor(SAMPLE_ID_COLUMN_NAME, PropertyType.INTEGER));
            fields.add(createPropertyDescriptor(SAMPLE_LSID_COLUMN_NAME, PropertyType.STRING, 300));
            fields.add(createPropertyDescriptor(IS_LINEAGE_UPDATE_COLUMN_NAME, PropertyType.BOOLEAN));
            fields.add(createPropertyDescriptor(INVENTORY_UPDATE_TYPE_COLUMN_NAME, PropertyType.STRING, 25));
            fields.add(createPropertyDescriptor(METADATA_COLUMN_NAME, PropertyType.STRING, -1));        // varchar max
            fields.add(createPropertyDescriptor(COLUMN_NAME_USER_COMMENT, PropertyType.STRING, -1));
            fields.add(createOldDataMapPropertyDescriptor());
            fields.add(createNewDataMapPropertyDescriptor());
            fields.add(createPropertyDescriptor(COLUMN_NAME_TRANSACTION_ID, PropertyType.BIGINT));        // varchar max
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

        @Override
        public Set<PropertyStorageSpec.Index> getPropertyIndices(Domain domain)
        {
            Set<PropertyStorageSpec.Index> indexes = super.getPropertyIndices(domain);
            indexes.add(new PropertyStorageSpec.Index(false, SAMPLE_ID_COLUMN_NAME));
            indexes.add(new PropertyStorageSpec.Index(false, SAMPLE_NAME_COLUMN_NAME));
            indexes.add(new PropertyStorageSpec.Index(false, COLUMN_NAME_TRANSACTION_ID)); // Issue 50202
            return indexes;
        }
    }
}
