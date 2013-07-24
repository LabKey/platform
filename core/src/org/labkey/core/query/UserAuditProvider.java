package org.labkey.core.query;

import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.security.UserManager;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * User: klum
 * Date: 7/9/13
 */
public class UserAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    @Override
    public String getEventName()
    {
        return UserManager.USER_AUDIT_EVENT;
    }

    @Override
    public String getLabel()
    {
        return "User events";
    }

    @Override
    public String getDescription()
    {
        return "Describes information about user logins, impersonations, and modifications.";
    }

    @Override
    protected DomainKind getDomainKind()
    {
        return new UserAuditDomainKind();
    }

    @Override
    public <K extends AuditTypeEvent> K convertEvent(AuditLogEvent event)
    {
        UserManager.UserAuditEvent bean = new UserManager.UserAuditEvent();
        copyStandardFields(bean, event);

        if (event.getIntKey1() != null)
            bean.setUser(event.getIntKey1());

        return (K)bean;
    }

    @Override
    public Map<String, String> legacyNameMap()
    {
        Map<String, String> legacyNames = super.legacyNameMap();
        legacyNames.put("intKey1", "User");
        return legacyNames;
    }

    public static class UserAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "UserAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;

        private static final Set<PropertyStorageSpec> _fields = new LinkedHashSet<>();

        static {
            _fields.add(createFieldSpec("User", JdbcType.INTEGER));
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
