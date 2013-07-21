package org.labkey.core.query;

import org.labkey.api.attachments.AttachmentService;
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
 * Date: 7/19/13
 */
public class AttachmentAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    @Override
    protected DomainKind getDomainKind()
    {
        return new AttachmentAuditDomainKind();
    }

    @Override
    public String getEventName()
    {
        return AttachmentService.ATTACHMENT_AUDIT_EVENT;
    }

    @Override
    public String getLabel()
    {
        return "Attachment events";
    }

    @Override
    public String getDescription()
    {
        return "Displays information about attachment events.";
    }

    @Override
    public <K extends AuditTypeEvent> K convertEvent(AuditLogEvent event)
    {
        AttachmentAuditEvent bean = new AttachmentAuditEvent();
        copyStandardFields(bean, event);

        bean.setAttachment(event.getKey1());

        return (K)bean;
    }

    public static class AttachmentAuditEvent extends AuditTypeEvent
    {
        private String _attachment;     // the attachment name

        public AttachmentAuditEvent()
        {
            super();
        }

        public AttachmentAuditEvent(String container, String comment)
        {
            super(AttachmentService.ATTACHMENT_AUDIT_EVENT, container, comment);
        }

        public String getAttachment()
        {
            return _attachment;
        }

        public void setAttachment(String attachment)
        {
            _attachment = attachment;
        }
    }

    public static class AttachmentAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "AttachmentAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;

        private static final Set<PropertyStorageSpec> _fields = new LinkedHashSet<>();

        static {
            _fields.add(createFieldSpec("Attachment", JdbcType.VARCHAR));
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
