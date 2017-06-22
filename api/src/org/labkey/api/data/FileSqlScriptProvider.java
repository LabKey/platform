/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

import org.apache.commons.collections4.ComparatorUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.SqlScriptRunner.SqlScript;
import org.labkey.api.data.SqlScriptRunner.SqlScriptException;
import org.labkey.api.data.SqlScriptRunner.SqlScriptProvider;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.resource.Resource;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.view.JspTemplate;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.writer.PrintWriters;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * User: adam
 * Date: Sep 18, 2007
 * Time: 10:26:29 AM
 */
public class FileSqlScriptProvider implements SqlScriptProvider
{
    private static final Logger _log = Logger.getLogger(FileSqlScriptProvider.class);

    private final Module _module;

    public FileSqlScriptProvider(Module module)
    {
        _module = module;
    }


    @NotNull
    @Override
    public Collection<DbSchema> getSchemas()
    {
        Collection<String> moduleSchemaNames = new LinkedHashSet<>(_module.getSchemaNames());
        moduleSchemaNames.removeAll(_module.getProvisionedSchemaNames());

        List<DbSchema> schemas = new LinkedList<>();

        for (String schemaName : moduleSchemaNames)
        {
            try
            {
                schemas.add(DbSchema.get(schemaName, DbSchemaType.Module));
            }
            catch (NotFoundException nfe)
            {
                // Possibly a module data source that hasn't been configured
            }
        }

        _module.getProvisionedSchemaNames().stream()
            .map(schemaName -> DbSchema.get(schemaName, DbSchemaType.Provisioned))
            .forEach(schemas::add);

        return schemas;
    }


    // Returns a sorted list of all valid scripts in the specified schema
    // schema = null returns all scripts
    @NotNull
    @Override
    public List<SqlScript> getScripts(@NotNull DbSchema schema) throws SqlScriptException
    {
        Set<String> filenames = getScriptFilenames(schema);

        List<SqlScript> scripts = new ArrayList<>(filenames.size());

        for (String filename : filenames)
        {
            SqlScript script = getScript(schema, filename);

            if (null != script)
                scripts.add(script);
        }

        return scripts;
    }


    @Override
    public SqlScript getScript(@Nullable DbSchema schema, String description)
    {
        FileSqlScript script = new FileSqlScript(this, schema, description);

        if (script.isValidName())
            return script;
        else
            return null;
    }


    /*
        Returns set of filenames in the specified directory matching one of the following patterns:

            schema == null          *.sql
            schema == <schema>      <schema display name>-*.sql, <schema display name>-*.jsp

        Returned set can be empty (i.e., schemas that have no scripts)
    */
    private @NotNull Set<String> getScriptFilenames(@NotNull DbSchema schema) throws SqlScriptException
    {
        return _module.getSqlScripts(schema);
    }

    // Returns all script file names listed in required_scripts.txt
    public @NotNull Collection<String> getRequiredScripts(SqlDialect dialect) throws IOException
    {
        Path path = Path.parse(_module.getSqlScriptsPath(dialect)).append("required_scripts.txt");
        Resource r = _module.getModuleResource(path);

        return null != r ? PageFlowUtil.getStreamContentsAsList(r.getInputStream(), true) : Collections.emptyList();
    }

    @Nullable
    @Override
    public SqlScript getDropScript(DbSchema schema)
    {
        return getOneOffScript(schema, "drop.sql");
    }

    @Nullable
    @Override
    public SqlScript getCreateScript(DbSchema schema)
    {
        return getOneOffScript(schema, "create.sql");
    }

    @Nullable
    private SqlScript getOneOffScript(DbSchema schema, String suffix)
    {
        String schemaName = schema.getDisplayName();
        SqlScript script = new FileSqlScript(this, schema, schemaName + "-" + suffix, schemaName);

        // Look for a .sql script first. If there isn't one, check for a jsp file.
        if (script.getContents().isEmpty())
        {
            script = new FileSqlScript(this, schema, schemaName + "-" + suffix.replace(".sql",".jsp"), schemaName);
            if (script.getContents().isEmpty())
                return null;
        }

        return script;
    }

    private String getContents(DbSchema schema, String filename) throws SqlScriptException
    {
        try
        {
            Path path = Path.parse(_module.getSqlScriptsPath(schema.getSqlDialect())).append(filename);
            Resource r = _module.getModuleResource(path);
            if (null == r || !r.isFile())
                throw new SqlScriptException("File not found: " + path, filename);

            if (filename.endsWith(".sql"))
            {
                return PageFlowUtil.getStreamContentsAsString(r.getInputStream());
            }
            else // jsp, the only other format allowed by FileSqlScript
            {
                ScriptContext ctx = new ScriptContext(schema);

                JspTemplate<ScriptContext> jspQuery = new JspTemplate<>(_module.getResourcePath() + "/" + path.toString(), ctx);
                return jspQuery.render();
            }
         }
        catch (Exception e)
        {
            throw new SqlScriptException(e, filename);
        }
    }

    @Override
    public String getProviderName()
    {
        return _module.getName();
    }

    public void saveScript(DbSchema schema, String description, String contents) throws IOException
    {
        saveScript(schema, description, contents, false);
    }

    public void saveScript(DbSchema schema, String description, String contents, boolean overwrite) throws IOException
    {
        if (!AppProps.getInstance().isDevMode())
            throw new IllegalStateException("Can't save scripts while in production mode");

        File scriptsDir = getScriptDirectory(schema.getSqlDialect());

        if (!scriptsDir.exists())
            throw new IllegalStateException("SQL scripts directory not found");

        File file = new File(scriptsDir, description);

        if (file.exists() && !overwrite)
            throw new IllegalStateException("File " + file.getAbsolutePath() + " already exists");

        try (PrintWriter pw = PrintWriters.getPrintWriter(file))
        {
            pw.write(contents);
            pw.flush();
        }
    }

    public File getScriptDirectory(SqlDialect dialect)
    {
        String scriptsPath = _module.getSqlScriptsPath(dialect);
        File scriptsDir = new File(new File(_module.getSourcePath(), "resources"), scriptsPath);

        // Handle file structure of old file-based modules, e.g., reagent
        if (!scriptsDir.exists())
            scriptsDir = new File(_module.getSourcePath(), scriptsPath);

        return scriptsDir;
    }

    @Override
    public UpgradeCode getUpgradeCode()
    {
        return _module.getUpgradeCode();
    }

    @Override
    public double getInstalledVersion()
    {
        return ModuleLoader.getInstance().getModuleContext(_module).getInstalledVersion();
    }

    public static class FileSqlScript implements SqlScript
    {
        private static final int SCHEMA_INDEX = 0;
        private static final int FROM_INDEX = 1;
        private static final int TO_INDEX = 2;
        private static final int SUFFIX_INDEX = 3;
        private static final Pattern _scriptFileNamePattern = Pattern.compile("(\\w+\\.)?\\w+-[0-9]{1,2}\\.[0-9]{2,3}-[0-9]{1,2}\\.[0-9]{2,3}(-\\w+)?.(sql|jsp)");

        private final FileSqlScriptProvider _provider;
        private final DbSchema _schema;
        private final String _fileName;

        private String _schemaName = null;
        private double _fromVersion = 0;
        private double _toVersion = 0;
        private String _suffix = null; // Optional suffix
        private boolean _validName = false;
        private String _errorMessage = null;

        public FileSqlScript(FileSqlScriptProvider provider, @Nullable DbSchema schema, String fileName)
        {
            _provider = provider;
            _fileName = fileName;

            if (!_scriptFileNamePattern.matcher(fileName).matches())
            {
                _schema = null;
                if (!fileName.endsWith("-create.sql") && !fileName.endsWith("-drop.sql"))
                    _log.info(provider.getProviderName() + ", ignoring file " + fileName + ": wrong format");
                return;
            }

            String[] parts = _fileName.substring(0, _fileName.length() - 4).split("-");

            if (parts.length < 3 || parts.length > 4)
            {
                _schema = null;
                return;
            }

            _schemaName = parts[SCHEMA_INDEX];
            _schema = null != schema ? schema : DbSchema.get(_schemaName, DbSchemaType.Unknown);

            try
            {
                _fromVersion = Double.parseDouble(parts[FROM_INDEX]);
                _toVersion = Double.parseDouble(parts[TO_INDEX]);
            }
            catch (NumberFormatException x)
            {
                _log.info(_provider.getProviderName() + ", ignoring file " + fileName + ": couldn't parse version numbers");
                return;
            }

            if (parts.length == 4)
                _suffix = parts[SUFFIX_INDEX];

            if (_fromVersion < _toVersion)
                _validName = true;
        }

        // Used for DROP and CREATE scripts... so we don't bother verifying filename or parsing info from it
        // Also, leave _validName = false so we don't record these scripts in the SqlScript table
        public FileSqlScript(FileSqlScriptProvider provider, DbSchema schema, String fileName, String schemaName)
        {
            _provider = provider;
            _schema = schema;
            _fileName = fileName;
            _schemaName = schemaName;
        }

        @Override
        public boolean isValidName()
        {
            return _validName;
        }

        @Override
        public boolean isIncremental()
        {
            double startVersion = _fromVersion * 10;
            double endVersion = _toVersion * 10;

            return (Math.floor(startVersion) != startVersion || Math.floor(endVersion) != endVersion);
        }

        @Override
        public DbSchema getSchema()
        {
            return _schema;
        }

        @Override
        public double getFromVersion()
        {
            return _fromVersion;
        }

        @Override
        public double getToVersion()
        {
            return _toVersion;
        }

        // Optional suffix
        @Override
        public @Nullable String getSuffix()
        {
            return _suffix;
        }

        public String toString()
        {
            return getDescription();
        }

        @Override
        public String getContents()
        {
            _errorMessage = null;

            try
            {
                return _provider.getContents(_schema, _fileName);
            }
            catch (SqlScriptException e)
            {
                _errorMessage = e.getMessage();
            }

            return "";
        }

        @Override
        public String getErrorMessage()
        {
            return _errorMessage;
        }

        @Override
        public String getDescription()
        {
            return _fileName;
        }

        @Override
        public SqlScriptProvider getProvider()
        {
            return _provider;
        }

        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            FileSqlScript that = (FileSqlScript) o;

            return !(_fileName != null ? !_fileName.equals(that._fileName) : that._fileName != null);
        }

        public int hashCode()
        {
            return (_fileName != null ? _fileName.hashCode() : 0);
        }

        public String createFilename(String schema, double fromVersion, double toVersion)
        {
            return schema + "-" + ModuleContext.formatVersion(fromVersion) + "-" + ModuleContext.formatVersion(toVersion) + ".sql"; 
        }

        private static final Comparator<String> SUFFIX_COMPARATOR = ComparatorUtils.nullHighComparator(String.CASE_INSENSITIVE_ORDER);

        @Override
        public int compareTo(@NotNull SqlScript script)
        {
            int schemaCompare = getSchema().getDisplayName().compareToIgnoreCase(script.getSchema().getDisplayName());

            if (0 != schemaCompare)
                return schemaCompare;

            int fromCompare = Double.compare(getFromVersion(), script.getFromVersion());

            if (0 != fromCompare)
                return fromCompare;

            int toCompare = Double.compare(getToVersion(), script.getToVersion());

            if (0 != toCompare)
                return toCompare;

            return SUFFIX_COMPARATOR.compare(getSuffix(), script.getSuffix());
        }
    }

    public static class ScriptContext
    {
        // Model bean for jsps generating SQL scripts. For now this is limited to the DbSchema and a map
        // of the datasource -> database names. Additional context can be added as needed.
        public final DbSchema schema;
        public final Map<String,String> dataSources = new HashMap<>();

        private ScriptContext(DbSchema schema)
        {
            this.schema = schema;
            for (DbScope scope : DbScope.getDbScopes())
            {
                dataSources.put(StringUtils.substringBefore(scope.getDataSourceName(), "DataSource"), scope.getSqlDialect().makeLegalIdentifier(scope.getDatabaseName()));
            }
        }
    }
}
