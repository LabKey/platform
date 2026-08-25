/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
package org.labkey.experiment.api.property;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DatabaseIdentifier;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DbScope.SchemaTableOptions;
import org.labkey.api.data.DbScope.Transaction;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MVDisplayColumnFactory;
import org.labkey.api.data.ParameterMapStatement;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.PropertyStorageSpec.Index;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableChange;
import org.labkey.api.data.TableChange.ChangeType;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableInfo.IndexDefinition;
import org.labkey.api.data.UpdateableTableInfo;
import org.labkey.api.data.VirtualTable;
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
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.AbstractDomainKind;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.property.TestDomainKind;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.test.TestTimeout;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.CPUTimer;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.ContainerUser;
import org.springframework.validation.BindException;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;

import static org.labkey.api.data.ColumnRenderPropertiesImpl.TEXT_CHOICE_CONCEPT_URI;

/**
 * Creates and maintains "hard" tables in the underlying database based on dynamically configured data types.
 * Will do CREATE TABLE and ALTER TABLE statements to make sure the table has the right set of requested columns.
 */
public class StorageProvisionerImpl implements StorageProvisioner
{
    private static final Logger log = LogHelper.getLogger(StorageProvisionerImpl.class, "Creates and maintains DB tables for admin-defined types");
    private static final CPUTimer create = new CPUTimer("StorageProvisioner.create");

    private static final StorageProvisionerImpl instance = new StorageProvisionerImpl();

    public static StorageProvisionerImpl get()
    {
        return instance;
    }

    private StorageProvisionerImpl()
    {
    }

    // #42641: Track recently created tables in a cache to limit size and duration
    private static final Cache<@NotNull String, StackTraceElement @NotNull []> RECENTLY_CREATED_TABLES = CacheBuilder.newBuilder()
        .maximumSize(10000)
        .expireAfterWrite(Duration.ofDays(1))
        .build();

    private String _create(DbScope scope, DomainKind<?> kind, Domain domain, boolean useProvidedStorageName)
    {
        //noinspection AssertWithSideEffects
        assert create.start();

        // CONSIDER: could combine the two SELECT here: SELECT...FOR UPDATE (domain.getDatabaseLock()) and the SELECT (getDomainDescriptor())
        try (Transaction transaction = scope.ensureTransaction(domain.getDatabaseLock()))
        {
            // reselect in a transaction
            DomainDescriptor dd = OntologyManager.getDomainDescriptor(domain.getTypeId());
            if (null == dd)
            {
                log.warn("Can't find domain descriptor: {} {}", domain.getTypeId(), domain.getTypeURI());
                transaction.commit();
                return null;
            }
            String tableName = dd.getStorageTableName();

            if (useProvidedStorageName)
            {
                if (null == tableName)
                {
                    throw new RuntimeException("Storage table name was null: " + domain.getTypeId() + " " + domain.getTypeURI());
                }
            }
            else
            {
                if (null != tableName)
                {
                    transaction.commit();
                    return tableName;
                }

                tableName = makeTableName(kind, domain);
            }
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
                        log.info("StorageProvisioner ignored property with name of built-in column: {}", property.getPropertyURI());
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

            List<Index> indices = new ArrayList<>();
            indices.addAll(kind.getPropertyIndices(domain));
            indices.addAll(domain.getPropertyIndices());
            change.setIndexedColumns(domain, indices);

            /*
             * TODO:
             * GitHub Issue 1117: Consider the generic fix below for develop. See https://github.com/LabKey/platform/pull/7804
             * Tests may need updates and domains FKs (for example, issues) maybe need to adjusted.
             *
             * Set<PropertyStorageSpec.ForeignKey> foreignKeys = new LinkedHashSet<>(kind.getPropertyForeignKeys(domain.getContainer()));
             * foreignKeys.addAll(domain.getPropertyForeignKeys());
             * change.setForeignKeys(foreignKeys);
             */
            change.setForeignKeys(domain.getPropertyForeignKeys());

            try
            {
                log.info("Attempting to create {}", tableName);
                change.execute();
                RECENTLY_CREATED_TABLES.put(tableName, Thread.currentThread().getStackTrace());
            }
            catch (RuntimeException re)
            {
                StackTraceElement[] previousCreationStack = RECENTLY_CREATED_TABLES.getIfPresent(tableName);

                if (null != previousCreationStack)
                    log.error("{} while attempting to create storage table. Previous creation stack trace:{}", re.getMessage(), ExceptionUtil.renderStackTrace(previousCreationStack));

                throw re;
            }

            if (!useProvidedStorageName)
            {
                DomainDescriptor editDD = dd.edit()
                    .setStorageTableName(tableName)
                    .setStorageSchemaName(kind.getStorageSchemaName())
                    .build();

                OntologyManager.ensureDomainDescriptor(editDD);
            }

            kind.invalidate(domain);

            transaction.commit();

            return tableName;
        }
        finally
        {
            //noinspection AssertWithSideEffects
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
    private PropertyStorageSpec getPropStorageSpecForMvColumn(TableInfo storageTable, PropertyDescriptor mainProp, String errMessage)
    {
        ColumnInfo mvColumn = getMvIndicatorColumn(storageTable, mainProp, errMessage);
        return new PropertyStorageSpec(mvColumn.getName(), mvColumn.getJdbcType(), mvColumn.getScale());
    }

    @Override
    public void drop(Domain domain)
    {
        if (null == domain)
            return;
        DomainKind<?> kind = domain.getDomainKind();
        if (kind == null)
        {
            if (null != domain.getStorageTableName())
                log.warn("Domain {} has no DomainKind, it cannot be dropped. URI: {}", domain.getName(), domain.getTypeURI(), new IllegalStateException());
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
                log.warn("Failed to drop table in schema {} for domain {} - {}", schemaName, domain.getName(), e.getMessage(), e);
                throw e;
            }
        }
        else
        {
            log.warn("Table {} in schema {} for domain {} does not exist. Ignoring drop.", tableName, schemaName, domain.getName());
        }
        // Issue 44467: Update DbSchema caches
        kind.invalidate(domain);
    }

    @Override
    public void addStorageProperties(Domain domain, Collection<PropertyStorageSpec> properties, boolean allowAddBaseProperty)
    {
        DomainKind<?> kind = domain.getDomainKind();
        DbScope scope = kind.getScope();

        // should be in a transaction
        assert scope.isTransactionActive();

        TableChange change = new TableChange(domain, ChangeType.AddColumns);

        Set<String> base = Sets.newCaseInsensitiveHashSet();
        for (PropertyStorageSpec s : kind.getBaseProperties(domain))
            base.add(s.getName());

        for (PropertyStorageSpec prop : properties)
        {
            if (prop.getName() == null || prop.getName().isEmpty())
                throw new IllegalArgumentException("Can't add property with no name.");

            if (!allowAddBaseProperty && base.contains(prop.getName()))
            {
                // apparently this is a case where the domain allows a propertydescriptor to be defined with the same
                // name as a built-in column. e.g. to allow setting overrides?
                log.warn("StorageProvisioner ignored property with name of built-in column: {}", prop.getName());
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

    public void addProperties(Domain domain, Collection<DomainProperty> properties, boolean allowAddBaseProperty)
    {
        DomainKind<?> kind = domain.getDomainKind();
        DbScope scope = kind.getScope();

        // should be in a transaction with propertydescriptor changes
        assert scope.isTransactionActive();

        if (null == domain.getStorageTableName())
        {
            _create(scope, kind, domain, false);
            return;
        }

        TableChange change = new TableChange(domain, ChangeType.AddColumns);

        Set<String> base = Sets.newCaseInsensitiveHashSet();
        for (PropertyStorageSpec s : kind.getBaseProperties(domain))
            base.add(s.getName());

        for (DomainProperty prop : properties)
        {
            if (prop.getName() == null || prop.getName().isEmpty())
                throw new IllegalArgumentException("Can't add property with no name: " + prop.getPropertyURI());

            if (!allowAddBaseProperty && base.contains(prop.getName()))
            {
                // apparently this is a case where the domain allows a propertydescriptor to be defined with the same
                // name as a built-in column. e.g. to allow setting overrides?
                log.warn("StorageProvisioner ignored property with name of built-in column: {}", prop.getPropertyURI());
                continue;
            }

            if (CoreSchema.getInstance().getSqlDialect().isReserved(prop.getName()))
                log.warn("Property name '{}' is a reserved word in the current SQL dialect.", prop.getName());

            PropertyStorageSpec spec = kind.getPropertySpec(prop.getPropertyDescriptor(), domain);
            if (null != spec)
            {
                change.addColumn(spec);
            }
            if (prop.isMvEnabled())
            {
                change.addColumn(makeMvColumn(prop));
            }
        }
        log.debug("addingProperties to {}", domain.getName());
        change.execute();
    }

    public void dropMvIndicator(DomainProperty prop, PropertyDescriptor pd)
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

    public void addMvIndicator(DomainProperty prop)
    {
        Domain domain = prop.getDomain();
        DomainKind<?> kind = domain.getDomainKind();
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

    public void dropProperties(Domain domain, Collection<DomainProperty> properties)
    {
        DomainKind<?> kind = domain.getDomainKind();
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

    public void renameProperty(Domain domain, DomainProperty domainProperty, PropertyDescriptor oldPropDescriptor, boolean mvDropped)
    {
        DomainKind<?> kind = domain.getDomainKind();
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

        if (base.contains(oldPropName))
        {
            throw new IllegalArgumentException("Cannot rename built-in column " + oldPropName);
        }
        else if (base.contains(prop.getName()))
        {
            throw new IllegalArgumentException("Cannot rename " + oldPropName + " to built-in column name " + prop.getName());
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

    @Override
    @NotNull
    public ColumnInfo getMvIndicatorColumn(TableInfo storageTable, PropertyDescriptor prop, String errMessage)
    {
        ColumnInfo mvColumn = storageTable.getColumn(prop.getMvIndicatorStorageColumnName());
        if (null == mvColumn)
        {
            for (String mvColumnName : PropertyStorageSpec.getLegacyMvIndicatorStorageColumnNames(prop))
            {
                mvColumn = storageTable.getColumn(mvColumnName);
                if (null != mvColumn)
                    return mvColumn;
            }
            throw new IllegalStateException(errMessage);
        }
        return mvColumn;
    }

    /**
     * Generate and execute the appropriate SQL statements to resize properties
     * @param domain to execute within
     */
    public void resizeProperty(Domain domain, DomainProperty prop, Integer oldScale) throws ChangePropertyDescriptorException
    {
        DomainKind<?> kind = domain.getDomainKind();
        DbScope scope = kind.getScope();

        // should be in a transaction with property descriptor changes
        if (!scope.isTransactionActive())
            throw new ChangePropertyDescriptorException("Unable to change property size. Transaction is not active within change scope");

        TableChange resizePropChange = new TableChange(domain, ChangeType.ResizeColumns);

        Set<String> base = Sets.newCaseInsensitiveHashSet();
        kind.getBaseProperties(domain).forEach(s ->
                base.add(s.getName()));

        if (!base.contains(prop.getName()))
            resizePropChange.addColumnResize(prop.getPropertyDescriptor(), oldScale);

        resizePropChange.execute();
    }

    /**
     * Generate and execute the appropriate SQL statements to resize properties
     * @param domain to execute within
     */
    public void changePropertyType(Domain domain, DomainProperty prop) throws ChangePropertyDescriptorException
    {
        DomainKind<?> kind = domain.getDomainKind();
        DbScope scope = kind.getScope();

        // should be in a transaction with propertydescriptor changes
        if (!scope.isTransactionActive())
            throw new ChangePropertyDescriptorException("Unable to change property type. Transaction is not active within change scope");

        TableChange propChange = new TableChange(domain, ChangeType.ChangeColumnTypes);

        Set<String> base = Sets.newCaseInsensitiveHashSet();
        kind.getBaseProperties(domain).forEach(s -> base.add(s.getName()));

        Map<String, PropertyType> oldPropTypes = new HashMap<>();
        if (!base.contains(prop.getName()))
        {
            if (prop instanceof DomainPropertyImpl dpi)
            {
                var oldPd = dpi._pdOld;
                if (oldPd != null)
                {
                    var newPd = dpi._pd;
                    if (oldPd.getPropertyType() == PropertyType.MULTI_CHOICE && TEXT_CHOICE_CONCEPT_URI.equals(newPd.getConceptURI()))
                    {
                        var selectColumnName = prop.getPropertyDescriptor().getLegalSelectName(scope.getSqlDialect());
                        SQLFragment sql = new SQLFragment("SELECT COUNT(*) FROM ")
                                .appendDottedIdentifiers(kind.getStorageSchemaName(), domain.getStorageTableName())
                                .append(" WHERE ")
                                .appendIdentifier(selectColumnName)
                                .append(" IS NOT NULL AND array_length(")
                                .appendIdentifier(selectColumnName)
                                .append(", 1) > 1");
                        long count = new SqlSelector(scope, sql).getObject(Long.class);
                        if (count > 0)
                        {
                            throw new ChangePropertyDescriptorException("Unable to change property type. There are rows with multiple values stored for '" + prop.getName() + "'.");
                        }
                    }
                    // GitHub Issue 935: Changing from MVTC to TC wraps all values in curly braces
                    // This is due to StorageColumnName differ from column name, resulting in column update skipped
                    oldPropTypes.put(prop.getPropertyDescriptor().getStorageColumnName(), oldPd.getPropertyType());
                }

            }

            propChange.addColumn(prop.getPropertyDescriptor());
        }

        propChange.setOldPropertyTypes(oldPropTypes);
        propChange.execute();
    }

    public String makeTableName(DomainKind<?> kind, Domain domain)
    {
        return makeTableName(kind.getScope().getSqlDialect(), domain.getContainer(), domain.getTypeId(), domain.getName());
    }

    // Needed by ExperimentUpgradeCode.shortenAllStorageNames(). When that code is removed, combine this with above variant.
    public String makeTableName(SqlDialect dialect, Container c, int typeId, String domainName)
    {
        String rawTableName = String.format("c%sd%s_%s", c.getRowId(), typeId, domainName);
        return new StorageNameGenerator(dialect).generateTableName(rawTableName);
    }

    /**
     * Return a TableInfo for this domain, creating if necessary. This method uses the DbSchema caching layer.
     */
    @Override
    @NotNull
    public TableInfo createTableInfoImpl(@NotNull Domain domain)
    {
        SchemaTableInfo sti = getSchemaTableInfo(domain);
        // TODO Can we handle everything _ProvisionedTable does in fixupProvisionedDomain()?
        _ProvisionedTable wrapper = new _ProvisionedTable(sti.getSchema(), sti.getName(), sti, domain);
        wrapper.wrapAllColumns();
        return wrapper;
    }

    @NotNull
    private SchemaTableInfo getSchemaTableInfo(@NotNull Domain domain, String schemaName, String tableName, DbSchema schema)
    {
        ProvisionedSchemaOptions options = new ProvisionedSchemaOptions(schema, tableName, domain);
        SchemaTableInfo sti = schema.getTable(options);
        if (null == sti)
            throw new TableNotFoundException(schemaName, tableName);
        return sti;
    }

    @NotNull
    private static DomainKind<?> getDomainKind(@NotNull Domain domain)
    {
        Objects.requireNonNull(domain);
        DomainKind<?> kind = domain.getDomainKind();
        if (null == kind)  // TODO: Consider using TableNotFoundException or something similar
            throw new IllegalArgumentException("Could not find information for domain (deleted?): " + domain.getTypeURI());
        return kind;
    }

    private static class _ProvisionedTable extends VirtualTable<UserSchema> implements UpdateableTableInfo
    {
        private final SchemaTableInfo _inner;
        private final Domain _domain;

        _ProvisionedTable(DbSchema schema, String name, SchemaTableInfo inner, Domain domain)
        {
            super(schema, name, null);
            _inner = inner;
            _domain = domain;
            if (StringUtils.isNotBlank(domain.getTitle()))
                setTitle(domain.getTitle());
        }

        public void wrapAllColumns()
        {
            CaseInsensitiveHashMap<DomainProperty> map = new CaseInsensitiveHashMap<>();
            for (var dp : _domain.getProperties())
            {
                if (null != dp.getPropertyDescriptor().getStorageColumnName())
                    map.put(dp.getPropertyDescriptor().getStorageColumnName(), dp);
            }
            var d = getSqlDialect();
            AliasManager am = new AliasManager(d);
            for (ColumnInfo from : _inner.getColumns())
            {
                var dp = map.get(from.getName());
                String name = null == dp ? from.getName() : dp.getName();
                AliasedColumn to = new AliasedColumn(this, new FieldKey(null, name), from, true)
                {
                    @Override
                    public DatabaseIdentifier getSelectIdentifier()
                    {
                        return _column.getSelectIdentifier();
                    }

                    @Override
                    public SQLFragment getValueSql(String tableAlias)
                    {
                        return super.getValueSql(tableAlias);
                    }
                };
                to.setAlias(d.makeDatabaseIdentifier(am.decideAlias(name)));

                if (null != dp)
                    to.setPropertyURI(dp.getPropertyURI());
                to.setHidden(from.isHidden());
                if (from.isUniqueIdField())
                {
                    // Issue 43760: We do not setUserEditable to false here because that hides it from the details view
                    to.setHasDbSequence(true);
                    to.setShownInInsertView(false);
                }
                this.addColumn(to);
            }
        }

        @Override
        public String toString()
        {
            // really shouldn't be doing this anymore, use getSelectName()?
            return _inner.toString();
        }

        @NotNull
        @Override
        public List<ColumnInfo> getAlternateKeyColumns()
        {
            // don't delegate to schema table info, it will return getPkColumns()
            return super.getAlternateKeyColumns();
        }

        @NotNull
        @Override
        public List<IndexDefinition> getUniqueIndices()
        {
            return _inner.getUniqueIndices();
        }

        @NotNull
        @Override
        public List<IndexDefinition> getAllIndices()
        {
            return _inner.getAllIndices();
        }

        @Override
        public boolean hasDbTriggers()
        {
            return _inner.hasDbTriggers();
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

        @Override
        public @Nullable SQLFragment getSQLName()
        {
            return _inner.getSQLName();
        }

        @Override
        public @Nullable DatabaseIdentifier getMetaDataIdentifier()
        {
            return _inner.getMetaDataIdentifier();
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
            return _domain.getDomainKind().getObjectUriColumn();
        }

        @Nullable
        @Override
        public String getObjectURIColumnName()
        {
            return _domain.getDomainKind().getObjectUriColumnName();
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
            CaseInsensitiveHashMap<String> map = new CaseInsensitiveHashMap<>();
            for (DomainProperty dp : _domain.getProperties())
            {
                String scn = dp.getPropertyDescriptor().getStorageColumnName();
                if (null != scn && !scn.equals(dp.getName()))
                    map.put(scn, dp.getName());
            }
            return map;
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
        public ParameterMapStatement insertStatement(Connection conn, User user) throws SQLException
        {
            return _inner.insertStatement(conn, user);
        }

        @Override
        public ParameterMapStatement updateStatement(Connection conn, User user, Set<String> columns)
        {
            return _inner.updateStatement(conn, user, columns);
        }

        @Override
        public ParameterMapStatement deleteStatement(Connection conn) throws SQLException
        {
            return _inner.deleteStatement(conn);
        }
    }

    /**
     * This is really an internal method, use createTableInfo() in most scenarios
     * This is public to support upgrade scenarios only.
     */
    @Override
    public String ensureStorageTable(Domain domain, DomainKind<?> kind, DbScope scope)
    {
        String tableName = domain.getStorageTableName();

        if (null == tableName)
        {
            try (var ignored = SpringActionController.ignoreSqlUpdates())
            {
                tableName = _create(scope, kind, domain, false);
            }
        }

        return tableName;
    }

    @Override
    public void createStorageTable(Domain domain, DomainKind<?> kind, DbScope scope)
    {
        _create(scope, kind, domain, true);
    }

    private void addTableIndices(Domain domain, Set<Index> indices, TableChange.IndexSizeMode sizeMode)
    {
        DbScope scope = validateDomain(domain);

        if (null == indices)
            throw new IllegalArgumentException("indices cannot be null");

        TableChange change = new TableChange(domain, ChangeType.AddIndices);

        if (null != sizeMode)
            change.setIndexSizeMode(sizeMode);

        change.setIndexedColumns(domain, indices);

        try (Transaction transaction = scope.ensureTransaction())
        {
            change.execute();
            transaction.commit();
        }
    }

    private DbScope validateDomain(Domain domain)
    {
        DomainKind<?> kind = domain.getDomainKind();
        DbScope scope = kind.getScope();

        String tableName = domain.getStorageTableName();
        if (null == tableName)
            throw new IllegalStateException("Table must already exist.");

        return scope;
    }

    public void dropTableIndices(Domain domain, Set<String> indexNames)
    {
        DbScope scope = validateDomain(domain);

        if (null == indexNames)
            throw new IllegalArgumentException("indices cannot be null");

        TableChange change = new TableChange(domain, ChangeType.DropIndicesByName);
        change.setIndicesToBeDroppedByName(indexNames);

        try (Transaction transaction = scope.ensureTransaction())
        {
            change.execute();
            transaction.commit();
        }
    }

    public void dropTableConstraints(Domain domain, Set<String> constraintNames)
    {
        DbScope scope = validateDomain(domain);

        if (null == constraintNames)
            throw new IllegalArgumentException("Constraints cannot be null");

        TableChange change = new TableChange(domain, ChangeType.DropConstraintsByName);
        change.setConstraintsToBeDroppedByName(constraintNames);

        try (Transaction transaction = scope.ensureTransaction())
        {
            change.execute();
            transaction.commit();
        }
    }

    @Override
    public void ensureTableIndices(@NotNull Domain domain)
    {
        ensureTableIndices(domain, () -> true);
    }

    @Override
    public void ensureTableIndices(@NotNull Domain domain, Supplier<Boolean> afterAddSupplier)
    {
        if (!domain.isProvisioned())
            throw new IllegalStateException("Domain " + domain.getName() + " is not provisioned!");

        // Issue 50059, acquiring the schema table info this way ensures that the domain fields are properly fixed up. See ProvisionedSchemaOptions.
        SchemaTableInfo schemaTableInfo = StorageProvisioner.get().getSchemaTableInfo(domain);
        if (schemaTableInfo != null)
        {
            DomainKind<?> kind = domain.getDomainKind();
            if (null == kind)
                throw new IllegalStateException("Domain kind of " + domain.getName() + " is null!");
            List<IndexDefinition> existingIndices = schemaTableInfo.getAllIndices();
            // Determine the desired indexes. Note that the index lists provided by Domain and DomainKind may overlap,
            // so we need to uniquify. Domain indices never specify "clustered" but DomainKind indices may (e.g.,
            // DatasetDomainKind), so compare using only column names and give preference to DomainKind.
            Set<Index> newIndices = new TreeSet<>(Comparator.comparing(index -> String.join("_", index.columnNames), String.CASE_INSENSITIVE_ORDER));
            newIndices.addAll(domain.getPropertyIndices());
            newIndices.addAll(kind.getPropertyIndices(domain));
            Set<String> toRemove = new HashSet<>();
            for (IndexDefinition def : existingIndices)
            {
                if (def.indexType() == TableInfo.IndexType.Primary)
                    continue;
                String[] columnNames = def.columns().stream()
                    .map(ColumnInfo::getColumnName)
                    .toArray(String[]::new);
                Index existingIndex = new Index(def.indexType() == TableInfo.IndexType.Unique, columnNames);
                boolean foundIt = false;
                for (Index propertyIndex : newIndices)
                {
                    if (Index.isSameIndex(propertyIndex, existingIndex))
                    {
                        foundIt = true;
                        newIndices.remove(propertyIndex);
                        break;
                    }
                }

                if (!foundIt)
                    toRemove.add(def.name());
            }

            if (!toRemove.isEmpty())
                dropTableIndices(domain, toRemove);

            boolean successfulAdd = afterAddSupplier.get();

            if (successfulAdd && !newIndices.isEmpty())
                addTableIndices(domain, newIndices, TableChange.IndexSizeMode.Normal);
        }
    }

    private static DbScope getScope(Domain domain)
    {
        return getDomainKind(domain).getScope();
    }

    private static SqlDialect getSqlDialect(Domain domain)
    {
        DbScope scope = getScope(domain);
        if (null != scope)
            return scope.getSqlDialect();
        return CoreSchema.getInstance().getSqlDialect();
    }

    @Override
    public SchemaTableInfo getSchemaTableInfo(Domain domain)
    {
        DomainKind<?> kind = getDomainKind(domain);

        DbScope scope = kind.getScope();

        String schemaName = kind.getStorageSchemaName();

        if (null == scope || null == schemaName)
            throw new IllegalArgumentException();

        String tableName = ensureStorageTable(domain, kind, scope);

        assert kind.getSchemaType() == DbSchemaType.Provisioned : "provisioned DomainKinds must declare a schema type of DbSchemaType.Provisioned, but type " + kind + " declared " + kind.getSchemaType();

        DbSchema schema = scope.getSchema(schemaName, kind.getSchemaType());

        return getSchemaTableInfo(domain, schemaName, tableName, schema);
    }

    @Override
    public void ensureBaseProperties(Domain domain)
    {
        DomainKind<?> kind = domain.getDomainKind();
        DbScope scope = kind.getScope();

        String tableName = ensureStorageTable(domain, kind, scope);
        TableInfo storageTable = DbSchema.get(domain.getDomainKind().getStorageSchemaName(), DbSchemaType.Provisioned).getTable(tableName);
        TableChange change = new TableChange(domain, ChangeType.AddColumns);

        for (PropertyStorageSpec prop : kind.getBaseProperties(domain))
        {
            if (prop.getName() == null || prop.getName().isEmpty())
                throw new IllegalArgumentException("Can't add property with no name.");

            if (null == storageTable.getColumn(prop.getName()))
                change.addColumn(prop);
        }

        if (!change.getColumns().isEmpty())
            change.execute();
    }

    public void fixupProvisionedDomain(SchemaTableInfo ti, DomainKind<?> kind, Domain domain, String tableName)
    {
        assert !ti.isLocked();

        int index = 0;

        // Some domains have property descriptors for base properties
        Set<String> basePropertyNames = new HashSet<>();
        for (PropertyStorageSpec s : kind.getBaseProperties(domain))
        {
            BaseColumnInfo c = (BaseColumnInfo)ti.getColumn(s.getName());
            basePropertyNames.add(s.getName().toLowerCase());

            if (null == c)
            {
                log.info("Column not found in storage table: {}.{}", tableName, s.getName());
                continue;
            }

            // The columns coming back from JDBC metadata aren't necessarily in the same order that the domain
            // wants them based on its current property order
            ti.setColumnIndex(c, index++);

            // Use column name casing from the storage spec
            c.setName(s.getName());
        }

        Supplier<Map<DomainProperty, Object>> defaultsSupplier = null;
        Set<String> seenProperties = new HashSet<>();
        for (DomainProperty p : domain.getProperties())
        {
            if (kind.hasPropertiesIncludeBaseProperties() && basePropertyNames.contains(p.getName().toLowerCase()))
                continue;

            if (!p.isDeleted() && !seenProperties.add(p.getName()))
            {
                // There is more than property descriptor with this name attached to this table. This shouldn't happen, but we've seen
                // at least one occurrence of it in a dev's db, thought to have been caused by in-flux code in 12/2013. The result would
                // be calls to retrieve metadata would throw an uninformative IllegalStateException on an array index out of bounds. Throwing this
                // RuntimeException instead gives better diagnostic info.
                throw new RuntimeException("Duplicate property descriptor name found for: " + tableName + "." + p.getName());
            }

            BaseColumnInfo c = (BaseColumnInfo)ti.getColumn(p.getPropertyDescriptor().getStorageColumnName());

            if (null == c)
            {
                if (p.getPropertyDescriptor().getStorageColumnName() == null)
                {
                    log.warn("No storage column name set for property {} on table {}", p.getName(), tableName);
                }
                else
                {
                    log.info("Column not found in storage table: {}.{}", tableName, p.getPropertyDescriptor().getStorageColumnName());
                }
                continue;
            }

            // The columns coming back from JDBC metadata aren't necessarily in the same order that the domain
            // wants them based on its current property order
            ti.setColumnIndex(c, index++);
            defaultsSupplier = PropertyColumn.copyAttributes(null, c, p, p.getContainer(), null, defaultsSupplier);

            if (p.isMvEnabled())
            {
                c.setDisplayColumnFactory(new MVDisplayColumnFactory());

                var mvColumn = (BaseColumnInfo)getMvIndicatorColumn(ti, p.getPropertyDescriptor(), "No MV column found for '" + p.getName() + "' in table '" + domain.getName() + "'");
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

    @Override
    public boolean repairDomain(Container c, String domainUri, BindException errors)
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
            DomainKind<?> kind = domain.getDomainKind();
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
                    TableInfo table = DbSchema.get(report.getSchemaName(), DbSchemaType.Provisioned).getTable(report.getTableName());
                    PropertyDescriptor pd = st.prop.getPropertyDescriptor();
                    if (st.prop.getPropertyDescriptor().getStorageColumnName() == null && null != table.getColumn(pd.getName()))
                    {
                        pd.setStorageColumnName(pd.getName());
                        OntologyManager.updatePropertyDescriptor(pd);
                        continue;
                    }

                    if (st.colName == null)
                    {
                        adds.addColumn(pd);
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

    @Override
    public ProvisioningReport getProvisioningReport()
    {
        return getProvisioningReport(null);
    }

    @Override
    public ProvisioningReport getProvisioningReport(@Nullable String domainuri)
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
            for (DomainKind<?> dk : PropertyService.get().getDomainKinds())
            {
                if (dk instanceof TestDomainKind)
                    continue;
                String schemaName = dk.getStorageSchemaName();
                if (null != schemaName)
                {
                    assert !"temp".equalsIgnoreCase(schemaName);
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
            DomainKind<?> kind = domain.getDomainKind();
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
                        .map(spec -> spec.getName().toLowerCase()).toList());
            }

            for (DomainProperty domainProp : domain.getProperties())
            {
                if (basePropertyNames.contains(domainProp.getName().toLowerCase()))
                    continue;

                ProvisioningReport.ColumnStatus status = new ProvisioningReport.ColumnStatus();
                domainReport.getColumns().add(status);
                status.prop = domainProp;
                PropertyDescriptor propDescriptor = domainProp.getPropertyDescriptor();

                if (null == propDescriptor.getStorageColumnName() && hardColumnNames.contains(domainProp.getName()))
                {
                    domainReport.addError(String.format("database table %s.%s column '%s' is missing the storage column name.", domainReport.getSchemaName(), domainReport.getTableName(), domainProp.getName()));
                    status.fix = "Add storage column name '" + domainProp.getName() + "' to property descriptor";
                    status.hasProblem = true;
                    hardColumnNames.remove(propDescriptor.getName());
                }
                else if (hardColumnNames.remove(propDescriptor.getStorageColumnName()))
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
                if (hardColumnNames.remove(mvColName)) // hashed
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
                domainReport.getColumns().add(status);
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
            // Copy the set because we modify it in the loop
            for (String name : new HashSet<>(hardColumnNames))
            {
                if (name.endsWith("_" + MvColumn.MV_INDICATOR_SUFFIX))
                    continue;
                domainReport.addError(String.format("database table %s.%s has column '%s' without a property descriptor",
                        domainReport.getSchemaName(), domainReport.getTableName(), name));
                ProvisioningReport.ColumnStatus status = new ProvisioningReport.ColumnStatus();
                domainReport.getColumns().add(status);

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
                domainReport.getColumns().add(status);
                status.mvColName = name;
                status.fix = "Delete column '" + name + "'";
                status.hasProblem = true;
            }
            if (!domainReport.getErrors().isEmpty())
            {
                ExperimentUrls urls = PageFlowUtil.urlProvider(ExperimentUrls.class);
                ActionURL fix = urls.getRepairTypeURL(domain.getContainer());
                fix.addParameter("domainUri", domain.getTypeURI());
                domainReport.addError("See this page for more info: " + fix.getURIString());
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

    private class ProvisionedSchemaOptions extends SchemaTableOptions
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
            DomainKind<?> kind = domain.getDomainKind();
            kind.afterLoadTable(ti, domain);

            fixupProvisionedDomain(ti, kind, domain, ti.getName());
        }
    }

    @TestTimeout(120)
    @TestWhen(TestWhen.When.BVT)
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
            domain = PropertyService.get().getDomain(domain.getTypeId(), true);
        }

        @After
        public void after() throws Exception
        {
            if (domain != null)
            {
                StorageProvisioner.get().drop(domain);
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
            domain = Objects.requireNonNull(PropertyService.get().getDomain(domain.getTypeId()));

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
            domain = Objects.requireNonNull(PropertyService.get().getDomain(domain.getTypeId(), true));

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
            domain = Objects.requireNonNull(PropertyService.get().getDomain(domain.getTypeId(), true));

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
            domain = Objects.requireNonNull(PropertyService.get().getDomain(domain.getTypeId(), true));

            propB = domain.getPropertyByName(propNameB);
            propB.setMvEnabled(false);

            domain.save(new User());
            domain = Objects.requireNonNull(PropertyService.get().getDomain(domain.getTypeId(), true));

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
        public void testProvisioningReport()
        {
            ProvisioningReport report = StorageProvisioner.get().getProvisioningReport();
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
            Assert.assertTrue(sb.toString(), success);
        }

        @Test
        public void testEnsureBaseProperties() throws Exception
        {
            final Set<PropertyStorageSpec> baseProperties = new LinkedHashSet<>();
            DomainKind<?> k = new AbstractDomainKind<String>()
            {
                @Override
                public Set<PropertyStorageSpec> getBaseProperties(Domain domain)
                {
                    return baseProperties;
                }

                @Override
                public String getStorageSchemaName()
                {
                    return "temp";
                }

                @Override
                public DbScope getScope()
                {
                    return CoreSchema.getInstance().getScope();
                }

                @Override
                public String getKindName()
                {
                    return "test";
                }

                @Override
                public Class<String> getTypeClass()
                {
                    return String.class;
                }

                @Override
                public String getTypeLabel(Domain domain)
                {
                    return "test";
                }

                @Override
                public SQLFragment sqlObjectIdsInDomain(Domain domain)
                {
                    return new SQLFragment("NULL");
                }

                @Override
                public ActionURL urlShowData(Domain domain, ContainerUser containerUser)
                {
                    return null;
                }

                @Override
                public @Nullable ActionURL urlEditDefinition(Domain domain, ContainerUser containerUser)
                {
                    return null;
                }

                @Override
                public @Nullable Priority getPriority(String object)
                {
                    return null;
                }
            };

            User user = TestContext.get().getUser();
            Container c = JunitUtil.getTestContainer();

            // create and save a domain with one base property
            baseProperties.add(new PropertyStorageSpec("first", JdbcType.INTEGER));
            String uri = "StorageProvisionImpl/" + GUID.makeGUID();
            DomainImpl d = null;
            try
            {
                d = new DomainImpl(c, uri, "test", true)
                {
                    @Override
                    public @NotNull DomainKind<?> getDomainKind()
                    {
                        return k;
                    }
                };
                d.save(user);
                StorageProvisioner.get().ensureStorageTable(d, k, k.getScope());

                // check that prop exists
                d = Objects.requireNonNull(PropertyServiceImpl.get().getDomain(c, uri));
                d._dd.setDomainKind(k); // needed for d.delete() otherwise it will try to lookup the DomainKind
                TableInfo t = StorageProvisioner.get().getSchemaTableInfo(d);
                assertNotNull(t.getColumn("first"));

                // add a base property
                baseProperties.add(new PropertyStorageSpec("second", JdbcType.VARCHAR));
                StorageProvisioner.get().ensureBaseProperties(d);

                // check that new property exists
                t = StorageProvisioner.get().getSchemaTableInfo(d);
                assertNotNull(t.getColumn("second"));
            }
            finally
            {
                if (null != d)
                    d.delete(user);
            }
        }

        private void addPropertyB() throws Exception
        {
            DomainProperty dp = domain.addProperty();
            dp.setPropertyURI(propNameB + "#" + propNameB);
            dp.setName(propNameB);

            domain.save(new User());
            domain = PropertyService.get().getDomain(domain.getTypeId(), true);
        }

        private @Nullable ColumnInfo getJdbcColumnMetadata(Domain domain, String columnName) throws Exception
        {
            DomainKind<?> kind = domain.getDomainKind();
            String schemaName = kind.getStorageSchemaName();
            String tableName = domain.getStorageTableName();

            DbSchema schema = kind.getScope().getSchema(schemaName, kind.getSchemaType());
            SchemaTableInfo ti = schema.getTable(tableName);

            // Slight overkill, given that tests merely verify column existence, but might as well reuse existing code
            Collection<BaseColumnInfo> cols = BaseColumnInfo.createFromDatabaseMetaData(schemaName, ti, columnName);

            return cols.isEmpty() ? null : cols.iterator().next();
        }
    }
}
