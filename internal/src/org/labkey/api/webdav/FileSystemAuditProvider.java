package org.labkey.api.webdav;

import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.exp.property.DomainKind;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: 7/19/13
 */
public class FileSystemAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    public static final String EVENT_TYPE = "FileSystem";

    @Override
    protected DomainKind getDomainKind()
    {
        return new FileSystemAuditDomainKind();
    }

    @Override
    public String getEventName()
    {
        return EVENT_TYPE;
    }

    @Override
    public String getLabel()
    {
        return "File events";
    }

    @Override
    public String getDescription()
    {
        return "Displays information about file uploads and modifications.";
    }

    @Override
    public <K extends AuditTypeEvent> K convertEvent(AuditLogEvent event)
    {
        FileSystemAuditEvent bean = new FileSystemAuditEvent();
        copyStandardFields(bean, event);

        bean.setDirectory(event.getKey1());
        bean.setFile(event.getKey2());

        return (K)bean;
    }

    public static class FileSystemAuditEvent extends AuditTypeEvent
    {
        private String _directory;      // the directory name
        private String _file;           // the file name

        public FileSystemAuditEvent()
        {
            super();
        }

        public FileSystemAuditEvent(String container, String comment)
        {
            super(EVENT_TYPE, container, comment);
        }

        public String getDirectory()
        {
            return _directory;
        }

        public void setDirectory(String directory)
        {
            _directory = directory;
        }

        public String getFile()
        {
            return _file;
        }

        public void setFile(String file)
        {
            _file = file;
        }
    }

    public static class FileSystemAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "FileSystemAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;
        private static final Set<PropertyStorageSpec> _fields = new LinkedHashSet<>();

        static {
            _fields.add(createFieldSpec("Directory", JdbcType.VARCHAR));
            _fields.add(createFieldSpec("File", JdbcType.VARCHAR));
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
