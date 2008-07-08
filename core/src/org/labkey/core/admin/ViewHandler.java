/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.core.admin;

import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.FileSqlScriptProvider;
import org.labkey.api.data.SqlScriptRunner;
import org.labkey.api.util.PageFlowUtil;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: adam
 * Date: Jun 29, 2008
 * Time: 5:32:14 AM
 */
public abstract class ViewHandler
{
    protected FileSqlScriptProvider _provider;
    protected String _schemaName;
    private enum ViewType {DROP, CREATE}
    private int _scriptLines = 0;

    protected Pattern _viewPattern;

    private ViewHandler(FileSqlScriptProvider provider, String schemaName)
    {
        _provider = provider;
        _schemaName = schemaName;
        _viewPattern = getViewPattern(DbSchema.get(_schemaName));
    }

    public static Pattern getViewPattern(DbSchema schema)
    {
        boolean isPostgreSQL = schema.getSqlDialect().isPostgreSQL();

        String viewNameRegEx = "((\\w+)\\.)?([a-zA-Z0-9]+)";  // Ignore view names that start with _
        String dropRegEx;
        String createRegEx;
        String endRegEx;

        if (isPostgreSQL)
        {
            dropRegEx = "((DROP VIEW " + viewNameRegEx + ")|(SELECT core.fn_dropifexists\\s*\\(\\s*'(\\w+)',\\s*'(\\w+)',\\s*'VIEW',\\s*NULL\\s*\\)))";      // exec core.fn_dropifexists 'materialsource', 'cabig','VIEW', NULL
            createRegEx = "(CREATE (?:OR REPLACE )*VIEW " + viewNameRegEx + " AS.+?)";
            endRegEx = ";";
        }
        else
        {
            dropRegEx = "((?:if exists \\(select \\* from dbo\\.sysobjects where id = object_id\\(N'\\[?[\\w]+\\]?.\\[?[\\w]+\\]?+'\\) and OBJECTPROPERTY\\(id, N'IsView'\\) = 1\\)\\s*)?(DROP VIEW " + viewNameRegEx + ")|(EXEC core.fn_dropifexists\\s*'(\\w+)',\\s*'(\\w+)',\\s*'VIEW',\\s*NULL))";      // exec core.fn_dropifexists 'materialsource', 'cabig','VIEW', NULL
            createRegEx = "(CREATE VIEW " + viewNameRegEx + " AS.+?)";
            endRegEx = "GO$";
        }

        String combinedRegEx = ("(?:" + dropRegEx + "|" + createRegEx + ")\\s*" + endRegEx + "\\s*").replaceAll(" ", "\\\\s+");
        return Pattern.compile(combinedRegEx, Pattern.CASE_INSENSITIVE + Pattern.DOTALL + Pattern.MULTILINE);
    }

    protected abstract List<SqlScriptRunner.SqlScript> getScripts() throws SqlScriptRunner.SqlScriptException;
    // Called only if one or more VIEW statements exist in this script
    protected abstract void handleScript(SqlScriptRunner.SqlScript script, Map<String, String> createStatements, Map<String, String> dropStatements, PrintWriter out) throws IOException;
    // Called only if one or more VIEW statements exist in this schema
    protected abstract void handleSchema(Map<String, String> createStatements, Map<String, String> dropStatements, PrintWriter out) throws IOException;

    public void handle(PrintWriter out) throws SqlScriptRunner.SqlScriptException, IOException
    {
        // These maps are ordered by most recent access, which helps us figure out the dependency order
        Map<String, String> createStatements = new LinkedHashMap<String, String>(10, 0.75f, true);
        Map<String, String> dropStatements = new LinkedHashMap<String, String>(10, 0.75f, true);

        for (SqlScriptRunner.SqlScript script : getScripts())
        {
            Matcher m = _viewPattern.matcher(script.getContents());

            while (m.find())
            {
                String schemaName;
                String viewName;
                ViewType type;

                // CREATE VIEW
                if (null != m.group(9))
                {
                    schemaName = m.group(11);
                    viewName = m.group(12);
                    type = ViewType.CREATE;
                }
                else
                {
                    // EXEC/SELECT core.fn_dropifexists
                    if (null != m.group(6))
                    {
                        schemaName = m.group(8);
                        viewName = m.group(7);
                        type = ViewType.DROP;
                    }
                    // DROP VIEW
                    else
                    {
                        assert null != m.group(2);
                        schemaName = m.group(4);
                        viewName = m.group(5);
                        type = ViewType.DROP;
                    }
                }

                // Regex should be igoring view names that start with _
                assert !viewName.startsWith("_");

                String key = getKey(_schemaName, schemaName, viewName);

                if (ViewType.CREATE == type)
                {
                    createStatements.put(key, m.group(0));
                }
                else
                {
                    dropStatements.put(key, (null == schemaName ? _schemaName : schemaName) + "." + viewName);
                    createStatements.remove(key);
                }
            }

            countScriptLines(script);

            if (!createStatements.isEmpty() || !dropStatements.isEmpty())
                handleScript(script, createStatements, dropStatements, out);
        }

        if (!createStatements.isEmpty() || !dropStatements.isEmpty())
        {
            handleSchema(createStatements, dropStatements, out);
        }
    }

    private void countScriptLines(SqlScriptRunner.SqlScript script)
    {
        Matcher lineCounter = Pattern.compile("$", Pattern.MULTILINE).matcher(script.getContents());

        while (lineCounter.find())
            _scriptLines++;
    }


    public int getScriptLines()
    {
        return _scriptLines;
    }


    private String getKey(String defaultSchemaName, String schemaName, String viewName)
    {
        return ((null != schemaName ? schemaName : defaultSchemaName) + "." + viewName).toLowerCase();
    }


    // Clears CREATE and DROP view statements from all current scripts
    public static class ViewClearer extends ViewHandler
    {
        ViewClearer(FileSqlScriptProvider provider, String schemaName)
        {
            super(provider, schemaName);
        }

        // Clear VIEW statements from all current scripts
        protected List<SqlScriptRunner.SqlScript> getScripts() throws SqlScriptRunner.SqlScriptException
        {
            return _provider.getScripts(_schemaName);
        }

        protected void handleScript(SqlScriptRunner.SqlScript script, Map<String, String> createStatements, Map<String, String> dropStatements, PrintWriter out) throws IOException
        {
            Matcher m = _viewPattern.matcher(script.getContents());
            String strippedScript = m.replaceAll("");
            out.println("-- " + script.getDescription());
            out.println(PageFlowUtil.filter(strippedScript));
            out.println();

            _provider.saveScript(script.getDescription(), strippedScript, true);
        }

        protected void handleSchema(Map<String, String> createStatements, Map<String, String> dropStatements, PrintWriter out)
        {
        }
    }

    // Creates DROP VIEW (for all views that have ever been created) and CREATE VIEW (all current VIEWs) scripts
    public static class ViewExtractor extends ViewHandler
    {
        private boolean _showCreate = true;
        private boolean _showDrop = true;
        private static String cr = "\r\n";

        public ViewExtractor(FileSqlScriptProvider provider, String schemaName)
        {
            super(provider, schemaName);
        }

        public ViewExtractor(FileSqlScriptProvider provider, String schemaName, boolean showDrop, boolean showCreate)
        {
            this(provider, schemaName);
            _showDrop = showDrop;
            _showCreate = showCreate;
        }

        // Use the recommended scripts from 0.00 to highest version in each schema
        protected List<SqlScriptRunner.SqlScript> getScripts() throws SqlScriptRunner.SqlScriptException
        {
            return SqlScriptRunner.getRecommendedScripts(_provider.getScripts(_schemaName), 0.0, Double.MAX_VALUE);
        }

        protected void handleScript(SqlScriptRunner.SqlScript script, Map<String, String> createStatements, Map<String, String> dropStatements, PrintWriter out)
        {
        }

        // Careful: createStatements and dropStatements are special LRU maps.  Their order will change on every access.
        protected void handleSchema(Map<String, String> createStatements, Map<String, String> dropStatements, PrintWriter out) throws IOException
        {
            // Get the CREATE VIEW names, reverse their order, and create a DROP statement for each
            List<String> dropNames = new ArrayList<String>();
            List<String> keys = new ArrayList<String>(createStatements.size());
            keys.addAll(createStatements.keySet());
            Collections.reverse(keys);

            for (String key : keys)
            {
                String createStatement = createStatements.get(key);    // NOTE: This access will change the map order.  Don't iterate createStatements past this point.
                Matcher m = _viewPattern.matcher(createStatement);
                m.find();
                String schemaName = (null != m.group(11) ? m.group(11) : _schemaName);
                String viewName = m.group(12);
                dropNames.add(schemaName + "." + viewName);
                dropStatements.remove(key);
            }

            if (_showDrop)
            {
                StringBuilder sb = new StringBuilder();

                // Add a DROP statement for everything left in dropStatements -- these are obsolete views, but we still need to drop them.
                if (!dropStatements.isEmpty())
                {
                    boolean plural = dropStatements.size() > 1;
                    String message = "-- DROP obsolete view" + (plural ? "s" : "") + ".  Do not remove; " + (plural ? "these are" : "this is") + " needed when upgrading from older versions.";
                    outputDropViews(sb, message, dropStatements.values());
                    sb.append(cr);
                }
                sb.append(cr);
                outputDropViews(sb, "-- DROP current view" + (dropNames.size() > 1 ? "s." : "."), dropNames);

                out.print(PageFlowUtil.filter(sb.toString()));

                _provider.saveScript(_schemaName + "-drop.sql", sb.toString());
            }

            if (_showCreate)
            {
                // Reverse the keys back to order of CREATE VIEW statements in the scripts.
                Collections.reverse(keys);

                StringBuilder sb = new StringBuilder();

                // Iterate keys, since the map order has probably changed.
                for (String key : keys)
                {
                    String create = createStatements.get(key);
                    out.println(PageFlowUtil.filter(createStatements.get(key)));
                    sb.append(create).append(cr);
                }

                _provider.saveScript(_schemaName + "-create.sql", sb.toString());
            }
        }

        private void outputDropViews(StringBuilder sb, String message, Collection<String> viewNames)
        {
            sb.append(message).append(cr);

            for (String viewName : viewNames)
            {
                String[] parts = viewName.split("\\.");
                DbSchema schema = DbSchema.get(parts[0]);
                String sql = schema.getSqlDialect().execute(CoreSchema.getInstance().getSchema(), "fn_dropifexists", "'" + parts[1] + "', '" + parts[0] + "', 'VIEW', NULL") + (schema.getSqlDialect().isPostgreSQL() ? ";" : "");
                sb.append(sql).append(cr);
            }

            if (DbSchema.get(_schemaName).getSqlDialect().isSqlServer())
            {
                sb.append("GO").append(cr);
            }
        }
    }
}
