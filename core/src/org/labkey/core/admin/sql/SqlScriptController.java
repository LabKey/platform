/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

package org.labkey.core.admin.sql;

import org.apache.commons.lang.ArrayUtils;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.data.*;
import org.labkey.api.data.SqlScriptRunner.SqlScript;
import org.labkey.api.data.SqlScriptRunner.SqlScriptException;
import org.labkey.api.data.SqlScriptRunner.SqlScriptProvider;
import org.labkey.api.jsp.JspLoader;
import org.labkey.api.module.*;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;
import org.labkey.api.view.template.PageConfig;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SqlScriptController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(SqlScriptController.class);

    public SqlScriptController()
    {
        setActionResolver(_actionResolver);
    }


    public static ActionURL getShowRunningScriptsURL(String moduleName)
    {
        ActionURL url = new ActionURL(ShowRunningScriptsAction.class, ContainerManager.getRoot());
        url.addParameter("moduleName", moduleName);
        return url;
    }


    @RequiresSiteAdmin
    @AllowedDuringUpgrade
    public class ShowRunningScriptsAction extends SimpleViewAction<SqlScriptForm>
    {
        public ModelAndView getView(SqlScriptForm form, BindException errors) throws Exception
        {
            List<SqlScript> scripts = SqlScriptRunner.getRunningScripts(form.getModuleName());

            if (scripts.isEmpty())
                HttpView.throwRedirect(PageFlowUtil.urlProvider(AdminUrls.class).getModuleStatusURL());

            getPageConfig().setTemplate(PageConfig.Template.Dialog);

            ShowRunningScriptsPage page = (ShowRunningScriptsPage) JspLoader.createPage(getViewContext().getRequest(), SqlScriptController.class, "showRunningScripts.jsp");
            page.setWaitForScriptsURL(getWaitForScriptsURL(form.getModuleName()));
            page.setCurrentURL(getViewContext().cloneActionURL());
            page.setScripts(scripts);

            return new JspView(page);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    private static ActionURL getWaitForScriptsURL(String moduleName)
    {
        ActionURL url = new ActionURL(WaitForScriptsAction.class, ContainerManager.getRoot());
        url.addParameter("moduleName", moduleName);
        return url;
    }


    @RequiresSiteAdmin
    @AllowedDuringUpgrade
    public class WaitForScriptsAction extends SimpleViewAction<SqlScriptForm>
    {
        public ModelAndView getView(SqlScriptForm form, BindException errors) throws Exception
        {
            getViewContext().getResponse().setHeader("Cache-Control", "no-cache");

            StringBuilder xml = new StringBuilder();

            xml.append("<?xml version=\"1.0\" encoding=\"iso-8859-1\"?>\n");
            xml.append("<status>");
            if (SqlScriptRunner.waitForScriptToFinish(form.getModuleName(), 2000))
            {
                xml.append("complete");
            }
            else
            {
                xml.append("incomplete");
            }
            xml.append("</status>");

            getPageConfig().setTemplate(PageConfig.Template.None);

            HtmlView view = new HtmlView(xml.toString());
            view.setFrame(WebPartView.FrameType.NONE);
            view.setContentType("text/xml");

            return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    public static class SqlScriptForm
    {
        private String _moduleName;
        private String _filename;

        public String getModuleName()
        {
            return _moduleName;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setModuleName(String moduleName)
        {
            _moduleName = moduleName;
        }

        public String getFilename()
        {
            return _filename;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setFilename(String filename)
        {
            _filename = filename;
        }
    }


    @RequiresSiteAdmin
    public class ScriptsAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            Container c = getContainer();
            StringBuilder html = new StringBuilder();
            if (AppProps.getInstance().isDevMode())
                html.append("[<a href='consolidateScripts.view'>consolidate scripts</a>]<p/>");
            html.append("<table><tr><td colspan=2>Scripts that have run on this server</td><td colspan=2>Scripts that have not run on this server</td></tr>");
            html.append("<tr><td>All</td><td>Incremental</td><td>All</td><td>Incremental</td></tr>");
            html.append("<tr valign=top>");

            TableInfo tinfo = CoreSchema.getInstance().getTableInfoSqlScripts();

            // Need both filename and moduleName to create links for each script
            List<Script> allRun = Arrays.asList(Table.select(tinfo, tinfo.getColumns("FileName, ModuleName"), null, new Sort("FileName"), Script.class));
            List<Script> incrementalRun = new ArrayList<Script>();

            for (Script script : allRun)
                if (isIncrementalScript(script))
                    incrementalRun.add(script);

            appendScripts(c, html, allRun);
            appendScripts(c, html, incrementalRun);

            List<Script> allNotRun = new ArrayList<Script>();
            List<Script> incrementalNotRun = new ArrayList<Script>();
            List<Module> modules = ModuleLoader.getInstance().getModules();

            for (Module module : modules)
            {
                if (module instanceof DefaultModule)
                {
                    DefaultModule defModule = (DefaultModule)module;

                    if (defModule.hasScripts())
                    {
                        SqlScriptProvider provider = new FileSqlScriptProvider(defModule);
                        List<SqlScript> scripts = provider.getScripts(null);

                        for (SqlScript script : scripts)
                        {
                            Script scriptBean = new Script(defModule.getName(), script.getDescription());

                            if (!allRun.contains(scriptBean))
                                allNotRun.add(scriptBean);
                        }
                    }
                }
            }

            for (Script script : allNotRun)
                if (isIncrementalScript(script))
                    incrementalNotRun.add(script);

            appendScripts(c, html, allNotRun);
            appendScripts(c, html, incrementalNotRun);

            html.append("</tr></table>");

            return new HtmlView(html.toString());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return PageFlowUtil.urlProvider(AdminUrls.class).appendAdminNavTrail(root, "SQL Scripts", getURL());
        }

        public ActionURL getURL()
        {
            return new ActionURL(SqlScriptController.ScriptsAction.class, ContainerManager.getRoot());
        }

        private boolean isIncrementalScript(Script script)
        {
            String filename = script.getFilename();
            String[] parts = filename.split("-|\\.sql");

            double startVersion = Double.parseDouble(parts[1]) * 10;
            double endVersion = Double.parseDouble(parts[2]) * 10;

            return (Math.floor(startVersion) != startVersion || Math.floor(endVersion) != endVersion);
        }

        private void appendScripts(Container c, StringBuilder html, List<Script> scripts)
        {
            html.append("<td>\n");

            if (scripts.size() > 0)
            {
                Collections.sort(scripts);

                for (Script script : scripts)
                {
                    ActionURL url = new ActionURL(ScriptAction.class, c);
                    url.addParameter("moduleName", script.getModuleName());
                    url.addParameter("filename", script.getFilename());

                    html.append("<a href=\"");
                    html.append(url);
                    html.append("\">");
                    html.append(script.getFilename());
                    html.append("</a><br>\n");
                }
            }
            else
                html.append("None");

            html.append("</td>\n");
        }
    }


    private ActionURL getConsolidateScriptsURL(double fromVersion, double toVersion)
    {
        ActionURL url = new ActionURL(ConsolidateScriptsAction.class, ContainerManager.getRoot());
        url.addParameter("fromVersion", Double.toString(fromVersion));
        url.addParameter("toVersion", Double.toString(toVersion));

        return url;
    }


    @RequiresSiteAdmin
    public class ConsolidateScriptsAction extends SimpleViewAction<ConsolidateForm>
    {
        public ModelAndView getView(ConsolidateForm form, BindException errors) throws Exception
        {
            List<Module> modules = ModuleLoader.getInstance().getModules();
            List<ScriptConsolidator> consolidators = new ArrayList<ScriptConsolidator>();

            double fromVersion = form.getFromVersion();
            double toVersion = form.getToVersion();

            for (Module module : modules)
            {
                if (module instanceof DefaultModule)
                {
                    DefaultModule defModule = (DefaultModule)module;

                    if (defModule.hasScripts())
                    {
                        FileSqlScriptProvider provider = new FileSqlScriptProvider(defModule);
                        Set<String> schemaNames = provider.getSchemaNames();

                        for (String schemaName : schemaNames)
                        {
                            ScriptConsolidator consolidator = new ScriptConsolidator(provider, schemaName, fromVersion, toVersion);

                            if (!consolidator.getScripts().isEmpty())
                                consolidators.add(consolidator);
                        }
                    }
                }
            }

            StringBuilder formHtml = new StringBuilder();

            formHtml.append("<form method=\"get\">\n");
            formHtml.append("  <table>\n");
            formHtml.append("    <tr><td>From:</td><td><input name=\"fromVersion\" size=\"10\" value=\"");
            formHtml.append(ModuleContext.formatVersion(fromVersion));
            formHtml.append("\"/></td></tr>\n");
            formHtml.append("    <tr><td>To:</td><td><input name=\"toVersion\" size=\"10\" value=\"");
            formHtml.append(ModuleContext.formatVersion(toVersion));
            formHtml.append("\"/></td></tr>\n");
            formHtml.append("    <tr><td colspan=2>");
            formHtml.append(PageFlowUtil.generateSubmitButton("Update"));
            formHtml.append("</td></tr>\n");
            formHtml.append("  </table>\n");
            formHtml.append("</form><br>\n");

            StringBuilder html = new StringBuilder();

            for (ScriptConsolidator consolidator : consolidators)
            {
                List<SqlScript> scripts = consolidator.getScripts();
                String filename = consolidator.getFilename();

                if (1 == scripts.size() && scripts.get(0).getDescription().equals(filename))
                    continue;  // No consolidation to do on this schema

                html.append("<b>Schema ").append(consolidator.getSchemaName()).append("</b><br>\n");

                for (SqlScript script : scripts)
                    html.append(script.getDescription()).append("<br>\n");

                html.append("<br>\n");

                ActionURL consolidateURL = getConsolidateSchemaURL(consolidator.getModuleName(), consolidator.getSchemaName(), fromVersion, toVersion);
                html.append("[<a href=\"").append(consolidateURL.getEncodedLocalURIString()).append("\">").append(1 == consolidator.getScripts().size() ? "copy" : "consolidate").append(" to ").append(filename).append("</a>]<br><br>\n");
            }

            if (0 == html.length())
                html.append("No schemas require consolidation in this range");

            html.insert(0, formHtml);

            return new HtmlView(html.toString());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            new ScriptsAction().appendNavTrail(root);
            root.addChild("Consolidate Scripts");
            return root;
        }
    }


    private static class ScriptConsolidator
    {
        private final FileSqlScriptProvider _provider;
        private final String _schemaName;
        private final List<SqlScript> _scripts;
        private final double _fromVersion;
        private final double _toVersion;

        protected boolean _includeOriginatingScriptComments = true;

        private ScriptConsolidator(FileSqlScriptProvider provider, String schemaName, double fromVersion, double toVersion) throws SqlScriptException
        {
            _provider = provider;
            _schemaName = schemaName;
            _fromVersion = fromVersion;
            _toVersion = toVersion;
            _scripts = SqlScriptRunner.getRecommendedScripts(provider.getScripts(schemaName), fromVersion, toVersion);
        }

        private List<SqlScript> getScripts()
        {
            return _scripts;
        }

        private String getSchemaName()
        {
            return _schemaName;
        }

        private double getFromVersion()
        {
            return _fromVersion;
        }

        private double getToVersion()
        {
            return _toVersion;
        }

        private String getFilename()
        {
            return getSchemaName() + "-" + ModuleContext.formatVersion(getFromVersion()) + "-" + ModuleContext.formatVersion(getToVersion()) + ".sql";
        }

        private String getModuleName()
        {
            return _provider.getProviderName();
        }

        // Concatenate all the recommended scripts together, removing all but the first copyright notice
        protected String getConsolidatedScript()
        {
            Pattern copyrightPattern = Pattern.compile("^/\\*\\s*\\*\\s*Copyright.*under the License.\\s*\\*/\\s*", Pattern.CASE_INSENSITIVE + Pattern.DOTALL + Pattern.MULTILINE);
            StringBuilder sb = new StringBuilder();
            boolean firstScript = true;

            for (SqlScript script : getScripts())
            {
                String contents = script.getContents().trim();
                Matcher licenseMatcher = copyrightPattern.matcher(contents);

                if (firstScript)
                {
                    int contentStartIndex = 0;

                    if (licenseMatcher.lookingAt())
                    {
                        contentStartIndex = licenseMatcher.end();
                        sb.append(contents.substring(0, contentStartIndex));
                    }

                    if (_includeOriginatingScriptComments)
                        sb.append("/* ").append(script.getDescription()).append(" */\n\n");

                    sb.append(contents.substring(contentStartIndex, contents.length()));
                    firstScript = false;
                }
                else
                {
                    sb.append("\n\n");

                    if (_includeOriginatingScriptComments)
                        sb.append("/* ").append(script.getDescription()).append(" */\n\n");

                    sb.append(licenseMatcher.replaceFirst(""));    // Remove license
                }
            }

            return sb.toString();
        }

        public void saveScript() throws IOException
        {
            _provider.saveScript(getFilename(), getConsolidatedScript());
        }
    }


    public static class ConsolidateForm
    {
        private String _module;
        private String _schema;
        private double _fromVersion = Math.floor(ModuleLoader.getInstance().getCoreModule().getVersion() * 10) / 10;
        private double _toVersion = _fromVersion + 0.1;

        public String getModule()
        {
            return _module;
        }

        public void setModule(String module)
        {
            _module = module;
        }

        public String getSchema()
        {
            return _schema;
        }

        public void setSchema(String schema)
        {
            _schema = schema;
        }

        public double getFromVersion()
        {
            return _fromVersion;
        }

        public void setFromVersion(double fromVersion)
        {
            _fromVersion = fromVersion;
        }

        public double getToVersion()
        {
            return _toVersion;
        }

        public void setToVersion(double toVersion)
        {
            _toVersion = toVersion;
        }
    }


    private ActionURL getConsolidateSchemaURL(String moduleName, String schemaName, double fromVersion, double toVersion)
    {
        ActionURL url = new ActionURL(ConsolidateSchemaAction.class, ContainerManager.getRoot());
        url.addParameter("module", moduleName);
        url.addParameter("schema", schemaName);
        url.addParameter("fromVersion", String.valueOf(fromVersion));
        url.addParameter("toVersion", String.valueOf(toVersion));
        return url;
    }


    @RequiresSiteAdmin
    public class ConsolidateSchemaAction extends FormViewAction<ConsolidateForm>
    {
        private String _schemaName;

        public void validateCommand(ConsolidateForm target, Errors errors)
        {
        }

        public ModelAndView getView(ConsolidateForm form, boolean reshow, BindException errors) throws Exception
        {
            _schemaName = form.getSchema();
            ScriptConsolidator consolidator = getConsolidator(form);

            StringBuilder html = new StringBuilder("<pre>\n");
            html.append(PageFlowUtil.filter(consolidator.getConsolidatedScript()));
            html.append("</pre>\n");

            html.append("<form method=\"post\">");
            html.append(PageFlowUtil.generateSubmitButton("Save to " + consolidator.getFilename()));
            html.append(PageFlowUtil.generateButton("Back", getSuccessURL(form)));
            html.append("</form>");

            return new HtmlView(html.toString());
        }

        public boolean handlePost(ConsolidateForm form, BindException errors) throws Exception
        {
            ScriptConsolidator consolidator = getConsolidator(form);
            consolidator.saveScript();

            return true;
        }

        public ActionURL getSuccessURL(ConsolidateForm form)
        {
            return getConsolidateScriptsURL(form.getFromVersion(), form.getToVersion());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Consolidate Scripts for Schema " + _schemaName);
        }

        private ScriptConsolidator getConsolidator(ConsolidateForm form) throws SqlScriptException
        {
            DefaultModule module = (DefaultModule)ModuleLoader.getInstance().getModule(form.getModule());
            FileSqlScriptProvider provider = new FileSqlScriptProvider(module);
            return getConsolidator(provider, form.getSchema(), form.getFromVersion(), form.getToVersion());
        }

        protected ScriptConsolidator getConsolidator(FileSqlScriptProvider provider, String schemaName, double fromVersion, double toVersion)  throws SqlScriptException
        {
            return new ScriptConsolidator(provider, schemaName, fromVersion, toVersion);
        }
    }


    public static class Script implements Comparable<Script>
    {
        private String _moduleName;
        private String _filename;
        private SqlScript _sqlScript = null;

        public Script()
        {
        }

        public Script(String moduleName, String filename)
        {
            _moduleName = moduleName;
            _filename = filename;
        }

        public String getModuleName()
        {
            return _moduleName;
        }

        public void setModuleName(String moduleName)
        {
            _moduleName = moduleName;
        }

        public String getFilename()
        {
            return _filename;
        }

        public void setFilename(String filename)
        {
            _filename = filename;
        }

        public int compareTo(Script s2)
        {
            return getFilename().compareTo(s2.getFilename());
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Script script = (Script) o;

            return !(_filename != null ? !_filename.equals(script._filename) : script._filename != null);
        }

        @Override
        public int hashCode()
        {
            return _filename != null ? _filename.hashCode() : 0;
        }
    }


    @RequiresSiteAdmin
    public class ScriptAction extends SimpleViewAction<SqlScriptForm>
    {
        private String _filename;

        public ModelAndView getView(SqlScriptForm form, BindException errors) throws Exception
        {
            DefaultModule module = (DefaultModule)ModuleLoader.getInstance().getModule(form.getModuleName());
            FileSqlScriptProvider provider = new FileSqlScriptProvider(module);
            _filename = form.getFilename();

            return getScriptView(provider.getScript(_filename));
        }

        protected ModelAndView getScriptView(SqlScript script)
        {
            return new ScriptView(script);
        }

        protected String getActionDescription()
        {
            return _filename;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            new ScriptsAction().appendNavTrail(root);
            root.addChild(getActionDescription());
            return root;
        }
    }


    private static class ScriptView extends HttpView<SqlScript>
    {
        private ScriptView(SqlScript script)
        {
            super(script);
        }

        @Override
        protected void renderInternal(SqlScript script, PrintWriter out) throws Exception
        {
            String contents = script.getContents();
            String errorMessage = script.getErrorMessage();

            if (null == errorMessage)
            {
                renderScript(contents, out);

                if (AppProps.getInstance().isDevMode())
                    renderButtons(script, out);
            }
            else
            {
                out.print("Error: " + PageFlowUtil.filter(errorMessage));
            }
        }

        protected void renderScript(String contents, PrintWriter out)
        {
            out.println("<pre>");
            out.println(PageFlowUtil.filter(contents));
            out.println("</pre>");
        }

        protected void renderButtons(SqlScript script, PrintWriter out)
        {
            ActionURL url = new ActionURL(ReorderScriptAction.class, getViewContext().getContainer());
            url.addParameter("moduleName", script.getProvider().getProviderName());
            url.addParameter("filename", script.getDescription());
            out.println(PageFlowUtil.generateButton("Reorder Script", url));
        }
    }


    @RequiresSiteAdmin
    public class ReorderScriptAction extends ScriptAction
    {
        @Override
        protected ModelAndView getScriptView(SqlScript script)
        {
            return new ReorderingScriptView(script);
        }

        @Override
        protected String getActionDescription()
        {
            return "Reorder " + super.getActionDescription();
        }
    }


    @RequiresSiteAdmin
    public class SaveReorderedScriptAction extends ReorderScriptAction
    {
        // TODO: Implement
    }


    private static class ReorderingScriptView extends ScriptView
    {
        private ReorderingScriptView(SqlScript script)
        {
            super(script);
        }

        @Override
        protected void renderScript(String contents, PrintWriter out)
        {
            out.println("<table>");
            ScriptReorderer reorderer = new ScriptReorderer(contents);
            out.println(reorderer.getReorderedScript(true));
            out.println("</table>");
        }

        protected void renderButtons(SqlScript script, PrintWriter out)
        {
            ActionURL url = new ActionURL(SaveReorderedScriptAction.class, getViewContext().getContainer());
            url.addParameter("moduleName", script.getProvider().getProviderName());
            url.addParameter("filename", script.getDescription());
            out.println(PageFlowUtil.generateButton("Save Reordered Script to " + script.getDescription(), url));
        }
    }


    private static class ScriptReorderer
    {
        private static final String TABLE_NAME_REGEX = "((?:(?:\\w+)\\.)?(?:[a-zA-Z0-9]+))";
        private static final String STATEMENT_ENDING_REGEX = "GO$(\\s*)";
        private static final String COMMENT_REGEX = "((/\\*.+?\\*/)|(^--.+?$))\\s*";   // Single-line or block comment, followed by white space

        private String _contents;
        private int _row = 0;
        private final Map<String, Collection<String>> _statements = new LinkedHashMap<String, Collection<String>>();
        private final List<String> _unknownStatements = new LinkedList<String>();

        private ScriptReorderer(String contents)
        {
            _contents = contents;
        }

        public String getReorderedScript(boolean isHtml)
        {
            Pattern commentPattern = compile(COMMENT_REGEX);

            List<Pattern> patterns = new LinkedList<Pattern>();
            patterns.add(compile("INSERT INTO " + TABLE_NAME_REGEX + " \\([^\\)]+?\\) VALUES \\([^\\)]+?\\)\\s*(" + STATEMENT_ENDING_REGEX + "|$(\\s*))"));
            patterns.add(compile("INSERT INTO " + TABLE_NAME_REGEX + " \\([^\\)]+?\\) SELECT .+? "+ STATEMENT_ENDING_REGEX));
            patterns.add(compile("EXEC sp_rename (?:@objname\\s*=\\s*)?'" + TABLE_NAME_REGEX + "'.+?" + STATEMENT_ENDING_REGEX ));
            patterns.add(compile("EXEC core\\.fn_dropifexists '(\\w+)', '(\\w+)'.+?" + STATEMENT_ENDING_REGEX));
            patterns.add(compile("CREATE (?:UNIQUE )?(?:CLUSTERED )?INDEX [a-zA-Z0-9_]+? ON " + TABLE_NAME_REGEX + ".+? " + STATEMENT_ENDING_REGEX));
            patterns.add(compile(getRegExWithPrefix("CREATE TABLE ")));
            patterns.add(compile(getRegExWithPrefix("ALTER TABLE ")));
            patterns.add(compile(getRegExWithPrefix("INSERT INTO ")));
            patterns.add(compile(getRegExWithPrefix("UPDATE ")));
            patterns.add(compile(getRegExWithPrefix("DELETE FROM ")));
            patterns.add(compile(getRegExWithPrefix("DROP TABLE ")));

            List<Pattern> nonTablePatterns = new LinkedList<Pattern>();
            nonTablePatterns.add(compile(getRegExWithPrefix("CREATE PROCEDURE ")));

            StringBuilder newScript = new StringBuilder();
            StringBuilder unknown = new StringBuilder();

            boolean firstMatch = true;

            while (0 < _contents.length())
            {
                // Parse all the comments first.  If we match a table statement next, we'll include the comments.
                StringBuilder comments = new StringBuilder();

                Matcher m = commentPattern.matcher(_contents);

                while (m.lookingAt())
                {
                    comments.append(m.group());
                    _contents = _contents.substring(m.end());
                    m = commentPattern.matcher(_contents);
                }

                boolean found = false;

                for (Pattern p : patterns)
                {
                    m = p.matcher(_contents);

                    if (m.lookingAt())
                    {
                        if (firstMatch)
                        {
                            // Section before first match (copyright, license, type creation, etc.) always goes first
                            addStatement("initial section", unknown.toString());
                            unknown = new StringBuilder();
                            firstMatch = false;
                        }

                        String tableName = m.group(1);

                        if (-1 == tableName.indexOf('.'))
                            tableName = m.group(2) + "." + m.group(1);

                        addStatement(tableName, comments + m.group());
                        _contents = _contents.substring(m.end());
                        found = true;
                        break;
                    }
                }

                String nonTableStatement = null;

                if (!found)
                {
                    for (Pattern p : nonTablePatterns)
                    {
                        m = p.matcher(_contents);

                        if (m.lookingAt())
                        {
                            nonTableStatement = comments + m.group();
                            _contents = _contents.substring(m.end());
                            found = true;
                            break;
                        }
                    }
                }

                if (found)
                {
                    if (unknown.length() > 0)
                    {
                        _unknownStatements.add(unknown.toString());
                        unknown = new StringBuilder();
                    }
                }
                else
                {
                    unknown.append(comments);

                    if (_contents.length() > 0)
                    {
                        unknown.append(_contents.charAt(0));
                        _contents = _contents.substring(1);
                    }
                }

                if (null != nonTableStatement)
                    _unknownStatements.add(nonTableStatement);
            }

            appendAllStatements(newScript, isHtml);

            if (unknown.length() > 0)
                _unknownStatements.add(unknown.toString());

            if (!_unknownStatements.isEmpty())
            {
                appendStatement(newScript, "\n=======================\n", isHtml);

                for (String unknownStatement : _unknownStatements)
                    appendStatement(newScript, unknownStatement, isHtml);
            }

            return newScript.toString();
        }

        private String getRegExWithPrefix(String prefix)
        {
            return prefix + TABLE_NAME_REGEX + ".+? " + STATEMENT_ENDING_REGEX;
        }

        private Pattern compile(String regEx)
        {
            return Pattern.compile(regEx.replaceAll(" ", "\\\\s+"), Pattern.CASE_INSENSITIVE + Pattern.DOTALL + Pattern.MULTILINE);
        }

        private void addStatement(String tableName, String statement)
        {
            String key = tableName.toLowerCase();

            Collection<String> tableStatements = _statements.get(key);

            if (null == tableStatements)
            {
                tableStatements = new LinkedList<String>();
                _statements.put(key, tableStatements);
            }

            tableStatements.add(statement);
        }

        private void appendAllStatements(StringBuilder sb, boolean html)
        {
            for (Map.Entry<String, Collection<String>> tableStatements : _statements.entrySet())
                for (String statement : tableStatements.getValue())
                    appendStatement(sb, statement, html);
        }

        private void appendStatement(StringBuilder sb, String statement, boolean html)
        {
            if (html)
            {
                sb.append("<tr class=\"");
                sb.append(0 == (_row % 2) ? "labkey-row" : "labkey-alternate-row");
                sb.append("\"><td>");
                sb.append(PageFlowUtil.filter(statement, true));
                sb.append("</td></tr>\n");
                _row++;
            }
            else
            {
                sb.append(statement);
            }
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class ExtractViewsAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            getPageConfig().setTemplate(PageConfig.Template.None);

            String type = getViewContext().getActionURL().getParameter("type");

            if ("drop".equals(type))
                return new ExtractDropView();
            else if ("create".equals(type))
                return new ExtractCreateView();
            else if ("clear".equals(type))
                return new ClearView();

            return new HtmlView("Error: must specify type parameter (\"drop\", \"create\", or \"clear\")");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }

        private abstract class ExtractView extends HttpView
        {
            abstract List<Module> getModules();
            abstract ViewHandler getHandler(FileSqlScriptProvider provider, String schemaName);

            @Override
            protected void renderInternal(Object model, PrintWriter out) throws Exception
            {
                int totalScriptLines = 0;

                out.println("<pre>");

                for (Module module : getModules())
                {
                    if (module instanceof DefaultModule)
                    {
                        DefaultModule defModule = (DefaultModule)module;

                        if (defModule.hasScripts())
                        {
                            FileSqlScriptProvider provider = new FileSqlScriptProvider(defModule);
                            Set<String> schemaNames = provider.getSchemaNames();

                            for (String schemaName : schemaNames)
                            {
                                ViewHandler handler = getHandler(provider, schemaName);
                                handler.handle(out);
                                totalScriptLines += handler.getScriptLines();
                            }
                        }
                    }
                }

                out.println("Total lines processed: " + totalScriptLines);
                out.println("</pre>");
            }
        }

        private class ExtractDropView extends ExtractView
        {
            List<Module> getModules()
            {
                List<Module> modules = new ArrayList<Module>(ModuleLoader.getInstance().getModules());
                Collections.reverse(modules);
                return modules;
            }

            ViewHandler getHandler(FileSqlScriptProvider provider, String schemaName)
            {
                return new ViewHandler.ViewExtractor(provider, schemaName, true, false);
            }
        }

        private class ExtractCreateView extends ExtractView
        {
            List<Module> getModules()
            {
                return ModuleLoader.getInstance().getModules();
            }

            ViewHandler getHandler(FileSqlScriptProvider provider, String schemaName)
            {
                return new ViewHandler.ViewExtractor(provider, schemaName, false, true);
            }
        }

        private class ClearView extends ExtractView
        {
            List<Module> getModules()
            {
                return ModuleLoader.getInstance().getModules();
            }

            ViewHandler getHandler(FileSqlScriptProvider provider, String schemaName)
            {
                return new ViewHandler.ViewClearer(provider, schemaName);
            }
        }
    }


    @RequiresSiteAdmin
    public class UnreachableScriptsAction extends SimpleViewAction<ConsolidateForm>
    {
        public ModelAndView getView(ConsolidateForm form, BindException errors) throws Exception
        {
            List<Module> modules = ModuleLoader.getInstance().getModules();
            Set<SqlScript> unreachableScripts = new TreeSet<SqlScript>(new Comparator<SqlScript>() {
                public int compare(SqlScript s1, SqlScript s2)
                {
                    // Order scripts by fromVersion.  If fromVersion is the same, use standard compare order (schema + from + to)
                    int fromCompare = new Double(s1.getFromVersion()).compareTo(s2.getFromVersion());

                    if (0 != fromCompare)
                        return fromCompare;

                    return s1.compareTo(s2);
                }
            });

            double[] fromVersions = new double[]{0.00, 2.00, 2.10, 2.20, 2.30, 8.10, 8.20, 8.30, 9.10, 9.20, 9.30, 10.1};
            double toVersion = form.getToVersion();

            for (Module module : modules)
            {
                if (module instanceof DefaultModule)
                {
                    DefaultModule defModule = (DefaultModule)module;

                    if (defModule.hasScripts())
                    {
                        FileSqlScriptProvider provider = new FileSqlScriptProvider(defModule);
                        Set<String> schemaNames = provider.getSchemaNames();

                        for (String schemaName : schemaNames)
                        {
                            Set<SqlScript> allSchemaScripts = new HashSet<SqlScript>(provider.getScripts(schemaName));
                            Set<SqlScript> reachableScripts = new HashSet<SqlScript>(allSchemaScripts.size());

                            for (double fromVersion : fromVersions)
                            {
                                List<SqlScript> recommendedScripts = SqlScriptRunner.getRecommendedScripts(provider.getScripts(schemaName), fromVersion, toVersion);
                                reachableScripts.addAll(recommendedScripts);
                            }

                            if (allSchemaScripts.size() != reachableScripts.size())
                            {
                                allSchemaScripts.removeAll(reachableScripts);
                                unreachableScripts.addAll(allSchemaScripts);
                            }
                        }
                    }
                }
            }

            double previousVersion = -1;

            StringBuilder html = new StringBuilder("SQL scripts that will never run when upgrading from any of the following versions: ");
            html.append(ArrayUtils.toString(fromVersions)).append("<br>");

            for (SqlScript script : unreachableScripts)
            {
                double roundedVersion = Math.floor(script.getFromVersion() * 10);

                if (previousVersion < roundedVersion)
                {
                    html.append("<br>");
                    previousVersion = roundedVersion;
                }

                html.append(script).append("<br>");
            }

            return new HtmlView(html.toString());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            new ScriptsAction().appendNavTrail(root);
            root.addChild("Unreachable Scripts");
            return root;
        }
    }
}
