package org.labkey.api.exp.property;

import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: 7/21/13
 */
public class DomainAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
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
