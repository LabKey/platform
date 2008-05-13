/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
import org.labkey.api.data.SqlScriptRunner.SqlScriptException;
import org.labkey.api.data.SqlScriptRunner.SqlScriptProvider;
import org.labkey.api.data.SqlScriptRunner.SqlScript;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.util.CaseInsensitiveHashSet;
import org.labkey.api.util.AppProps;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * User: adam
 * Date: Sep 18, 2007
 * Time: 10:26:29 AM
 */
public class FileSqlScriptProvider implements SqlScriptProvider
{
    private DefaultModule _module;
    private static Logger _log = Logger.getLogger(FileSqlScriptProvider.class);

    public FileSqlScriptProvider(DefaultModule module)
    {
        _module = module;
    }


    public Set<String> getSchemaNames() throws SqlScriptException
    {
        List<SqlScript> allScripts = getScripts(null);
        Set<String> schemaNames = new HashSet<String>();

        for (SqlScript script : allScripts)
            if (!schemaNames.contains(script.getSchemaName()))
                schemaNames.add(script.getSchemaName());

        return schemaNames;
    }


    // Returns a sorted list of all valid scripts in the specified schema
    // schemaName = null returns all scripts
    public List<SqlScript> getScripts(String schemaName) throws SqlScriptException
    {
        List<String> filenames = getScriptFilenames(schemaName);

        List<SqlScript> scripts = new ArrayList<SqlScript>(filenames.size());

        for (String filename : filenames)
        {
            SqlScript script = getScript(filename);

            if (null != script)
                scripts.add(script);
        }

        return scripts;
    }


    protected boolean shouldInclude(SqlScript script)
    {
        return true;
    }


    public SqlScript getScript(String description)
    {
        FileSqlScript script = new FileSqlScript(this, description);

        if (script.isValidName() && shouldInclude(script))
            return script;
        else
            return null;
    }


    /* Returns filenames in the specified directory matching one of the following patterns:

            schemaName == null          *.sql
            schemaName == <schema>      <schema>*.sql

    */
    private List<String> getScriptFilenames(String schemaName) throws SqlScriptException
    {
        String path = getScriptPath();
        Set<String> filenames = _module.getManifest(path, "manifest.txt");

        if (null == filenames)
            throw new SqlScriptException("Script directory does not exist", path);

        CaseInsensitiveHashSet validFilenames = new CaseInsensitiveHashSet(filenames.size());

        // Get rid of path.  Only take SQL files that start with specified schema name.
        for (String fileName : filenames)
        {
            int index = fileName.lastIndexOf('/');

            if (-1 != index)
                fileName = fileName.substring(index + 1);

            if (fileName.endsWith(".sql") && (null == schemaName || fileName.startsWith(schemaName)))
                validFilenames.add(fileName);
        }

        // Every script directory should have at least one script... but don't fail in production mode.
        assert !validFilenames.isEmpty() : "SQL script directory " + path + " has no valid scripts";

        List<String> list = new ArrayList<String>(validFilenames);
        Collections.sort(list);

        return list;
    }


    private String getContents(String filename) throws SqlScriptException
    {
        StringBuffer contents = new StringBuffer();
        InputStream is;
        BufferedReader br = null;

        try
        {
            is = _module.getResourceStream(getScriptPath() + "/" + filename);

            // TODO: use PageFlowUtil.getStreamContentsAsString()?
            br = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = br.readLine()) != null)
            {
                contents.append(line);
                contents.append('\n');
            }
        }
        catch (FileNotFoundException e)
        {
            throw new SqlScriptException(e, filename);
        }
        catch (IOException e)
        {
            throw new SqlScriptException(e, filename);
        }
        finally
        {
            try
            {
                if (br != null) br.close();
            }
            catch (IOException e)
            {
                //
            }
        }

        return contents.toString();
    }

    public String getProviderName()
    {
        return _module.getName();
    }

    public void saveScript(String description, String contents) throws IOException
    {
        if (!AppProps.getInstance().isDevMode())
            throw new IllegalStateException("Can't save scripts while in production mode");

        File file = new File(_module.getBuildPath() + "/src" + getScriptPath() + "/" + description);

        if (file.exists())
            throw new IllegalStateException("File " + file.getAbsolutePath() + " already exists");

        FileWriter fw = null;

        try
        {
            fw = new FileWriter(file);
            fw.write(contents);
            fw.flush();
        }
        finally
        {
            if (null != fw)
                fw.close();
        }
    }

    private void afterScriptRuns(FileSqlScript fileSqlScript)
    {
        _module.afterScriptRuns(fileSqlScript);
    }

    private String getScriptPath()
    {
        return "/META-INF/" + _module.getName().toLowerCase() +  "/scripts/" + CoreSchema.getInstance().getSqlDialect().getSQLScriptPath();
    }

    public static class FileSqlScript implements SqlScript
    {
        private static final int SCHEMA_INDEX = 0;
        private static final int FROM_INDEX = 1;
        private static final int TO_INDEX = 2;
        private static final Pattern _scriptFileNamePattern = Pattern.compile("\\w+-[0-9]\\.[0-9]{2,3}-[0-9]\\.[0-9]{2,3}.sql");

        private FileSqlScriptProvider _provider = null;
        private String _fileName = null;
        private String _schemaName = null;
        private double _fromVersion = 0;
        private double _toVersion = 0;
        private boolean _validName = false;
        private String _errorMessage = null;

        public FileSqlScript(FileSqlScriptProvider provider, String fileName)
        {
            if (!_scriptFileNamePattern.matcher(fileName).matches())
            {
                _log.debug(provider.getProviderName() + ", ignoring file " + fileName + ": wrong format");
                return;
            }

            _provider = provider;
            _fileName = fileName;

            String[] parts = _fileName.substring(0, _fileName.length() - 4).split("-");

            if (parts.length != 3)
                return;

            _schemaName = parts[SCHEMA_INDEX];

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

            if (_fromVersion < _toVersion)
                _validName = true;
        }

        private boolean isValidName()
        {
            return _validName;
        }

        public String getSchemaName()
        {
            return _schemaName;
        }

        public double getFromVersion()
        {
            return _fromVersion;
        }

        public double getToVersion()
        {
            return _toVersion;
        }

        public String toString()
        {
            return _schemaName + " " + _fromVersion + " " + _toVersion;
        }

        public String getContents()
        {
            _errorMessage = null;

            try
            {
                return _provider.getContents(_fileName);
            }
            catch (SqlScriptException e)
            {
                _errorMessage = e.getMessage();
            }

            return "";
        }

        public String getErrorMessage()
        {
            return _errorMessage;
        }

        public String getDescription()
        {
            return _fileName;
        }

        public SqlScriptProvider getProvider()
        {
            return _provider;
        }

        public void afterScriptRuns()
        {
            _provider.afterScriptRuns(this);
        }

        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            FileSqlScript that = (FileSqlScript) o;

            if (_fileName != null ? !_fileName.equals(that._fileName) : that._fileName != null) return false;

            return true;
        }

        public int hashCode()
        {
            return (_fileName != null ? _fileName.hashCode() : 0);
        }

        public String createFilename(String schema, double fromVersion, double toVersion)
        {
            return schema + "-" + ModuleContext.formatVersion(fromVersion) + "-" + ModuleContext.formatVersion(toVersion) + ".sql"; 
        }
    }
}
