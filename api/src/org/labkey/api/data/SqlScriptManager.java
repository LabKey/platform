package org.labkey.api.data;

import org.apache.commons.lang.StringUtils;
import org.labkey.api.data.SqlScriptRunner.SqlScript;
import org.labkey.api.data.SqlScriptRunner.SqlScriptException;
import org.labkey.api.data.SqlScriptRunner.SqlScriptProvider;
import org.labkey.api.security.User;

import java.sql.SQLException;
import java.util.*;


/**
 * User: adam
 * Date: Sep 20, 2007
 * Time: 3:14:35 PM
 */
public class SqlScriptManager
{
    private static CoreSchema _core = CoreSchema.getInstance();

    public static TableInfo getTableInfo()
    {
        return _core.getTableInfoSqlScripts();
    }

    // Return sql scripts that have been run by this provider
    public static Set<SqlScript> getRunScripts(SqlScriptProvider provider) throws SQLException
    {
        // Skip if the table hasn't been created yet (bootstrap case)
        if (_core.getTableInfoSqlScripts().getTableType() != TableInfo.TABLE_TYPE_TABLE)
            return Collections.emptySet();

        String[] runFilenames = Table.executeArray(_core.getSchema(), "SELECT FileName FROM " + _core.getTableInfoSqlScripts() + " WHERE ModuleName = ?", new Object[]{provider.getProviderName()}, String.class);

        Set<SqlScript> runScripts = new HashSet<SqlScript>(runFilenames.length);

        for (String filename : runFilenames)
        {
            SqlScript script = provider.getScript(filename);

            if (null != script)
                runScripts.add(script);
        }

        return runScripts;
    }


    public static void deleteAllScripts(SqlScriptProvider provider) throws SQLException
    {
        Table.execute(_core.getSchema(), "DELETE FROM " + _core.getTableInfoSqlScripts() + " WHERE ModuleName = ?", new Object[]{provider.getProviderName()});
    }


    public static void deleteSelectedScripts(SqlScriptProvider provider, List list) throws SQLException
    {
        String fileNames = "'" + StringUtils.join(list.iterator(), "','") + "'";

        Table.execute(_core.getSchema(), "DELETE FROM " + _core.getTableInfoSqlScripts() + " WHERE ModuleName = ? AND FileName IN (" + fileNames + ")", new Object[]{provider.getProviderName()});
    }


    public static SqlScriptBean loadScriptBean(SqlScriptProvider provider, String fileName)
    {
        // Make sure DbSchema thinks SqlScript table is in the database.  If not, we're bootstrapping and it's either just before or just after the first
        // script is run.  In either case, invalidate to force reloading schema from database meta data.
        if (_core.getTableInfoSqlScripts().getTableType() == TableInfo.TABLE_TYPE_NOT_IN_DB)
        {
            DbSchema.invalidateSchemas();
            return null;
        }

        return Table.selectObject(_core.getTableInfoSqlScripts(), new String[]{provider.getProviderName(), fileName}, SqlScriptBean.class);
    }


    public static boolean hasBeenRun(SqlScript script)
    {
        return null != loadScriptBean(script.getProvider(), script.getDescription());
    }


    public static void insert(User user, SqlScript script) throws SQLException
    {
        insert(user, script.getProvider(), script.getDescription());
    }


    public static void insert(User user, SqlScriptProvider provider, String fileName) throws SQLException
    {
        SqlScriptBean ss = new SqlScriptBean(provider.getProviderName(), fileName);

        Table.insert(user, _core.getTableInfoSqlScripts(), ss);
    }


    public static void update(User user, SqlScript script) throws SQLException
    {
        Object[] pk = new Object[]{script.getProvider().getProviderName(), script.getDescription()};

        Table.update(user, _core.getTableInfoSqlScripts(), new HashMap(), pk, null);  // Update user and modified date
    }

    static void runScript(User user, SqlScript script) throws SqlScriptException, SQLException
    {
        DbSchema schema = DbSchema.get(script.getSchemaName());
        SqlDialect dialect = schema.getSqlDialect();
        String contents = script.getContents();

        if (0 == contents.length())
        {
            String error = script.getErrorMessage();

            if (null != error)
                throw new SqlScriptException(error, script.getDescription());

            return;
        }

        try
        {
            dialect.checkSqlScript(contents, script.getToVersion());
            dialect.runSql(schema, contents);
        }
        catch(SQLException e)
        {
            throw new SqlScriptException(e, script.getDescription());
        }

        if (hasBeenRun(script))
            update(user, script);
        else
            insert(user, script);
    }


    // TODO: Combine with SqlScript?
    public static class SqlScriptBean extends Entity
    {
        private String _moduleName;
        private String _fileName;

        public SqlScriptBean()
        {
        }

        public SqlScriptBean(String moduleName, String fileName)
        {
            _moduleName = moduleName;
            _fileName = fileName;
        }

        public String getModuleName()
        {
            return _moduleName;
        }

        public void setModuleName(String moduleName)
        {
            _moduleName = moduleName;
        }

        public String getFileName()
        {
            return _fileName;
        }

        public void setFileName(String fileName)
        {
            _fileName = fileName;
        }
    }
}
