/*
 * Copyright (c) 2007-2012 LabKey Corporation
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

package org.labkey.api.data;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.SqlScriptRunner.SqlScript;
import org.labkey.api.data.SqlScriptRunner.SqlScriptException;
import org.labkey.api.data.SqlScriptRunner.SqlScriptProvider;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.security.User;
import org.labkey.api.module.ModuleContext;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;


/**
 * User: adam
 * Date: Sep 20, 2007
 * Time: 3:14:35 PM
 */
public class SqlScriptManager
{
    private static final CoreSchema _core = CoreSchema.getInstance();

    // Return sql scripts that have been run by this provider
    public static Set<SqlScript> getRunScripts(SqlScriptProvider provider) throws SQLException
    {
        // Skip if the table hasn't been created yet (bootstrap case)
        if (_core.getTableInfoSqlScripts().getTableType() != DatabaseTableType.TABLE)
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


    private static SqlScriptBean loadScriptBean(SqlScriptProvider provider, String fileName)
    {
        // Make sure DbSchema thinks SqlScript table is in the database.  If not, we're bootstrapping and it's either just before or just after the first
        // script is run.  In either case, invalidate to force reloading schema from database meta data.
        if (_core.getTableInfoSqlScripts().getTableType() == DatabaseTableType.NOT_IN_DB)
        {
            CacheManager.clearAllKnownCaches();
            return null;
        }

        return Table.selectObject(_core.getTableInfoSqlScripts(), new String[]{provider.getProviderName(), fileName}, SqlScriptBean.class);
    }


    public static boolean hasBeenRun(SqlScript script)
    {
        return null != loadScriptBean(script.getProvider(), script.getDescription());
    }


    private static void insert(@Nullable User user, SqlScript script) throws SQLException
    {
        insert(user, script.getProvider(), script.getDescription());
    }


    private static void insert(@Nullable User user, SqlScriptProvider provider, String fileName) throws SQLException
    {
        SqlScriptBean ss = new SqlScriptBean(provider.getProviderName(), fileName);

        Table.insert(user, _core.getTableInfoSqlScripts(), ss);
    }


    private static void update(@Nullable User user, SqlScript script) throws SQLException
    {
        Object[] pk = new Object[]{script.getProvider().getProviderName(), script.getDescription()};

        Table.update(user, _core.getTableInfoSqlScripts(), new HashMap(), pk);  // Update user and modified date
    }

    public static void runScript(@Nullable User user, SqlScript script, ModuleContext moduleContext, @Nullable Connection conn) throws SqlScriptException, SQLException
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
            dialect.checkSqlScript(contents);
            Logger.getLogger(SqlScriptManager.class).info("start running script : " + script.getDescription());
            dialect.runSql(schema, contents, script.getProvider().getUpgradeCode(), moduleContext, conn);
            Logger.getLogger(SqlScriptManager.class).info("finished running script : " + script.getDescription());
        }
        catch(SQLException e)
        {
            throw new SqlScriptException(e, script.getDescription());
        }

        if (script.isValidName())
        {
            if (hasBeenRun(script))
                update(user, script);
            else
                insert(user, script);
        }
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
