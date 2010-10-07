package org.labkey.api.exp.api;

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.SqlDialect;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableChange;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.AliasManager;
import org.labkey.api.util.CPUTimer;
import org.labkey.api.util.ResultSetUtil;

import javax.servlet.ServletException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Collection;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: newton
 * Date: Aug 11, 2010
 * Time: 2:52:33 PM
 */

public class StorageProvisioner
{
    private static final Logger log = Logger.getLogger(StorageProvisioner.class);


    public static void create(Domain domain) throws SQLException
    {
        DomainKind kind = domain.getDomainKind();
        if (null == kind)
            return;

        create(kind, domain);
    }


    public static void create(DomainKind kind, Domain domain) throws SQLException
    {
        DbScope scope = kind.getScope();
        String schemaName = kind.getStorageSchemaName();
        if (scope == null || schemaName == null)
            return;

        _create(scope, kind, domain);
    }

    static CPUTimer create = new CPUTimer("StorageProvisioner.create");


    private static String _create(DbScope scope, DomainKind kind, Domain domain) throws SQLException
    {
        assert create.start();
        Connection conn = null;
        boolean outerTransaction = scope.isTransactionActive();

        try
        {
            if (!outerTransaction)
                scope.beginTransaction();

            // reselect in a transaction
            DomainDescriptor dd = OntologyManager.getDomainDescriptor(domain.getTypeId(), true);
            if (null == dd)
            {
                Logger.getLogger(StorageProvisioner.class).error("HUH?? " + domain.getTypeId() + " " + domain.getTypeURI());
                return null;
            }
            String tableName = dd.getStorageTableName();
            if (null != tableName)
                return tableName;

            tableName = makeTableName(kind, domain);

            TableChange change = new TableChange(kind.getStorageSchemaName(), tableName, TableChange.ChangeType.CreateTable);
            for (DomainProperty property : domain.getProperties())
            {
                change.addColumn(property.getPropertyDescriptor());
                if (property.isMvEnabled())
                {
                    change.addColumn(makeMvColumn(property));
                }
            }

            for (PropertyStorageSpec spec : kind.getBaseProperties())
            {
                change.addColumn(spec);
            }

            change.setIndexedColumns(kind.getPropertyIndices());

            conn = scope.getConnection();

            for (String sql : scope.getSqlDialect().getChangeStatements(change))
            {
                log.info("Will issue: " + sql);
                conn.prepareStatement(sql).execute();
            }

            dd.setStorageTableName(tableName);
            dd.setStorageSchemaName(kind.getStorageSchemaName());
            OntologyManager.updateDomainDescriptor(dd);

            scope.releaseConnection(conn);
            conn = null;

            if (!outerTransaction)
                scope.commitTransaction();
            return tableName;
        }
        finally
        {
            if (null != conn)
                scope.releaseConnection(conn);
            if (!outerTransaction && scope.isTransactionActive())
                scope.rollbackTransaction();
            assert create.stop();
        }
    }

    private static PropertyStorageSpec makeMvColumn(DomainProperty property)
    {
        return new PropertyStorageSpec(property.getName() + "_" + MvColumn.MV_INDICATOR_SUFFIX, Types.VARCHAR, 50);
    }


    public static void drop(Domain domain)
    {
        if (null == domain)
            return;
        DomainKind kind = domain.getDomainKind();
        if (kind == null)
        {
            log.warn("domain " + domain.getName() + " has no DomainKind");
            return;
        }

        DbScope scope = kind.getScope();
        String schemaName = kind.getStorageSchemaName();
        if (scope == null || schemaName == null)
            return;

        // should be in a trasaction with propertydescriptor changes
        assert scope.isTransactionActive();

        String tableName = domain.getStorageTableName();
        if (null == tableName)
        {
            return;
        }

        TableChange change = new TableChange(kind.getStorageSchemaName(), tableName, TableChange.ChangeType.DropTable);

        Connection con = null;
        try
        {
            con = scope.getConnection();
            for (String sql : scope.getSqlDialect().getChangeStatements(change))
            {
                log.info("Will issue: " + sql);
                con.prepareStatement(sql).execute();
            }
        }
        catch (SQLException e)
        {
            log.warn(String.format("Failed to drop table in schema %s for domain %s - %s",
                    schemaName, domain.getName(), e.getMessage()), e);
        }
        finally
        {
            scope.releaseConnection(con);
        }
    }


    public static void addProperties(Domain domain, Collection<DomainProperty> properties) throws SQLException
    {
        DomainKind kind = domain.getDomainKind();
        DbScope scope = kind.getScope();

        // should be in a transaction with propertydescriptor changes
        assert scope.isTransactionActive();

        String tableName = domain.getStorageTableName();
        if (null == tableName)
        {
            log.warn("addProperties() called before table is provisioned: " + domain.getTypeURI());
            tableName = _create(scope, kind, domain);
            return;
        }

        TableChange change = new TableChange(kind.getStorageSchemaName(), tableName, TableChange.ChangeType.AddColumns);


        for (DomainProperty prop : properties)
        {
            change.addColumn(prop.getPropertyDescriptor());
            if (prop.isMvEnabled())
            {
                change.addColumn(makeMvColumn(prop));
            }
        }

        Connection con = null;

        try
        {
            con = scope.getConnection();
            for (String sql : scope.getSqlDialect().getChangeStatements(change))
            {
                log.info("Will issue: " + sql);
                con.prepareStatement(sql).execute();
            }
        }
        finally
        {
            scope.releaseConnection(con);
        }
    }

    public static void dropMvIndicator(DomainProperty... props) throws SQLException
    {
        assert(props.length > 0);
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
            for (String sql : scope.getSqlDialect().getChangeStatements(change))
            {
                log.info("Will issue: " + sql);
                con.prepareStatement(sql).execute();
            }
        }
        finally
        {
            scope.releaseConnection(con);
        }
    }

    public static void addMvIndicator(DomainProperty... props) throws SQLException
    {
        assert(props.length > 0);
        Domain domain = props[0].getDomain();
        DomainKind kind = domain.getDomainKind();
        DbScope scope = kind.getScope();

        // should be in a trasaction with propertydescriptor changes
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
            for (String sql : scope.getSqlDialect().getChangeStatements(change))
            {
                log.info("Will issue: " + sql);
                con.prepareStatement(sql).execute();
            }
        }
        finally
        {
            scope.releaseConnection(con);
        }
    }


    public static void dropProperties(Domain domain, Collection<DomainProperty> properties) throws SQLException
    {
        DomainKind kind = domain.getDomainKind();
        DbScope scope = kind.getScope();

        // should be in a trasaction with propertydescriptor changes
        assert scope.isTransactionActive();

        String tableName = domain.getStorageTableName();

        TableChange change = new TableChange(kind.getStorageSchemaName(), tableName, TableChange.ChangeType.DropColumns);
        for (DomainProperty prop : properties)
        {
            change.addColumn(prop.getPropertyDescriptor());
            if (prop.isMvEnabled())
            {
                change.addColumn(makeMvColumn(prop));
            }
        }

        Connection con = null;

        try
        {
            con = scope.getConnection();
            for (String sql : scope.getSqlDialect().getChangeStatements(change))
            {
                log.info("Will issue: " + sql);
                con.prepareStatement(sql).execute();
            }
        }
        finally
        {
            scope.releaseConnection(con);
        }
    }

    /**
     * @param domain
     * @param propsRenamed map where keys are the current properties including the new names, values are the old column names.
     */
    public static void renameProperties(Domain domain, Map<DomainProperty, String> propsRenamed) throws SQLException
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

            for (Map.Entry<DomainProperty, String> rename : propsRenamed.entrySet())
            {
                PropertyStorageSpec prop = new PropertyStorageSpec(rename.getKey().getPropertyDescriptor());
                String oldPropName = rename.getValue();
                renamePropChange.addColumnRename(oldPropName, prop.getName());
                if (prop.isMvEnabled())
                {
                    renamePropChange.addColumnRename(prop.getMvIndicatorColumnName(oldPropName), prop.getMvIndicatorColumnName(prop.getName()));
                }

            }

            for (String sql : scope.getSqlDialect().getChangeStatements(renamePropChange))
            {
                log.info("Will issue: " + sql);
                con.prepareStatement(sql).execute();
            }

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
     * return a TableInfo for this domain, creating if necessary
     * this method DOES NOT cache
     *
     * @param kind
     * @param domain
     * @param parentSchema Schema to attach table to, should NOT be the physical db schema of the storage provider
     * @return
     */

    public static TableInfo createTableInfo(DomainKind kind, Domain domain, DbSchema parentSchema)
    {
        DbScope scope = kind.getScope();
        String schemaName = kind.getStorageSchemaName();

        if (null == scope || null == schemaName)
            throw new IllegalArgumentException();

        Connection conn = null;
        try
        {
            String tableName = domain.getStorageTableName();

            if (null == tableName)
                tableName = _create(scope, kind, domain);

            SchemaTableInfo ti = new SchemaTableInfo(tableName, schemaName + ".\"" + tableName + "\"", parentSchema);

            conn = scope.getConnection();
            ti.setMetaDataName(tableName);
            ti.loadFromMetaData(conn.getMetaData(), scope.getDatabaseName(), schemaName);

            for (DomainProperty p : domain.getProperties())
            {
                ColumnInfo c = ti.getColumn(p.getName());
                if (null == c)
                {
                    Logger.getLogger(StorageProvisioner.class).info("Column not found in storage table: " + tableName + "." + p.getName());
                    continue;
                }
                PropertyColumn.copyAttributes(null, c, p.getPropertyDescriptor());
            }

            ti.setTableType(TableInfo.TABLE_TYPE_TABLE);
            return ti;
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
        finally
        {
            if (null != conn)
                scope.releaseConnection(conn);
        }
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testVerifyStudyDatasetSchema() throws IOException, SQLException, ServletException
        {
            ResultSet rs = null;
            Connection conn = null;
            DbScope scope = DbSchema.get("study").getScope();

            try
            {
                scope.beginTransaction();
                conn = DbSchema.get("study").getScope().getConnection();

                CaseInsensitiveHashSet domains = new CaseInsensitiveHashSet();
                rs = Table.executeQuery(DbSchema.get("study"), "SELECT storagetablename FROM exp.domaindescriptor WHERE storagetablename IS NOT NULL and storageschemaname = 'studydataset'", null);
                while (rs.next())
                    domains.add(rs.getString(1));
                ResultSetUtil.close(rs);
                rs = null;

                CaseInsensitiveHashSet database = new CaseInsensitiveHashSet();
                if (DbSchema.get("study").getSqlDialect().treatCatalogsAsSchemas())
                    rs = conn.getMetaData().getTables("studydataset", null, "%", new String[]{"TABLE"});
                else
                    rs = conn.getMetaData().getTables(DbSchema.get("study").getScope().getDatabaseName(), "studydataset", "%", new String[]{"TABLE"});
                while (rs.next())
                {
                    database.add(rs.getString("TABLE_NAME").trim());
                }

                for (String s : domains)
                {
                    if (!database.contains(s))
                        fail("Table not found in database: " + s);
                }
                for (String s : database)
                {
                    if (!domains.contains(s))
                        fail("Domain not found for : " + s);
                }
            }
            catch (SQLException x)
            {
                fail("sqlexception");
            }
            finally
            {
                ResultSetUtil.close(rs);
                if (null != conn)
                    DbSchema.get("study").getScope().releaseConnection(conn);
                scope.rollbackTransaction();
            }
        }
    }
}
