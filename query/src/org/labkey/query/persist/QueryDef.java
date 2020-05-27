/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.query.persist;

import org.apache.commons.lang3.StringUtils;
import org.apache.xmlbeans.XmlError;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Entity;
import org.labkey.api.query.MetadataParseException;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.data.xml.TablesDocument;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class QueryDef extends Entity implements Cloneable
{
    public QueryDef()
    {
        assert MemTracker.getInstance().put(this);
    }

    private int _queryDefId;
    private String _sql;
    // Hold the ParsedMetadata reference here so that it can be shared across wrappers of this QueryDef
    @NotNull
    private ParsedMetadata _parsedMetadata = EMPTY_METADATA;
    private double _schemaVersion;
    private int _flags;
    private String _name;
    private String _description;
    private SchemaKey _schema;

    public int getQueryDefId()
    {
        return _queryDefId;
    }

    public void setQueryDefId(int id)
    {
        _queryDefId = id;
    }

    public String getSql()
    {
        return _sql;
    }
    public void setSql(String sql)
    {
        _sql = sql;
    }
    public String getMetaData()
    {
        return _parsedMetadata._xml;
    }
    public void setMetaData(String tableInfo)
    {
        _parsedMetadata = new ParsedMetadata(tableInfo);
    }
    public double getSchemaVersion()
    {
        return _schemaVersion;
    }
    public void setSchemaVersion(double schemaVersion)
    {
        _schemaVersion = schemaVersion;
    }
    public int getFlags()
    {
        return _flags;
    }
    public void setFlags(int flags)
    {
        _flags = flags;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getSchema()
    {
        return _schema.toString();
    }

    public void setSchema(String schema)
    {
        _schema = SchemaKey.fromString(schema);
    }

    public SchemaKey getSchemaPath()
    {
        return _schema;
    }

    public void setSchemaPath(SchemaKey schemaName)
    {
        _schema = schemaName;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public String toString()
    {
        return getName() + ": " + getSql() + " -- " + getDescription();
    }

    @Override
    public QueryDef clone()
    {
        try
        {
            return (QueryDef) super.clone();
        }
        catch (CloneNotSupportedException cnse)
        {
            throw UnexpectedException.wrap(cnse);
        }
    }

    @NotNull
    public ParsedMetadata getParsedMetadata()
    {
        return _parsedMetadata;
    }

    public void setParsedMetadata(@NotNull ParsedMetadata parsedMetadata)
    {
        _parsedMetadata = parsedMetadata;
    }

    private static final ParsedMetadata EMPTY_METADATA = new ParsedMetadata(null);

    public static ParsedMetadata createParsedMetadata(String xml)
    {
        return StringUtils.trimToNull(xml) == null ? EMPTY_METADATA : new ParsedMetadata(xml);
    }

    /** Wrapper around the XML metadata. Lazily parses into an XML Bean and caches the result */
    public static class ParsedMetadata
    {
        @Nullable
        private final String _xml;
        @Nullable
        private TablesDocument _doc;
        private List<QueryException> _errors = Collections.emptyList();

        public ParsedMetadata(@Nullable String xml)
        {
            _xml = StringUtils.trimToNull(xml);
        }

        @Nullable
        public String getXml()
        {
            return _xml;
        }

        @Nullable
        public TablesDocument getTablesDocument(Collection<QueryException> errors)
        {
            if (_doc == null && _xml != null)
            {
                XmlOptions options = XmlBeansUtil.getDefaultParseOptions();
                List<XmlError> xmlErrors = new ArrayList<>();
                List<QueryException> localErrors = new ArrayList<>();
                options.setErrorListener(xmlErrors);
                try
                {
                    _doc = TablesDocument.Factory.parse(_xml, options);
                }
                catch (XmlException e)
                {
                    localErrors.add(new MetadataParseException(XmlBeansUtil.getErrorMessage(e)));
                }
                for (XmlError xmle : xmlErrors)
                {
                    localErrors.add(new MetadataParseException(XmlBeansUtil.getErrorMessage(xmle)));
                }
                _errors = Collections.unmodifiableList(localErrors);
            }

            errors.addAll(_errors);
            return _doc;
        }
    }

}
