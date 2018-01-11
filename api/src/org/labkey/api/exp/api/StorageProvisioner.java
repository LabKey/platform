/*
 * Copyright (c) 2010-2017 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.CaseInsensitiveMapWrapper;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.*;
import org.labkey.api.data.DbScope.SchemaTableOptions;
import org.labkey.api.data.DbScope.Transaction;
import org.labkey.api.data.TableChange.ChangeType;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.exceptions.TableNotFoundException;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.test.TestTimeout;
import org.labkey.api.util.CPUTimer;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.view.ActionURL;
import org.springframework.validation.BindException;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Creates and maintains "hard" tables in the underlying database based on dynamically configured data types.
 * Will do CREATE TABLE and ALTER TABLE statements to make sure the table has the right set of requested columns.
 * User: newton
 * Date: Aug 11, 2010
 */
public class StorageProvisioner
{
    private static final Logger log = Logger.getLogger(StorageProvisioner.class);
    private static final CPUTimer create = new CPUTimer("StorageProvisioner.create");
    private static boolean allowRenameOfColumnsDuringUpgrade = false;  // TODO: Remove this? No longer used because dataset migration code is gone.

    private static String _create(DbScope scope, DomainKind kind, Domain domain)
    {
        assert create.start();

        try (Transaction transaction = scope.ensureTransaction())
        {
            // reselect in a transaction
            DomainDescriptor dd = OntologyManager.getDomainDescriptor(domain.getTypeId());
            if (null == dd)
            {
                Logger.getLogger(StorageProvisioner.class).warn("Can't find domain descriptor: " + domain.getTypeId() + " " + domain.getTypeURI());
                transaction.commit();
                return null;
            }
            String tableName = dd.getStorageTableName();
            if (null != tableName)
            {
                transaction.commit();
                return tableName;
            }

            tableName = makeTableName(kind, domain);
            TableChange change = new TableChange(domain, ChangeType.CreateTable, tableName);

            Set<String> base = Sets.newCaseInsensitiveHashSet();

            for (PropertyStorageSpec spec : kind.getBaseProperties(domain))
            {
                change.addColumn(spec);
                base.add(spec.getName());
            }

            for (DomainProperty property : domain.getProperties())
            {
                if (base.contains(property.getName()))
                {
                    // apparently this is a case where the domain allows a propertydescriptor to be defined with the same
                    // name as a built-in column. e.g. to allow setting overrides?
                    if (!kind.hasPropertiesIncludeBaseProperties())
                        log.info("StorageProvisioner ignored property with name of built-in column: " + property.getPropertyURI());
                    continue;
                }

                PropertyStorageSpec spec = kind.getPropertySpec(property.getPropertyDescriptor(), domain);
                if (null != spec)
                {
                    change.addColumn(spec);
                }
                if (property.isMvEnabled())
                {
                    change.addColumn(makeMvColumn(property));
                }
            }

            List<PropertyStorageSpec.Index> indices = new ArrayList<>();
            indices.addAll(kind.getPropertyIndices(domain));
            indices.addAll(domain.getPropertyIndices());
            change.setIndexedColumns(indices);

            change.setForeignKeys(domain.getPropertyForeignKeys());

            change.execute();

            DomainDescriptor editDD = dd.edit()
                    .setStorageTableName(tableName)
                    .setStorageSchemaName(kind.getStorageSchemaName())
                    .build();

            OntologyManager.updateDomainDescriptor(editDD);

            kind.invalidate(domain);

            transaction.commit();

            return tableName;
        }
        finally
        {
            assert create.stop();
        }
    }

    private static PropertyStorageSpec makeMvColumn(DomainProperty property)
    {
        return makeMvColumn(new PropertyStorageSpec(property.getPropertyDescriptor()));
    }

    private static PropertyStorageSpec makeMvColumn(PropertyStorageSpec property)
    {
        return new PropertyStorageSpec(property.getName() + "_" + MvColumn.MV_INDICATOR_SUFFIX, JdbcType.VARCHAR, 50);
    }

    @NotNull
    private static PropertyStorageSpec getPropStorageSpecForMvColumn(TableInfo storageTable, PropertyDescriptor mainProp, String errMessage)
    {
        ColumnInfo mvColumn = getMvIndicatorColumn(storageTable, mainProp, errMessage);
        return new PropertyStorageSpec(mvColumn.getName(), mvColumn.getJdbcType(), mvColumn.getScale());
    }

    public static void drop(Domain domain)
    {
        if (null == domain)
            return;
        DomainKind kind = domain.getDomainKind();
        if (kind == null)
        {
            if (null != domain.getStorageTableName())
                log.warn("Domain " + domain.getName() + " has no DomainKind, it cannot be dropped. URI: " + domain.getTypeURI(), new IllegalStateException());
            return;
        }

        DbScope scope = kind.getScope();
        String schemaName = kind.getStorageSchemaName();
        if (scope == null || schemaName == null)
            return;

        String tableName = domain.getStorageTableName();
        if (null == tableName)
            return;

        if (scope.getSqlDialect().isTableExists(scope, schemaName, tableName))
        {
            TableChange change = new TableChange(domain, ChangeType.DropTable);

            try (Transaction transaction = scope.ensureTransaction())
            {
                change.execute();
                transaction.commit();
            }
            catch (RuntimeSQLException e)
            {
                log.warn(String.format("Failed to drop table in schema %s for domain %s - %s", schemaName, domain.getName(), e.getMessage()), e);
                throw e;
            }
        }
        else
        {
            log.warn(String.format("Table %s in schema %s for domain %s does not exist. Ignoring drop.", tableName, schemaName, domain.getName()));
        }
    }

    public static void addStorageProperties(Domain domain, Collection<PropertyStorageSpec> properties, boolean allowAddBaseProperty)
    {
        DomainKind kind = domain.getDomainKind();
        DbScope scope = kind.getScope();

        // should be in a transaction
        assert scope.isTransactionActive();

        TableChange change = new TableChange(domain, ChangeType.AddColumns);

        Set<String> base = Sets.newCaseInsensitiveHashSet();
        for (PropertyStorageSpec s : kind.getBaseProperties(domain))
            base.add(s.getName());

        for (PropertyStorageSpec prop : properties)
        {
            if (prop.getName() == null || prop.getName().length() == 0)
                throw new IllegalArgumentException("Can't add property with no name.");

            if (!allowAddBaseProperty && base.contains(prop.getName()))
            {
                // apparently this is a case where the domain allows a propertydescriptor to be defined with the same
                // name as a built-in column. e.g. to allow setting overrides?
                log.warn("StorageProvisioner ignored property with name of built-in column: " + prop.getName());
                continue;
            }

            change.addColumn(prop);

            if (prop.isMvEnabled())
            {
                change.addColumn(makeMvColumn(prop));
            }
        }

        change.execute();
    }


    public static void addProperties(Domain domain, Collection<DomainProperty> properties, boolean allowAddBaseProperty)
    {
        DomainKind kind = domain.getDomainKind();
        DbScope scope = kind.getScope();

        // should be in a transaction with propertydescriptor changes
        assert scope.isTransactionActive();

        String tableName = domain.getStorageTableName();
        if (null == tableName)
        {
            tableName = _create(scope, kind, domain);
            return;
        }

        TableChange change = new TableChange(domain, ChangeType.AddColumns);

        Set<String> base = Sets.newCaseInsensitiveHashSet();
        for (PropertyStorageSpec s : kind.getBaseProperties(domain))
            base.add(s.getName());

        int changeCount = 0;
        for (DomainProperty prop : properties)
        {
            if (prop.getName() == null || prop.getName().length() == 0)
                throw new IllegalArgumentException("Can't add property with no name: " + prop.getPropertyURI());

            if (!allowAddBaseProperty && base.contains(prop.getName()))
            {
                // apparently this is a case where the domain allows a propertydescriptor to be defined with the same
                // name as a built-in column. e.g. to allow setting overrides?
                log.warn("StorageProvisioner ignored property with name of built-in column: " + prop.getPropertyURI());
                continue;
            }
            PropertyStorageSpec spec = kind.getPropertySpec(prop.getPropertyDescriptor(), domain);
            if (null != spec)
            {
                change.addColumn(spec);
                changeCount++;
            }
            if (prop.isMvEnabled())
            {
                change.addColumn(makeMvColumn(prop));
                changeCount++;
            }
        }

        change.execute();
    }

    public static void addOrDropConstraints(Domain domain, Collection<Constraint> constraints, boolean toAdd)
    {
        assert getScope(domain).isTransactionActive() : "should be in a transaction with propertydescriptor changes";

        if (domain.getStorageTableName() == null)
        {
            throw new IllegalStateException("No storage table name set for domain: " + domain.getTypeURI());
        }

        TableChange change = new TableChange(domain, (toAdd?ChangeType.AddConstraints:ChangeType.DropConstraints));
        change.setConstraints(constraints);

        change.execute();
    }

    public static void dropMvIndicator(DomainProperty prop, PropertyDescriptor pd)
    {
        Domain domain = prop.getDomain();

        // should be in a transaction with propertydescriptor changes
        assert getScope(domain).isTransactionActive();

        TableChange change = new TableChange(domain, ChangeType.DropColumns);
        TableInfo storageTable = DbSchema.get(domain.getDomainKind().getStorageSchemaName(), DbSchemaType.Provisioned).getTable(domain.getStorageTableName());

        change.addColumn(getPropStorageSpecForMvColumn(storageTable, pd,
                                                       "No MV column found for '" + pd.getName() + "' in table '" + domain.getName() + "'"));

        change.execute();
    }

    public static void addMvIndicator(DomainProperty prop)
    {
        Domain domain = prop.getDomain();
        DomainKind kind = domain.getDomainKind();
        DbScope scope = kind.getScope();

        // should be in a transaction with propertydescriptor changes
        assert scope.isTransactionActive();

        String tableName = domain.getStorageTableName();
        if (null == tableName)
            tableName = makeTableName(kind, domain);

        TableChange change = new TableChange(domain, ChangeType.AddColumns, tableName);

        change.addColumn(makeMvColumn(prop));

        change.execute();
    }

    public static void dropStorageProperties(Domain domain, Collection<PropertyStorageSpec> properties)
    {
        DomainKind kind = domain.getDomainKind();
        DbScope scope = kind.getScope();

        assert scope.isTransactionActive() : "should be in a transaction with propertydescriptor changes";

        if (domain.getStorageTableName() == null)
        {
            throw new IllegalStateException("No storage table name set for domain: " + domain.getTypeURI());
        }

        TableChange change = new TableChange(domain, ChangeType.DropColumns);

        for (PropertyStorageSpec prop : properties)
        {
            change.addColumn(prop);
        }

        change.execute();
    }

    public static void dropProperties(Domain domain, Collection<DomainProperty> properties)
    {
        DomainKind kind = domain.getDomainKind();
        DbScope scope = kind.getScope();

        assert scope.isTransactionActive() : "should be in a transaction with propertydescriptor changes";

        Set<String> base = Sets.newCaseInsensitiveHashSet();
        for (PropertyStorageSpec s : kind.getBaseProperties(domain))
            base.add(s.getName());

        if (domain.getStorageTableName() == null)
        {
            throw new IllegalStateException("No storage table name set for domain: " + domain.getTypeURI());
        }

        TableChange change = new TableChange(domain, ChangeType.DropColumns);

        for (DomainProperty prop : properties)
        {
            if (base.contains(prop.getName()))
                continue;
            change.addColumn(prop.getPropertyDescriptor());
            if (prop.isMvEnabledForDrop())
            {
                change.addColumn(makeMvColumn(prop));
            }
        }

        change.execute();
    }

    public static void renameProperty(Domain domain, DomainProperty domainProperty, PropertyDescriptor oldPropDescriptor, boolean mvDropped)
    {
        DomainKind kind = domain.getDomainKind();
        DbScope scope = kind.getScope();

        // should be in a transaction with propertydescriptor changes
        assert scope.isTransactionActive();

        TableChange renamePropChange = new TableChange(domain, ChangeType.RenameColumns);

        Set<String> base = Sets.newCaseInsensitiveHashSet();
        for (PropertyStorageSpec s : kind.getBaseProperties(domain))
            base.add(s.getName());

        PropertyDescriptor newPropDescriptor = domainProperty.getPropertyDescriptor();
        PropertyStorageSpec prop = new PropertyStorageSpec(newPropDescriptor);
        String oldPropName = oldPropDescriptor.getName();
        String oldColumnName = oldPropDescriptor.getStorageColumnName();

        renamePropChange.addColumnRename(oldColumnName, newPropDescriptor.getStorageColumnName());

        if (!allowRenameOfColumnsDuringUpgrade)
        {
            if (base.contains(oldPropName))
            {
                throw new IllegalArgumentException("Cannot rename built-in column " + oldPropName);
            }
            else if (base.contains(prop.getName()))
            {
                throw new IllegalArgumentException("Cannot rename " + oldPropName + " to built-in column name " + prop.getName());
            }
        }

        // Rename the MV column if it already exists. We'll handle removing it later if the new version
        // of the column doesn't have MV enabled
        if (oldPropDescriptor.isMvEnabled() && !mvDropped)
        {
            TableInfo storageTable = DbSchema.get(domain.getDomainKind().getStorageSchemaName(), DbSchemaType.Provisioned).getTable(domain.getStorageTableName());
            ColumnInfo mvColumn = getMvIndicatorColumn(storageTable, oldPropDescriptor, "No MV column found for '" + oldPropDescriptor.getName() + "' in table '" + domain.getName() + "'");
            renamePropChange.addColumnRename(mvColumn.getName(), newPropDescriptor.getMvIndicatorStorageColumnName());
        }

        renamePropChange.execute();
    }

    @NotNull
    public static ColumnInfo getMvIndicatorColumn(TableInfo storageTable, PropertyDescriptor prop, String errMessage)
    {
        ColumnInfo mvColumn = storageTable.getColumn(prop.getMvIndicatorStorageColumnName());
        if (null == mvColumn)
        {
            for(String mvColumnName : PropertyStorageSpec.getLegacyMvIndicatorStorageColumnNames(prop))
            {
                mvColumn = storageTable.getColumn(mvColumnName);
                if (null != mvColumn)
                    break;
            }
            if (null == mvColumn)
                throw new IllegalStateException(errMessage);
        }
        return mvColumn;
    }

    /**
     * Generate and execute the appropriate SQL statements to resize properties
     * @param domain to execute within
     */
    public static void resizeProperty(Domain domain, DomainProperty prop, Integer scale) throws ChangePropertyDescriptorException
    {
        DomainKind kind = domain.getDomainKind();
        DbScope scope = kind.getScope();

        // should be in a transaction with propertydescriptor changes
        if (!scope.isTransactionActive())
            throw new ChangePropertyDescriptorException("Unable to change property size. Transaction is not active within change scope");

        TableChange resizePropChange = new TableChange(domain, ChangeType.ResizeColumns);

        Set<String> base = Sets.newCaseInsensitiveHashSet();
        kind.getBaseProperties(domain).forEach(s ->
                base.add(s.getName()));

        if (!base.contains(prop.getName()))
            resizePropChange.addColumnResize(prop.getPropertyDescriptor(), scale);

        resizePropChange.execute();
    }

    public static String makeTableName(DomainKind kind, Domain domain)
    {
        String rawTableName = String.format("c%sd%s_%s", domain.getContainer().getRowId(), domain.getTypeId(), domain.getName());
        SqlDialect dialect = kind.getScope().getSqlDialect();
        String alias = AliasManager.makeLegalName(rawTableName.toLowerCase(), dialect);
        alias = alias.replaceAll("_+", "_");
        return alias;
    }


    /**
     * Return a TableInfo for this domain, creating if necessary. This method uses the DbSchema caching layer.
     */
    @NotNull
    public static TableInfo createTableInfo(@NotNull Domain domain)
    {
        DomainKind kind = getDomainKind(domain);

        DbScope scope = kind.getScope();
        String schemaName = kind.getStorageSchemaName();

        if (null == scope || null == schemaName)
            throw new IllegalArgumentException();

        String tableName = ensureStorageTable(domain, kind, scope);

        assert kind.getSchemaType() == DbSchemaType.Provisioned : "provisioned DomainKinds must declare a schema type of DbSchemaType.Provisioned, but type " + kind + " declared " + kind.getSchemaType();

        DbSchema schema = scope.getSchema(schemaName, kind.getSchemaType());

        SchemaTableInfo sti = getSchemaTableInfo(domain, schemaName, tableName, schema);

        // NOTE we could handle this in ProvisionedSchemaOptions.afterLoadTable(), but that would require
        // messing with renaming columns etc, and since this is pretty rare, we'll just do this with an aliased table
        CaseInsensitiveHashMap<String> map = new CaseInsensitiveHashMap<>();
        for (DomainProperty dp : domain.getProperties())
        {
            String scn = dp.getPropertyDescriptor().getStorageColumnName();
            String name = dp.getName();
            if (null != scn && !scn.equals(name))
                map.put(scn, name);
        }

        VirtualTable wrapper = new _VirtualTable(schema, sti.getName(), sti, map);

        for (ColumnInfo from : sti.getColumns())
        {
            String name = StringUtils.defaultString(map.get(from.getName()), from.getName());
            ColumnInfo to = new AliasedColumn(wrapper, new FieldKey(null, name), from, true)
            {
                @Override
                public String getSelectName()
                {
                    return _column.getSelectName();
                }

                @Override
                public String getAlias()
                {
                    // it seems that alias like selectname in some places (CompareClause.toSQLFragment())
                    return _column.getAlias();
                }

                @Override
                public SQLFragment getValueSql(String tableAlias)
                {
                    return super.getValueSql(tableAlias);
                }
            };
            to.setHidden(from.isHidden());
            wrapper.addColumn(to);
        }

        return wrapper;
    }

    @NotNull
    private static SchemaTableInfo getSchemaTableInfo(@NotNull Domain domain, String schemaName, String tableName, DbSchema schema)
    {
        ProvisionedSchemaOptions options = new ProvisionedSchemaOptions(schema, tableName, domain);

        SchemaTableInfo sti = schema.getTable(options);
        if (null == sti)
            throw new TableNotFoundException(schemaName, tableName);
        return sti;
    }

    @NotNull
    private static DomainKind getDomainKind(@NotNull Domain domain)
    {
        if (null == domain)
            throw new NullPointerException("domain is null");
        DomainKind kind = domain.getDomainKind();
        if (null == kind)  // TODO: Consider using TableNotFoundException or something similar
            throw new IllegalArgumentException("Could not find information for domain (deleted?): " + domain.getTypeURI());
        return kind;
    }


    public static class _VirtualTable extends VirtualTable implements UpdateableTableInfo
    {
        private final SchemaTableInfo _inner;
        private final CaseInsensitiveHashMap<String> _map = new CaseInsensitiveHashMap<>();

        _VirtualTable(DbSchema schema, String name, SchemaTableInfo inner, Map<String,String> map)
        {
            super(schema, name);
            _inner = inner;
            _map.putAll(map);
        }

        @Override
        public String toString()
        {
            // really shouldn't be doing this any more, use getSelectName()?
            return _inner.toString();
        }

        @NotNull
        @Override
        public List<ColumnInfo> getAlternateKeyColumns()
        {
            return _inner.getAlternateKeyColumns();
        }

        @NotNull
        @Override
        public Map<String, Pair<IndexType, List<ColumnInfo>>> getUniqueIndices()
        {
            return _inner.getUniqueIndices();
        }

        @NotNull
        @Override
        public Map<String, Pair<IndexType, List<ColumnInfo>>> getAllIndices()
        {
            return _inner.getAllIndices();
        }

        @Override
        public boolean hasDbTriggers()
        {
            return _inner.hasDbTriggers();
        }

        @Override
        public Path getNotificationKey()
        {
            return _inner.getNotificationKey();
        }

        @Override
        public DatabaseTableType getTableType()
        {
            return _inner.getTableType();
        }

        @Override
        public String getSelectName()
        {
            return _inner.getSelectName();
        }

        @Nullable
        @Override
        public String getMetaDataName()
        {
            return _inner.getMetaDataName();
        }

        @NotNull
        @Override
        public SQLFragment getFromSQL(String alias)
        {
            return _inner.getFromSQL(alias);
        }

        @Override
        public boolean insertSupported()
        {
            return _inner.insertSupported();
        }

        @Override
        public boolean updateSupported()
        {
            return _inner.updateSupported();
        }

        @Override
        public boolean deleteSupported()
        {
            return _inner.deleteSupported();
        }

        @Override
        public TableInfo getSchemaTableInfo()
        {
            return _inner;
        }

        @Override
        public ObjectUriType getObjectUriType()
        {
            return null;
        }

        @Nullable
        @Override
        public String getObjectURIColumnName()
        {
            return null;
        }

        @Nullable
        @Override
        public String getObjectIdColumnName()
        {
            return null;
        }

        @Nullable
        @Override
        public CaseInsensitiveHashMap<String> remapSchemaColumns()
        {
            return _map;
        }

        @Nullable
        @Override
        public CaseInsensitiveHashSet skipProperties()
        {
            return null;
        }

        @Override
        public DataIteratorBuilder persistRows(DataIteratorBuilder data, DataIteratorContext context)
        {
            return _inner.persistRows(data,context);
        }

        @Override
        public Parameter.ParameterMap insertStatement(Connection conn, User user) throws SQLException
        {
            return _inner.insertStatement(conn, user);
        }

        @Override
        public Parameter.ParameterMap updateStatement(Connection conn, User user, Set<String> columns) throws SQLException
        {
            return _inner.updateStatement(conn, user, columns);
        }

        @Override
        public Parameter.ParameterMap deleteStatement(Connection conn) throws SQLException
        {
            return _inner.deleteStatement(conn);
        }
    }



    private static final Object ENSURE_LOCK = new Object();

    private static String ensureStorageTable(Domain domain, DomainKind kind, DbScope scope)
    {
        synchronized (ENSURE_LOCK)
        {
            String tableName = domain.getStorageTableName();

            if (null == tableName)
                tableName = _create(scope, kind, domain);

            return tableName;
        }
    }

    enum RequiredIndicesAction
    {

        Drop
                {
                    @Override
                    protected void doOperation(Domain domain, SchemaTableInfo schemaTableInfo, Map<String, PropertyStorageSpec.Index> requiredIndicesMap)
                    {
                        dropNotRequiredIndices(domain, schemaTableInfo, requiredIndicesMap);
                    }
                },
        Add
                {
                    @Override
                    protected void doOperation(Domain domain, SchemaTableInfo schemaTableInfo, Map<String, PropertyStorageSpec.Index> requiredIndicesMap)
                    {
                        addMissingRequiredIndices(domain, schemaTableInfo, requiredIndicesMap);

                    }
                };

        protected abstract void doOperation(Domain domain, SchemaTableInfo schemaTableInfo, Map<String, PropertyStorageSpec.Index> requiredIndicesMap);

    }

    public static void dropNotRequiredIndices(Domain domain)
    {
        if(!domain.isProvisioned()){
            return;
        }
        updateTableIndices(domain, RequiredIndicesAction.Drop);
    }

    public static void addMissingRequiredIndices(Domain domain)
    {
        updateTableIndices(domain, RequiredIndicesAction.Add);
    }

    private static void updateTableIndices(Domain domain,@NotNull RequiredIndicesAction requiredIndicesAction)
    {
        SchemaTableInfo schemaTableInfo = getSchemaTableInfo(domain);

        SqlDialect sqlDialect = getSqlDialect(domain);

        Map<String, PropertyStorageSpec.Index> requiredIndicesMap = getRequiredIndices(domain, sqlDialect);

        requiredIndicesAction.doOperation(domain, schemaTableInfo, requiredIndicesMap);
    }

    @NotNull
    private static Map<String, PropertyStorageSpec.Index> getRequiredIndices(Domain domain, SqlDialect sqlDialect)
    {
        Collection<PropertyStorageSpec.Index> requiredIndices = new ArrayList<>();
        requiredIndices.addAll(domain.getDomainKind().getPropertyIndices(domain));
        requiredIndices.addAll(domain.getPropertyIndices());

        Map<String,PropertyStorageSpec.Index> requiredIndicesMap = new CaseInsensitiveMapWrapper<>(new HashMap<>());

        String storageTableName = domain.getStorageTableName();

        if(storageTableName == null){
            storageTableName = makeTableName(getDomainKind(domain),domain);
        }

        for (PropertyStorageSpec.Index index : requiredIndices)
        {
            requiredIndicesMap.put(sqlDialect.nameIndex(storageTableName, index.columnNames),index);
        }
        return requiredIndicesMap;
    }

    private static void dropNotRequiredIndices(Domain domain, SchemaTableInfo schemaTableInfo, Map<String, PropertyStorageSpec.Index> requiredIndicesMap)
    {
        Set<String> indicesToDrop = new HashSet<>();

        buildListOfIndicesToDrop(schemaTableInfo, requiredIndicesMap, indicesToDrop);

        dropIndicesFromTable(domain, indicesToDrop);
    }

    private static void dropIndicesFromTable(Domain domain, Set<String> indicesToDrop)
    {
        if(indicesToDrop.size() > 0)
        {
            TableChange change = new TableChange(domain, ChangeType.DropIndicesByName);

            change.setIndicesToBeDroppedByName(indicesToDrop);

            try (Transaction transaction = getScope(domain).ensureTransaction())
            {
                change.execute();
                transaction.commit();
            }
        }
    }

    private static void buildListOfIndicesToDrop(SchemaTableInfo schemaTableInfo, Map<String, PropertyStorageSpec.Index> requiredIndicesMap, Set<String> indicesToDrop)
    {
        for (Map.Entry<String, Pair<TableInfo.IndexType, List<ColumnInfo>>> index : schemaTableInfo.getAllIndices().entrySet())
        {
            boolean isPrimaryKey = index.getValue().getKey().equals(TableInfo.IndexType.Primary);
            boolean tableIndexNameNotFoundInRequiredIndices = !requiredIndicesMap.keySet().contains(index.getKey().toLowerCase());

            if(!isPrimaryKey && tableIndexNameNotFoundInRequiredIndices){
                indicesToDrop.add(index.getKey());
            }
        }
    }

    private static void addMissingRequiredIndices(Domain domain, SchemaTableInfo schemaTableInfo, Map<String, PropertyStorageSpec.Index> requiredIndicesMap)
    {
        Set<PropertyStorageSpec.Index> indicesToAdd = new HashSet<>();

        buildListOfIndicesToAdd(schemaTableInfo, requiredIndicesMap, indicesToAdd);

        addIndicesToTable(domain, indicesToAdd);
    }

    private static void addIndicesToTable(Domain domain, Set<PropertyStorageSpec.Index> indicesToAdd)
    {
        if(indicesToAdd.size() >0)
        {
            TableChange change;
            if (domain.getStorageTableName() == null)
            {
                change = new TableChange(domain, ChangeType.AddIndices, makeTableName(getDomainKind(domain),domain));
            }
            else
            {
                change = new TableChange(domain, ChangeType.AddIndices);
            }

            change.getIndexedColumns().addAll(indicesToAdd);

            try (Transaction transaction = getScope(domain).ensureTransaction())
            {
                change.execute();
                transaction.commit();
            }
        }
    }

    private static void buildListOfIndicesToAdd(SchemaTableInfo schemaTableInfo, Map<String, PropertyStorageSpec.Index> requiredIndicesMap, Set<PropertyStorageSpec.Index> indicesToAdd)
    {
        CaseInsensitiveHashSet tableIndexNames = new CaseInsensitiveHashSet(schemaTableInfo.getAllIndices().keySet());
        for (Map.Entry<String, PropertyStorageSpec.Index> requiredIndexEntry : requiredIndicesMap.entrySet())
        {
            boolean requiredIndexNotFoundInTable = !tableIndexNames.contains(requiredIndexEntry.getKey());
            if (requiredIndexNotFoundInTable)
            {
                ensureIndexToBeAddedHasNoPrimaryKeys(schemaTableInfo, requiredIndexEntry);
                indicesToAdd.add(requiredIndexEntry.getValue());
            }
        }
    }

    private static void ensureIndexToBeAddedHasNoPrimaryKeys(SchemaTableInfo schemaTableInfo, Map.Entry<String, PropertyStorageSpec.Index> requiredIndexEntry)
    {
        for (String indexColumnName : requiredIndexEntry.getValue().columnNames)
        {
            for (String primaryKeyName : schemaTableInfo.getPkColumnNames())
            {
                if (indexColumnName.equalsIgnoreCase(primaryKeyName))
                {
                    throw new UnsupportedOperationException(
                            "Adding an index with primary key columns is not supported. Primary keys are " + String.join(",", schemaTableInfo.getPkColumnNames()));
                }
            }
        }
    }

    private static DbScope getScope(Domain domain)
    {
        return getDomainKind(domain).getScope();
    }

    private static SqlDialect getSqlDialect(Domain domain){
        return getScope(domain).getSqlDialect();
    }

    public static SchemaTableInfo getSchemaTableInfo(Domain domain)
    {
        DomainKind kind = getDomainKind(domain);

        DbScope scope = kind.getScope();

        String schemaName = kind.getStorageSchemaName();

        if (null == scope || null == schemaName)
            throw new IllegalArgumentException();

        String tableName = ensureStorageTable(domain, kind, scope);

        assert kind.getSchemaType() == DbSchemaType.Provisioned : "provisioned DomainKinds must declare a schema type of DbSchemaType.Provisioned, but type " + kind + " declared " + kind.getSchemaType();

        DbSchema schema = scope.getSchema(schemaName, kind.getSchemaType());

        return getSchemaTableInfo(domain, schemaName, tableName, schema);
    }

    public static void addOrDropTableIndices(Domain domain, Set<PropertyStorageSpec.Index> indices, boolean doAdd, TableChange.IndexSizeMode sizeMode)
    {
        DomainKind kind = domain.getDomainKind();
        DbScope scope = kind.getScope();

        String tableName = domain.getStorageTableName();
        if (null == tableName)
            throw new IllegalStateException("Table must already exist.");

        TableChange change = new TableChange(domain, doAdd ? ChangeType.AddIndices : ChangeType.DropIndices);

        if(null != sizeMode)
            change.setIndexSizeMode(sizeMode);

        // If indices not passed in, get them from domain definition
        if(null == indices)
        {
            indices = new HashSet<>();
            indices.addAll(kind.getPropertyIndices(domain));
            indices.addAll(domain.getPropertyIndices());
        }

        change.setIndexedColumns(indices);

        try (Transaction transaction = scope.ensureTransaction())
        {
            change.execute();
            transaction.commit();
        }
    }

    public static void fixupProvisionedDomain(SchemaTableInfo ti, DomainKind kind, Domain domain, String tableName)
    {
        assert !ti.isLocked();

        int index = 0;

        // Some domains have property descriptors for base properties
        Set<String> basePropertyNames = new HashSet<>();
        for (PropertyStorageSpec s : kind.getBaseProperties(domain))
        {
            ColumnInfo c = ti.getColumn(s.getName());
            basePropertyNames.add(s.getName().toLowerCase());

            if (null == c)
            {
                Logger.getLogger(StorageProvisioner.class).info("Column not found in storage table: " + tableName + "." + s.getName());
                continue;
            }

            // The columns coming back from JDBC metadata aren't necessarily in the same order that the domain
            // wants them based on its current property order
            ti.setColumnIndex(c, index++);

            // Use column name casing from the storage spec
            c.setName(s.getName());
        }

        HashSet<String> seenProperties = new HashSet<>();
        for (DomainProperty p : domain.getProperties())
        {
            if (kind.hasPropertiesIncludeBaseProperties() && basePropertyNames.contains(p.getName().toLowerCase()))
                continue;

            if (!seenProperties.add(p.getName()))
            {
                // There is more than property descriptor with this name attached to this table. This shouldn't happen, but we've seen
                // at least one occurrence of it in a dev's db, thought to have been caused by in-flux code in 12/2013. The result would
                // be calls to retrieve metadata would throw an uninformative IllegalStateException on an array index out of bounds. Throwing this
                // RuntimeException instead gives better diagnostic info.
                throw new RuntimeException("Duplicate property descriptor name found for: " + tableName + "." + p.getName());
            }

            ColumnInfo c = ti.getColumn(p.getPropertyDescriptor().getStorageColumnName());

            if (null == c)
            {
                if (p.getPropertyDescriptor().getStorageColumnName() == null)
                {
                    log.warn("No storage column name set for property " + p.getName() + " on table " + tableName);
                }
                else
                {
                    log.info("Column not found in storage table: " + tableName + "." + p.getPropertyDescriptor().getStorageColumnName());
                }
                continue;
            }

            // The columns coming back from JDBC metadata aren't necessarily in the same order that the domain
            // wants them based on its current property order
            ti.setColumnIndex(c, index++);
            PropertyColumn.copyAttributes(null, c, p, p.getContainer(), null);

            if (p.isMvEnabled())
            {
                c.setDisplayColumnFactory(new MVDisplayColumnFactory());

                ColumnInfo mvColumn = getMvIndicatorColumn(ti, p.getPropertyDescriptor(), "No MV column found for '" + p.getName() + "' in table '" + domain.getName() + "'");
                c.setMvColumnName(mvColumn.getFieldKey());
                mvColumn.setMvIndicatorColumn(true);
                // The UI for the main column will include MV input as well, so no need for another column in insert/update views
                mvColumn.setShownInUpdateView(false);
                mvColumn.setShownInInsertView(false);
            }
            c.setScale(p.getScale());
        }
    }


    /**
     * We are mostly making the storage table match the existing property descriptors, because that is easiest.
     * Sometimes it would be better or more conservative to update the property descriptors instead
     */

    public static boolean repairDomain(Container c, String domainUri, BindException errors)
    {
        DbScope scope = CoreSchema.getInstance().getScope();

        try (Transaction transaction = scope.ensureTransaction())
        {
            Domain domain = PropertyService.get().getDomain(c, domainUri);
            if (null == domain)
            {
                errors.reject(SpringActionController.ERROR_MSG, "Could not find domain: " + domainUri);
                return false;
            }
            DomainKind kind = domain.getDomainKind();
            if (null == kind)
            {
                errors.reject(SpringActionController.ERROR_MSG, "Could not find domain kind: " + domainUri);
                return false;
            }
            ProvisioningReport preport = getProvisioningReport(domainUri);
            if (preport.getProvisionedDomains().size() != 1)
            {
                errors.reject(SpringActionController.ERROR_MSG, "Could not generate report.");
                return false;
            }
            ProvisioningReport.DomainReport report = preport.getProvisionedDomains().iterator().next();

            TableChange drops = new TableChange(domain, ChangeType.DropColumns);
            TableChange adds = new TableChange(domain, ChangeType.AddColumns);

            for (ProvisioningReport.ColumnStatus st : report.getColumns())
            {
                if (!st.hasProblem)
                    continue;
                if (st.spec == null && st.prop == null)
                {
                    if (null != st.colName)
                    {
                        drops.dropColumnExactName(st.colName);
                    }
                    if (null != st.mvColName)
                    {
                        drops.dropColumnExactName(st.mvColName);
                    }
                }
                else if (st.prop != null)
                {
                    if (st.colName == null)
                    {
                        adds.addColumn(st.prop.getPropertyDescriptor());
                    }
                    if (st.mvColName == null && st.prop.isMvEnabled())
                    {
                        adds.addColumn(makeMvColumn(st.prop));
                    }
                    if (st.mvColName != null && !st.prop.isMvEnabled())
                    {
                        drops.dropColumnExactName(st.mvColName);
                    }
                }
            }


            drops.execute();
            adds.execute();
            kind.invalidate(domain);
            transaction.commit();
            return !errors.hasErrors();
        }
        catch (Exception x)
        {
            errors.reject(SpringActionController.ERROR_MSG, x.getMessage());
            return false;
        }
    }

    public static ProvisioningReport getProvisioningReport()
    {
        return getProvisioningReport(null);
    }

    public static ProvisioningReport getProvisioningReport(@Nullable String domainuri)
    {
        final ProvisioningReport report = new ProvisioningReport();
        SQLFragment sql = new SQLFragment("SELECT domainid, name, storageschemaname, storagetablename FROM ")
                .append(OntologyManager.getTinfoDomainDescriptor().getFromSQL("dd"));
        if (null != domainuri)
        {
            sql.append(" WHERE domainuri=?");
            sql.add(domainuri);
        }

        TreeSet<Path> schemaNames = new TreeSet<>();
        Map<Path, Set<String>> nonProvisionedTableMap = new TreeMap<>();
        final TreeSet<Path> provisionedTables = new TreeSet<>();
        if (null == domainuri)
        {
            for (DomainKind dk : PropertyService.get().getDomainKinds())
            {
                String schemaName = dk.getStorageSchemaName();
                if (null != schemaName)
                {
                    Path path = new Path(schemaName);
                    schemaNames.add(path);
                    nonProvisionedTableMap.put(path, dk.getNonProvisionedTableNames());
                }
            }
            for (Path schemaName : schemaNames)
            {
                DbSchema schema = DbSchema.get(schemaName.getName(), DbSchemaType.Provisioned);
                Collection<String> tableNames;

                try
                {
                    tableNames = DbSchema.loadTableMetaData(schema.getScope(), schema.getName()).keySet();
                }
                catch (SQLException e)
                {
                    throw new RuntimeSQLException(e);
                }

                for (String name : tableNames)
                {
                    if (!nonProvisionedTableMap.get(schemaName).contains(name.toLowerCase()))
                        provisionedTables.add(schemaName.append(name));
                }
            }
        }

        new SqlSelector(OntologyManager.getExpSchema(), sql).forEach(rs -> {
            ProvisioningReport.DomainReport domain = new ProvisioningReport.DomainReport();
            domain.setId(rs.getInt("domainid"));
            domain.setName(rs.getString("name"));
            if (rs.getString("storagetablename") == null)
            {
                report.addUnprovisioned(domain);
            }
            else
            {
                domain.setSchemaName(rs.getString("storageschemaname"));
                domain.setTableName(rs.getString("storagetablename"));
                report.addProvisioned(domain);
                // table is accounted for
                provisionedTables.remove(new Path(domain.getSchemaName(), domain.getTableName()));
            }
        });

        // TODO: Switch to normal schema/table cache (now that we actually use a cache for them)
        Map<String,DbSchema> schemas = new HashMap<>();

        for (ProvisioningReport.DomainReport domainReport : report.getProvisionedDomains())
        {
            DbSchema schema = schemas.get(domainReport.getSchemaName());
            if (schema == null)
            {
                try
                {
                    // Provisioned tables are always in the labkey database (for now)
                    schema = DbSchema.createFromMetaData(DbScope.getLabKeyScope(), domainReport.getSchemaName(), DbSchemaType.Bare);
                    schemas.put(domainReport.getSchemaName(), schema);
                }
                catch (Exception e)
                {
                    domainReport.addError("error resolving schema " + domainReport.getSchemaName() + " - " + e.getMessage());
                    continue;
                }
            }

            TableInfo table = schema.getTable(domainReport.getTableName());
            if (table == null)
            {
                domainReport.addError(String.format("metadata for domain %s specifies a database table at %s.%s but that table is not present",
                        domainReport.getName(), domainReport.getSchemaName(), domainReport.getTableName()));
                continue;
            }
            Set<String> hardColumnNames = Sets.newCaseInsensitiveHashSet(table.getColumnNameSet());
            Domain domain = PropertyService.get().getDomain(domainReport.getId());
            if (domain == null)
            {
                domainReport.addError(String.format("Could not find a domain for %s.%s",
                        domainReport.getSchemaName(), domainReport.getTableName()));
                continue;
            }
            DomainKind kind = domain.getDomainKind();
            if (kind == null)
            {
                domainReport.addError(String.format("Could not find a domain kind for %s.%s",
                        domainReport.getSchemaName(), domainReport.getTableName()));
                continue;
            }

            // Some domains have property descriptors for base properties
            Set<String> basePropertyNames = new HashSet<>();
            if (kind.hasPropertiesIncludeBaseProperties())
            {
                basePropertyNames.addAll(kind.getBaseProperties(domain)
                    .stream()
                    .map(spec -> spec.getName().toLowerCase())
                    .collect(Collectors.toList()));
            }

            for (DomainProperty domainProp : domain.getProperties())
            {
                if (basePropertyNames.contains(domainProp.getName().toLowerCase()))
                    continue;

                ProvisioningReport.ColumnStatus status = new ProvisioningReport.ColumnStatus();
                domainReport._columns.add(status);
                status.prop = domainProp;
                PropertyDescriptor propDescriptor = domainProp.getPropertyDescriptor();
                if (hardColumnNames.remove(propDescriptor.getStorageColumnName()))
                {
                    status.colName = domainProp.getName();
                }
                else
                {
                    domainReport.addError(String.format("database table %s.%s did not contain expected column '%s'", domainReport.getSchemaName(), domainReport.getTableName(), domainProp.getName()));
                    status.fix = "Create column '" + domainProp.getName() + "'";
                    status.hasProblem = true;
                }
                String mvColName = PropertyStorageSpec.getMvIndicatorDisplayColumnName(propDescriptor);
                if (hardColumnNames.remove(mvColName))
                    status.mvColName = mvColName;
                if (null == status.mvColName && domainProp.isMvEnabled())
                {
                    domainReport.addError(String.format("database table %s.%s has mvindicator enabled but expected '%s' column wasn't present",
                            domainReport.getSchemaName(), domainReport.getTableName(), mvColName));
                    status.fix += (status.fix.isEmpty() ? "C" : " and c") + "reate column '" + mvColName + "'";
                    status.hasProblem = true;
                }
                if (null != status.mvColName && !domainProp.isMvEnabled())
                {
                    domainReport.addError(String.format("database table %s.%s has mvindicator disabled but '%s' column is present",
                            domainReport.getSchemaName(), domainReport.getTableName(), mvColName));
                    status.fix += (status.fix.isEmpty() ? "D" : " and d") +  "rop column '" + status.mvColName + "'";
                    status.hasProblem = true;
                }
            }
            for (PropertyStorageSpec spec : kind.getBaseProperties(domain))
            {
                ProvisioningReport.ColumnStatus status = new ProvisioningReport.ColumnStatus();
                domainReport._columns.add(status);
                status.spec = spec;
                if (hardColumnNames.remove(spec.getName()))
                    status.colName = spec.getName();
                else
                {
                    domainReport.addError(String.format("database table %s.%s did not contain expected column '%s'", domainReport.getSchemaName(), domainReport.getTableName(), spec.getName()));
                    status.fix = "'" + spec.getName() + "' is a built-in column.  Contact LabKey support.";
                    status.hasProblem = true;
                }
                String mvColName = PropertyStorageSpec.getMvIndicatorDisplayColumnName(spec);
                if (hardColumnNames.remove(mvColName))
                    status.mvColName = mvColName;
                if (null == status.mvColName && spec.isMvEnabled())
                {
                        domainReport.addError(String.format("database table %s.%s has mvindicator enabled but expected '%s' column wasn't present",
                                domainReport.getSchemaName(), domainReport.getTableName(), mvColName));
                        status.fix = "'" + spec.getName() + "' is a built-in column.  Contact LabKey support.";
                        status.hasProblem = true;
                }
                if (null != status.mvColName && !spec.isMvEnabled())
                {
                        domainReport.addError(String.format("database table %s.%s has mvindicator disabled but '%s' column is present",
                                domainReport.getSchemaName(), domainReport.getTableName(), mvColName));
                        status.fix = "'" + spec.getName() + "' is a built-in column.  Contact LabKey support.";
                        status.hasProblem = true;
                }
            }
            for (String name : hardColumnNames.toArray(new String[hardColumnNames.size()]))
            {
                if (name.endsWith("_" + MvColumn.MV_INDICATOR_SUFFIX))
                    continue;
                domainReport.addError(String.format("database table %s.%s has column '%s' without a property descriptor",
                        domainReport.getSchemaName(), domainReport.getTableName(), name));
                ProvisioningReport.ColumnStatus status = new ProvisioningReport.ColumnStatus();
                domainReport._columns.add(status);

                hardColumnNames.remove(name);
                status.colName = name;
                if (hardColumnNames.remove(PropertyStorageSpec.getMvIndicatorDisplayColumnName(name)))
                    status.mvColName = PropertyStorageSpec.getMvIndicatorDisplayColumnName(name);
                status.fix = "Delete column '" + name + "'" + (null == status.mvColName ? "" : " and column '" + status.mvColName + "'");
                status.hasProblem = true;
            }
            for (String name : hardColumnNames)
            {
                domainReport.addError(String.format("database table %s.%s has column '%s' without a property descriptor",
                        domainReport.getSchemaName(), domainReport.getTableName(), name));
                ProvisioningReport.ColumnStatus status = new ProvisioningReport.ColumnStatus();
                domainReport._columns.add(status);
                status.mvColName = name;
                status.fix = "Delete column '" + name + "'";
                status.hasProblem = true;
            }
            if (!domainReport._errors.isEmpty())
            {
                ExperimentUrls urls = PageFlowUtil.urlProvider(ExperimentUrls.class);
                if (null != urls)
                {
                    ActionURL fix = urls.getRepairTypeURL(domain.getContainer());
                    fix.addParameter("domainUri", domain.getTypeURI());
                    domainReport.addError("See this page for more info: " + fix.getURIString());
                }
            }
        }

        for (Path orphan : provisionedTables)
        {
            String schema = orphan.get(0);
            String table = orphan.get(1);
            report.addGlobalError("Table " + schema + "." + table + " does not have an associated domain.");
        }

        return report;
    }


    public static class ProvisioningReport
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
            private Integer _id;
            private String _name;
            private String _schemaName;
            private String _tableName;

            private final List<String> _errors = new ArrayList<>();
            private final List<ColumnStatus> _columns = new ArrayList<>();

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

    private static class ProvisionedSchemaOptions extends SchemaTableOptions
    {
        private final Domain _domain;

        private ProvisionedSchemaOptions(DbSchema schema, String tableName, Domain domain)
        {
            super(schema, tableName);
            _domain = domain;
        }

        public Domain getDomain()
        {
            return _domain;
        }

        @Override
        public void afterLoadTable(SchemaTableInfo ti)
        {
            Domain domain = getDomain();
            DomainKind kind = domain.getDomainKind();
            kind.afterLoadTable(ti, domain);

            fixupProvisionedDomain(ti, kind, domain, ti.getName());
        }
    }

    @TestTimeout(120)
    public static class TestCase extends Assert
    {
        private final Container container = JunitUtil.getTestContainer();
        private final String notNullPropName = "a_" + System.currentTimeMillis();
        private final String propNameB = "b_" + System.currentTimeMillis();
        private final String propBMvColumnName = PropertyStorageSpec.getMvIndicatorDisplayColumnName(propNameB).toLowerCase();

        private Domain domain;

        @Before
        public void before() throws Exception
        {
            String domainName = "testdomain_" + System.currentTimeMillis();
            String domainKindName = ModuleLoader.getInstance().hasModule("Study") ? "TestDatasetDomainKind" : "TestDomainKind";

            Lsid lsid = new Lsid(domainKindName, "Folder-" + container.getRowId(), domainName);
            domain = PropertyService.get().createDomain(container, lsid.toString(), domainName);
            domain.save(new User());
            StorageProvisioner.createTableInfo(domain);
            domain = PropertyService.get().getDomain(domain.getTypeId());
        }

        @After
        public void after() throws Exception
        {
            if (domain != null)
            {
                StorageProvisioner.drop(domain);
                OntologyManager.deleteDomain(domain.getTypeURI(), container);
                domain = null;
            }
        }


        @Test
        public void testAddProperty() throws Exception
        {
            addPropertyB();
            Assert.assertNotNull("adding a property added a new column to the hard table", getJdbcColumnMetadata(domain, propNameB));
        }

        @Test
        public void testDropProperty() throws Exception
        {
            addPropertyB();
            DomainProperty propB = domain.getPropertyByName(propNameB);
            propB.delete();

            domain.save(new User());
            domain = PropertyService.get().getDomain(domain.getTypeId());

            Assert.assertNull("column for dropped property is gone", getJdbcColumnMetadata(domain, propNameB));
        }

        @Test
        public void testRenameProperty() throws Exception
        {
            addPropertyB();
            DomainProperty propB = domain.getPropertyByName(propNameB);
            String oldColumnName = propB.getPropertyDescriptor().getStorageColumnName();
            String newName = "new_" + propNameB;
            propB.setName(newName);

            domain.save(new User());
            domain = PropertyService.get().getDomain(domain.getTypeId());

            Assert.assertNull("renamed column is not present in old name", getJdbcColumnMetadata(domain, oldColumnName));

            propB = domain.getPropertyByName(newName);
            String newColumnName = propB.getPropertyDescriptor().getStorageColumnName();
            Assert.assertNotNull("renamed column is provisioned in new name", getJdbcColumnMetadata(domain, newColumnName));
        }
/*

    is it actually a functional requirement that isRequired on a prop makes a not null constraint on its column?

        @Test
        public void testNotNullableProperty() throws Exception
        {
            addNotNullProperty();
            ColumnMetadata col = getJdbcColumnMetadata(domain.getDomainKind().getStorageSchemaName(),
                            domain.getStorageTableName(), notNullPropName);
            Assert.assertFalse("required property is NOT NULL in db", col.nullable);
        }
*/

        @Test
        public void testEnableMv() throws Exception
        {
            addPropertyB();
            DomainProperty propB = domain.getPropertyByName(propNameB);
            propB.setMvEnabled(true);

            domain.save(new User());
            domain = PropertyService.get().getDomain(domain.getTypeId());

            ColumnInfo col = getJdbcColumnMetadata(domain, propBMvColumnName);
            Assert.assertNotNull("enabled mvindicator causes mvindicator column to be provisioned", col);
        }


        @Test
        public void testDisableMv() throws Exception
        {
            addPropertyB();
            DomainProperty propB = domain.getPropertyByName(propNameB);
            propB.setMvEnabled(true);

            domain.save(new User());
            domain = PropertyService.get().getDomain(domain.getTypeId());

            propB = domain.getPropertyByName(propNameB);
            propB.setMvEnabled(false);

            domain.save(new User());
            domain = PropertyService.get().getDomain(domain.getTypeId());

            Assert.assertNull("property with disabled mvindicator has no mvindicator column", getJdbcColumnMetadata(domain, propBMvColumnName));
        }

/*

XXX FIXME UNDONE TODO This is a valid test and it fails because we don't handle
renaming a property AND toggling mvindicator on in the same change.

        @Test
        public void testRenameAndEnableMvAtOnce () throws Exception
        {
            // should fail, known problem
            addPropertyB();
            DomainProperty propB = domain.getPropertyByName(propNameB);
            String newName = "new_" + propNameB;
            String newMvName = PropertyStorageSpec.getMvIndicatorDisplayColumnName(newName);
            propB.setName(newName);
            propB.setMvEnabled(true);
            domain.save(new User());
            Assert.assertNull("renamed column is not present in old name",
                    getJdbcColumnMetadata(domain.getDomainKind().getStorageSchemaName(),
                            domain.getStorageTableName(), propNameB));
            Assert.assertNotNull("renamed column is provisioned in new name",
                    getJdbcColumnMetadata(domain.getDomainKind().getStorageSchemaName(),
                            domain.getStorageTableName(), newName));
            Assert.assertNotNull("enabled mvindicator causes mvindicator column to be provisioned",
                    getJdbcColumnMetadata(domain.getDomainKind().getStorageSchemaName(),
                            domain.getStorageTableName(), newMvName));
        }

*/


        @Test
        public void testProvisioningReport() throws Exception
        {
            ProvisioningReport report = StorageProvisioner.getProvisioningReport();
            Assert.assertNotNull(report);
            boolean success = true;
            StringBuilder sb = new StringBuilder();
            for (ProvisioningReport.DomainReport dr : report.getProvisionedDomains())
            {
                if (!dr.getErrors().isEmpty())
                {
                    success = false;
                    sb.append(dr.getErrors().toString());
                }
            }
            if (!report.getGlobalErrors().isEmpty())
                sb.append(report.getGlobalErrors().toString());
            //18775: StorageProvisioner junit test fails when external modules are not present
            //Assert.assertTrue(sb.toString(), success);
        }

        private void addPropertyB() throws Exception
        {
            DomainProperty dp = domain.addProperty();
            dp.setPropertyURI(propNameB + "#" + propNameB);
            dp.setName(propNameB);

            domain.save(new User());
            domain = PropertyService.get().getDomain(domain.getTypeId());
        }

        private void addNotNullProperty() throws Exception
        {
            DomainProperty dp = domain.addProperty();
            dp.setPropertyURI(notNullPropName + "#" + notNullPropName);
            dp.setName(notNullPropName);
            dp.setRequired(true);
            domain.save(new User());
            domain = PropertyService.get().getDomain(domain.getTypeId());
        }


        private @Nullable ColumnInfo getJdbcColumnMetadata(Domain domain, String columnName) throws Exception
        {
            DomainKind kind = domain.getDomainKind();
            String schemaName = kind.getStorageSchemaName();
            String tableName = domain.getStorageTableName();

            DbSchema schema = kind.getScope().getSchema(schemaName, kind.getSchemaType());
            SchemaTableInfo ti = schema.getTable(tableName);

            // Slight overkill, given that tests merely verify column existence, but might as well reuse existing code
            Collection<ColumnInfo> cols = ColumnInfo.createFromDatabaseMetaData(schemaName, ti, columnName);

            return cols.isEmpty() ? null : cols.iterator().next();
        }
    }
}
