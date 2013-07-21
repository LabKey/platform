package org.labkey.announcements;

import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.util.MailHelper;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: 7/21/13
 */
public class MessageAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    @Override
    protected DomainKind getDomainKind()
    {
        return new MessageAuditDomainKind();
    }

    @Override
    public String getEventName()
    {
        return MailHelper.MESSAGE_AUDIT_EVENT;
    }

    @Override
    public String getLabel()
    {
        return "Message events";
    }

    @Override
    public String getDescription()
    {
        return "Message events";
    }

    @Override
    public <K extends AuditTypeEvent> K convertEvent(AuditLogEvent event)
    {
        MessageAuditEvent bean = new MessageAuditEvent();
        copyStandardFields(bean, event);

        bean.setFrom(event.getKey1());
        bean.setTo(event.getKey2());
        bean.setContentType(event.getKey3());

        return (K)bean;
    }

    public static class MessageAuditEvent extends AuditTypeEvent
    {
        private String _from;
        private String _to;
        private String _contentType;

        public MessageAuditEvent()
        {
            super();
        }

        public MessageAuditEvent(String container, String comment)
        {
            super(MailHelper.MESSAGE_AUDIT_EVENT, container, comment);
        }

        public String getFrom()
        {
            return _from;
        }

        public void setFrom(String from)
        {
            _from = from;
        }

        public String getTo()
        {
            return _to;
        }

        public void setTo(String to)
        {
            _to = to;
        }

        public String getContentType()
        {
            return _contentType;
        }

        public void setContentType(String contentType)
        {
            _contentType = contentType;
        }
    }

    public static class MessageAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "MessageAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;
        private static final Set<PropertyStorageSpec> _fields = new LinkedHashSet<>();

        static {
            _fields.add(createFieldSpec("From", JdbcType.VARCHAR));
            _fields.add(createFieldSpec("To", JdbcType.VARCHAR));
            _fields.add(createFieldSpec("ContentType", JdbcType.VARCHAR));
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
