/*
 * Copyright (c) 2010-2019 LabKey Corporation
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
package org.labkey.api.exp.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.TableChange;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.services.ServiceRegistry;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Creates and maintains "hard" tables in the underlying database based on dynamically configured data types.
 * Will do CREATE TABLE and ALTER TABLE statements to make sure the table has the right set of requested columns.
 * User: newton
 * Date: Aug 11, 2010
 */
public interface StorageProvisioner
{
    static StorageProvisioner get()
    {
        return ServiceRegistry.get().getService(StorageProvisioner.class);
    }

    static void setInstance(StorageProvisioner impl)
    {
        ServiceRegistry.get().registerService(StorageProvisioner.class, impl);
    }

    void drop(Domain domain);
    void addStorageProperties(Domain domain, Collection<PropertyStorageSpec> properties, boolean allowAddBaseProperty);

    @NotNull
    ColumnInfo getMvIndicatorColumn(TableInfo storageTable, PropertyDescriptor prop, String errMessage);

    /**
     * Return a TableInfo for this domain, creating if necessary. This method uses the DbSchema caching layer.
     */
    @NotNull
    static TableInfo createTableInfo(@NotNull Domain domain)
    {
        return get().createTableInfoImpl(domain);
    }

    /* NOTE: static createTable/createTableImpl is a very minor hack to avoid having to update a zillions repos at once */
    TableInfo createTableInfoImpl(@NotNull Domain domain);

    /**
     * This is really an internal method, use createTableInfo() in most scenarios
     * This is public to support upgrade scenarios only.
     */
    String ensureStorageTable(Domain domain, DomainKind<?> kind, DbScope scope);

    enum RequiredIndicesAction
    {
        Drop
            {
                @Override
                public void doOperation(Domain domain, SchemaTableInfo schemaTableInfo, Map<String, PropertyStorageSpec.Index> requiredIndicesMap)
                {
                    StorageProvisioner.get().dropNotRequiredIndices(domain, schemaTableInfo, requiredIndicesMap);
                }
            },
        Add
            {
                @Override
                public void doOperation(Domain domain, SchemaTableInfo schemaTableInfo, Map<String, PropertyStorageSpec.Index> requiredIndicesMap)
                {
                    StorageProvisioner.get().addMissingRequiredIndices(domain, schemaTableInfo, requiredIndicesMap);
                }
            };

        public abstract void doOperation(Domain domain, SchemaTableInfo schemaTableInfo, Map<String, PropertyStorageSpec.Index> requiredIndicesMap);
    }

    void dropNotRequiredIndices(Domain domain);
    void dropNotRequiredIndices(Domain domain, SchemaTableInfo schemaTableInfo, Map<String, PropertyStorageSpec.Index> requiredIndicesMap);
    void addMissingRequiredIndices(Domain domain);
    void addMissingRequiredIndices(Domain domain, SchemaTableInfo schemaTableInfo, Map<String, PropertyStorageSpec.Index> requiredIndicesMap);

    SchemaTableInfo getSchemaTableInfo(Domain domain);

    void addOrDropTableIndices(Domain domain, Set<PropertyStorageSpec.Index> indices, boolean doAdd, TableChange.IndexSizeMode sizeMode);

    /**
     * This helper can be used to update domain type if columns are added to DomainKind.getBaseProperties().
     * Only handles adding new properties.
     */
    void ensureBaseProperties(Domain domain);

    /**
     * We are mostly making the storage table match the existing property descriptors, because that is easiest.
     * Sometimes it would be better or more conservative to update the property descriptors instead
     */

    boolean repairDomain(Container c, String domainUri, BindException errors);

    default ProvisioningReport getProvisioningReport()
    {
        return getProvisioningReport(null);
    }

    ProvisioningReport getProvisioningReport(@Nullable String domainuri);

    class ProvisioningReport
    {
        private final Set<DomainReport> unprovisionedDomains = new HashSet<>();
        private final Set<DomainReport> provisionedDomains = new HashSet<>();
        private final List<String> globalErrors = new ArrayList<>();

        public void addUnprovisioned(DomainReport domain)
        {
            unprovisionedDomains.add(domain);
        }

        public void addProvisioned(DomainReport domain)
        {
            provisionedDomains.add(domain);
        }

        public Set<DomainReport> getUnprovisionedDomains()
        {
            return unprovisionedDomains;
        }

        public Set<DomainReport> getProvisionedDomains()
        {
            return provisionedDomains;
        }

        public void addGlobalError(String s)
        {
            globalErrors.add(s);
        }

        public List<String> getGlobalErrors()
        {
            return globalErrors;
        }

        public int getErrorCount()
        {
            int errors = globalErrors.size();
            for (DomainReport d : getProvisionedDomains())
            {
                errors += d.getErrors().size();
            }

            return errors;
        }

        public static class ColumnStatus
        {
            public String colName, mvColName;
            public DomainProperty prop;            // propertydescriptor column
            public PropertyStorageSpec spec;       // domainkind/reserved column
            public boolean hasProblem;
            public String fix = "";
            public String getName()
            {
                if (null != prop) return prop.getName();
                if (null != spec) return spec.getName();
                return null;
            }
            public boolean hasMv()
            {
                if (null != prop) return prop.isMvEnabled();
                if (null != spec) return spec.isMvEnabled();
                return false;
            }
        }

        public static class DomainReport
        {
            Integer _id;
            String _name;
            String _schemaName;
            String _tableName;

            final List<String> _errors = new ArrayList<>();
            final List<ColumnStatus> _columns = new ArrayList<>();

            public Integer getId()
            {
                return _id;
            }

            public void setId(Integer id)
            {
                _id = id;
            }

            public String getName()
            {
                return _name;
            }

            public void setName(String name)
            {
                _name = name;
            }

            public String getSchemaName()
            {
                return _schemaName;
            }

            public void setSchemaName(String schemaName)
            {
                _schemaName = schemaName;
            }

            public String getTableName()
            {
                return _tableName;
            }

            public void setTableName(String tableName)
            {
                _tableName = tableName;
            }

            public void addError(String message)
            {
                _errors.add(message);
            }

            public List<String> getErrors()
            {
                return _errors;
            }

            public List<ColumnStatus> getColumns()
            {
                return _columns;
            }
        }
    }
}
