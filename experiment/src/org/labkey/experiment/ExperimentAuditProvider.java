package org.labkey.experiment;

import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.data.ProtocolColumn;
import org.labkey.api.audit.data.RunColumn;
import org.labkey.api.audit.data.RunGroupColumn;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.UserSchema;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: klum
 * Date: 7/21/13
 */
public class ExperimentAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    public static final String EXPERIMENT_AUDIT_EVENT = "ExperimentAuditEvent";

    public static final String COLUMN_NAME_PROTOCOL_LSID = "ProtocolLsid";
    public static final String COLUMN_NAME_RUN_LSID = "RunLsid";
    public static final String COLUMN_NAME_PROTOCOL_RUN = "ProtocolRun";
    public static final String COLUMN_NAME_RUN_GROUP = "RunGroup";

    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static {

        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_IMPERSONATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_PROJECT_ID));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_PROTOCOL_LSID));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_RUN_LSID));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_RUN_GROUP));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_COMMENT));
    }

    @Override
    protected DomainKind getDomainKind()
    {
        return new ExperimentAuditDomainKind();
    }

    @Override
    public String getEventName()
    {
        return EXPERIMENT_AUDIT_EVENT;
    }

    @Override
    public String getLabel()
    {
        return "Assay/Experiment events";
    }

    @Override
    public String getDescription()
    {
        return "Describes information about assay run events.";
    }

    @Override
    public <K extends AuditTypeEvent> K convertEvent(AuditLogEvent event)
    {
        ExperimentAuditEvent bean = new ExperimentAuditEvent();
        copyStandardFields(bean, event);

        bean.setProtocolLsid(event.getKey1());
        bean.setRunLsid(event.getKey2());
        bean.setProtocolRun(event.getKey3());

        if (event.getIntKey1() != null)
            bean.setRunGroup(event.getIntKey1());

        return (K)bean;
    }

    @Override
    public Map<FieldKey, String> legacyNameMap()
    {
        Map<FieldKey, String> legacyNames = super.legacyNameMap();
        legacyNames.put(FieldKey.fromParts("key1"), COLUMN_NAME_PROTOCOL_LSID);
        legacyNames.put(FieldKey.fromParts("key2"), COLUMN_NAME_RUN_LSID);
        legacyNames.put(FieldKey.fromParts("key3"), COLUMN_NAME_PROTOCOL_RUN);
        legacyNames.put(FieldKey.fromParts("intKey1"), COLUMN_NAME_RUN_GROUP);
        return legacyNames;
    }

    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>)ExperimentAuditEvent.class;
    }

    @Override
    public TableInfo createTableInfo(UserSchema userSchema)
    {
        Domain domain = getDomain();
        DbSchema dbSchema =  DbSchema.get(SCHEMA_NAME);

        DefaultAuditTypeTable table = new DefaultAuditTypeTable(this, domain, dbSchema, userSchema)
        {
            @Override
            protected void initColumn(ColumnInfo col)
            {
                if (COLUMN_NAME_PROTOCOL_LSID.equalsIgnoreCase(col.getName()))
                {
                    final ColumnInfo containerCol = getColumn(FieldKey.fromParts(COLUMN_NAME_CONTAINER));
                    final ColumnInfo protocolRunCol = getColumn(FieldKey.fromParts(COLUMN_NAME_PROTOCOL_RUN));

                    col.setLabel("Assay/Protocol");
                    col.setDisplayColumnFactory(new DisplayColumnFactory()
                    {
                        public DisplayColumn createRenderer(ColumnInfo colInfo)
                        {
                            return new ProtocolColumn(colInfo, containerCol, protocolRunCol);
                        }
                    });
                }
                else if (COLUMN_NAME_RUN_LSID.equalsIgnoreCase(col.getName()))
                {
                    final ColumnInfo containerCol = getColumn(FieldKey.fromParts(COLUMN_NAME_CONTAINER));
                    final ColumnInfo protocolRunCol = getColumn(FieldKey.fromParts(COLUMN_NAME_PROTOCOL_RUN));

                    col.setLabel("Run");
                    col.setDisplayColumnFactory(new DisplayColumnFactory()
                    {
                        public DisplayColumn createRenderer(ColumnInfo colInfo)
                        {
                            return new RunColumn(colInfo, containerCol, protocolRunCol);
                        }
                    });
                }
                else if (COLUMN_NAME_RUN_GROUP.equalsIgnoreCase(col.getName()))
                {
                    final ColumnInfo containerCol = getColumn(FieldKey.fromParts(COLUMN_NAME_CONTAINER));
                    final ColumnInfo protocolRunCol = getColumn(FieldKey.fromParts(COLUMN_NAME_PROTOCOL_RUN));

                    col.setLabel("Run Group");
                    col.setDisplayColumnFactory(new DisplayColumnFactory()
                    {
                        public DisplayColumn createRenderer(ColumnInfo colInfo)
                        {
                            return new RunGroupColumn(colInfo, containerCol, protocolRunCol);
                        }
                    });
                }
            }

            @Override
            public List<FieldKey> getDefaultVisibleColumns()
            {
                return defaultVisibleColumns;
            }
        };

        return table;
    }

    public static class ExperimentAuditEvent extends AuditTypeEvent
    {
        private String _protocolLsid;
        private String _runLsid;
        private String _protocolRun;
        private int _runGroup;

        public ExperimentAuditEvent()
        {
            super();
        }

        public ExperimentAuditEvent(String container, String comment)
        {
            super(EXPERIMENT_AUDIT_EVENT, container, comment);
        }

        public String getProtocolLsid()
        {
            return _protocolLsid;
        }

        public void setProtocolLsid(String protocolLsid)
        {
            _protocolLsid = protocolLsid;
        }

        public String getRunLsid()
        {
            return _runLsid;
        }

        public void setRunLsid(String runLsid)
        {
            _runLsid = runLsid;
        }

        public String getProtocolRun()
        {
            return _protocolRun;
        }

        public void setProtocolRun(String protocolRun)
        {
            _protocolRun = protocolRun;
        }

        public int getRunGroup()
        {
            return _runGroup;
        }

        public void setRunGroup(int runGroup)
        {
            _runGroup = runGroup;
        }
    }

    public static class ExperimentAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "ExperimentAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;
        private static final Set<PropertyStorageSpec> _fields = new LinkedHashSet<>();

        static {
            _fields.add(createFieldSpec(COLUMN_NAME_PROTOCOL_LSID, JdbcType.VARCHAR));
            _fields.add(createFieldSpec(COLUMN_NAME_RUN_LSID, JdbcType.VARCHAR));
            _fields.add(createFieldSpec(COLUMN_NAME_PROTOCOL_RUN, JdbcType.VARCHAR));
            _fields.add(createFieldSpec(COLUMN_NAME_RUN_GROUP, JdbcType.INTEGER));
        }

        @Override
        protected Set<PropertyStorageSpec> getColumns()
        {
            return _fields;
        }

        @Override
        protected String getNamespacePrefix()
        {
            return NAMESPACE_PREFIX;
        }

        @Override
        public String getKindName()
        {
            return NAME;
        }
    }
}
