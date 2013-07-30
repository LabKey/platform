package org.labkey.core.query;

import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.GroupManager;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * User: klum
 * Date: 7/17/13
 */
public class GroupAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{

    public static final String COLUMN_NAME_USER = "User";
    public static final String COLUMN_NAME_GROUP = "Group";

    @Override
    public String getEventName()
    {
        return GroupManager.GROUP_AUDIT_EVENT;
    }

    @Override
    public String getLabel()
    {
        return "Group events";
    }

    @Override
    public String getDescription()
    {
        return "Information about group modifications and security changes.";
    }

    @Override
    protected DomainKind getDomainKind()
    {
        return new GroupAuditDomainKind();
    }

    @Override
    public <K extends AuditTypeEvent> K convertEvent(AuditLogEvent event)
    {
        GroupAuditEvent bean = new GroupAuditEvent();
        copyStandardFields(bean, event);

        if (event.getIntKey1() != null)
            bean.setUser(event.getIntKey1());

        if (event.getIntKey2() != null)
            bean.setGroup(event.getIntKey2());

        return (K)bean;
    }

    @Override
    public Map<FieldKey, String> legacyNameMap()
    {
        Map<FieldKey, String> legacyNames = super.legacyNameMap();
        legacyNames.put(FieldKey.fromParts("intKey1"), COLUMN_NAME_USER);
        legacyNames.put(FieldKey.fromParts("intKey2"), COLUMN_NAME_GROUP);
        return legacyNames;
    }

    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>)GroupAuditEvent.class;
    }

    public static class GroupAuditEvent extends AuditTypeEvent
    {
        int _user;
        int _group;

        public GroupAuditEvent()
        {
            super();
        }

        public GroupAuditEvent(String container, String comment)
        {
            super(GroupManager.GROUP_AUDIT_EVENT, container, comment);
        }

        public int getUser()
        {
            return _user;
        }

        public void setUser(int user)
        {
            _user = user;
        }

        public int getGroup()
        {
            return _group;
        }

        public void setGroup(int group)
        {
            _group = group;
        }
    }

    public static class GroupAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "GroupAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;

        private static final Set<PropertyStorageSpec> _fields = new LinkedHashSet<>();

        static {
            _fields.add(createFieldSpec(COLUMN_NAME_USER, JdbcType.INTEGER));
            _fields.add(createFieldSpec(COLUMN_NAME_GROUP, JdbcType.INTEGER));
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
