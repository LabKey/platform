package org.labkey.list.model;

import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.exp.property.DomainKind;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: 7/21/13
 */
public class ListAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    @Override
    protected DomainKind getDomainKind()
    {
        return new ListAuditDomainKind();
    }

    @Override
    public String getEventName()
    {
        return ListManager.LIST_AUDIT_EVENT;
    }

    @Override
    public String getLabel()
    {
        return "List events";
    }

    @Override
    public String getDescription()
    {
        return "List events";
    }

    @Override
    public <K extends AuditTypeEvent> K convertEvent(AuditLogEvent event)
    {
        ListAuditEvent bean = new ListAuditEvent();
        copyStandardFields(bean, event);

        if (event.getIntKey1() != null)
            bean.setListId(event.getIntKey1());

        bean.setListDomainUri(event.getKey1());
        bean.setListItemEntityId(event.getKey2());
        bean.setListName(event.getKey3());

        return (K)bean;
    }

    public static class ListAuditEvent extends AuditTypeEvent
    {
        private int _listId;
        private String _listDomainUri;
        private String _listItemEntityId;
        private String _listName;
        private String _oldRecord;
        private String _newRecord;

        public ListAuditEvent()
        {
            super();
        }

        public ListAuditEvent(String container, String comment)
        {
            super(ListManager.LIST_AUDIT_EVENT, container, comment);
        }

        public int getListId()
        {
            return _listId;
        }

        public void setListId(int listId)
        {
            _listId = listId;
        }

        public String getListDomainUri()
        {
            return _listDomainUri;
        }

        public void setListDomainUri(String listDomainUri)
        {
            _listDomainUri = listDomainUri;
        }

        public String getListItemEntityId()
        {
            return _listItemEntityId;
        }

        public void setListItemEntityId(String listItemEntityId)
        {
            _listItemEntityId = listItemEntityId;
        }

        public String getListName()
        {
            return _listName;
        }

        public void setListName(String listName)
        {
            _listName = listName;
        }

        public String getOldRecord()
        {
            return _oldRecord;
        }

        public void setOldRecord(String oldRecord)
        {
            _oldRecord = oldRecord;
        }

        public String getNewRecord()
        {
            return _newRecord;
        }

        public void setNewRecord(String newRecord)
        {
            _newRecord = newRecord;
        }
    }

    public static class ListAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "ListAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;
        private static final Set<PropertyStorageSpec> _fields = new LinkedHashSet<>();

        static {
            _fields.add(createFieldSpec("ListId", JdbcType. INTEGER));
            _fields.add(createFieldSpec("ListDomainUri", JdbcType.VARCHAR));
            _fields.add(createFieldSpec("ListItemEntityId", JdbcType.VARCHAR));
            _fields.add(createFieldSpec("ListName", JdbcType.VARCHAR));
            _fields.add(createFieldSpec(OLD_RECORD_PROP_NAME, JdbcType.VARCHAR));
            _fields.add(createFieldSpec(NEW_RECORD_PROP_NAME, JdbcType.VARCHAR));
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
