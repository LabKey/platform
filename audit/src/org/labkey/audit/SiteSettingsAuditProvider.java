package org.labkey.audit;

import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.settings.WriteableAppProps;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: 7/21/13
 */
public class SiteSettingsAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    @Override
    protected DomainKind getDomainKind()
    {
        return new SiteSettingsAuditDomainKind();
    }

    @Override
    public String getEventName()
    {
        return WriteableAppProps.AUDIT_EVENT_TYPE;
    }

    @Override
    public String getLabel()
    {
        return "Site Settings events";
    }

    @Override
    public String getDescription()
    {
        return "Displays information about modifications to the site settings.";
    }

    @Override
    public <K extends AuditTypeEvent> K convertEvent(AuditLogEvent event)
    {
        AuditTypeEvent bean = new AuditTypeEvent();
        copyStandardFields(bean, event);

        return (K)bean;
    }

    public static class SiteSettingsAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "SiteSettingsAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;
        private static final Set<PropertyStorageSpec> _fields = new LinkedHashSet<>();

        static {
            _fields.add(createFieldSpec("Changes", JdbcType.VARCHAR));
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
