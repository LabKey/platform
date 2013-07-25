package org.labkey.api.exp.property;

import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: 7/21/13
 */
public class DomainAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static {

        defaultVisibleColumns.add(FieldKey.fromParts("Created"));
        defaultVisibleColumns.add(FieldKey.fromParts("CreatedBy"));
        defaultVisibleColumns.add(FieldKey.fromParts("ImpersonatedBy"));
        defaultVisibleColumns.add(FieldKey.fromParts("ProjectId"));
        defaultVisibleColumns.add(FieldKey.fromParts("DomainUri"));
        defaultVisibleColumns.add(FieldKey.fromParts("Comment"));
    }

    public static final String DOMAIN_AUDIT_EVENT = "DomainAuditEvent";

    @Override
    protected DomainKind getDomainKind()
    {
        return new DomainAuditDomainKind();
    }

    @Override
    public String getEventName()
    {
        return DOMAIN_AUDIT_EVENT;
    }

    @Override
    public String getLabel()
    {
        return "Domain events";
    }

    @Override
    public String getDescription()
    {
        return "Domain events";
    }

    @Override
    public TableInfo createTableInfo(UserSchema userSchema)
    {
        Domain domain = getDomain();
        DbSchema dbSchema =  DbSchema.get(SCHEMA_NAME);

        return new DefaultAuditTypeTable(this, domain, dbSchema, userSchema)
        {
            @Override
            protected void initColumn(ColumnInfo col)
            {
                if ("domainuri".equalsIgnoreCase(col.getName()))
                {
                    final ColumnInfo container = getColumn(FieldKey.fromParts("Container"));
                    final ColumnInfo name = getColumn(FieldKey.fromParts("DomainName"));

                    col.setLabel("Domain");
                    col.setDisplayColumnFactory(new DisplayColumnFactory()
                    {
                        public DisplayColumn createRenderer(ColumnInfo colInfo)
                        {
                            return new DomainAuditViewFactory.DomainColumn(colInfo, container, name);
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
    }

    @Override
    public <K extends AuditTypeEvent> K convertEvent(AuditLogEvent event)
    {
        DomainAuditEvent bean = new DomainAuditEvent();
        copyStandardFields(bean, event);

        bean.setDomainUri(event.getKey1());
        bean.setDomainName(event.getKey3());

        return (K)bean;
    }

    public static class DomainAuditEvent extends AuditTypeEvent
    {
        private String _domainUri;
        private String _domainName;

        public DomainAuditEvent()
        {
            super();
        }

        public DomainAuditEvent(String container, String comment)
        {
            super(DOMAIN_AUDIT_EVENT, container, comment);
        }

        public String getDomainUri()
        {
            return _domainUri;
        }

        public void setDomainUri(String domainUri)
        {
            _domainUri = domainUri;
        }

        public String getDomainName()
        {
            return _domainName;
        }

        public void setDomainName(String domainName)
        {
            _domainName = domainName;
        }
    }

    public static class DomainAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "DomainAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;
        private static final Set<PropertyStorageSpec> _fields = new LinkedHashSet<>();

        static {
            _fields.add(createFieldSpec("DomainUri", JdbcType.VARCHAR));
            _fields.add(createFieldSpec("DomainName", JdbcType.VARCHAR));
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
