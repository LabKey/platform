/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.Constants;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.IgnoresAllocationTracking;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.FileSqlScriptProvider;
import org.labkey.api.data.SqlScriptManager;
import org.labkey.api.data.SqlScriptRunner;
import org.labkey.api.data.SqlScriptRunner.SqlScript;
import org.labkey.api.data.SqlScriptRunner.SqlScriptProvider;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.module.AllowedDuringUpgrade;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.AdminConsoleAction;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AbstractActionPermissionTest;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.element.CsrfInput;
import org.labkey.api.vcs.Vcs;
import org.labkey.api.vcs.VcsService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.RedirectException;
import org.labkey.core.admin.AdminController;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SqlScriptController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(SqlScriptController.class);
    private static final Logger LOG = LogManager.getLogger(SqlScriptController.class);

    public SqlScriptController()
    {
        setActionResolver(_actionResolver);
    }

    @IgnoresAllocationTracking
    @SuppressWarnings("unused")
    @RequiresPermission(AdminOperationsPermission.class)
    @AllowedDuringUpgrade
    public class GetModuleStatusAction extends ReadOnlyApiAction
    {
        @Override
        public ApiResponse execute(Object o, BindException errors)
        {
            JSONObject result = new JSONObject();
            JSONArray modulesJSON = new JSONArray();

            SqlScriptRunner runner = ModuleLoader.getInstance().getUpgradeScriptRunner();
            String currentlyUpgradingModule = runner.getCurrentModuleName();
            result.put("currentlyUpgradingModule", currentlyUpgradingModule);

            for (Module module : ModuleLoader.getInstance().getModules())
            {
                JSONObject moduleJSON = new JSONObject();
                ModuleContext ctx = ModuleLoader.getInstance().getModuleContext(module);
                moduleJSON.put("name", module.getName());
                moduleJSON.put("state", ctx.getModuleState().toString());

                JSONArray scriptsJSON = new JSONArray();

                if (module.getName().equals(currentlyUpgradingModule))
                {
                    moduleJSON.put("currentlyUpgrading", true);
                    List<SqlScript> sqlScripts = runner.getRunningScripts(currentlyUpgradingModule);
                    for (SqlScript sqlScript : sqlScripts)
                    {
                        JSONObject scriptJSON = new JSONObject();
                        scriptJSON.put("description", sqlScript.getDescription());
                        scriptJSON.put("fromVersion", sqlScript.getFromVersion());
                        scriptJSON.put("toVersion", sqlScript.getToVersion());
                        scriptsJSON.put(scriptJSON);
                    }
                }
                else
                {
                    moduleJSON.put("currentlyUpgrading", false);
                }

                moduleJSON.put("scripts", scriptsJSON);
                modulesJSON.put(moduleJSON);
            }
            result.put("modules", modulesJSON);
            result.put("message", ModuleLoader.getInstance().getStartingUpMessage());
            result.put("upgradeRequired", ModuleLoader.getInstance().isUpgradeRequired());
            result.put("upgradeInProgress", ModuleLoader.getInstance().isUpgradeInProgress());
            result.put("startupInProgress", ModuleLoader.getInstance().isStartupInProgress());
            result.put("startupComplete", ModuleLoader.getInstance().isStartupComplete());
            result.put("newInstall", ModuleLoader.getInstance().isNewInstall());

            return new ApiSimpleResponse(result);
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


    public static class ScriptsForm
    {
        private boolean _managedOnly = false;

        public boolean isManagedOnly()
        {
            return _managedOnly;
        }

        @SuppressWarnings("unused")
        public void setManagedOnly(boolean managedOnly)
        {
            _managedOnly = managedOnly;
        }
    }

    @AdminConsoleAction
    @RequiresPermission(AdminOperationsPermission.class)
    public class ScriptsAction extends SimpleViewAction<ScriptsForm>
    {
        @Override
        public ModelAndView getView(ScriptsForm form, BindException errors) throws Exception
        {
            setHelpTopic(new HelpTopic("sqlScripts#admin"));

            StringBuilder html = new StringBuilder("<table>");

            html.append("<tr><td colspan=4>");

            if (AppProps.getInstance().isDevMode())
            {
                if (form.isManagedOnly())
                    html.append(PageFlowUtil.link("show all modules").href(new ActionURL(ScriptsAction.class, ContainerManager.getRoot())));
                else
                    html.append(PageFlowUtil.link("show only managed modules").href(new ActionURL(ScriptsAction.class, ContainerManager.getRoot()).addParameter("managedOnly", true)));

                html.append(PageFlowUtil.link("consolidate scripts").href(new ActionURL(ConsolidateScriptsAction.class, ContainerManager.getRoot())));
                html.append(PageFlowUtil.link("orphaned scripts").href(new ActionURL(OrphanedScriptsAction.class, ContainerManager.getRoot())));
                html.append(PageFlowUtil.link("scripts with errors").href(new ActionURL(ScriptsWithErrorsAction.class, ContainerManager.getRoot())));
//                html.append(PageFlowUtil.textLink("reorder all scripts", new ActionURL(ReorderAllScriptsAction.class, ContainerManager.getRoot())));
            }

            // Make this link available on production deployments
            html.append(PageFlowUtil.link("upgrade code").href(new ActionURL(UpgradeCodeAction.class, ContainerManager.getRoot())));
            html.append("</td></tr>");
            html.append("<tr><td>&nbsp;</td></tr>");

            html.append("<tr><td colspan=2>Scripts that have run on this server</td><td colspan=2>Scripts that have not run on this server</td></tr>");
            html.append("<tr><td>All</td><td>Incremental</td><td>All</td><td>Incremental</td></tr>");
            html.append("<tr valign=top>");

            List<Module> modules = ModuleLoader.getInstance().getModules();

            if (form.isManagedOnly())
            {
                modules = modules.stream()
                    .filter(Module::shouldManageVersion)
                    .collect(Collectors.toList());
            }

            ArrayList<SqlScript> allRun = new ArrayList<>();

            for (Module module : modules)
            {
                FileSqlScriptProvider provider = new FileSqlScriptProvider(module);

                for (DbSchema schema : provider.getSchemas())
                    allRun.addAll(SqlScriptManager.get(provider, schema).getPreviouslyRunScripts());
            }

            ArrayList<SqlScript> incrementalRun = allRun
                .stream()
                .filter(SqlScript::isIncremental)
                .collect(Collectors.toCollection(ArrayList::new));

            appendScripts(html, allRun);
            appendScripts(html, incrementalRun);

            ArrayList<SqlScript> allNotRun = new ArrayList<>();
            ArrayList<SqlScript> incrementalNotRun = new ArrayList<>();
            Set<String> allFilenames = new LinkedHashSet<>();

            for (Module module : modules)
            {
                SqlScriptProvider provider = new FileSqlScriptProvider(module);

                for (DbSchema schema : provider.getSchemas())
                {
                    List<SqlScript> scripts = provider.getScripts(schema);

                    scripts.stream()
                        .filter(script -> !allRun.contains(script))
                        .forEach(allNotRun::add);

                    scripts.forEach(script -> allFilenames.add(script.getDescription()));
                }
            }

            allNotRun.stream()
                .filter(SqlScript::isIncremental)
                .forEach(incrementalNotRun::add);

            appendScripts(html, allNotRun);
            appendScripts(html, incrementalNotRun);

            html.append("</tr></table>");

            // In dev mode, check for some special scripts that need to remain, even though they appear to be incremental
            // and might not run during bootstrap.
            if (AppProps.getInstance().isDevMode())
            {
                SqlDialect dialect = CoreSchema.getInstance().getSqlDialect();
                List<Pair<String, Module>> requiredScripts = new LinkedList<>();

                for (Module module : modules)
                {
                    new FileSqlScriptProvider(module).getRequiredScripts(dialect)
                        .forEach(filename -> requiredScripts.add(new Pair<>(filename, module)));
                }

                StringBuilder warningHtml = new StringBuilder();

                for (Pair<String, Module> pair : requiredScripts)
                {
                    if (!allFilenames.contains(pair.getKey()))
                        warningHtml.append("<span class=\"labkey-error\">Warning: The script ").append(PageFlowUtil.filter(pair.getKey())).append(" from module ").append(PageFlowUtil.filter(pair.getValue().getName())).append(" was not found!</span><br>\n");
                }

                if (warningHtml.length() > 0)
                    html.insert(0, warningHtml);
            }

            return new HtmlView(html.toString());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            urlProvider(AdminUrls.class).addAdminNavTrail(root, "SQL Scripts", getClass(), ContainerManager.getRoot());
        }
    }


    // We sort the list of scripts, so insist on an ArrayList
    private void appendScripts(StringBuilder html, ArrayList<SqlScript> scripts)
    {
        Container c = getContainer();
        html.append("<td>\n");

        if (scripts.isEmpty())
        {
            html.append("None");
        }
        else
        {
            Collections.sort(scripts);

            for (SqlScript script : scripts)
            {
                ActionURL url = new ActionURL(ScriptAction.class, c);
                url.addParameter("moduleName", script.getProvider().getProviderName());
                url.addParameter("filename", script.getDescription());

                html.append("<a href=\"");
                html.append(PageFlowUtil.filter(url));
                html.append("\">");
                html.append(PageFlowUtil.filter(script.getDescription()));

                html.append("</a><br>\n");
            }
        }

        html.append("</td>\n");
    }


    @RequiresPermission(AdminOperationsPermission.class)
    public class ScriptsWithErrorsAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            setHelpTopic(new HelpTopic("sqlScripts#admin"));

            ArrayList<SqlScript> scriptsWithErrors = new ArrayList<>();
            Map<SqlScript, String> errorMessages = new HashMap<>();

            for (Module module : ModuleLoader.getInstance().getModules())
            {
                FileSqlScriptProvider provider = new FileSqlScriptProvider(module);

                for (DbSchema schema : provider.getSchemas())
                {
                    for (SqlScript script : provider.getScripts(schema))
                    {
                        Collection<String> warnings = script.getSchema().getSqlDialect().getScriptWarnings(script.getDescription(), script.getContents());

                        if (!warnings.isEmpty())
                        {
                            scriptsWithErrors.add(script);
                            errorMessages.put(script, warnings.iterator().next());
                        }
                    }
                }
            }

            StringBuilder html = new StringBuilder("<table><tr>");
            appendScripts(html, scriptsWithErrors);

            html.append("<td>\n");

            for (SqlScript script : scriptsWithErrors)
            {
                html.append(errorMessages.get(script)).append("<br>\n");
            }

            html.append("<td>\n");
            html.append("</tr></table>");

            return new HtmlView(html.toString());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            new ScriptsAction().addNavTrail(root);
            root.addChild("Scripts With Errors");
        }
    }


    private ActionURL getConsolidateScriptsURL(double fromVersion, double toVersion, boolean includeSingleScripts)
    {
        ActionURL url = new ActionURL(ConsolidateScriptsAction.class, ContainerManager.getRoot());
        url.addParameter("fromVersion", Double.toString(fromVersion));
        url.addParameter("toVersion", Double.toString(toVersion));

        if (includeSingleScripts)
            url.addParameter("includeSingleScripts", true);

        return url;
    }


    private static List<ScriptConsolidator> getConsolidators(double fromVersion, double toVersion, boolean includeSingleScripts)
    {
        List<Module> modules = ModuleLoader.getInstance().getModules();
        List<ScriptConsolidator> consolidators = new ArrayList<>();

        for (Module module : modules)
        {
            if (!module.shouldManageVersion())
                continue;

            FileSqlScriptProvider provider = new FileSqlScriptProvider(module);
            Collection<DbSchema> schemas = provider.getSchemas();

            for (DbSchema schema : schemas)
            {
                ScriptConsolidator consolidator = new ScriptConsolidator(provider, schema, fromVersion, toVersion);
                List<SqlScript> scripts = consolidator.getScripts();

                if (!scripts.isEmpty())
                {
                    if (scripts.size() > 1)
                    {
                        consolidators.add(consolidator);
                    }
                    else if (includeSingleScripts)
                    {
                        String filename = consolidator.getFilename();
                        SqlScript script = scripts.get(0);

                        // Skip if the single script in this range is the consolidation script
                        if (!script.getDescription().equalsIgnoreCase(filename) && 0.0 != script.getFromVersion())
                            consolidators.add(consolidator);
                    }
                }
            }
        }

        // Order by schema name
        consolidators.sort(Comparator.comparing(ScriptConsolidator::getSchemaName));

        return consolidators;
    }


    private static abstract class AbstractScriptRangeAction<K extends ScriptRangeForm> extends SimpleViewAction<K>
    {
        @Override
        protected String getCommandClassMethodName()
        {
            return "getScriptHtml";
        }

        @Override
        public final ModelAndView getView(K form, BindException errors)
        {
            setHelpTopic(new HelpTopic("sqlScripts#admin"));

            double _fromVersion = form.getFromVersion();
            double _toVersion = form.getToVersion();

            StringBuilder formHtml = new StringBuilder();

            formHtml.append("<form method=\"get\">\n");
            formHtml.append("  <table>\n");
            formHtml.append("    <tr><td>From:</td><td><input name=\"fromVersion\" size=\"10\" value=\"");
            formHtml.append(ModuleContext.formatVersion(_fromVersion));
            formHtml.append("\"/></td></tr>\n");
            formHtml.append("    <tr><td>To:</td><td><input name=\"toVersion\" size=\"10\" value=\"");
            formHtml.append(ModuleContext.formatVersion(_toVersion));
            formHtml.append("\"/></td></tr>\n");

            appendExtraRows(formHtml, form);

            formHtml.append("    <tr><td colspan=2>");
            formHtml.append(PageFlowUtil.button("Update").submit(true));
            formHtml.append("</td></tr>\n");
            formHtml.append("  </table>\n");
            formHtml.append("</form><br>\n");

            StringBuilder html = getScriptHtml(form);
            html.insert(0, formHtml);

            return new HtmlView(html.toString());
        }

        protected abstract void appendExtraRows(StringBuilder html, K form);
        public abstract StringBuilder getScriptHtml(K form);
    }


    @RequiresPermission(AdminOperationsPermission.class)
    public class ConsolidateScriptsAction extends AbstractScriptRangeAction<ConsolidateForm>
    {
        @Override
        protected void appendExtraRows(StringBuilder html, ConsolidateForm form)
        {
            html.append("    <tr><td colspan=2><input type=\"checkbox\" name=\"includeSingleScripts\"");
            html.append(form.getIncludeSingleScripts() ? " checked" : "");
            html.append("/>Include single scripts that don't start with 0.00</td></tr>\n");
        }

        @Override
        public StringBuilder getScriptHtml(ConsolidateForm form)
        {
            List<ScriptConsolidator> consolidators = getConsolidators(form.getFromVersion(), form.getToVersion(), form.getIncludeSingleScripts());
            StringBuilder html = new StringBuilder();

            for (ScriptConsolidator consolidator : consolidators)
            {
                List<SqlScript> scripts = consolidator.getScripts();
                String filename = consolidator.getFilename();

                html.append("<b>Schema ").append(consolidator.getSchemaName()).append("</b><br>\n");

                for (SqlScript script : scripts)
                    html.append(script.getDescription()).append("<br>\n");

                html.append("<br>\n");

                ActionURL consolidateURL = getConsolidateSchemaURL(consolidator.getModuleName(), consolidator.getSchemaName(), form.getFromVersion(), form.getToVersion(), form.getIncludeSingleScripts());
                html.append(PageFlowUtil.button((1 == consolidator.getScripts().size() ? "copy" : "consolidate") + " to " + filename).href(consolidateURL)).append("<br><br>\n");
            }

            if (0 == html.length())
                html.append("No schemas require consolidation in this range");

            return html;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addNavTrail(root, null);
        }

        public void addNavTrail(NavTree root, @Nullable ActionURL consolidatedScriptsURL)
        {
            new ScriptsAction().addNavTrail(root);

            if (null == consolidatedScriptsURL)
                root.addChild("Consolidate Scripts");
            else
                root.addChild("Consolidate Scripts", consolidatedScriptsURL);
        }
    }


    private static class ScriptConsolidator
    {
        private final FileSqlScriptProvider _provider;
        private final DbSchema _schema;
        private final List<SqlScript> _scripts;
        private final double _targetFrom;
        private final double _targetTo;
        private final double _actualTo;

        private Collection<String> _errors = null;

        private ScriptConsolidator(FileSqlScriptProvider provider, DbSchema schema, double targetFrom, double targetTo)
        {
            _provider = provider;
            _schema = schema;
            _targetFrom = targetFrom;
            _targetTo = targetTo;
            _scripts = SqlScriptManager.get(provider, schema).getRecommendedScripts(provider.getScripts(schema), targetFrom, targetTo);
            _actualTo = _scripts.isEmpty() ? -1 : _scripts.get(_scripts.size() - 1).getToVersion();
        }

        private List<SqlScript> getScripts()
        {
            return _scripts;
        }

        private String getSchemaName()
        {
            return _schema.getDisplayName();
        }

        private String getFilename()
        {
            // Ending version should be next 0.1 increment after last script in this batch, unless it's already a 0.1 multiple
            // or we're targeting something less
//            double adjustedTo = Math.min(Math.floor(_actualTo * 10 + .999)/10, _targetTo);

            // TODO: Shouldn't provider assemble the filename?
            return getSchemaName() + "-" + ModuleContext.formatVersion(_targetFrom) + "-" + ModuleContext.formatVersion(_targetTo) + ".sql";
        }

        private String getModuleName()
        {
            return _provider.getProviderName();
        }

        // Concatenate all the recommended scripts together, removing all but the first copyright notice
        protected String getConsolidatedScript()
        {
            Pattern copyrightPattern = Pattern.compile("^/\\*\\s*\\*\\s*Copyright.*?\\*/\\s*", Pattern.CASE_INSENSITIVE + Pattern.DOTALL + Pattern.MULTILINE);
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
                        sb.append(contents, 0, contentStartIndex);
                    }

                    // Skip for incremental and existing bootstrap scripts
                    if (!script.isIncremental() && script.getFromVersion() != 0.0)
                        sb.append("/* ").append(script.getDescription()).append(" */\n\n");

                    sb.append(contents.substring(contentStartIndex));
                    firstScript = false;
                }
                else
                {
                    sb.append("\n\n");

                    // Skip for incremental scripts
                    if (!script.isIncremental())
                        sb.append("/* ").append(script.getDescription()).append(" */\n\n");

                    sb.append(licenseMatcher.replaceFirst(""));    // Remove license
                }
            }

            String consolidated = sb.toString();
            _errors = _schema.getSqlDialect().getScriptWarnings(getFilename(), consolidated);
            return consolidated;
        }

        public @NotNull Collection<String> getErrors()
        {
            if (null == _errors)
                throw new IllegalStateException("Must call getConsolidatedScript() first");

            return _errors;
        }

        public void saveScript() throws IOException
        {
            _provider.saveScript(_schema, getFilename(), getConsolidatedScript());
        }

        public File getScriptDirectory()
        {
            return _provider.getScriptDirectory(_schema.getSqlDialect());
        }
    }


    public static class ScriptRangeForm
    {
        protected double _fromVersion;
        protected double _toVersion = Constants.getLowestSchemaVersion() + 1;

        public double getFromVersion()
        {
            return _fromVersion;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setFromVersion(double fromVersion)
        {
            _fromVersion = fromVersion;
        }

        public double getToVersion()
        {
            return _toVersion;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setToVersion(double toVersion)
        {
            _toVersion = toVersion;
        }
    }


    public static class ConsolidateForm extends ScriptRangeForm
    {
        private String _module;
        private String _schema;
        private boolean _includeSingleScripts = false;

        public ConsolidateForm()
        {
            _fromVersion = 0.0;
            _toVersion = Constants.getEarliestUpgradeVersion();
        }

        public String getModule()
        {
            return _module;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setModule(String module)
        {
            _module = module;
        }

        public String getSchema()
        {
            return _schema;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setSchema(String schema)
        {
            _schema = schema;
        }

        public boolean getIncludeSingleScripts()
        {
            return _includeSingleScripts;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setIncludeSingleScripts(boolean includeSingleScripts)
        {
            _includeSingleScripts = includeSingleScripts;
        }
    }


    private ActionURL getConsolidateSchemaURL(String moduleName, String schemaName, double fromVersion, double toVersion, boolean includeSingleScripts)
    {
        ActionURL url = new ActionURL(ConsolidateSchemaAction.class, ContainerManager.getRoot());
        url.addParameter("module", moduleName);
        url.addParameter("schema", schemaName);
        url.addParameter("fromVersion", ModuleContext.formatVersion(fromVersion));
        url.addParameter("toVersion", ModuleContext.formatVersion(toVersion));

        if (includeSingleScripts)
            url.addParameter("includeSingleScripts", true);

        return url;
    }


    @RequiresPermission(AdminOperationsPermission.class)
    public class ConsolidateSchemaAction extends FormViewAction<ConsolidateForm>
    {
        private String _schemaName;
        private double _fromVersion;
        private double _toVersion;
        private boolean _includeSingleScripts;

        @Override
        public void validateCommand(ConsolidateForm target, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(ConsolidateForm form, boolean reshow, BindException errors)
        {
            _fromVersion = form.getFromVersion();
            _toVersion = form.getToVersion();
            _includeSingleScripts = form.getIncludeSingleScripts();
            _schemaName = form.getSchema();

            ScriptConsolidator consolidator = getConsolidator(form);
            String consolidated = consolidator.getConsolidatedScript();

            StringBuilder html = new StringBuilder();
            html.append(getErrorHtml(consolidator.getErrors()));

            html.append("<pre>\n");
            html.append(PageFlowUtil.filter(consolidated));
            html.append("</pre>\n");

            html.append("<form method=\"post\">");
            html.append(new CsrfInput(getViewContext()));
            html.append(PageFlowUtil.button("Save to " + consolidator.getFilename()).submit(true));
            html.append(PageFlowUtil.button("Back").href(getSuccessURL(form)));
            html.append("</form>");

            return new HtmlView(html.toString());
        }

        @Override
        public boolean handlePost(ConsolidateForm form, BindException errors) throws Exception
        {
            ScriptConsolidator consolidator = getConsolidator(form);
            consolidator.saveScript();
            deleteScripts(consolidator.getScriptDirectory(), consolidator.getScripts());

            return true;
        }

        @Override
        public ActionURL getSuccessURL(ConsolidateForm form)
        {
            return getConsolidateScriptsURL(form.getFromVersion(), form.getToVersion(), form.getIncludeSingleScripts());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            new ConsolidateScriptsAction().addNavTrail(root, getConsolidateScriptsURL(_fromVersion, _toVersion, _includeSingleScripts));
            root.addChild("Consolidate Scripts for Schema " + _schemaName);
        }

        private ScriptConsolidator getConsolidator(ConsolidateForm form)
        {
            Module module = ModuleLoader.getInstance().getModule(form.getModule());
            FileSqlScriptProvider provider = new FileSqlScriptProvider(module);
            return getConsolidator(provider, DbSchema.get(form.getSchema(), DbSchemaType.Module), form.getFromVersion(), form.getToVersion());
        }

        protected ScriptConsolidator getConsolidator(FileSqlScriptProvider provider, DbSchema schema, double fromVersion, double toVersion)
        {
            return new ScriptConsolidator(provider, schema, fromVersion, toVersion);
        }
    }


    private void deleteScripts(File dir, List<SqlScript> scripts)
    {
        Vcs vcs = VcsService.get().getVcs(dir);

        if (null != vcs)
        {
            scripts.forEach(sqlScript -> vcs.deleteFile(sqlScript.getDescription()));
        }
    }


    private static @NotNull String getErrorHtml(@NotNull Collection<String> errors)
    {
        if (errors.isEmpty())
            return "";

        StringBuilder html = new StringBuilder();

            html.append("<div class=\"labkey-error\">");

            for (String message : errors)
            {
                html.append(PageFlowUtil.filter(message));
                html.append("<br>\n");
            }

            html.append("</div>\n");

        return html.toString();
    }


    @RequiresPermission(AdminOperationsPermission.class)
    public class OrphanedScriptsAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object form, BindException errors) throws IOException
        {
            setHelpTopic(new HelpTopic("sqlScripts#admin"));

            Set<SqlScript> orphanedScripts = new TreeSet<>();
            Set<String> unclaimedFiles = new TreeSet<>();
            Map<SqlScript, SqlScript> successors = new HashMap<>();
            List<Module> modules = ModuleLoader.getInstance().getModules();

            for (Module module : modules)
            {
                if (!module.shouldManageVersion())
                    continue;

                FileSqlScriptProvider provider = new FileSqlScriptProvider(module);
                Collection<DbSchema> schemas = provider.getSchemas();
                Set<String> allFiles = new CaseInsensitiveHashSet();

                // If module advertises no schemas then still look in the labkey dialect directory for spurious scripts
                if (schemas.isEmpty())
                {
                    addFiles(allFiles, CoreSchema.getInstance().getSqlDialect(), provider);
                }
                else
                {
                    Set<String> dialectNames = new HashSet<>();

                    for (DbSchema schema : schemas)
                    {
                        SqlDialect dialect = schema.getSqlDialect();
                        String dialectName = dialect.getProductName();

                        if (!dialectNames.contains(dialectName))
                        {
                            dialectNames.add(dialectName);
                            addFiles(allFiles, dialect, provider);
                        }
                    }
                }

                for (DbSchema schema : schemas)
                {
                    Set<SqlScript> scripts = new TreeSet<>(provider.getScripts(schema));
                    SqlScript previous = null;

                    for (SqlScript script : scripts)
                    {
                        allFiles.remove(script.getDescription());

                        if (null != previous && (previous.getSchema().equals(script.getSchema()) && previous.getFromVersion() == script.getFromVersion()))
                        {
                            // Save the script so we can render them in order
                            orphanedScripts.add(previous);
                            // Save successor as well to render with the orphaned script name
                            successors.put(previous, script);
                        }

                        previous = script;
                    }

                    SqlScript create = provider.getCreateScript(schema);

                    if (null != create)
                        allFiles.remove(create.getDescription());

                    SqlScript drop = provider.getDropScript(schema);

                    if (null != drop)
                        allFiles.remove(drop.getDescription());

                    // Remove all the filenames listed in ignored_scripts.txt
                    allFiles.removeAll(provider.getIgnoredScripts(schema.getSqlDialect()));
                }

                // Remove standard files that can exist in any script directory
                allFiles.remove("ignored_scripts.txt");
                allFiles.remove("README.txt");
                allFiles.remove("required_scripts.txt");

                // Anything that's left is "unclaimed" and will get a warning below
                unclaimedFiles.addAll(allFiles);
            }

            StringBuilder html = new StringBuilder();
            html.append("  <table>\n");
            html.append("    <tr><td>The SQL scripts listed below will never execute, because another script has the same" +
                    " \"from\" version and a later \"to\" version. These scripts can be deleted safely.</td></tr>\n");
            html.append("    <tr><td>&nbsp;</td></tr>\n");
            html.append("  </table>\n");

            html.append("  <table>\n");
            html.append("    <tr><th align=\"left\">Orphaned Script</th><th align=\"left\">Superseded By</th></tr>\n");

            for (SqlScript orphanedScript : orphanedScripts)
            {
                html.append("    <tr><td>");
                html.append(orphanedScript.getDescription());
                html.append("</td><td>");
                html.append(successors.get(orphanedScript).getDescription());
                html.append("</td></tr>\n");
            }

            html.append("  </table>\n");

            if (!unclaimedFiles.isEmpty())
            {
                html.append("<br><b>WARNING: Unrecognized files ").append(unclaimedFiles.toString()).append("</b>");
            }

            return new HtmlView(html.toString());
        }

        private void addFiles(Set<String> allFiles, SqlDialect dialect, FileSqlScriptProvider provider)
        {
            File dir = provider.getScriptDirectory(dialect);

            if (null != dir && dir.exists())
            {
                File[] files = dir.listFiles();

                assert null != files;

                for (File file : files)
                    if (file.isFile())
                        allFiles.add(file.getName());
            }
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            new ScriptsAction().addNavTrail(root);
            root.addChild("Orphaned Scripts");
        }
    }


    @RequiresPermission(AdminOperationsPermission.class)
    public class ScriptAction extends SimpleViewAction<SqlScriptForm>
    {
        private String _filename;

        @Override
        public ModelAndView getView(SqlScriptForm form, BindException errors) throws Exception
        {
            Module module = ModuleLoader.getInstance().getModule(form.getModuleName());
            FileSqlScriptProvider provider = new FileSqlScriptProvider(module);
            _filename = form.getFilename();

            return getScriptView(provider.getScript(null, _filename));
        }

        protected ModelAndView getScriptView(SqlScript script) throws IOException
        {
            return new ScriptView(script);
        }

        protected String getActionDescription()
        {
            return _filename;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            new ScriptsAction().addNavTrail(root);
            root.addChild(getActionDescription());
        }
    }


    private static class ScriptView extends HttpView<SqlScript>
    {
        private ScriptView(SqlScript script)
        {
            super(script);
        }

        @Override
        protected void renderInternal(SqlScript script, PrintWriter out)
        {
            // First, review contents for errors
            script.getContents();
            String errorMessage = script.getErrorMessage();

            if (null == errorMessage)
            {
                renderScript(script, out);

                if (AppProps.getInstance().isDevMode())
                    renderButtons(script, out);
            }
            else
            {
                out.print(PageFlowUtil.filter("Error: " + errorMessage));
            }
        }

        protected void renderScript(SqlScript script, PrintWriter out)
        {
            String contents = script.getContents();
            Collection<String> warnings = script.getSchema().getSqlDialect().getScriptWarnings(script.getDescription(), contents);
            out.println(getErrorHtml(warnings));

            out.println("<pre>");
            out.println(PageFlowUtil.filter(contents));
            out.println("</pre>");
        }

        protected void renderButtons(SqlScript script, PrintWriter out)
        {
            ActionURL url = new ActionURL(ReorderScriptAction.class, getViewContext().getContainer());
            url.addParameter("moduleName", script.getProvider().getProviderName());
            url.addParameter("filename", script.getDescription());
            out.println(PageFlowUtil.button("Reorder Script").href(url));
        }
    }


    @RequiresPermission(AdminOperationsPermission.class)
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


    @RequiresPermission(AdminOperationsPermission.class)
    public class ReorderAllScriptsAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            return new HttpView()
            {
                @Override
                protected void renderInternal(Object model, PrintWriter out)
                {
                    for (Module module : ModuleLoader.getInstance().getModules())
                    {
                        FileSqlScriptProvider provider = new FileSqlScriptProvider(module);

                        for (DbSchema schema : provider.getSchemas())
                        {
                            for (SqlScript script : provider.getScripts(schema))
                            {
                                ScriptReorderer reorderer = new ScriptReorderer(schema, script.getContents());
                                out.println("<table>");
                                out.println("<tr><td><b>" + script.getDescription() + "</b></td></tr>\n");
                                out.println(reorderer.getReorderedScript(true));
                                out.println("</table>");
                            }
                        }
                    }
                }
            };
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Reorder all scripts");
        }
    }


    @RequiresPermission(AdminOperationsPermission.class)
    public class SaveReorderedScriptAction extends ScriptAction
    {
        @Override
        protected ModelAndView getScriptView(SqlScript script) throws RedirectException, IOException
        {
            ScriptReorderer reorderer = new ScriptReorderer(script.getSchema(), script.getContents());
            String reorderedScript = reorderer.getReorderedScript(false);
            ((FileSqlScriptProvider)script.getProvider()).saveScript(script.getSchema(), script.getDescription(), reorderedScript, true);
            
            final ActionURL url = new ActionURL(ScriptAction.class, getContainer());
            url.addParameter("moduleName", script.getProvider().getProviderName());
            url.addParameter("filename", script.getDescription());

            throw new RedirectException(url);
        }
    }


    private static class ReorderingScriptView extends ScriptView
    {
        private ReorderingScriptView(SqlScript script)
        {
            super(script);
        }

        @Override
        protected void renderScript(SqlScript script, PrintWriter out)
        {
            out.println("<table>");
            ScriptReorderer reorderer = new ScriptReorderer(script.getSchema(), script.getContents());
            out.println(reorderer.getReorderedScript(true));
            out.println("</table>");
        }

        @Override
        protected void renderButtons(SqlScript script, PrintWriter out)
        {
            ActionURL reorderUrl = new ActionURL(SaveReorderedScriptAction.class, getViewContext().getContainer());
            reorderUrl.addParameter("moduleName", script.getProvider().getProviderName());
            reorderUrl.addParameter("filename", script.getDescription());
            out.println(PageFlowUtil.button("Save Reordered Script to " + script.getDescription()).href(reorderUrl));

            ActionURL backUrl = new ActionURL(ScriptAction.class, getViewContext().getContainer());
            backUrl.addParameter("moduleName", script.getProvider().getProviderName());
            backUrl.addParameter("filename", script.getDescription());
            out.println(PageFlowUtil.button("Back").href(backUrl));
        }
    }


    @RequiresPermission(AdminOperationsPermission.class)
    public class UnreachableScriptsAction extends SimpleViewAction<ScriptRangeForm>
    {
        @Override
        public ModelAndView getView(ScriptRangeForm form, BindException errors)
        {
            // Order scripts by fromVersion. If fromVersion is the same, use standard compare order (schema + from + to)
            Set<SqlScript> unreachableScripts = new TreeSet<>(Comparator.comparingDouble(SqlScript::getFromVersion).thenComparing(s -> s));

            Collection<Double> fromVersions = new LinkedList<>();
            fromVersions.add(0.00);
            fromVersions.addAll(Constants.getMajorSchemaVersions());

            double toVersion = form.getToVersion();

            ModuleLoader.getInstance().getModules().stream()
                .filter(AdminController.ManageFilter.ManagedOnly::accept)
                .forEach(module->
                    {
                        FileSqlScriptProvider provider = new FileSqlScriptProvider(module);
                        Collection<DbSchema> schemas = provider.getSchemas();

                        for (DbSchema schema : schemas)
                        {
                            Set<SqlScript> allSchemaScripts = new HashSet<>(provider.getScripts(schema));
                            Set<SqlScript> reachableScripts = new HashSet<>(allSchemaScripts.size());
                            SqlScriptManager manager = SqlScriptManager.get(provider, schema);

                            for (double fromVersion : fromVersions)
                            {
                                List<SqlScript> recommendedScripts = manager.getRecommendedScripts(provider.getScripts(schema), fromVersion, toVersion);
                                reachableScripts.addAll(recommendedScripts);
                            }

                            if (allSchemaScripts.size() != reachableScripts.size())
                            {
                                allSchemaScripts.removeAll(reachableScripts);
                                unreachableScripts.addAll(allSchemaScripts);
                            }
                        }
                    }
                );

            double previousRoundedVersion = -1;
            List<SqlScript> batch = new LinkedList<>();

            StringBuilder html = new StringBuilder("SQL scripts that will never run when upgrading to schema version " + ModuleContext.formatVersion(toVersion) + " from any of the following schema versions: ");
            String joinedVersions = fromVersions.stream()
                .map(ModuleContext::formatVersion)
                .collect(Collectors.joining(", "));
            html.append("[").append(joinedVersions).append("]<br>");

            for (SqlScript script : unreachableScripts)
            {
                double roundedVersion = Math.floor(script.getFromVersion() * 10);

                if (previousRoundedVersion < roundedVersion)
                {
                    appendBatch(html, batch);
                    previousRoundedVersion = roundedVersion;
                    batch = new ArrayList<>();
                }

                batch.add(script);
            }

            appendBatch(html, batch);

            return new HtmlView(html.toString());
        }

        private void appendBatch(StringBuilder html, List<SqlScript> batch)
        {
            Collections.sort(batch);

            for (SqlScript sqlScript : batch)
                html.append(sqlScript).append("<br>");

            html.append("<br>");
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            new ScriptsAction().addNavTrail(root);
            root.addChild("Unreachable Scripts");
        }
    }

    public static class UpgradeCodeForm
    {
        private String _module;
        private String _method;

        public String getCombined()
        {
            return null;
        }

        public void setCombined(String combined)
        {
            String[] parts = combined.split(": ");
            if (parts.length == 2)
            {
                _module = parts[0];
                _method = parts[1];
            }
        }

        public String getModule()
        {
            return _module;
        }

        public String getMethod()
        {
            return _method;
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public class UpgradeCodeAction extends FormViewAction<UpgradeCodeForm>
    {
        private Method _method;
        private ModuleContext _ctx;
        private UpgradeCode _code;

        @Override
        public void validateCommand(UpgradeCodeForm form, Errors errors)
        {
            Module module = ModuleLoader.getInstance().getModule(form.getModule());
            if (null == module)
            {
                errors.reject(ERROR_MSG, "Module not found");
            }
            else
            {
                _code = module.getUpgradeCode();
                if (null == _code)
                {
                    errors.reject(ERROR_MSG, "Module doesn't have UpgradeCode");
                }
                else
                {
                    try
                    {
                        _method = _code.getClass().getDeclaredMethod(form.getMethod(), ModuleContext.class);
                        _ctx = ModuleLoader.getInstance().getModuleContext(form.getModule());
                        if (null == _ctx)
                            errors.reject(ERROR_MSG, "ModuleContext not found");
                    }
                    catch (NoSuchMethodException e)
                    {
                        errors.reject(ERROR_MSG, "Method doesn't exist");
                    }
                }
            }
        }

        @Override
        public ModelAndView getView(UpgradeCodeForm form, boolean reshow, BindException errors) throws Exception
        {
            return new JspView<>("/org/labkey/core/admin/view/upgradeCode.jsp", null, errors);
        }

        @Override
        public boolean handlePost(UpgradeCodeForm form, BindException errors) throws Exception
        {
            LOG.info("Executing " + _method.getDeclaringClass().getSimpleName() + "." + _method.getName() + "(ModuleContext moduleContext)");
            _method.invoke(_code, _ctx);
            return true;
        }

        @Override
        public URLHelper getSuccessURL(UpgradeCodeForm form)
        {
            return new ActionURL(ScriptsAction.class, ContainerManager.getRoot());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            new ScriptsAction().addNavTrail(root);
            root.addChild("Upgrade Code");
        }
    }

    public static class TestCase extends AbstractActionPermissionTest
    {
        @Override
        public void testActionPermissions()
        {
            User user = TestContext.get().getUser();
            assertTrue(user.hasSiteAdminPermission());

            SqlScriptController controller = new SqlScriptController();

            // @RequiresPermission(AdminOperationsPermission.class)
            assertForAdminOperationsPermission(user,
                controller.new ConsolidateSchemaAction(),
                controller.new ConsolidateScriptsAction(),
                controller.new GetModuleStatusAction(),
                controller.new OrphanedScriptsAction(),
                controller.new ReorderAllScriptsAction(),
                controller.new ReorderScriptAction(),
                controller.new SaveReorderedScriptAction(),
                controller.new ScriptAction(),
                controller.new ScriptsWithErrorsAction(),
                controller.new UnreachableScriptsAction(),
                controller.new UpgradeCodeAction()
            );

            // @AdminConsoleAction
            // @RequiresPermission(AdminOperationsPermission.class)
            assertForAdminOperationsPermission(ContainerManager.getRoot(), user,
                controller.new ScriptsAction()
            );
        }
    }
}
