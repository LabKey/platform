/*
 * Copyright (c) 2012-2013 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.CacheKey;
import org.labkey.api.data.Container;
import org.labkey.api.data.Entity;
import org.labkey.data.xml.externalSchema.TemplateSchemaType;
import org.labkey.query.QueryServiceImpl;

/**
 * Common entity for the shared fields of ExternalSchemaDef and LinkedSchemaDef.
 */
public abstract class AbstractExternalSchemaDef extends Entity
{
    public enum Column
    {
        schemaType,
        externalSchemaId,
        userschemaname
    }

    public enum SchemaType
    {
        external,
        linked
    }

    public static abstract class Key<T extends AbstractExternalSchemaDef> extends CacheKey<T, AbstractExternalSchemaDef.Column>
    {
        public Key(Class<T> clazz, @Nullable Container container)
        {
            super(QueryManager.get().getTableInfoExternalSchema(), clazz, container);
            addCondition(Column.schemaType, getSchemaType());
        }

        public abstract SchemaType getSchemaType();

        public void setExternalSchemaId(int id)
        {
            addCondition(Column.externalSchemaId, id);
        }

        public void setUserSchemaName(String name)
        {
            addCaseInsensitive(Column.userschemaname, name);
        }
    }

    public int _externalSchemaId;
    public String _userSchemaName;
    // The source schema name (a database-schema name for external schemas or a query-schema name for linked schemas)
    public String _sourceSchemaName;
    public String _metaData;
    // The data source name for external schemas, the source container id for linked schemas.
    public String _dataSource;
    public String _tables;

    // If defined in a module, the name of the schema definition otherwise null.
    private String _schemaTemplate;

    public AbstractExternalSchemaDef()
    {
    }

    public int getExternalSchemaId()
    {
        return _externalSchemaId;
    }

    public void setExternalSchemaId(int id)
    {
        _externalSchemaId = id;
    }

    public @NotNull String getUserSchemaName()
    {
        return _userSchemaName;
    }

    public void setUserSchemaName(@NotNull String name)
    {
        _userSchemaName = name;
    }

    public @Nullable String getSourceSchemaName()
    {
        return _sourceSchemaName;
    }

    public void setSourceSchemaName(@Nullable String name)
    {
        _sourceSchemaName = name;
    }

    public @Nullable String getMetaData()
    {
        return _metaData;
    }

    public void setMetaData(@Nullable String metaData)
    {
        _metaData = metaData;
    }

    public @NotNull String getDataSource()
    {
        return _dataSource;
    }

    public void setDataSource(@NotNull String dataSource)
    {
        _dataSource = dataSource;
    }

    public @Nullable String getTables()
    {
        return _tables;
    }

    public void setTables(@Nullable String tables)
    {
        _tables = tables;
    }

    public @Nullable String getSchemaTemplate()
    {
        return _schemaTemplate;
    }

    public void setSchemaTemplate(@Nullable String schemaTemplate)
    {
        _schemaTemplate = schemaTemplate;
    }

    public TemplateSchemaType lookupTemplate(Container sourceContainer)
    {
        if (_schemaTemplate == null)
            return null;

        return QueryServiceImpl.get().getSchemaTemplate(sourceContainer, _schemaTemplate);
    }

    public abstract boolean isEditable();

    public abstract boolean isIndexable();

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AbstractExternalSchemaDef that = (AbstractExternalSchemaDef) o;

        if (_externalSchemaId != that._externalSchemaId) return false;
        if (_dataSource != null ? !_dataSource.equals(that._dataSource) : that._dataSource != null) return false;
        if (_sourceSchemaName != null ? !_sourceSchemaName.equals(that._sourceSchemaName) : that._sourceSchemaName != null)
            return false;
        if (_metaData != null ? !_metaData.equals(that._metaData) : that._metaData != null) return false;
        if (_schemaTemplate != null ? !_schemaTemplate.equals(that._schemaTemplate) : that._schemaTemplate != null)
            return false;
        if (_tables != null ? !_tables.equals(that._tables) : that._tables != null) return false;
        if (_userSchemaName != null ? !_userSchemaName.equals(that._userSchemaName) : that._userSchemaName != null)
            return false;

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = _externalSchemaId;
        result = 31 * result + (_userSchemaName != null ? _userSchemaName.hashCode() : 0);
        result = 31 * result + (_sourceSchemaName != null ? _sourceSchemaName.hashCode() : 0);
        result = 31 * result + (_metaData != null ? _metaData.hashCode() : 0);
        result = 31 * result + (_dataSource != null ? _dataSource.hashCode() : 0);
        result = 31 * result + (_tables != null ? _tables.hashCode() : 0);
        result = 31 * result + (_schemaTemplate != null ? _schemaTemplate.hashCode() : 0);
        return result;
    }
}
