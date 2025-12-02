/*
 * Copyright (c) 2015-2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.audit.provider;

import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.TransactionAuditProvider;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.data.Container;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.query.FieldKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
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
    public static final String COLUMN_NAME_PROVIDED_FILE = "ProvidedFileName";
    public static final String COLUMN_NAME_FIELD_NAME = "FieldName";
    public static final String COLUMN_NAME_RESOURCE_PATH = "ResourcePath";

    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static {

        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_IMPERSONATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_DIRECTORY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_FILE));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_PROVIDED_FILE));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_FIELD_NAME));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_COMMENT));
    }

    public FileSystemAuditProvider()
    {
        super(new FileSystemAuditDomainKind());
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
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>)FileSystemAuditEvent.class;
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }

    public static class FileSystemAuditEvent extends AuditTypeEvent
    {
        private String _directory;      // the directory name
        private String _file;           // the file name
        private String _resourcePath;   // the webdav resource path
        private String _providedFileName;   // the name of the file as provided by the user, before renaming to make it unique and/or legal
        private String _fieldName;      // name of the field associated with the file, if any

        /** Important for reflection-based instantiation */
        public FileSystemAuditEvent()
        {
            super();
            setEventType(EVENT_TYPE);
        }

        public FileSystemAuditEvent(Container container, String comment)
        {
            super(EVENT_TYPE, container, comment);
            setTransactionEvent(TransactionAuditProvider.getCurrentTransactionAuditEvent(), EVENT_TYPE);
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

        public String getResourcePath()
        {
            return _resourcePath;
        }

        public void setResourcePath(String resourcePath)
        {
            _resourcePath = resourcePath;
        }

        public String getProvidedFileName()
        {
            return _providedFileName;
        }

        public void setProvidedFileName(String providedFileName)
        {
            _providedFileName = providedFileName;
        }

        public String getFieldName()
        {
            return _fieldName;
        }

        public void setFieldName(String fieldName)
        {
            _fieldName = fieldName;
        }

        @Override
        public Map<String, Object> getAuditLogMessageElements()
        {
            Map<String, Object> elements = new LinkedHashMap<>();
            elements.put("directory", getDirectory());
            elements.put("file", getFile());
            elements.put("resourcePath", getResourcePath());
            elements.put("providedFileName", getProvidedFileName());
            elements.put("fieldName", getFieldName());
            elements.put("transactionId", getTransactionId());
            elements.putAll(super.getAuditLogMessageElements());
            return elements;
        }
    }

    public static class FileSystemAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "FileSystemAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;

        private final Set<PropertyDescriptor> _fields;

        public FileSystemAuditDomainKind()
        {
            super(EVENT_TYPE);

            Set<PropertyDescriptor> fields = new LinkedHashSet<>();
            fields.add(createPropertyDescriptor(COLUMN_NAME_DIRECTORY, PropertyType.STRING));
            fields.add(createPropertyDescriptor(COLUMN_NAME_FILE, PropertyType.STRING));
            fields.add(createPropertyDescriptor(COLUMN_NAME_PROVIDED_FILE, PropertyType.STRING));
            fields.add(createPropertyDescriptor(COLUMN_NAME_FIELD_NAME, PropertyType.STRING));
            fields.add(createPropertyDescriptor(COLUMN_NAME_RESOURCE_PATH, PropertyType.STRING));
            fields.add(createPropertyDescriptor(COLUMN_NAME_TRANSACTION_ID, PropertyType.BIGINT));
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
