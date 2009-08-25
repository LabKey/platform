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

package org.labkey.core.admin.sql;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.data.*;
import org.labkey.api.jsp.JspLoader;
import org.labkey.api.module.*;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;
import org.labkey.api.view.template.PageConfig;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

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
            List<SqlScriptRunner.SqlScript> scripts = SqlScriptRunner.getRunningScripts(form.getModuleName());

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

        public String getModuleName()
        {
            return _moduleName;
        }

        public void setModuleName(String moduleName)
        {
            _moduleName = moduleName;
        }
    }


    @RequiresSiteAdmin
    public class ScriptsAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            TableInfo tinfo = CoreSchema.getInstance().getTableInfoSqlScripts();
            List<String> allRun = Arrays.asList(Table.executeArray(tinfo, tinfo.getColumn("FileName"), null, new Sort("FileName"), String.class));
            List<String> incrementalRun = new ArrayList<String>();

            for (String filename : allRun)
                if (isIncrementalScript(filename))
                    incrementalRun.add(filename);

            StringBuilder html = new StringBuilder();
            if (AppProps.getInstance().isDevMode())
                html.append("[<a href='consolidateScripts.view'>consolidate scripts</a>]<p/>");
            html.append("<table><tr><td colspan=2>Scripts that have run on this server</td><td colspan=2>Scripts that have not run on this server</td></tr>");
            html.append("<tr><td>All</td><td>Incremental</td><td>All</td><td>Incremental</td></tr>");

            html.append("<tr valign=top>");

            appendFilenames(html, allRun);
            appendFilenames(html, incrementalRun);

            List<String> allNotRun = new ArrayList<String>();
            List<String> incrementalNotRun = new ArrayList<String>();
            List<Module> modules = ModuleLoader.getInstance().getModules();

            for (Module module : modules)
            {
                if (module instanceof DefaultModule)
                {
                    DefaultModule defModule = (DefaultModule)module;

                    if (defModule.hasScripts())
                    {
                        SqlScriptRunner.SqlScriptProvider provider = new FileSqlScriptProvider(defModule);
                        List<SqlScriptRunner.SqlScript> scripts = provider.getScripts(null);

                        for (SqlScriptRunner.SqlScript script : scripts)
                            if (!allRun.contains(script.getDescription()))
                                allNotRun.add(script.getDescription());
                    }
                }
            }

            for (String filename : allNotRun)
                if (isIncrementalScript(filename))
                    incrementalNotRun.add(filename);

            appendFilenames(html, allNotRun);
            appendFilenames(html, incrementalNotRun);

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
    }


    private boolean isIncrementalScript(String filename)
    {
        String[] parts = filename.split("-|\\.sql");

        double startVersion = Double.parseDouble(parts[1]) * 10;
        double endVersion = Double.parseDouble(parts[2]) * 10;

        return (Math.floor(startVersion) != startVersion || Math.floor(endVersion) != endVersion);
    }


    private void appendFilenames(StringBuilder html, List<String> filenames)
    {
        html.append("<td>\n");

        if (filenames.size() > 0)
        {
            Object[] filenameArray = filenames.toArray();
            Arrays.sort(filenameArray);
            html.append(StringUtils.join(filenameArray, "<br>\n"));
        }
        else
            html.append("None");

        html.append("</td>\n");
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
                List<SqlScriptRunner.SqlScript> scripts = consolidator.getScripts();
                String filename = consolidator.getFilename();

                if (1 == scripts.size() && scripts.get(0).getDescription().equals(filename))
                    continue;  // No consolidation to do on this schema

                html.append("<b>Schema ").append(consolidator.getSchemaName()).append("</b><br>\n");

                for (SqlScriptRunner.SqlScript script : scripts)
                    html.append(script.getDescription()).append("<br>\n");

                html.append("<br>\n");

                ActionURL consolidateURL = getConsolidateSchemaURL(ConsolidateSchemaAction.class, consolidator.getModuleName(), consolidator.getSchemaName(), fromVersion, toVersion);
                ActionURL consolidateAndReorderURL = getConsolidateSchemaURL(ConsolidateSchemaAndReorderAction.class, consolidator.getModuleName(), consolidator.getSchemaName(), fromVersion, toVersion);
                html.append("[<a href=\"").append(consolidateURL.getEncodedLocalURIString()).append("\">").append(1 == consolidator.getScripts().size() ? "copy" : "consolidate").append(" to ").append(filename).append("</a>] " +
                            "[<a href=\"").append(consolidateAndReorderURL.getEncodedLocalURIString()).append("\">").append(1 == consolidator.getScripts().size() ? "copy" : "consolidate").append(" and reorder to ").append(filename).append("</a>]<br><br>\n");
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
        private final List<SqlScriptRunner.SqlScript> _scripts;
        private final double _fromVersion;
        private final double _toVersion;

        protected boolean _includeOriginatingScriptComments = true;

        private ScriptConsolidator(FileSqlScriptProvider provider, String schemaName, double fromVersion, double toVersion) throws SqlScriptRunner.SqlScriptException
        {
            _provider = provider;
            _schemaName = schemaName;
            _fromVersion = fromVersion;
            _toVersion = toVersion;
            _scripts = SqlScriptRunner.getRecommendedScripts(provider.getScripts(schemaName), fromVersion, toVersion);
        }

        private List<SqlScriptRunner.SqlScript> getScripts()
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

            for (SqlScriptRunner.SqlScript script : getScripts())
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


    private ActionURL getConsolidateSchemaURL(Class<? extends Controller> actionClass, String moduleName, String schemaName, double fromVersion, double toVersion)
    {
        ActionURL url = new ActionURL(actionClass, ContainerManager.getRoot());
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

        private ScriptConsolidator getConsolidator(ConsolidateForm form) throws SqlScriptRunner.SqlScriptException
        {
            DefaultModule module = (DefaultModule)ModuleLoader.getInstance().getModule(form.getModule());
            FileSqlScriptProvider provider = new FileSqlScriptProvider(module);
            return getConsolidator(provider, form.getSchema(), form.getFromVersion(), form.getToVersion());
        }

        protected ScriptConsolidator getConsolidator(FileSqlScriptProvider provider, String schemaName, double fromVersion, double toVersion)  throws SqlScriptRunner.SqlScriptException
        {
            return new ScriptConsolidator(provider, schemaName, fromVersion, toVersion);
        }
    }


    @RequiresSiteAdmin
    public class ConsolidateSchemaAndReorderAction extends ConsolidateSchemaAction
    {
        @Override
        protected ScriptConsolidator getConsolidator(FileSqlScriptProvider provider, String schemaName, double fromVersion, double toVersion) throws SqlScriptRunner.SqlScriptException
        {
            return new ReorderingScriptConsolidator(provider, schemaName, fromVersion, toVersion);
        }
    }


    private static class ReorderingScriptConsolidator extends ScriptConsolidator
    {
        private static final String TABLE_NAME_REGEX = "((?:(?:\\w+)\\.)?(?:[a-zA-Z0-9]+))";
        private static final String STATEMENT_ENDING_REGEX = "GO$(\\s*)";
        private static final String COMMENT_REGEX = "((/\\*.+?\\*/)|(^--.+?$))\\s*";   // Single-line or block comment, followed by white space

        private Map<String, Collection<String>> _statements = new LinkedHashMap<String, Collection<String>>();

        private ReorderingScriptConsolidator(FileSqlScriptProvider provider, String schemaName, double fromVersion, double toVersion)
                throws SqlScriptRunner.SqlScriptException
        {
            super(provider, schemaName, fromVersion, toVersion);
            _includeOriginatingScriptComments = false;
        }

        @Override
        protected String getConsolidatedScript()
        {
            Pattern commentPattern = compile(COMMENT_REGEX);

            List<Pattern> patterns = new ArrayList<Pattern>(10);
            patterns.add(compile("INSERT INTO " + TABLE_NAME_REGEX + " \\(.+?\\) VALUES \\(.+?\\)(" + STATEMENT_ENDING_REGEX + "|$(\\s*))"));
            patterns.add(compile("EXEC sp_rename '" + TABLE_NAME_REGEX + ".+?(" + STATEMENT_ENDING_REGEX + "|$(\\s*))"));
            patterns.add(compile("CREATE (?:UNIQUE )?(?:CLUSTERED )?INDEX [a-zA-Z0-9_]+? ON " + TABLE_NAME_REGEX + ".+? " + STATEMENT_ENDING_REGEX));
            patterns.add(compile(getRegExWithPrefix("CREATE TABLE ")));
            patterns.add(compile(getRegExWithPrefix("ALTER TABLE ")));
            patterns.add(compile(getRegExWithPrefix("INSERT INTO ")));
            patterns.add(compile(getRegExWithPrefix("UPDATE ")));
            patterns.add(compile(getRegExWithPrefix("DELETE FROM ")));

            StringBuilder newScript = new StringBuilder();
            StringBuilder unknown = new StringBuilder();
            String script = super.getConsolidatedScript();

            boolean firstMatch = true;

            while (0 < script.length())
            {
                // Parse all the comments first.  If we match a table statement next, we'll include the comments.
                StringBuilder comments = new StringBuilder();

                Matcher m = commentPattern.matcher(script);

                while (m.lookingAt())
                {
                    comments.append(m.group());
                    script = script.substring(m.end());
                    m = commentPattern.matcher(script);
                }

                boolean found = false;

                for (Pattern p : patterns)
                {
                    m = p.matcher(script);

                    if (m.lookingAt())
                    {
                        if (firstMatch)
                        {
                            newScript.append(unknown);
                            unknown = new StringBuilder();
                            firstMatch = false;
                        }

                        addStatement(m.group(1), comments + m.group());
                        script = script.substring(m.end());
                        found = true;
                        break;
                    }
                }

                if (!found)
                {
                    unknown.append(comments);

                    if (script.length() > 0)
                    {
                        unknown.append(script.charAt(0));
                        script = script.substring(1);
                    }
                }
            }

            appendAllStatements(newScript);

            if (unknown.length() > 0)
            {
                newScript.append("\n=======================\n");
                newScript.append(unknown);
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

        private void appendAllStatements(StringBuilder sb)
        {
            for (Map.Entry<String, Collection<String>> tableStatements : _statements.entrySet())
            {
                for (String statement : tableStatements.getValue())
                {
                    sb.append(statement);
                }
            }
        }
    }


    @RequiresPermission(ACL.PERM_ADMIN)
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
            Set<SqlScriptRunner.SqlScript> unreachableScripts = new TreeSet<SqlScriptRunner.SqlScript>(new Comparator<SqlScriptRunner.SqlScript>() {
                public int compare(SqlScriptRunner.SqlScript s1, SqlScriptRunner.SqlScript s2)
                {
                    // Order scripts by fromVersion.  If fromVersion is the same, use standard compare order (schema + from + to)
                    int fromCompare = new Double(s1.getFromVersion()).compareTo(s2.getFromVersion());

                    if (0 != fromCompare)
                        return fromCompare;

                    return s1.compareTo(s2);
                }
            });

            double[] fromVersions = new double[]{0.00, 2.00, 2.10, 2.20, 2.30, 8.10, 8.20};
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
                            Set<SqlScriptRunner.SqlScript> allSchemaScripts = new HashSet<SqlScriptRunner.SqlScript>(provider.getScripts(schemaName));
                            Set<SqlScriptRunner.SqlScript> reachableScripts = new HashSet<SqlScriptRunner.SqlScript>(allSchemaScripts.size());

                            for (double fromVersion : fromVersions)
                            {
                                List<SqlScriptRunner.SqlScript> recommendedScripts = SqlScriptRunner.getRecommendedScripts(provider.getScripts(schemaName), fromVersion, toVersion);
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

            for (SqlScriptRunner.SqlScript script : unreachableScripts)
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
