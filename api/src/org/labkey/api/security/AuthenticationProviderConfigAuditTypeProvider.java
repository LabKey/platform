package org.labkey.api.security;

import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * User: tgaluhn
 * Date: 3/20/2015
 */
public class AuthenticationProviderConfigAuditTypeProvider extends AbstractAuditTypeProvider
{
    private static final String EVENT_TYPE = "AuthenticatoinProviderConfiguration";
    private static final String COLUMN_NAME_CHANGES = "Changes";

    static final List<FieldKey> DEFAULT_VISIBLE_COLUMNS = new ArrayList<>();

    static {

        DEFAULT_VISIBLE_COLUMNS.add(FieldKey.fromParts(COLUMN_NAME_CREATED));
        DEFAULT_VISIBLE_COLUMNS.add(FieldKey.fromParts(COLUMN_NAME_CREATED_BY));
        DEFAULT_VISIBLE_COLUMNS.add(FieldKey.fromParts(COLUMN_NAME_IMPERSONATED_BY));
        DEFAULT_VISIBLE_COLUMNS.add(FieldKey.fromParts(COLUMN_NAME_COMMENT));
        DEFAULT_VISIBLE_COLUMNS.add(FieldKey.fromParts(COLUMN_NAME_CHANGES));
    }

    @Override
    protected AbstractAuditDomainKind getDomainKind()
    {
        return new AuthProviderConfigAuditDomainKind();
    }

    @Override
    public String getEventName()
    {
        return EVENT_TYPE;
    }

    @Override
    public String getLabel()
    {
        return "Authentication Provider Configuration events";
    }

    @Override
    public String getDescription()
    {
        return "Displays information about modifications to the authentication provider configuration.";
    }

    @Override
    public <K extends AuditTypeEvent> K convertEvent(AuditLogEvent event)
    {
        throw new UnsupportedOperationException("Postdates migration, no need to convert");
    }

    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>)AuthProviderConfigAuditEvent.class;
    }

    @Override
    public TableInfo createTableInfo(UserSchema userSchema)
    {
        return new DefaultAuditTypeTable(this, getDomain(), userSchema)
        {
            @Override
            public List<FieldKey> getDefaultVisibleColumns()
            {
                return DEFAULT_VISIBLE_COLUMNS;
            }
        };
    }

    public static class AuthProviderConfigAuditEvent extends AuditTypeEvent
    {
        private String _changes;

        public AuthProviderConfigAuditEvent()
        {
            super();
        }

        public AuthProviderConfigAuditEvent(String container, String comment)
        {
            super(EVENT_TYPE, container, comment);
        }

        public String getChanges()
        {
            return _changes;
        }

        public void setChanges(String changes)
        {
            _changes = changes;
        }
    }


    public static class AuthProviderConfigAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "AuthenticationProviderConfigAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;

        private final Set<PropertyDescriptor> _fields;

        public AuthProviderConfigAuditDomainKind()
        {
            super(EVENT_TYPE);

            Set<PropertyDescriptor> fields = new LinkedHashSet<>();
            fields.add(createPropertyDescriptor(COLUMN_NAME_CHANGES, PropertyType.STRING));
            _fields = Collections.unmodifiableSet(fields);
        }

        @Override
        public Set<PropertyDescriptor> getProperties()
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
