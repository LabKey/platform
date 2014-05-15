/*
 * Copyright (c) 2010-2014 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MVDisplayColumnFactory;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableChange;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
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
import org.labkey.api.query.AliasManager;
import org.labkey.api.security.User;
import org.labkey.api.test.TestTimeout;
import org.labkey.api.util.CPUTimer;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.data.xml.TableType;
import org.springframework.validation.BindException;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
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

/**
 * User: newton
 * Date: Aug 11, 2010
 * Time: 2:52:33 PM
 */

public class StorageProvisioner
{
    private static final Logger log = Logger.getLogger(StorageProvisioner.class);
    private static final CPUTimer create = new CPUTimer("StorageProvisioner.create");
    private static boolean allowRenameOfColumnsDuringUpgrade = false;

    // this is a bit of hackery to avoid worse hackery. avoid rename of built-in columns check.
    public static void setAllowRenameOfColumnsDuringUpgrade(boolean b)
    {
        allowRenameOfColumnsDuringUpgrade = b;
    }


    private static String _create(DbScope scope, DomainKind kind, Domain domain)
    {
        assert create.start();

        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            // reselect in a transaction
            DomainDescriptor dd = OntologyManager.getDomainDescriptor(domain.getTypeId(), true);
            if (null == dd)
            {
                Logger.getLogger(StorageProvisioner.class).warn("Can't find domain desciptor: " + domain.getTypeId() + " " + domain.getTypeURI());
                return null;
            }
            String tableName = dd.getStorageTableName();
            if (null != tableName)
                return tableName;

            tableName = makeTableName(kind, domain);

            TableChange change = new TableChange(kind.getStorageSchemaName(), tableName, TableChange.ChangeType.CreateTable);

            Set<String> base = Sets.newCaseInsensitiveHashSet();

            for (PropertyStorageSpec spec : kind.getBaseProperties())
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

            change.setIndexedColumns(kind.getPropertyIndices());
            change.setForeignKeys(domain.getPropertyForeignKeys());

            execute(scope, scope.getConnection(), change);

            dd.setStorageTableName(tableName);
            dd.setStorageSchemaName(kind.getStorageSchemaName());
            OntologyManager.updateDomainDescriptor(dd);

            kind.invalidate(domain);

            transaction.commit();

            return tableName;
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            assert create.stop();
        }
    }

    private static PropertyStorageSpec makeMvColumn(DomainProperty property)
    {
        return new PropertyStorageSpec(property.getName() + "_" + MvColumn.MV_INDICATOR_SUFFIX, JdbcType.VARCHAR, 50);
    }


    public static void drop(Domain domain)
    {
        if (null == domain)
            return;
        DomainKind kind = domain.getDomainKind();
        if (kind == null)
        {
            log.warn("Domain " + domain.getName() + " has no DomainKind, it cannot be dropped. URI: " + domain.getTypeURI(), new IllegalStateException());
            return;
        }

        DbScope scope = kind.getScope();
        String schemaName = kind.getStorageSchemaName();
        if (scope == null || schemaName == null)
            return;

        String tableName = domain.getStorageTableName();
        if (null == tableName)
        {
            return;
        }

        TableChange change = new TableChange(kind.getStorageSchemaName(), tableName, TableChange.ChangeType.DropTable);


        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            Connection con = transaction.getConnection();
            execute(scope, con, change);
            kind.invalidate(domain);
            transaction.commit();
        }
        catch (RuntimeSQLException e)
        {
            log.warn(String.format("Failed to drop table in schema %s for domain %s - %s",
                    schemaName, domain.getName(), e.getMessage()), e);
            throw e;
        }
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

        TableChange change = new TableChange(kind.getStorageSchemaName(), tableName, TableChange.ChangeType.AddColumns);

        Set<String> base = Sets.newCaseInsensitiveHashSet();
        for (PropertyStorageSpec s : kind.getBaseProperties())
            base.add(s.getName());

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
            }
            if (prop.isMvEnabled())
            {
                change.addColumn(makeMvColumn(prop));
            }
        }

        Connection con = null;

        try
        {
            con = scope.getConnection();
            execute(scope, con, change);
            kind.invalidate(domain);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            scope.releaseConnection(con);
        }
    }

    public static void dropMvIndicator(DomainProperty... props)
    {
        assert (props.length > 0);
        Domain domain = props[0].getDomain();
        DomainKind kind = domain.getDomainKind();
        DbScope scope = kind.getScope();

        // should be in a trasaction with propertydescriptor changes
        assert scope.isTransactionActive();

        String tableName = domain.getStorageTableName();

        TableChange change = new TableChange(kind.getStorageSchemaName(), tableName, TableChange.ChangeType.DropColumns);
        for (DomainProperty prop : props)
        {
            change.addColumn(makeMvColumn(prop));
        }

        Connection con = null;

        try
        {
            con = scope.getConnection();
            execute(scope, con, change);
            kind.invalidate(domain);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            scope.releaseConnection(con);
        }
    }

    public static void addMvIndicator(DomainProperty... props)
    {
        assert (props.length > 0);
        Domain domain = props[0].getDomain();
        DomainKind kind = domain.getDomainKind();
        DbScope scope = kind.getScope();

        // should be in a transaction with propertydescriptor changes
        assert scope.isTransactionActive();

        String tableName = domain.getStorageTableName();
        if (null == tableName)
            tableName = makeTableName(kind, domain);

        TableChange change = new TableChange(kind.getStorageSchemaName(), tableName, TableChange.ChangeType.AddColumns);
        for (DomainProperty prop : props)
        {
            change.addColumn(makeMvColumn(prop));
        }

        Connection con = null;

        try
        {
            con = scope.getConnection();
            execute(scope, con, change);
            kind.invalidate(domain);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            scope.releaseConnection(con);
        }
    }


    public static void dropProperties(Domain domain, Collection<DomainProperty> properties)
    {
        DomainKind kind = domain.getDomainKind();
        DbScope scope = kind.getScope();

        // should be in a trasaction with propertydescriptor changes
        assert scope.isTransactionActive();

        String tableName = domain.getStorageTableName();

        Set<String> base = Sets.newCaseInsensitiveHashSet();
        for (PropertyStorageSpec s : kind.getBaseProperties())
            base.add(s.getName());

        TableChange change = new TableChange(kind.getStorageSchemaName(), tableName, TableChange.ChangeType.DropColumns);
        for (DomainProperty prop : properties)
        {
            if (base.contains(prop.getName()))
                continue;
            change.addColumn(prop.getPropertyDescriptor());
            if (prop.isMvEnabled())
            {
                change.addColumn(makeMvColumn(prop));
            }
        }

        if (change.getColumns().isEmpty())
        {
            // Nothing to do, so don't try to run an ALTER TABLE that doesn't actually do anything
            return;
        }

        Connection con = null;

        try
        {
            con = scope.getConnection();
            execute(scope, con, change);
            kind.invalidate(domain);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            scope.releaseConnection(con);
        }
    }

    /**
     * @param propsRenamed map where keys are the current properties including the new names, values are the old PropertyDescriptors
     */
    public static void renameProperties(Domain domain, Map<DomainProperty, PropertyDescriptor> propsRenamed)
    {
        DomainKind kind = domain.getDomainKind();
        DbScope scope = kind.getScope();

        // should be in a trasaction with propertydescriptor changes
        assert scope.isTransactionActive();

        Connection con = null;

        try
        {
            con = scope.getConnection();
            TableChange renamePropChange = new TableChange(kind.getStorageSchemaName(), domain.getStorageTableName(), TableChange.ChangeType.RenameColumns);

            Set<String> base = Sets.newCaseInsensitiveHashSet();
            for (PropertyStorageSpec s : kind.getBaseProperties())
                base.add(s.getName());

            for (Map.Entry<DomainProperty, PropertyDescriptor> rename : propsRenamed.entrySet())
            {
                PropertyStorageSpec prop = new PropertyStorageSpec(rename.getKey().getPropertyDescriptor());
                String oldPropName = rename.getValue().getName();
                renamePropChange.addColumnRename(oldPropName, prop.getName());


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
                if (rename.getValue().isMvEnabled())
                {
                    renamePropChange.addColumnRename(PropertyStorageSpec.getMvIndicatorColumnName(oldPropName), prop.getMvIndicatorColumnName());
                }
            }

            execute(scope, con, renamePropChange);
            kind.invalidate(domain);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            scope.releaseConnection(con);
        }
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
     * Return a TableInfo for this domain, creating if necessary. This method uses the DbSchema caching layer
     *
     * @param parentSchema Schema to attach table to, should NOT be the physical db schema of the storage provider
     */
    @NotNull
    public static SchemaTableInfo createCachedTableInfo(Domain domain, DbSchema parentSchema)
    {
        return createTableInfo(domain, parentSchema, null, true);
    }

    /**
     * return a TableInfo for this domain, creating if necessary
     * this method DOES NOT cache
     *
     * @param parentSchema Schema to attach table to, should NOT be the physical db schema of the storage provider
     * @deprecated usages should be migrated to createCachedTableInfo
     */
    @NotNull
    public static SchemaTableInfo createTableInfo(Domain domain, DbSchema parentSchema)
    {
        return createTableInfo(domain, parentSchema, null, false);
    }

    /**
     * return a TableInfo for this domain, creating if necessary
     * this method DOES NOT cache
     *
     * @param parentSchema Schema to attach table to, should NOT be the physical db schema of the storage provider
     * @deprecated usages should be migrated to createCachedTableInfo
     */
    @NotNull
    public static SchemaTableInfo createTableInfo(Domain domain, DbSchema parentSchema, @Nullable String title)
    {
        return createTableInfo(domain, parentSchema, title, false);
    }

    @NotNull
    private static SchemaTableInfo createTableInfo(Domain domain, DbSchema parentSchema, @Nullable String title, boolean cache)
    {
        DomainKind kind = domain.getDomainKind();
        if (null == kind)
            throw new IllegalArgumentException("Could not find information for domain (deleted?): " + domain.getTypeURI());

        DbScope scope = kind.getScope();
        String schemaName = kind.getStorageSchemaName();

        if (null == scope || null == schemaName)
            throw new IllegalArgumentException();

        String tableName = domain.getStorageTableName();

        if (null == tableName)
            tableName = _create(scope, kind, domain);

        SchemaTableInfo ti;
        if (cache)
        {
            assert kind.getSchemaType() == DbSchemaType.Provisioned : "provisioned DomainKinds that are cached must declare a schema type of : DbSchemaType.Provisioned";

            DbSchema schema = scope.getSchema(schemaName, kind.getSchemaType());
            ProvisionedSchemaOptions options = new ProvisionedSchemaOptions(schema, tableName, domain);

            ti = schema.getTable(options);
        }
        else
        {
            // non-cached legacy version
            ti =  new SchemaTableInfo(parentSchema, DatabaseTableType.TABLE, tableName, tableName, schemaName + ".\"" + tableName + "\"", title);
            // TODO: could DataSetDefinition use this mechanism for metadata?
            if (null != kind.getMetaDataSchemaName())
            {
                ti.setMetaDataSchemaName(schemaName);
                TableType xmlTable = DbSchema.get(kind.getMetaDataSchemaName()).getTableXmlMap().get(kind.getMetaDataTableName());
                ti.loadTablePropertiesFromXml(xmlTable, true);
            }
            else
            {
                ti.setMetaDataSchemaName(schemaName);

            }
            fixupProvisionedDomain(ti, kind, domain, tableName);
        }
        return ti;
    }

    public static void fixupProvisionedDomain(SchemaTableInfo ti, DomainKind kind, Domain domain, String tableName)
    {
        assert !ti.isLocked();

        int index = 0;

        // Some domains have property descriptors for base properties
        Set<String> basePropertyNames = new HashSet<>();
        for (PropertyStorageSpec s : kind.getBaseProperties())
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
                // at least one occurance of it in a dev's db, thought to have been caused by in-flux code in 12/2013. The result would
                // be calls to retrieve metadata would throw an uniformative IllegalStateException on an array index out of bounds. Throwing this
                // runtimeexception instead gives better diagnostic info.
                throw new RuntimeException("Duplicate property descriptor name found for: " + tableName + "." + p.getName());
            }

            ColumnInfo c = ti.getColumn(p.getName());

            if (null == c)
            {
                Logger.getLogger(StorageProvisioner.class).info("Column not found in storage table: " + tableName + "." + p.getName());
                continue;
            }

            // The columns coming back from JDBC metadata aren't necessarily in the same order that the domain
            // wants them based on its current property order
            ti.setColumnIndex(c, index++);
            PropertyColumn.copyAttributes(null, c, p.getPropertyDescriptor(), p.getContainer(), null);

            if (p.isMvEnabled())
            {
                c.setDisplayColumnFactory(new MVDisplayColumnFactory());

                ColumnInfo mvColumn = ti.getColumn(PropertyStorageSpec.getMvIndicatorColumnName(p.getName()));
                assert mvColumn != null : "No MV column found for " + p.getName();
                if (mvColumn != null)
                {
                    c.setMvColumnName(mvColumn.getFieldKey());
                    mvColumn.setMvIndicatorColumn(true);
                    // The UI for the main column will include MV input as well, so no need for another column in insert/update views
                    mvColumn.setShownInUpdateView(false);
                    mvColumn.setShownInInsertView(false);
                }
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
        DbScope scope = DbSchema.get("core").getScope();

        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            Connection conn = scope.getConnection();
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

            TableChange drops = new TableChange(kind.getStorageSchemaName(), domain.getStorageTableName(), TableChange.ChangeType.DropColumns);
            boolean hasDrops = false;
            TableChange adds = new TableChange(kind.getStorageSchemaName(), domain.getStorageTableName(), TableChange.ChangeType.AddColumns);
            boolean hasAdds = false;

            for (ProvisioningReport.ColumnStatus st : report.getColumns())
            {
                if (!st.hasProblem)
                    continue;
                if (st.spec == null && st.prop == null)
                {
                    if (null != st.colName)
                    {
                        drops.dropColumnExactName(st.colName);
                        hasDrops = true;
                    }
                    if (null != st.mvColName)
                    {
                        drops.dropColumnExactName(st.mvColName);
                        hasDrops = true;
                    }
                }
                else if (st.prop != null)
                {
                    if (st.colName == null)
                    {
                        adds.addColumn(st.prop.getPropertyDescriptor());
                        hasAdds = true;
                    }
                    if (st.mvColName == null && st.prop.isMvEnabled())
                    {
                        adds.addColumn(makeMvColumn(st.prop));
                        hasAdds = true;
                    }
                    if (st.mvColName != null && !st.prop.isMvEnabled())
                    {
                        drops.dropColumnExactName(st.mvColName);
                        hasDrops = true;
                    }
                }
            }

            if (hasDrops)
                execute(scope, conn, drops);
            if (hasAdds)
                execute(scope, conn, adds);
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

    private static void execute(DbScope scope, Connection conn, TableChange change)
    {
        for (String sql : scope.getSqlDialect().getChangeStatements(change))
        {
            try
            {
                log.debug("Will issue: " + sql);
                conn.prepareStatement(sql).execute();
            }
            catch (SQLException e)
            {
                log.error(sql);
                throw new RuntimeSQLException(e);
            }
        }
    }

    public static ProvisioningReport getProvisioningReport()
    {
        return getProvisioningReport(null);
    }

    public static ProvisioningReport getProvisioningReport(@Nullable String domainuri)
    {
        final ProvisioningReport report = new ProvisioningReport();
        SQLFragment sql = new SQLFragment("SELECT domainid, name, storageschemaname, storagetablename FROM " +
                OntologyManager.getTinfoDomainDescriptor().getFromSQL("dd"));
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
                DbSchema schema = DbSchema.get(schemaName.getName(), DbSchemaType.Unknown);

                for (String name : schema.getTableNames())
                {
                    if (!nonProvisionedTableMap.get(schemaName).contains(name.toLowerCase()))
                        provisionedTables.add(schemaName.append(name));
                }
            }
        }

        new SqlSelector(OntologyManager.getExpSchema(), sql).forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
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
                    schema = DbSchema.createFromMetaData(DbScope.getLabkeyScope(), domainReport.getSchemaName(), DbSchemaType.Bare);
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
                for (PropertyStorageSpec spec : kind.getBaseProperties())
                    basePropertyNames.add(spec.getName().toLowerCase());
            }

            for (DomainProperty domainProp : domain.getProperties())
            {
                if (basePropertyNames.contains(domainProp.getName().toLowerCase()))
                    continue;

                ProvisioningReport.ColumnStatus status = new ProvisioningReport.ColumnStatus();
                domainReport.columns.add(status);
                status.prop = domainProp;
                if (hardColumnNames.remove(domainProp.getName()))
                    status.colName = domainProp.getName();
                else
                {
                    domainReport.addError(String.format("database table %s.%s did not contain expected column '%s'", domainReport.getSchemaName(), domainReport.getTableName(), domainProp.getName()));
                    status.fix = "Create column '" + domainProp.getName() + "'";
                    status.hasProblem = true;
                }
                if (hardColumnNames.remove(PropertyStorageSpec.getMvIndicatorColumnName(domainProp.getName())))
                    status.mvColName = PropertyStorageSpec.getMvIndicatorColumnName(domainProp.getName());
                if (null == status.mvColName && domainProp.isMvEnabled())
                {
                    domainReport.addError(String.format("database table %s.%s has mvindicator enabled but expected '%s' column wasn't present",
                            domainReport.getSchemaName(), domainReport.getTableName(), PropertyStorageSpec.getMvIndicatorColumnName(domainProp.getName())));
                    status.fix += (status.fix.isEmpty() ? "C" : " and c") + "reate column '" + PropertyStorageSpec.getMvIndicatorColumnName(domainProp.getName()) + "'";
                    status.hasProblem = true;
                }
                if (null != status.mvColName && !domainProp.isMvEnabled())
                {
                    domainReport.addError(String.format("database table %s.%s has mvindicator disabled but '%s' column is present",
                            domainReport.getSchemaName(), domainReport.getTableName(), PropertyStorageSpec.getMvIndicatorColumnName(domainProp.getName())));
                    status.fix += (status.fix.isEmpty() ? "D" : " and d") +  "rop column '" + status.mvColName + "'";
                    status.hasProblem = true;
                }
            }
            for (PropertyStorageSpec spec : kind.getBaseProperties())
            {
                ProvisioningReport.ColumnStatus status = new ProvisioningReport.ColumnStatus();
                domainReport.columns.add(status);
                status.spec = spec;
                if (hardColumnNames.remove(spec.getName()))
                    status.colName = spec.getName();
                else
                {
                    domainReport.addError(String.format("database table %s.%s did not contain expected column '%s'", domainReport.getSchemaName(), domainReport.getTableName(), spec.getName()));
                    status.fix = "'" + spec.getName() + "' is a built-in column.  Contact LabKey support.";
                    status.hasProblem = true;
                }
                if (hardColumnNames.remove(spec.getMvIndicatorColumnName()))
                    status.mvColName = spec.getMvIndicatorColumnName();
                if (null == status.mvColName && spec.isMvEnabled())
                {
                        domainReport.addError(String.format("database table %s.%s has mvindicator enabled but expected '%s' column wasn't present",
                                domainReport.getSchemaName(), domainReport.getTableName(), spec.getMvIndicatorColumnName()));
                        status.fix = "'" + spec.getName() + "' is a built-in column.  Contact LabKey support.";
                        status.hasProblem = true;
                }
                if (null != status.mvColName && !spec.isMvEnabled())
                {
                        domainReport.addError(String.format("database table %s.%s has mvindicator disabled but '%s' column is present",
                                domainReport.getSchemaName(), domainReport.getTableName(), spec.getMvIndicatorColumnName()));
                        status.fix = "'" + spec.getName() + "' is a built-in column.  Contact LabKey support.";
                        status.hasProblem = true;
                }
            }
            for (String name : hardColumnNames.toArray(new String[0]))
            {
                if (name.endsWith("_" + MvColumn.MV_INDICATOR_SUFFIX))
                    continue;
                domainReport.addError(String.format("database table %s.%s has column '%s' without a property descriptor",
                        domainReport.getSchemaName(), domainReport.getTableName(), name));
                ProvisioningReport.ColumnStatus status = new ProvisioningReport.ColumnStatus();
                domainReport.columns.add(status);

                hardColumnNames.remove(name);
                status.colName = name;
                if (hardColumnNames.remove(PropertyStorageSpec.getMvIndicatorColumnName(name)))
                    status.mvColName = PropertyStorageSpec.getMvIndicatorColumnName(name);
                status.fix = "Delete column '" + name + "'" + (null == status.mvColName ? "" : " and column '" + status.mvColName + "'");
                status.hasProblem = true;
            }
            for (String name : hardColumnNames)
            {
                domainReport.addError(String.format("database table %s.%s has column '%s' without a property descriptor",
                        domainReport.getSchemaName(), domainReport.getTableName(), name));
                ProvisioningReport.ColumnStatus status = new ProvisioningReport.ColumnStatus();
                domainReport.columns.add(status);
                status.mvColName = name;
                status.fix = "Delete column '" + name + "'";
                status.hasProblem = true;
            }
            if (!domainReport.errors.isEmpty())
            {
                ActionURL fix = new ActionURL("experiment-types", "repair", domain.getContainer());
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


    public static class ProvisioningReport
    {
        private Set<DomainReport> unprovisionedDomains = new HashSet<>();
        private Set<DomainReport> provisionedDomains = new HashSet<>();
        private List<String> globalErrors = new ArrayList<>();

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
            Integer id;
            String name;
            String schemaName;
            String tableName;
            List<String> errors = new ArrayList<>();
            List<ColumnStatus> columns = new ArrayList<>();

            public Integer getId()
            {
                return id;
            }

            public void setId(Integer id)
            {
                this.id = id;
            }

            public String getName()
            {
                return name;
            }

            public void setName(String name)
            {
                this.name = name;
            }

            public String getSchemaName()
            {
                return schemaName;
            }

            public void setSchemaName(String schemaName)
            {
                this.schemaName = schemaName;
            }

            public String getTableName()
            {
                return tableName;
            }

            public void setTableName(String tableName)
            {
                this.tableName = tableName;
            }

            public void addError(String message)
            {
                errors.add(message);
            }

            public List<String> getErrors()
            {
                return errors;
            }

            public List<ColumnStatus> getColumns()
            {
                return columns;
            }
        }
    }

    public static class ProvisionedSchemaOptions extends DbScope.SchemaTableOptions
    {
        private Domain _domain;

        public ProvisionedSchemaOptions(DbSchema schema, String tableName, Domain domain)
        {
            super(schema, tableName);
            _domain = domain;
        }
        public Domain getDomain()
        {
            return _domain;
        }
    }

    @TestTimeout(120)
    public static class TestCase extends Assert
    {
        Container container = JunitUtil.getTestContainer();
        Domain domain;
        final String notNullPropName = "a_" + System.currentTimeMillis();
        final String propNameB = "b_" + System.currentTimeMillis();
        final String propBMvColumnName = PropertyStorageSpec.getMvIndicatorColumnName(propNameB).toLowerCase();

        @Before
        public void before() throws Exception
        {
            String domainName = "testdomain_" + System.currentTimeMillis();

            Lsid lsid = new Lsid("TestDatasetDomainKind", "Folder-" + container.getRowId(), domainName);
            domain = PropertyService.get().createDomain(container, lsid.toString(), domainName);
            domain.save(new User());
            DomainKind kind = domain.getDomainKind();
            StorageProvisioner.createTableInfo(domain, DbSchema.get(kind.getStorageSchemaName(), kind.getSchemaType()));
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
            Assert.assertNotNull("adding a property added a new column to the hard table",
                    getJdbcColumnMetadata(domain.getDomainKind().getStorageSchemaName(),
                            domain.getStorageTableName(), propNameB));
        }

        @Test
        public void testDropProperty() throws Exception
        {
            addPropertyB();
            DomainProperty propB = domain.getPropertyByName(propNameB);
            propB.delete();
            domain.save(new User());
            Assert.assertNull("column for dropped property is gone", getJdbcColumnMetadata(domain.getDomainKind().getStorageSchemaName(),
                    domain.getStorageTableName(), propNameB));
        }

        @Test
        public void testRenameProperty() throws Exception
        {
            addPropertyB();
            DomainProperty propB = domain.getPropertyByName(propNameB);
            String newName = "new_" + propNameB;
            propB.setName(newName);
            domain.save(new User());
            Assert.assertNull("renamed column is not present in old name",
                    getJdbcColumnMetadata(domain.getDomainKind().getStorageSchemaName(),
                            domain.getStorageTableName(), propNameB));
            Assert.assertNotNull("renamed column is provisioned in new name",
                    getJdbcColumnMetadata(domain.getDomainKind().getStorageSchemaName(),
                            domain.getStorageTableName(), newName));
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
            ColumnMetadata col = getJdbcColumnMetadata(domain.getDomainKind().getStorageSchemaName(),
                            domain.getStorageTableName(), propBMvColumnName);
            Assert.assertNotNull("enabled mvindicator causes mvindicator column to be provisioned",
                    col);
        }


        @Test
        public void testDisableMv() throws Exception
        {
            addPropertyB();
            DomainProperty propB = domain.getPropertyByName(propNameB);
            propB.setMvEnabled(true);
            domain.save(new User());
            propB = domain.getPropertyByName(propNameB);
            propB.setMvEnabled(false);
            domain.save(new User());
            Assert.assertNull("property with disabled mvindicator has no mvindicator column",
                    getJdbcColumnMetadata(domain.getDomainKind().getStorageSchemaName(),
                            domain.getStorageTableName(), propBMvColumnName));
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
            String newMvName = PropertyStorageSpec.getMvIndicatorColumnName(newName);
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
        }

        private void addNotNullProperty() throws Exception
        {
            DomainProperty dp = domain.addProperty();
            dp.setPropertyURI(notNullPropName + "#" + notNullPropName);
            dp.setName(notNullPropName);
            dp.setRequired(true);
            domain.save(new User());
        }


        // TODO: We have this (or something much like it) in the SqlDialect -- merge!
        private ColumnMetadata getJdbcColumnMetadata(String schema, String table, String column) throws Exception
        {
            Connection con = null;
            ResultSet rs = null;

            try
            {
                con = DbScope.getLabkeyScope().getConnection();
                DatabaseMetaData dbmd = con.getMetaData();
                rs = dbmd.getColumns(null, schema, table, column);

                if (!rs.next())
                    // no column matched the column, table and schema given
                    return null;

                return new ColumnMetadata(rs.getString("IS_NULLABLE"), rs.getInt("COLUMN_SIZE"), rs.getInt("DATA_TYPE"), rs.getString("COLUMN_NAME"));
            }
            finally
            {
                ResultSetUtil.close(rs);
                if (con != null) { con.close(); }
            }
        }

        class ColumnMetadata
        {
            final public boolean nullable;
            final public int size;
            final public int sqlTypeInt;
            final public String name;

            ColumnMetadata(String nullable, int size, int sqlTypeInt, String name)
            {
                this.nullable = !nullable.equals("NO"); // spec claims it can be empty string, which we'd count as nullable.
                this.size = size;
                this.sqlTypeInt = sqlTypeInt;
                this.name = name;
            }

        }
    }
}
