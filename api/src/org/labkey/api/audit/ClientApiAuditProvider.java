package org.labkey.api.audit;

import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.exp.property.DomainKind;

import java.util.Collections;
import java.util.Set;

/**
 * User: klum
 * Date: 7/21/13
 */
public class ClientApiAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    public static final String EVENT_TYPE = "Client API Actions";

    @Override
    protected DomainKind getDomainKind()
    {
        return new ClientApiAuditDomainKind();
    }

    @Override
    public String getEventName()
    {
        return EVENT_TYPE;
    }

    @Override
    public String getLabel()
    {
        return EVENT_TYPE;
    }

    @Override
    public String getDescription()
    {
        return "Information about audit events created through the client API.";
    }

    @Override
    public <K extends AuditTypeEvent> K convertEvent(AuditLogEvent event)
    {
        AuditTypeEvent bean = new AuditTypeEvent();
        copyStandardFields(bean, event);

        return (K)bean;
    }

    public static class ClientApiAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "ClientApiAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;

        @Override
        protected Set<PropertyStorageSpec> getColumns()
        {
            // UNDONE: We need to create hard-table columns -- should we give them names to match the purpose found in LABKEY.Utils.onError()?
            // key1: error type
            // key2: url
            // key3: stacktrace
            return Collections.emptySet();
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
