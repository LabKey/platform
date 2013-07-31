package org.labkey.api.webdav;

import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: klum
 * Date: 7/19/13
 */
public class FileSystemAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    public static final String EVENT_TYPE = "FileSystem";

    public static final String COLUMN_NAME_DIRECTORY = "Directory";
    public static final String COLUMN_NAME_FILE = "File";

    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static {

        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_IMPERSONATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_DIRECTORY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_FILE));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_COMMENT));
    }

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

    @Override
    public Map<FieldKey, String> legacyNameMap()
    {
        Map<FieldKey, String> legacyNames = super.legacyNameMap();
        legacyNames.put(FieldKey.fromParts("key1"), COLUMN_NAME_DIRECTORY);
        legacyNames.put(FieldKey.fromParts("key2"), COLUMN_NAME_FILE);
        return legacyNames;
    }

    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>)FileSystemAuditEvent.class;
    }

    @Override
    public TableInfo createTableInfo(UserSchema userSchema)
    {
        Domain domain = getDomain();
        DbSchema dbSchema =  DbSchema.get(SCHEMA_NAME);

        DefaultAuditTypeTable table = new DefaultAuditTypeTable(this, domain, dbSchema, userSchema)
        {
            @Override
            public List<FieldKey> getDefaultVisibleColumns()
            {
                return defaultVisibleColumns;
            }
        };

        return table;
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
            _fields.add(createFieldSpec(COLUMN_NAME_DIRECTORY, JdbcType.VARCHAR));
            _fields.add(createFieldSpec(COLUMN_NAME_FILE, JdbcType.VARCHAR));
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
