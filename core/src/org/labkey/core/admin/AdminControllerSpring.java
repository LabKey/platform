package org.labkey.core.admin;

import org.labkey.api.action.*;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.data.FileSqlScriptProvider;
import org.labkey.api.data.SqlScriptRunner;
import org.labkey.api.data.SqlScriptRunner.SqlScript;
import org.labkey.api.exp.api.AdminUrls;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Feb 27, 2008
 */
public class AdminControllerSpring extends SpringActionController
{
    static DefaultActionResolver _actionResolver = new BeehivePortingActionResolver(AdminController.class, AdminControllerSpring.class);

    public AdminControllerSpring() throws Exception
    {
        super();
        setActionResolver(_actionResolver);
    }

    @RequiresPermission(ACL.PERM_NONE)
    public class ShowAuditLogAction extends QueryViewAction<ShowAuditLogForm, QueryView>
    {
        public ShowAuditLogAction()
        {
            super(ShowAuditLogForm.class);
        }

        protected ModelAndView getHtmlView(ShowAuditLogForm form, BindException errors) throws Exception
        {
            if (!getViewContext().getUser().isAdministrator())
                HttpView.throwUnauthorized();
            VBox view = new VBox();

            String selected = form.getView();
            if (selected == null)
                selected = AuditLogService.get().getAuditViewFactories()[0].getEventType();

            JspView jspView = new JspView("/org/labkey/core/admin/auditLog.jsp");
            ((ModelAndView)jspView).addObject("currentView", selected);

            view.addView(jspView);
            view.addView(createInitializedQueryView(form, errors, false, null));

            return view;
        }

        protected QueryView createQueryView(ShowAuditLogForm form, BindException errors, boolean forExport, String dataRegion) throws Exception
        {
            String selected = form.getView();
            if (selected == null)
                selected = AuditLogService.get().getAuditViewFactories()[0].getEventType();

            AuditLogService.AuditViewFactory factory = AuditLogService.get().getAuditViewFactory(selected);
            if (factory != null)
                return factory.createDefaultQueryView(getViewContext());
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Audit Log");
        }
    }

    public static class ShowAuditLogForm extends QueryViewAction.QueryExportForm
    {
        private String _view;

        public String getView()
        {
            return _view;
        }

        public void setView(String view)
        {
            _view = view;
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public static class ShowModuleErrors extends SimpleViewAction
    {

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Module Errors");
        }

        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            JspView jspView = new JspView("/org/labkey/core/admin/moduleErrors.jsp");
            return jspView;
        }
    }

    public static class AdminUrlsImpl implements AdminUrls
    {

        public ActionURL getModuleErrorsUrl(Container container)
        {
            return new ActionURL(ShowModuleErrors.class, container);
        }
    }


    private ActionURL getConsolidateScriptsURL(Double toVersion)
    {
        ActionURL url = new ActionURL(ConsolidateScriptsAction.class);

        if (null != toVersion)
            url.addParameter("toVersion", toVersion.toString());

        return url;
    }


    @RequiresSiteAdmin
    public class ConsolidateScriptsAction extends SimpleViewAction<ConsolidateForm>
    {
        public ModelAndView getView(ConsolidateForm form, BindException errors) throws Exception
        {
            StringBuilder html = new StringBuilder();
            List<Module> modules = ModuleLoader.getInstance().getModules();
            List<ScriptConsolidator> consolidators = new ArrayList<ScriptConsolidator>();

            double maxToVersion = -Double.MAX_VALUE;

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
                            ScriptConsolidator consolidator = new ScriptConsolidator(provider, schemaName);

                            if (!consolidator.getScripts().isEmpty())
                            {
                                consolidators.add(consolidator);

                                for (SqlScript script : consolidator.getScripts())
                                    if (script.getToVersion() > maxToVersion)
                                        maxToVersion = script.getToVersion();
                            }
                        }
                    }
                }
            }

            double toVersion = Math.ceil(maxToVersion * 10) / 10 - 0.01;

            for (ScriptConsolidator consolidator : consolidators)
            {
                consolidator.setSharedToVersion(toVersion);
                List<SqlScript> scripts = consolidator.getScripts();
                String filename = consolidator.getFilename();

                if (1 == scripts.size() && scripts.get(0).getDescription().equals(filename))
                    continue;  // No consolidation to do on this schema

                ActionURL url = getConsolidateSchemaURL(consolidator.getModuleName(), consolidator.getSchemaName(), toVersion);
                html.append("<b>Schema ").append(consolidator.getSchemaName()).append("</b><br>\n");

                for (SqlScript script : scripts)
                    html.append(script.getDescription()).append("<br>\n");

                html.append("<br>\n");
                html.append("[<a href=\"").append(url.getEncodedLocalURIString()).append("\">").append(1 == consolidator.getScripts().size() ? "copy" : "consolidate").append(" to ").append(filename).append("</a>]<br><br>\n");
            }

            if (0 == html.length())
                html.append("No schemas require consolidation");

            return new HtmlView(html.toString());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Consolidate Scripts");
        }
    }


    private static class ScriptConsolidator
    {
        private FileSqlScriptProvider _provider;
        private String _schemaName;
        private List<SqlScript> _scripts = new ArrayList<SqlScript>();
        private double sharedToVersion = -1;

        private ScriptConsolidator(FileSqlScriptProvider provider, String schemaName) throws SqlScriptRunner.SqlScriptException
        {
            _provider = provider;
            _schemaName = schemaName;

            List<SqlScript> recommendedScripts = SqlScriptRunner.getRecommendedScripts(provider.getScripts(schemaName), 0, 9999.0);

            for (SqlScript script : recommendedScripts)
            {
                if (isIncrementalScript(script))
                    _scripts.add(script);
                else
                    _scripts.clear();
            }
        }

        private void setSharedToVersion(double sharedToVersion)
        {
            this.sharedToVersion = sharedToVersion;
        }

        private double getSharedToVersion()
        {
            if (-1 == sharedToVersion)
                throw new IllegalStateException("SharedToVersion is not set");

            return sharedToVersion;
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
            return _scripts.get(0).getFromVersion();
        }

        private double getToVersion()
        {
            return Math.max(_scripts.get(_scripts.size() - 1).getToVersion(), getSharedToVersion());
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
        private String getConsolidatedScript()
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

                    sb.append("/* ").append(script.getDescription()).append(" */\n\n");
                    sb.append(contents.substring(contentStartIndex, contents.length()));
                    firstScript = false;
                }
                else
                {
                    sb.append("\n\n");
                    sb.append("/* ").append(script.getDescription()).append(" */\n\n");
                    sb.append(licenseMatcher.replaceFirst(""));    // Remove license
                }
            }

            return sb.toString();
        }

        private static boolean isIncrementalScript(SqlScript script)
        {
            double startVersion = script.getFromVersion() * 10;
            double endVersion = script.getToVersion() * 10;

            return (Math.floor(startVersion) != startVersion || Math.floor(endVersion) != endVersion);
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
        private double _toVersion;

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

        public double getToVersion()
        {
            return _toVersion;
        }

        public void setToVersion(double toVersion)
        {
            _toVersion = toVersion;
        }
    }


    private ActionURL getConsolidateSchemaURL(String moduleName, String schemaName, double toVersion)
    {
        ActionURL url = new ActionURL(ConsolidateSchemaAction.class);
        url.addParameter("module", moduleName);
        url.addParameter("schema", schemaName);
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
            html.append(consolidator.getConsolidatedScript());
            html.append("</pre>\n");

            html.append("<form method=\"post\">");
            html.append("<input type=\"image\" src=\"").append(PageFlowUtil.buttonSrc("Save to " + consolidator.getFilename())).append("\"> ");
            html.append(PageFlowUtil.buttonLink("Back", getSuccessURL(form)));
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
            return getConsolidateScriptsURL(form.getToVersion());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Consolidate Scripts for Schema " + _schemaName);
        }

        private ScriptConsolidator getConsolidator(ConsolidateForm form) throws SqlScriptRunner.SqlScriptException
        {
            DefaultModule module = (DefaultModule)ModuleLoader.getInstance().getModule(form.getModule());
            FileSqlScriptProvider provider = new FileSqlScriptProvider(module);
            ScriptConsolidator consolidator = new ScriptConsolidator(provider, form.getSchema());
            consolidator.setSharedToVersion(form.getToVersion());

            return consolidator;
        }
    }
}
