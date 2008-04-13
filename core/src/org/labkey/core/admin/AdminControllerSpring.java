package org.labkey.core.admin;

import org.apache.beehive.netui.pageflow.FormData;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateUtils;
import org.apache.log4j.Logger;
import org.apache.struts.action.ActionMapping;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;
import org.labkey.api.action.*;
import org.labkey.api.attachments.*;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.*;
import org.labkey.api.data.SqlScriptRunner.SqlScript;
import org.labkey.api.exp.api.AdminUrls;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.ms2.MS2Service;
import org.labkey.api.ms2.SearchClient;
import org.labkey.api.pipeline.PipelineStatusUrls;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.*;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.util.*;
import org.labkey.api.view.*;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.view.template.PageConfig.Template;
import org.labkey.api.wiki.WikiRenderer;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiService;
import org.labkey.common.util.Pair;
import org.labkey.core.analytics.AnalyticsController;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import javax.net.ssl.HttpsURLConnection;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.beans.Introspector;
import java.io.*;
import java.lang.management.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Feb 27, 2008
 */
public class AdminControllerSpring extends SpringActionController
{
    private static DefaultActionResolver _actionResolver = new BeehivePortingActionResolver(AdminController.class, AdminControllerSpring.class);
    private static long _errorMark = 0;
    private static NumberFormat formatInteger = DecimalFormat.getIntegerInstance();

    public AdminControllerSpring() throws Exception
    {
        super();
        setActionResolver(_actionResolver);
    }


    public NavTree appendAdminNavTrail(NavTree root, String childTitle)
    {
        root.addChild("Admin Console", getShowAdminURL()).addChild(childTitle);
        return root;
    }


    public static ActionURL getShowAdminURL()
    {
        return new ActionURL(ShowAdminAction.class, ContainerManager.getRoot());
    }


    @RequiresSiteAdmin
    public class ShowAdminAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            ActionURL url;
            Container c = getContainer();
            AdminBean bean = new AdminBean(getUser());
            JspView view = new JspView<AdminBean>("/org/labkey/core/admin/admin.jsp", bean);

            // Configuration
            bean.addConfigurationLink("site settings", new ActionURL("admin", "showCustomizeSite.view", ""));
            bean.addConfigurationLink("authentication", new ActionURL("login", "configure.view", "").addParameter("returnUrl", getViewContext().getActionURL().getLocalURIString()));
            bean.addConfigurationLink("flow cytometry", new ActionURL("Flow", "flowAdmin.view", ""));
            bean.addConfigurationLink("email customization", new ActionURL("admin", "customizeEmail.view", ""));
            bean.addConfigurationLink("project display order", new ActionURL("admin", "reorderFolders.view", ""));
            bean.addConfigurationLink("R view configuration", new ActionURL("reports", "configureRReport.view", ""));
            bean.addConfigurationLink("analytics settings", new ActionURL(AnalyticsController.BeginAction.class, ContainerManager.getRoot()));

            // Management
            bean.addManagementLink("ms1", new ActionURL("ms1", "showAdmin.view", ""));
            bean.addManagementLink("ms2", new ActionURL("ms2", "showMS2Admin.view", ""));
            url = PageFlowUtil.urlProvider(PipelineStatusUrls.class).urlBegin(c).addParameter("StatusFiles.Status~neqornull", "COMPLETE");
            bean.addManagementLink("pipeline", url);
            url = PageFlowUtil.urlProvider(PipelineUrls.class).urlSetup(c);
            bean.addManagementLink("pipeline email notification", url);
            bean.addManagementLink("protein databases", new ActionURL("MS2", "showProteinAdmin.view", ""));
            if (AuditLogService.get().isViewable())
                bean.addManagementLink("audit log", new ActionURL("admin", "showAuditLog.view", ""));

            // Diagnostics
            bean.addDiagnosticsLink("running threads", new ActionURL("admin", "showThreads.view", ""));
            bean.addDiagnosticsLink("memory usage", new ActionURL("admin", "memTracker.view", ""));
            bean.addDiagnosticsLink("actions", new ActionURL("admin", "actions.view", ""));
            bean.addDiagnosticsLink("scripts", new ActionURL("admin", "scripts.view", ""));
            bean.addDiagnosticsLink("groovy templates", new ActionURL("admin", "groovy.view", ""));
            bean.addDiagnosticsLink("view all site errors", new ActionURL("admin", "showAllErrors.view", ""));
            bean.addDiagnosticsLink("view all site errors since reset", new ActionURL("admin", "showErrorsSinceMark.view", ""));
            bean.addDiagnosticsLink("reset site errors", new ActionURL("admin", "resetErrorMark.view", ""));
            bean.addDiagnosticsLink("test ldap", new ActionURL("admin", "testLdap.view", ""));
            bean.addDiagnosticsLink("check database", new ActionURL("admin", "dbChecker.view", ""));
            bean.addDiagnosticsLink("credits", new ActionURL("admin", "credits.view", ""));

            return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Admin Console");
            return root;
        }
    }


    public static class AdminBean
    {
        public List<Module> modules = ModuleLoader.getInstance().getModules();
        public String javaVersion = System.getProperty("java.version");
        public String userName = System.getProperty("user.name");
        public String osName = System.getProperty("os.name");
        public String mode = AppProps.getInstance().isDevMode() ? "Development" : "Production";
        public String servletContainer = ModuleLoader.getServletContext().getServerInfo();
        public DbSchema schema = CoreSchema.getInstance().getSchema();
        public List<String> emails = UserManager.getUserEmailList();
        public List<Pair<String, Long>> active = UserManager.getActiveUsers(System.currentTimeMillis() - DateUtils.MILLIS_PER_HOUR);
        public String userEmail;

        public List<Pair<String, ActionURL>> configurationLinks = new ArrayList<Pair<String, ActionURL>>();
        public List<Pair<String, ActionURL>> managementLinks = new ArrayList<Pair<String, ActionURL>>();
        public List<Pair<String, ActionURL>> diagnosticsLinks = new ArrayList<Pair<String, ActionURL>>();

        private AdminBean(User user)
        {
            userEmail = user.getEmail();
        }

        private void addConfigurationLink(String linkText, ActionURL url)
        {
            addLink(configurationLinks, linkText, url);
        }

        private void addManagementLink(String linkText, ActionURL url)
        {
            addLink(managementLinks, linkText, url);
        }

        private void addDiagnosticsLink(String linkText, ActionURL url)
        {
            addLink(diagnosticsLinks, linkText, url);
        }

        private void addLink(List<Pair<String, ActionURL>> list, String linkText, ActionURL url)
        {
            list.add(new Pair<String, ActionURL>(linkText, url));
        }
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
            return appendAdminNavTrail(root, "Audit Log");
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


    @RequiresSiteAdmin
    public class ShowModuleErrors extends SimpleViewAction
    {
        public NavTree appendNavTrail(NavTree root)
        {
            return appendAdminNavTrail(root, "Module Errors");
        }

        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new JspView("/org/labkey/core/admin/moduleErrors.jsp");
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
        ActionURL url = new ActionURL(ConsolidateScriptsAction.class, ContainerManager.getRoot());

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

            double toVersion = 0.0 != form.getToVersion() ?  form.getToVersion() : Math.ceil(maxToVersion * 10) / 10 - 0.01;

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
        ActionURL url = new ActionURL(ConsolidateSchemaAction.class, ContainerManager.getRoot());
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


    @RequiresPermission(ACL.PERM_NONE)
    public class MaintenanceAction extends SimpleViewAction<AdminController>
    {
        public ModelAndView getView(AdminController adminController, BindException errors) throws Exception
        {
            getPageConfig().setTemplate(Template.Dialog);
            WikiRenderer formatter = WikiService.get().getRenderer(WikiRendererType.RADEOX);
            String content = formatter.format(ModuleLoader.getInstance().getAdminOnlyMessage()).getHtml();
            return new HtmlView("The site is currently undergoing maintenance", content);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresPermission(ACL.PERM_NONE)
    public class ContainerIdAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            getPageConfig().setTemplate(Template.None);
            return new HtmlView(
                getContainer().getName() + "<br>" +
                getContainer().getId() + "<br>" +
                getContainer().getRowId()
                );
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresPermission(ACL.PERM_NONE)
    public class GuidAction extends ExportAction
    {
        public void export(Object o, HttpServletResponse response) throws Exception
        {
            response.getWriter().write(GUID.makeGUID());
        }
    }


    @RequiresPermission(ACL.PERM_NONE)
    public class CreditsAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            // No security checks... anyone (even guests) can view the credits page

            HttpView jars = new CreditsView(getCreditsFile("jars.txt"), getWebInfJars(true), "JAR", "webapp", "^[\\w|-]+\\.jar\\|");
            HttpView scripts = new CreditsView(getCreditsFile("scripts.txt"), null, "javascript", "/internal/webapp directory", null);
            HttpView bins = new CreditsView(getCreditsFile("executables.txt"), getBinFilenames(), "executable", "/external/bin directory", null);

            return new VBox(jars, scripts, bins);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendAdminNavTrail(root, "Credits");
        }
    }


    private static class CreditsView extends WebPartView
    {
        private final String WIKI_LINE_SEP = "\r\n\r\n";
        private String _html;

        CreditsView(String wikiSource, List<String> filenames, String fileType, String foundWhere, String wikiSourceSearchPattern)
        {
            super();
            setTitle(StringUtils.capitalize(fileType) + " Files Shipped with LabKey");

            if (null != filenames)
            {
                String undocumented = getUndocumentedFilesText(wikiSource,  filenames, fileType, foundWhere);
                String missing = getMissingFilesText(wikiSource, filenames, fileType, foundWhere, wikiSourceSearchPattern);

                wikiSource = wikiSource + undocumented + missing;
            }

            WikiRenderer wf = WikiService.get().getRenderer(WikiRendererType.RADEOX);
            _html = wf.format(wikiSource).getHtml();
        }


        private String getUndocumentedFilesText(String wikiSource, List<String> filenames, String fileType, String foundWhere)
        {
            List<String> undocumented = new ArrayList<String>();

            for (String filename : filenames)
                if (!wikiSource.contains(filename))
                    undocumented.add(filename);

            if (undocumented.isEmpty())
                return "";
            else
                return WIKI_LINE_SEP + "**WARNING: The following " + fileType + " files were found in your " + foundWhere + " but are not documented in " + fileType.toLowerCase() + "s.txt:**\\\\" + StringUtils.join(undocumented.iterator(), "\\\\");
        }


        private String getMissingFilesText(String wikiSource, List<String> filenames, String fileType, String foundWhere, String wikiSourceSearchPattern)
        {
            if (null == wikiSourceSearchPattern)
                return "";

            Pattern p = Pattern.compile("^[\\w|-]+\\.jar\\|", Pattern.MULTILINE);
            Matcher m = p.matcher(wikiSource);

            List<String> missing = new ArrayList<String>();

            while(m.find())
            {
                String found = wikiSource.substring(m.start(), m.end() - 1);

                if (!filenames.contains(found))
                    missing.add(found);
            }

            if (missing.isEmpty())
                return "";
            else
                return WIKI_LINE_SEP + "**WARNING: The following " + fileType + " files are documented in " + fileType.toLowerCase() + "s.txt but were not found in your " + foundWhere + ":**\\\\" + StringUtils.join(missing.iterator(), "\\\\");
        }


        @Override
        public void renderView(Object model, PrintWriter out) throws IOException, ServletException
        {
            out.print(_html);
        }
    }


    private String getCreditsFile(String filename) throws IOException
    {
        Module core = ModuleLoader.getInstance().getCoreModule();
        InputStream is = core.getResourceStream("/META-INF/" + filename);
        return PageFlowUtil.getStreamContentsAsString(is);
    }


    private static final String _libPath = "/WEB-INF/lib/";

    private List<String> getWebInfJars(boolean removeInternalJars)
    {
        if (!AppProps.getInstance().isDevMode())
            return null;

        Set<String> resources = ViewServlet.getViewServletContext().getResourcePaths(_libPath);
        List<String> filenames = new ArrayList<String>(resources.size());

        // Remove path prefix and copy to a modifiable collection
        for (String filename : resources)
            filenames.add(filename.substring(_libPath.length()));

        if (removeInternalJars)
        {
            filenames.remove("api.jar");            // Internal JAR
            filenames.remove("schemas.jar");        // Internal JAR
            filenames.remove("common.jar");         // Internal JAR
            filenames.remove("internal.jar");       // Internal JAR
        }

        Collections.sort(filenames, String.CASE_INSENSITIVE_ORDER);
        return filenames;
    }


    private List<String> getBinFilenames()
    {
        if (!AppProps.getInstance().isDevMode())
            return null;

        Module core = ModuleLoader.getInstance().getCoreModule();

        File binRoot = new File(core.getBuildPath(), "../../../external/bin");

        if (!binRoot.exists())
            return null;

        List<String> filenames = new ArrayList<String>();

        addAllChildren(binRoot, filenames);

        return filenames;
    }


    private static FileFilter _fileFilter = new FileFilter() {
        public boolean accept(File f)
        {
            return !f.isDirectory();
        }
    };

    private static FileFilter _dirFilter = new FileFilter() {
        public boolean accept(File f)
        {
            return f.isDirectory() && !".svn".equals(f.getName());
        }
    };

    private void addAllChildren(File root, List<String> filenames)
    {
        File[] files = root.listFiles(_fileFilter);

        for (File file : files)
            filenames.add(file.getName());

        File[] dirs = root.listFiles(_dirFilter);

        for (File dir : dirs)
            addAllChildren(dir, filenames);
    }


    @RequiresSiteAdmin
    public class TestLdapAction extends FormViewAction<TestLdapForm>
    {
        public void validateCommand(TestLdapForm target, Errors errors)
        {
        }

        public ModelAndView getView(TestLdapForm form, boolean reshow, BindException errors) throws Exception
        {
            HttpView view = new JspView<TestLdapForm>("/org/labkey/core/admin/testLdap.jsp", form, errors);
            PageConfig page = new PageConfig();
            if (null == form.getMessage() || form.getMessage().length() < 200)
                page.setFocusId("server");
            page.setTemplate(Template.Dialog);
            return view;
        }

        public boolean handlePost(TestLdapForm form, BindException errors) throws Exception
        {
            try
            {
                boolean success = SecurityManager.LDAPConnect(form.getServer(), form.getPrincipal(), form.getPassword(), form.getAuthentication());
                form.setMessage("<b>Connected to server.  Authentication " + (success ? "succeeded" : "failed") + ".</b>");
            }
            catch(Exception e)
            {
                String message = "<b>Failed to connect with these settings.  Error was:</b><br>" + ExceptionUtil.renderException(e);
                form.setMessage(message);
            }
            return false;
        }

        public ActionURL getSuccessURL(TestLdapForm testLdapAction)
        {
            return null;   // Always reshow form
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    public static class TestLdapForm extends FormData
    {
        private String server;
        private String principal;
        private String password;
        private String message;
        private boolean authentication;

        public void reset(ActionMapping actionMapping, HttpServletRequest httpServletRequest)
        {
            User user = (User) httpServletRequest.getUserPrincipal();
            server = AppProps.getInstance().getLDAPServersArray()[0];
            authentication = AppProps.getInstance().useSASLAuthentication();
            ValidEmail email;

            try
            {
                email = new ValidEmail(user.getEmail());
            }
            catch(ValidEmail.InvalidEmailException e)
            {
                throw new RuntimeException(e);
            }
            principal = SecurityManager.emailToLdapPrincipal(email);

            super.reset(actionMapping, httpServletRequest);
        }

        public String getPrincipal()
        {
            return (null == principal ? "" : principal);
        }

        public void setPrincipal(String principal)
        {
            this.principal = principal;
        }

        public String getPassword()
        {
            return (null == password ? "" : password);
        }

        public void setPassword(String password)
        {
            this.password = password;
        }

        public String getServer()
        {
            return (null == server ? "" : server);
        }

        public void setServer(String server)
        {
            this.server = server;
        }

        public boolean getAuthentication()
        {
            return authentication;
        }

        public void setAuthentication(boolean authentication)
        {
            this.authentication = authentication;
        }

        public String getMessage()
        {
            return message;
        }

        public void setMessage(String message)
        {
            this.message = message;
        }
    }


    @RequiresSiteAdmin
    public class ResetFaviconAction extends SimpleRedirectAction
    {
        public ActionURL getRedirectURL(Object o) throws Exception
        {
            deleteExistingFavicon();
            WriteableAppProps.incrementLookAndFeelRevisionAndSave();

            return getCustomizeSiteURL();
        }
    }


    private void deleteExistingFavicon() throws SQLException
    {
        AttachmentService.get().deleteAttachment(ContainerManager.RootContainer.get(), AttachmentCache.FAVICON_FILE_NAME);
        AttachmentCache.clearFavIconCache();
    }


    @RequiresSiteAdmin
    public class ResetLogoAction extends SimpleRedirectAction
    {
        public ActionURL getRedirectURL(Object o) throws Exception
        {
            deleteExistingLogo();
            WriteableAppProps.incrementLookAndFeelRevisionAndSave();
            return getCustomizeSiteURL();
        }
    }


    private void deleteExistingLogo() throws SQLException
    {
        ContainerManager.RootContainer rootContainer = ContainerManager.RootContainer.get();
        Attachment[] attachments = AttachmentService.get().getAttachments(rootContainer);
        for (Attachment attachment : attachments)
        {
            if (attachment.getName().startsWith(AttachmentCache.LOGO_FILE_NAME_PREFIX))
            {
                AttachmentService.get().deleteAttachment(rootContainer, attachment.getName());
                AttachmentCache.clearLogoCache();
            }
        }
    }


    private void handleIconFile(MultipartFile file) throws SQLException, IOException, ServletException, AttachmentService.DuplicateFilenameException
    {
        User user = getViewContext().getUser();

        if (!file.getOriginalFilename().toLowerCase().endsWith(".ico"))
        {
            throw new ServletException("FavIcon must be a .ico file");
        }

        deleteExistingFavicon();

        AttachmentFile renamed = new SpringAttachmentFile(file);
        renamed.setFilename(AttachmentCache.FAVICON_FILE_NAME);
        AttachmentService.get().addAttachments(user, ContainerManager.RootContainer.get(), Collections.<AttachmentFile>singletonList(renamed));
        AttachmentCache.clearFavIconCache();
    }


    private void handleLogoFile(MultipartFile file) throws ServletException, SQLException, IOException, AttachmentService.DuplicateFilenameException
    {
        User user = getViewContext().getUser();

        // Set the name to something we'll recognize as a logo file
        String uploadedFileName = file.getOriginalFilename();
        int index = uploadedFileName.lastIndexOf(".");
        if (index == -1)
        {
            throw new ServletException("No file extension on the uploaded image");
        }

        // Get rid of any existing logos
        deleteExistingLogo();

        AttachmentFile renamed = new SpringAttachmentFile(file);
        renamed.setFilename(AttachmentCache.LOGO_FILE_NAME_PREFIX + uploadedFileName.substring(index));
        AttachmentService.get().addAttachments(user, ContainerManager.RootContainer.get(), Collections.<AttachmentFile>singletonList(renamed));
        AttachmentCache.clearLogoCache();
    }


    private void validateNetworkDrive(SiteAdminForm form, BindException errors)
    {
        if (form.getNetworkDriveLetter() == null || form.getNetworkDriveLetter().trim().length() > 1)
        {
            errors.reject(ERROR_MSG, "Network drive letter must be a single character");
        }
        char letter = form.getNetworkDriveLetter().trim().toLowerCase().charAt(0);
        if (letter < 'a' || letter > 'z')
        {
            errors.reject(ERROR_MSG, "Network drive letter must be a letter");
        }
        if (form.getNetworkDrivePath() == null || form.getNetworkDrivePath().trim().length() == 0)
        {
            errors.reject(ERROR_MSG, "If you specify a network drive letter, you must also specify a path");
        }
    }


    // TODO: Move to public interface
    public static ActionURL getCustomizeSiteURL()
    {
        return new ActionURL(ShowCustomizeSiteAction.class, ContainerManager.getRoot());
    }


    public static ActionURL getCustomizeSiteURL(boolean upgradeInProgress)
    {
        ActionURL url = getCustomizeSiteURL();

        if (upgradeInProgress)
            url.addParameter("upgradeInProgress", "1");

        return url;
    }


    @RequiresSiteAdmin
    public class ShowCustomizeSiteAction extends FormViewAction<SiteAdminForm>
    {
        public ModelAndView getView(SiteAdminForm form, boolean reshow, BindException errors) throws Exception
        {
            if (form.isUpgradeInProgress())
                getPageConfig().setTemplate(Template.Dialog);

            CustomizeSiteBean bean = new CustomizeSiteBean(form.isUpgradeInProgress(), form.getThemeName(), form.isTestInPage());
            return new JspView<CustomizeSiteBean>("/org/labkey/core/admin/customizeSite.jsp", bean, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendAdminNavTrail(root, "Customize Site");
        }

        public void validateCommand(SiteAdminForm target, Errors errors)
        {
        }

        public boolean handlePost(SiteAdminForm form, BindException errors) throws Exception
        {
            ModuleLoader.getInstance().setDeferUsageReport(false);
            HttpServletRequest request = getViewContext().getRequest();

            // We only need to check that SSL is running if the user isn't already using SSL
            if (form.isSslRequired() && !(request.isSecure() && (form.getSslPort() == request.getServerPort())))
            {
                URL testURL = new URL("https", request.getServerName(), form.getSslPort(), AppProps.getInstance().getContextPath());
                String error = null;
                try
                {
                    HttpsURLConnection connection = (HttpsURLConnection)testURL.openConnection();
                    HttpsUtil.disableValidation(connection);
                    if (connection.getResponseCode() != 200)
                    {
                        error = "Bad response code, " + connection.getResponseCode() + " when connecting to the SSL port over HTTPS";
                    }
                }
                catch (IOException e)
                {
                    error = "Error connecting over HTTPS - ensure that the web server is configured for SSL and that the port was correct. " +
                            "If you are receiving this message even though SSL is enabled, try saving these settings while connected via SSL. " +
                            "Attempted to connect to " + testURL + " and received the following error: " +
                            (e.getMessage() == null ? e.toString() : e.getMessage());
                }
                if (error != null)
                {
                    errors.reject(ERROR_MSG, error);
                    return false;
                }
            }

            Map<String, MultipartFile> fileMap = getFileMap();
            MultipartFile logoFile = fileMap.get("logoImage");
            if (logoFile != null && !logoFile.isEmpty())
            {
                try
                {
                    handleLogoFile(logoFile);
                }
                catch (Exception e)
                {
                    errors.reject(ERROR_MSG, e.getMessage());
                    return false;
                }
            }

            MultipartFile iconFile = fileMap.get("iconImage");
            if (logoFile != null && !iconFile.isEmpty())
            {
                try
                {
                    handleIconFile(iconFile);
                }
                catch (Exception e)
                {
                    errors.reject(ERROR_MSG, e.getMessage());
                    return false;
                }
            }

            // Make sure we can parse the system maintenance time
            Date systemMaintenanceTime = SystemMaintenance.parseSystemMaintenanceTime(form.getSystemMaintenanceTime());

            if (null == systemMaintenanceTime)
            {
                errors.reject(ERROR_MSG, "Invalid format for System Maintenance Time - please enter time in 24-hour format (e.g., 0:30 for 12:30AM, 14:00 for 2:00PM)");
                return false;
            }

            if (!"".equals(form.getMascotServer()))
            {
                // we perform the Mascot setting test here in case user did not do so
                SearchClient mascotClient = MS2Service.get().createSearchClient("mascot",form.getMascotServer(), Logger.getLogger("null"),
                    form.getMascotUserAccount(), form.getMascotUserPassword());
                mascotClient.setProxyURL(form.getMascotHTTPProxy());
                mascotClient.findWorkableSettings(true);

                if (0 != mascotClient.getErrorCode())
                {
                    errors.reject(ERROR_MSG, mascotClient.getErrorString());
                    return false;
                }
            }

            if (!"".equals(form.getSequestServer()))
            {
                // we perform the Sequest setting test here in case user did not do so
                SearchClient sequestClient = MS2Service.get().createSearchClient("sequest",form.getSequestServer(), Logger.getLogger("null"),
                    null, null);
                sequestClient.findWorkableSettings(true);
                if (0 != sequestClient.getErrorCode())
                {
                    errors.reject(ERROR_MSG, sequestClient.getErrorString());
                    return false;
                }
            }

            WriteableAppProps props = AppProps.getWriteableInstance();
            try
            {
                if (form.getThemeName() != null)
                {
                    WebTheme theme = WebTheme.getTheme(form.getThemeName());
                    if (theme != null)
                    {
                        WebTheme.setTheme(theme);
                        props.setThemeName(theme.getFriendlyName());
                    }
                    ThemeFont themeFont = ThemeFont.getThemeFont(form.getThemeFont());
                    if (themeFont != null)
                    {
                        ThemeFont.setThemeFont(themeFont);
                        props.setThemeFont(themeFont.getFriendlyName());
                    }
                    AttachmentCache.clearGradientCache();
                    ButtonServlet.resetColorScheme();
                }
            }
            catch (IllegalArgumentException e)
            {
            }

            props.setCompanyName(form.getCompanyName());
            props.setDefaultDomain(form.getDefaultDomain());
            props.setDefaultLsidAuthority(form.getDefaultLsidAuthority());
            props.setLDAPDomain(form.getLDAPDomain());
            props.setLDAPPrincipalTemplate(form.getLDAPPrincipalTemplate());
            props.setLDAPServers(form.getLDAPServers());
            props.setLDAPAuthentication(form.useSASLAuthentication());
            props.setLogoHref(form.getLogoHref());
            props.setReportAProblemPath(form.getReportAProblemPath());
            props.setSystemDescription(form.getSystemDescription());

            // Need to strip out any extraneous characters from the email address.
            // E.g. "Labkey <support@labkey.com>" -> "support@labkey.com"
            try
            {
                String address = form.getSystemEmailAddress().trim();
                // Manually check for a space or a quote, as these will later
                // fail to send via JavaMail.
                if (address.contains(" ") || address.contains("\""))
                    throw new ValidEmail.InvalidEmailException(address);

                // this will throw an InvalidEmailException for some types
                // of invalid email addresses
                new ValidEmail(form.getSystemEmailAddress());
                props.setSystemEmailAddresses(address);
            }
            catch (ValidEmail.InvalidEmailException e)
            {
                errors.reject(ERROR_MSG, "Invalid System Email Address: ["
                        + e.getBadEmail() + "]. Please enter a valid email address.");
                return false;
            }

            props.setSystemShortName(form.getSystemShortName());
            props.setPipelineCluster(form.isPipelineCluster());
            props.setPipelineToolsDir(form.getPipelineToolsDirectory());
            props.setSequestServer(form.getSequestServer());
            props.setSSLRequired(form.isSslRequired());
            props.setSSLPort(form.getSslPort());
            props.setMemoryUsageDumpInterval(form.getMemoryUsageDumpInterval());
            props.setNavigationBarWidth(form.getNavigationBarWidth());
            FolderDisplayMode folderDisplayMode = FolderDisplayMode.ALWAYS;
            try
            {
                folderDisplayMode = FolderDisplayMode.fromString(form.getFolderDisplayMode());
            }
            catch (IllegalArgumentException e)
            {
            }
            props.setFolderDisplayMode(folderDisplayMode);

            // Save the old system maintenance property values, compare with the new ones, and set a flag if they've changed
            String oldInterval = props.getSystemMaintenanceInterval();
            Date oldTime = props.getSystemMaintenanceTime();
            props.setSystemMaintenanceInterval(form.getSystemMaintenanceInterval());
            props.setSystemMaintenanceTime(systemMaintenanceTime);

            boolean setSystemMaintenanceTimer = (!oldInterval.equals(props.getSystemMaintenanceInterval()) || !oldTime.equals(props.getSystemMaintenanceTime()));

            props.setAdminOnlyMessage(form.getAdminOnlyMessage());
            props.setUserRequestedAdminOnlyMode(form.isAdminOnlyMode());
            props.setMascotServer(form.getMascotServer());
            props.setMascotUserAccount(form.getMascotUserAccount());
            props.setMascotUserPassword(form.getMascotUserPassword());
            props.setMascotHTTPProxy(form.getMascotHTTPProxy());
            props.setPipelineFTPHost(form.getPipelineFTPHost());
            props.setPipelineFTPPort(form.getPipelineFTPPort());
            props.setPipelineFTPSecure(form.isPipelineFTPSecure());

            props.setMicroarrayFeatureExtractionServer(form.getMicroarrayFeatureExtractionServer());

            try
            {
                ExceptionReportingLevel level = ExceptionReportingLevel.valueOf(form.getExceptionReportingLevel());
                props.setExceptionReportingLevel(level);
            }
            catch (IllegalArgumentException e)
            {
            }

            UsageReportingLevel level = null;

            try
            {
                level = UsageReportingLevel.valueOf(form.getUsageReportingLevel());
                props.setUsageReportingLevel(level);
            }
            catch (IllegalArgumentException e)
            {
            }
            props.incrementLookAndFeelRevision();

            if (form.getNetworkDriveLetter() != null && form.getNetworkDriveLetter().trim().length() > 0)
            {
                validateNetworkDrive(form, errors);

                if (errors.hasErrors())
                    return false;
            }

            props.setNetworkDriveLetter(form.getNetworkDriveLetter() == null ? null : form.getNetworkDriveLetter().trim());
            props.setNetworkDrivePath(form.getNetworkDrivePath() == null ? null : form.getNetworkDrivePath().trim());
            props.setNetworkDriveUser(form.getNetworkDriveUser() == null ? null : form.getNetworkDriveUser().trim());
            props.setNetworkDrivePassword(form.getNetworkDrivePassword() == null ? null : form.getNetworkDrivePassword().trim());
            props.setCaBIGEnabled(form.isCaBIGEnabled());

            if (null != form.getBaseServerUrl())
            {
                try
                {
                    props.setBaseServerUrl(form.getBaseServerUrl());
                }
                catch (URISyntaxException e)
                {
                    errors.reject(ERROR_MSG, "Invalid Base Server URL, " + e.getMessage() + ".  Please enter a valid URL, for example: http://www.labkey.org, https://www.labkey.org, or http://www.labkey.org:8080");
                    return false;
                }
            }

            props.save();

            if (null != level)
                level.scheduleUpgradeCheck();

            if (setSystemMaintenanceTimer)
                SystemMaintenance.setTimer();

            return true;
        }

        public ActionURL getSuccessURL(SiteAdminForm form)
        {
            if (form.isUpgradeInProgress())
            {
                return AppProps.getInstance().getHomePageActionURL();
            }
            else
            {
                return getCustomizeSiteURL();
            }
        }
    }


    public static class CustomizeSiteBean
    {
        public List<WebTheme> themes = WebTheme.getWebThemes();
        public WebTheme currentTheme = WebTheme.getTheme();
        public List<ThemeFont> themeFonts = ThemeFont.getThemeFonts();
        public ThemeFont currentThemeFont = ThemeFont.getThemeFont();
        public Attachment customLogo;
        public Attachment customFavIcon;
        public String helpLink = "<a href=\"" + (new HelpTopic("configAdmin", HelpTopic.Area.SERVER)).getHelpTopicLink() + "\" target=\"labkey\">more info...</a>";
        public String ftpHelpLink = "<a href=\"" + (new HelpTopic("configureFtp", HelpTopic.Area.SERVER)).getHelpTopicLink() + "\" target=\"labkey\">help configuring ftp...</a>";
        public boolean upgradeInProgress;
        public boolean testInPage;
        public WebTheme newTheme = null;

        private CustomizeSiteBean(boolean upgradeInProgress, String newThemeName, boolean testInPage) throws SQLException
        {
            this.upgradeInProgress = upgradeInProgress;
            this.testInPage = testInPage;

            customLogo = AttachmentCache.lookupLogoAttachment();
            customFavIcon = AttachmentCache.lookupFavIconAttachment();

            //if new color scheme defined, get new theme name from url
            if (newThemeName != null)
                newTheme = WebTheme.getTheme(newThemeName);
        }
    }


    public static class SiteAdminForm
    {
        private boolean _upgradeInProgress = false;
        private boolean _testInPage = false;

        private String _systemDescription;
        private String _companyName;
        private String _systemShortName;
        private String _logoHref;
        private String _LDAPServers;
        private String _LDAPDomain;
        private String _LDAPPrincipalTemplate;
        private boolean _LDAPAuthentication;
        private String _systemEmailAddress;
        private String _defaultDomain;
        private String _defaultLsidAuthority;
        private String _reportAProblemPath;
        private String _themeName;
        private boolean _pipelineCluster;
        private String _pipelineToolsDirectory;
        private boolean _sequest;
        private String _sequestServer;
        private boolean _sslRequired;
        private boolean _adminOnlyMode;
        private String _adminOnlyMessage;
        private int _sslPort;
        private String _systemMaintenanceInterval;
        private String _systemMaintenanceTime;
        private int _memoryUsageDumpInterval;
        private String _exceptionReportingLevel;
        private String _usageReportingLevel;
        private String _mascotServer;
        private String _mascotUserAccount;
        private String _mascotUserPassword;
        private String _mascotHTTPProxy;
        private String _themeFont;
        private String _pipelineFTPHost;
        private String _pipelineFTPPort;
        private boolean _pipelineFTPSecure;
        private String _navigationBarWidth;
        private String _folderDisplayMode;

        private String _networkDriveLetter;
        private String _networkDrivePath;
        private String _networkDriveUser;
        private String _networkDrivePassword;
        private boolean _caBIGEnabled;
        private String _baseServerUrl;
        private String _microarrayFeatureExtractionServer;

        public String getSystemDescription()
        {
            return _systemDescription;
        }

        public void setSystemDescription(String systemDescription)
        {
            _systemDescription = systemDescription;
        }

        public String getSystemShortName()
        {
            return _systemShortName;
        }

        public void setSystemShortName(String systemShortName)
        {
            _systemShortName = systemShortName;
        }

        public String getLogoHref()
        {
            return _logoHref;
        }

        public void setLogoHref(String logoHref)
        {
            _logoHref = logoHref;
        }

        public String getLDAPServers()
        {
            return _LDAPServers;
        }

        public void setLDAPServers(String LDAPServers)
        {
            _LDAPServers = LDAPServers;
        }

        public String getLDAPDomain()
        {
            return _LDAPDomain;
        }

        public void setLDAPDomain(String LDAPDomain)
        {
            _LDAPDomain = LDAPDomain;
        }

        public String getLDAPPrincipalTemplate()
        {
            return _LDAPPrincipalTemplate;
        }

        public void setLDAPPrincipalTemplate(String LDAPPrincipalTemplate)
        {
            _LDAPPrincipalTemplate = LDAPPrincipalTemplate;
        }

        public boolean useSASLAuthentication()
        {
            return _LDAPAuthentication;
        }

        public void setLDAPAuthentication(boolean LDAPAuthentication)
        {
            _LDAPAuthentication = LDAPAuthentication;
        }

        public String getMascotServer()
        {
            return (null == _mascotServer) ? "" : _mascotServer;
        }

        public void setMascotServer(String mascotServer)
        {
            _mascotServer = mascotServer;
        }

        public String getMascotUserAccount()
        {
            return (null == _mascotUserAccount) ? "" : _mascotUserAccount;
        }

        public void setMascotUserAccount(String mascotUserAccount)
        {
            _mascotUserAccount = mascotUserAccount;
        }

        public String getMascotUserPassword()
        {
            return (null == _mascotUserPassword) ? "" : _mascotUserPassword;
        }

        public void setMascotUserPassword(String mascotUserPassword)
        {
            _mascotUserPassword = mascotUserPassword;
        }

        public String getMascotHTTPProxy()
        {
            return (null == _mascotHTTPProxy) ? "" : _mascotHTTPProxy;
        }

        public void setMascotHTTPProxy(String mascotHTTPProxy)
        {
            _mascotHTTPProxy = mascotHTTPProxy;
        }

        public String getPipelineFTPHost()
        {
            return _pipelineFTPHost;
        }

        public void setPipelineFTPHost(String pipelineFTPHost)
        {
            _pipelineFTPHost = pipelineFTPHost;
        }

        public String getPipelineFTPPort()
        {
            return _pipelineFTPPort;
        }

        public void setPipelineFTPPort(String pipelineFTPPort)
        {
            _pipelineFTPPort = pipelineFTPPort;
        }

        public boolean isPipelineFTPSecure()
        {
            return _pipelineFTPSecure;
        }

        public void setPipelineFTPSecure(boolean pipelineFTPSecure)
        {
            _pipelineFTPSecure = pipelineFTPSecure;
        }

        public String getSystemEmailAddress()
        {
            return _systemEmailAddress;
        }

        public void setSystemEmailAddress(String systemEmailAddress)
        {
            _systemEmailAddress = systemEmailAddress;
        }

        public void setDefaultDomain(String defaultDomain)
        {
            _defaultDomain = defaultDomain;
        }

        public String getDefaultDomain()
        {
            return _defaultDomain;
        }

        public String getDefaultLsidAuthority()
        {
            return _defaultLsidAuthority;
        }

        public void setDefaultLsidAuthority(String defaultLsidAuthority)
        {
            _defaultLsidAuthority = defaultLsidAuthority;
        }

        public String getReportAProblemPath()
        {
            return _reportAProblemPath;
        }

        public void setReportAProblemPath(String reportAProblemPath)
        {
            _reportAProblemPath = reportAProblemPath;
        }

        public String getThemeName()
        {
            return _themeName;
        }

        public void setThemeName(String themeName)
        {
            _themeName = themeName;
        }

        public String getThemeFont()
        {
            return _themeFont;
        }

        public void setThemeFont(String themeFont)
        {
            _themeFont = themeFont;
        }

        public String getCompanyName()
        {
            return _companyName;
        }

        public void setCompanyName(String companyName)
        {
            _companyName = companyName;
        }

        public boolean isPipelineCluster()
        {
            return _pipelineCluster;
        }

        public void setPipelineCluster(boolean pipelineCluster)
        {
            _pipelineCluster = pipelineCluster;
        }

        public String getPipelineToolsDirectory()
        {
            return _pipelineToolsDirectory;
        }

        public void setPipelineToolsDirectory(String pipelineToolsDirectory)
        {
            _pipelineToolsDirectory = pipelineToolsDirectory;
        }

        public boolean isSequest()
        {
            return _sequest;
        }

        public void setSequest(boolean sequest)
        {
            _sequest = sequest;
        }

        public String getSequestServer()
        {
            return (null == _sequestServer) ? "" : _sequestServer;
        }

        public void setSequestServer(String sequestServer)
        {
            _sequestServer = sequestServer;
        }

        public boolean isSslRequired()
        {
            return _sslRequired;
        }

        public void setSslRequired(boolean sslRequired)
        {
            _sslRequired = sslRequired;
        }

        public String getSystemMaintenanceInterval()
        {
            return _systemMaintenanceInterval;
        }

        public void setSystemMaintenanceInterval(String systemMaintenanceInterval)
        {
            _systemMaintenanceInterval = systemMaintenanceInterval;
        }

        public String getSystemMaintenanceTime()
        {
            return _systemMaintenanceTime;
        }

        public void setSystemMaintenanceTime(String systemMaintenanceTime)
        {
            _systemMaintenanceTime = systemMaintenanceTime;
        }

        public int getSslPort()
        {
            return _sslPort;
        }

        public void setSslPort(int sslPort)
        {
            _sslPort = sslPort;
        }

        public boolean isAdminOnlyMode()
        {
            return _adminOnlyMode;
        }

        public void setAdminOnlyMode(boolean adminOnlyMode)
        {
            _adminOnlyMode = adminOnlyMode;
        }

        public String getAdminOnlyMessage()
        {
            return _adminOnlyMessage;
        }

        public void setAdminOnlyMessage(String adminOnlyMessage)
        {
            _adminOnlyMessage = adminOnlyMessage;
        }

        public String getExceptionReportingLevel()
        {
            return _exceptionReportingLevel;
        }

        public void setExceptionReportingLevel(String exceptionReportingLevel)
        {
            _exceptionReportingLevel = exceptionReportingLevel;
        }

        public String getUsageReportingLevel()
        {
            return _usageReportingLevel;
        }

        public void setUsageReportingLevel(String usageReportingLevel)
        {
            _usageReportingLevel = usageReportingLevel;
        }

        public boolean isUpgradeInProgress()
        {
            return _upgradeInProgress;
        }

        public void setUpgradeInProgress(boolean upgradeInProgress)
        {
            _upgradeInProgress = upgradeInProgress;
        }

        public int getMemoryUsageDumpInterval()
        {
            return _memoryUsageDumpInterval;
        }

        public void setMemoryUsageDumpInterval(int memoryUsageDumpInterval)
        {
            _memoryUsageDumpInterval = memoryUsageDumpInterval;
        }

        public String getNavigationBarWidth()
        {
            return _navigationBarWidth;
        }

        public void setNavigationBarWidth(String navigationBarWidth)
        {
            _navigationBarWidth = navigationBarWidth;
        }

        public String getFolderDisplayMode()
        {
            return _folderDisplayMode;
        }

        public void setFolderDisplayMode(String folderDisplayMode)
        {
            _folderDisplayMode = folderDisplayMode;
        }

        public String getNetworkDriveLetter()
        {
            return _networkDriveLetter;
        }

        public void setNetworkDriveLetter(String networkDriveLetter)
        {
            _networkDriveLetter = networkDriveLetter;
        }

        public String getNetworkDrivePassword()
        {
            return _networkDrivePassword;
        }

        public void setNetworkDrivePassword(String networkDrivePassword)
        {
            _networkDrivePassword = networkDrivePassword;
        }

        public String getNetworkDrivePath()
        {
            return _networkDrivePath;
        }

        public void setNetworkDrivePath(String networkDrivePath)
        {
            _networkDrivePath = networkDrivePath;
        }

        public String getNetworkDriveUser()
        {
            return _networkDriveUser;
        }

        public void setNetworkDriveUser(String networkDriveUser)
        {
            _networkDriveUser = networkDriveUser;
        }

        public boolean isCaBIGEnabled()
        {
            return _caBIGEnabled;
        }

        public void setCaBIGEnabled(boolean caBIGEnabled)
        {
            _caBIGEnabled = caBIGEnabled;
        }

        public String getBaseServerUrl()
        {
            return _baseServerUrl;
        }

        public void setBaseServerUrl(String baseServerUrl)
        {
            _baseServerUrl = baseServerUrl;
        }

        public String getMicroarrayFeatureExtractionServer()
        {
            return _microarrayFeatureExtractionServer;
        }

        public void setMicroarrayFeatureExtractionServer(String microarrayFeatureExtractionServer)
        {
            _microarrayFeatureExtractionServer = microarrayFeatureExtractionServer;
        }

        public boolean isTestInPage()
        {
            return _testInPage;
        }

        public void setTestInPage(boolean testInPage)
        {
            _testInPage = testInPage;
        }
    }


    @RequiresSiteAdmin
    public class ShowThreadsAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new JspView<ThreadsBean>("/org/labkey/core/admin/threads.jsp", new ThreadsBean());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendAdminNavTrail(root, "Current Threads");
        }
    }


    public static class ThreadsBean
    {
        public Map<Thread, Set<Integer>> spids = new HashMap<Thread, Set<Integer>>();
        public Thread[] threads;

        ThreadsBean()
        {
            int threadCount = Thread.activeCount();
            threads = new Thread[threadCount];
            Thread.enumerate(threads);
            Arrays.sort(threads, new Comparator<Thread>()
            {
                public int compare(Thread o1, Thread o2)
                {
                    return o1.getName().compareToIgnoreCase(o2.getName());
                }
            });

            spids = new HashMap<Thread, Set<Integer>>();

            for (Thread t : threads)
            {
                spids.put(t, ConnectionWrapper.getSPIDsForThread(t));
            }
        }
    }


    @RequiresSiteAdmin
    public class ShowNetworkDriveTestAction extends SimpleViewAction<SiteAdminForm>
    {
        public ModelAndView getView(SiteAdminForm form, BindException errors) throws Exception
        {
            NetworkDrive testDrive = new NetworkDrive();
            testDrive.setPassword(form.getNetworkDrivePassword());
            testDrive.setPath(form.getNetworkDrivePath());
            testDrive.setUser(form.getNetworkDriveUser());

            validateNetworkDrive(form, errors);

            TestNetworkDriveBean bean = new TestNetworkDriveBean();

            if (!errors.hasErrors())
            {
                char driveLetter = form.getNetworkDriveLetter().trim().charAt(0);
                try
                {
                    String mountError = testDrive.mount(driveLetter);
                    if (mountError != null)
                    {
                        errors.reject(ERROR_MSG, mountError);
                    }
                    else
                    {
                        File f = new File(driveLetter + ":\\");
                        if (!f.exists())
                        {
                            errors.reject(ERROR_MSG, "Could not access network drive");
                        }
                        else
                        {
                            String[] fileNames = f.list();
                            if (fileNames == null)
                                fileNames = new String[0];
                            Arrays.sort(fileNames);
                            bean.setFiles(fileNames);
                        }
                    }
                }
                catch (IOException e)
                {
                    errors.reject(ERROR_MSG, "Error mounting drive: " + e);
                }
                catch (InterruptedException e)
                {
                    errors.reject(ERROR_MSG, "Error mounting drive: " + e);
                }
                try
                {
                    testDrive.unmount(driveLetter);
                }
                catch (IOException e)
                {
                    errors.reject(ERROR_MSG, "Error mounting drive: " + e);
                }
                catch (InterruptedException e)
                {
                    errors.reject(ERROR_MSG, "Error mounting drive: " + e);
                }
            }

            getPageConfig().setTemplate(Template.Dialog);
            return new JspView<TestNetworkDriveBean>("/org/labkey/core/admin/testNetworkDrive.jsp", bean);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresSiteAdmin
    public class ImpersonateAction extends SimpleRedirectAction
    {
        public ActionURL getRedirectURL(Object o) throws Exception
        {
            String rawEmail = getViewContext().getActionURL().getParameter("email");
            ValidEmail email = new ValidEmail(rawEmail);

            if (!UserManager.userExists(email))
                throw new NotFoundException("User doesn't exist");

            final User impersonatedUser = UserManager.getUser(email);
            SecurityManager.setAuthenticatedUser(getViewContext().getRequest(), impersonatedUser);
            AuditLogService.get().addEvent(getViewContext(), UserManager.USER_AUDIT_EVENT, getUser().getUserId(),
                    getUser().getEmail() + " impersonated user: " + email);
            AuditLogService.get().addEvent(getViewContext(), UserManager.USER_AUDIT_EVENT, impersonatedUser.getUserId(),
                    email + " was impersonated by user: " + getUser().getEmail());
            return AppProps.getInstance().getHomePageActionURL();
        }
    }


    @RequiresSiteAdmin
    public class ResetErrorMarkAction extends SimpleRedirectAction
    {
        public ActionURL getRedirectURL(Object o) throws Exception
        {
            File errorLogFile = getErrorLogFile();
            _errorMark = errorLogFile.length();
            return getShowAdminURL();
        }
    }


    @RequiresSiteAdmin
    public class ShowErrorsSinceMarkAction extends ExportAction
    {
        public void export(Object o, HttpServletResponse response) throws Exception
        {
            showErrors(response, _errorMark);
        }
    }


    @RequiresSiteAdmin
    public class ShowAllErrorsAction extends ExportAction
    {
        public void export(Object o, HttpServletResponse response) throws Exception
        {
            showErrors(response, 0);
        }
    }


    private File getErrorLogFile()
    {
        File tomcatHome = new File(System.getProperty("catalina.home"));
        return new File(tomcatHome, "logs/labkey-errors.log");
    }


    public void showErrors(HttpServletResponse response, long startingOffset) throws Exception
    {
        File errorLogFile = getErrorLogFile();
        if (errorLogFile.exists())
        {
            FileInputStream fIn = null;
            try
            {
                fIn = new FileInputStream(errorLogFile);
                //noinspection ResultOfMethodCallIgnored
                fIn.skip(startingOffset);
                OutputStream out = response.getOutputStream();
                response.setContentType("text/plain");
                byte[] b = new byte[4096];
                int i;
                while ((i = fIn.read(b)) != -1)
                {
                    out.write(b, 0, i);
                }
            }
            finally
            {
                if (fIn != null)
                {
                    fIn.close();
                }
            }
        }
    }


    private static ActionURL getActionsURL()
    {
        return new ActionURL(ActionsAction.class, ContainerManager.getRoot());
    }


    @RequiresSiteAdmin
    public class ActionsAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new ActionsTabStrip();
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendAdminNavTrail(root, "Spring Actions");
        }
    }


    private static class ActionsTabStrip extends TabStripView
    {
        protected List<TabInfo> getTabList()
        {
            List<TabInfo> tabs = new ArrayList<TabInfo>(2);

            tabs.add(new TabInfo("Summary", "summary", getActionsURL()));
            tabs.add(new TabInfo("Details", "details", getActionsURL()));

            return tabs;
        }

        protected HttpView getTabView(String tabId) throws Exception
        {
            ActionsView view = new ActionsView();
            view.setSummary("summary".equals(tabId));
            return view;
        }
    }


    private static class ActionsView extends HttpView
    {
        private boolean _summary = false;

        public boolean isSummary()
        {
            return _summary;
        }

        public void setSummary(boolean summary)
        {
            _summary = summary;
        }

        protected void renderInternal(Object model, PrintWriter out) throws Exception
        {
            List<Module> modules = ModuleLoader.getInstance().getModules();

            out.print("<table>");

            if (_summary)
                out.print("<tr align=left><th>Spring Controller</th><th>Actions</th><th>Invoked</th><th>Coverage</th></tr>");
            else
                out.print("<tr align=left><th>Spring Controller</th><th>Action</th><th>Invocations</th><th>Cumulative Time</th><th>Average Time</th><th>Max Time</th></tr>");

            int totalActions = 0;
            int totalInvoked = 0;

            for (Module module : modules)
            {
                Map<String, Class> pageFlows = module.getPageFlowNameToClass();
                Set<Class> controllerClasses = new HashSet<Class>(pageFlows.values());

                for (Class controllerClass : controllerClasses)
                {
                    if (Controller.class.isAssignableFrom(controllerClass))
                    {
                        SpringActionController controller = (SpringActionController)ViewServlet.getController(module, controllerClass);
                        ActionResolver ar = controller.getActionResolver();
                        Comparator<ActionDescriptor> comp = new Comparator<ActionDescriptor>(){
                            public int compare(ActionDescriptor ad1, ActionDescriptor ad2)
                            {
                                return ad1.getActionClass().getSimpleName().compareTo(ad2.getActionClass().getSimpleName());
                            }
                        };
                        Set<ActionDescriptor> set = new TreeSet<ActionDescriptor>(comp);
                        set.addAll(ar.getActionDescriptors());

                        String controllerTd = "<td>" + controller.getClass().getSimpleName() + "</td>";

                        if (_summary)
                        {
                            out.print("<tr>");
                            out.print(controllerTd);
                        }

                        int invokedCount = 0;

                        for (ActionDescriptor ad : set)
                        {
                            if (!_summary)
                            {
                                out.print("<tr>");
                                out.print(controllerTd);
                                controllerTd = "<td>&nbsp;</td>";
                                out.print("<td>");
                                out.print(ad.getActionClass().getSimpleName());
                                out.print("</td>");
                            }

                            // Synchronize to ensure the stats aren't updated half-way through rendering
                            synchronized(ad)
                            {
                                if (ad.getCount() > 0)
                                    invokedCount++;

                                if (_summary)
                                    continue;

                                renderTd(out, ad.getCount());
                                renderTd(out, ad.getElapsedTime());
                                renderTd(out, 0 == ad.getCount() ? 0 : ad.getElapsedTime() / ad.getCount());
                                renderTd(out, ad.getMaxTime());
                            }

                            out.print("</tr>");
                        }

                        totalActions += set.size();
                        totalInvoked += invokedCount;

                        double coverage = set.isEmpty() ? 0 : invokedCount / (double)set.size();

                        if (!_summary)
                            out.print("<tr><td>&nbsp;</td><td>Action Coverage</td>");
                        else
                        {
                            out.print("<td>");
                            out.print(set.size());
                            out.print("</td><td>");
                            out.print(invokedCount);
                            out.print("</td>");
                        }

                        out.print("<td>");
                        out.print(Formats.percent1.format(coverage));
                        out.print("</td></tr>");

                        if (!_summary)
                            out.print("<tr><td colspan=6>&nbsp;</td></tr>");
                    }
                }
            }

            double totalCoverage = (0 == totalActions ? 0 : totalInvoked / (double)totalActions);

            if (_summary)
            {
                out.print("<tr><td colspan=4>&nbsp;</td></tr><tr><td>Total</td><td>");
                out.print(totalActions);
                out.print("</td><td>");
                out.print(totalInvoked);
                out.print("</td>");
            }
            else
            {
                out.print("<tr><td colspan=2>Total Action Coverage</td>");
            }

            out.print("<td>");
            out.print(Formats.percent1.format(totalCoverage));
            out.print("</td></tr>");
            out.print("</table>");
        }


        private void renderTd(PrintWriter out, Number d)
        {
            out.print("<td>");
            out.print(formatInteger.format(d));
            out.print("</td>");
        }
    }


    @RequiresSiteAdmin
    public class RunSystemMaintenanceAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            SystemMaintenance sm = new SystemMaintenance(false);
            sm.run();

            return new HtmlView("System maintenance task started");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendAdminNavTrail(root, "System Maintenance");
        }
    }


    @RequiresSiteAdmin
    public class GroovyAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            StringBuilder sb = new StringBuilder();

            InputStream s = this.getClass().getResourceAsStream("/META-INF/groovy.txt");
            List<String> allTemplates = PageFlowUtil.getStreamContentsAsList(s);  // Enumerate this one
            Collections.sort(allTemplates);

            // Need a copy of allTemplates that we can modify
            List<String> modifiable = new ArrayList<String>(allTemplates);

            // Create copy of the rendered templates list -- we need to sort it and modify it
            List<String> renderedTemplates = new ArrayList<String>(GroovyView.getRenderedTemplates());

            int templateCount = allTemplates.size();
            int renderedCount = renderedTemplates.size();

            sb.append("Groovy templates that have rendered successfully since server startup:<br><br>");

            for (String template : allTemplates)
            {
                for (String rt : renderedTemplates)
                {
                    if (template.endsWith(rt))
                    {
                        sb.append("&nbsp;&nbsp;");
                        sb.append(template);
                        sb.append("<br>\n");
                        modifiable.remove(template);
                        renderedTemplates.remove(rt);
                        break;
                    }
                }
            }

            if (!renderedTemplates.isEmpty())
            {
                renderedCount = renderedCount - renderedTemplates.size();
                sb.append("<br><br><b>Warning: unknown Groovy templates:</b><br><br>\n");

                for (String path : renderedTemplates)
                {
                    sb.append("&nbsp;&nbsp;");
                    sb.append(path);
                    sb.append("<br>\n");
                }
            }

            sb.append("<br><br>Groovy templates that have not rendered successfully since server startup:<br><br>\n");

            for (String template : modifiable)
            {
                sb.append("&nbsp;&nbsp;");
                sb.append(template);
                sb.append("<br>\n");
            }

            sb.append("<br><br>Rendered ").append(renderedCount).append("/").append(templateCount).append(" (").append(Formats.percent.format(renderedCount / (float)templateCount)).append(").");

            return new HtmlView(sb.toString());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendAdminNavTrail(root, "Groovy Templates");
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

            StringBuilder html = new StringBuilder("<table><tr><td colspan=2>Scripts that have run on this server</td><td colspan=2>Scripts that have not run on this server</td></tr>");
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
            return appendAdminNavTrail(root, "SQL Scripts");
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


    public static ActionURL getMemTrackerURL(boolean clearCaches, boolean gc)
    {
        ActionURL url = new ActionURL(MemTrackerAction.class, ContainerManager.getRoot());

        if (clearCaches)
            url.addParameter(MemForm.Params.clearCaches, "1");

        if (gc)
            url.addParameter(MemForm.Params.gc, "1");

        return url;
    }


    @RequiresSiteAdmin
    public class MemTrackerAction extends SimpleViewAction<MemForm>
    {
        public ModelAndView getView(MemForm form, BindException errors) throws Exception
        {
            if (form.isClearCaches())
            {
                Introspector.flushCaches();
                CacheMap.purgeAllCaches();
            }

            if (form.isGc())
                System.gc();

            return new JspView<MemBean>("/org/labkey/core/admin/memTracker.jsp", new MemBean(getViewContext().getRequest()));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendAdminNavTrail(root, "Memory usage -- " + DateUtil.formatDateTime());
        }
    }


    public static class MemForm
    {
        private enum Params {clearCaches, gc}

        private boolean _clearCaches = false;
        private boolean _gc = false;

        public boolean isClearCaches()
        {
            return _clearCaches;
        }

        public void setClearCaches(boolean clearCaches)
        {
            _clearCaches = clearCaches;
        }

        public boolean isGc()
        {
            return _gc;
        }

        public void setGc(boolean gc)
        {
            _gc = gc;
        }
    }


    public static class MemBean
    {
        public List<Pair<String, Object>> systemProperties = new ArrayList<Pair<String,Object>>();
        public List<MemTracker.HeldReference> all = MemTracker.getReferences();
        public List<MemTracker.HeldReference> references = new ArrayList<MemTracker.HeldReference>(all.size());
        public List<String> graphNames = new ArrayList<String>();
        public boolean assertsEnabled = false;

        private MemBean(HttpServletRequest request)
        {
            // removeCache recentely allocated
            long threadId = Thread.currentThread().getId();
            long start = ViewServlet.getRequestStartTime(request);
            for (MemTracker.HeldReference r : all)
            {
                if (r.getThreadId() == threadId && r.getAllocationTime() >= start)
                    continue;
                references.add(r);
            }

            // memory:
            graphNames.add("Heap");
            graphNames.add("Non Heap");

            MemoryMXBean membean = ManagementFactory.getMemoryMXBean();
            if (membean != null)
            {
                systemProperties.add(new Pair<String,Object>("Total Heap Memory", getUsageString(membean.getHeapMemoryUsage())));
                systemProperties.add(new Pair<String,Object>("Total Non-heap Memory", getUsageString(membean.getNonHeapMemoryUsage())));
            }

            List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
            for (MemoryPoolMXBean pool : pools)
            {
                systemProperties.add(new Pair<String,Object>(pool.getName() + " " + pool.getType(), getUsageString(pool)));
                graphNames.add(pool.getName());
            }

            // class loader:
            ClassLoadingMXBean classbean = ManagementFactory.getClassLoadingMXBean();
            if (classbean != null)
            {
                systemProperties.add(new Pair<String,Object>("Loaded Class Count", classbean.getLoadedClassCount()));
                systemProperties.add(new Pair<String,Object>("Unloaded Class Count", classbean.getUnloadedClassCount()));
                systemProperties.add(new Pair<String,Object>("Total Loaded Class Count", classbean.getTotalLoadedClassCount()));
            }

            // runtime:
            RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
            if (runtimeBean != null)
            {
                systemProperties.add(new Pair<String,Object>("VM Start Time", DateUtil.formatDateTime(new Date(runtimeBean.getStartTime()))));
                long upTime = runtimeBean.getUptime(); // round to sec
                upTime = upTime - (upTime % 1000);
                systemProperties.add(new Pair<String,Object>("VM Uptime", DateUtil.formatDuration(upTime)));
                systemProperties.add(new Pair<String,Object>("VM Version", runtimeBean.getVmVersion()));
                systemProperties.add(new Pair<String,Object>("VM Classpath", runtimeBean.getClassPath()));
            }

            // threads:
            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            if (threadBean != null)
            {
                systemProperties.add(new Pair<String,Object>("Thread Count", threadBean.getThreadCount()));
                systemProperties.add(new Pair<String,Object>("Peak Thread Count", threadBean.getPeakThreadCount()));
                long[] deadlockedThreads = threadBean.findMonitorDeadlockedThreads();
                systemProperties.add(new Pair<String,Object>("Deadlocked Thread Count", deadlockedThreads != null ? deadlockedThreads.length : 0));
            }

            // threads:
            List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
            for (GarbageCollectorMXBean gcBean : gcBeans)
            {
                systemProperties.add(new Pair<String,Object>(gcBean.getName() + " GC count", gcBean.getCollectionCount()));
                systemProperties.add(new Pair<String,Object>(gcBean.getName() + " GC time", DateUtil.formatDuration(gcBean.getCollectionTime())));
            }

            systemProperties.add(new Pair<String, Object>("In-use Connections", ConnectionWrapper.getActiveConnectionCount()));

            //noinspection ConstantConditions
            assert assertsEnabled = true;
        }
    }


    private static String getUsageString(MemoryPoolMXBean pool)
    {
        try
        {
            return getUsageString(pool.getUsage());
        }
        catch (IllegalArgumentException x)
        {
            // sometimes we get usage>committed exception with older verions of JRockit
            return "exception getting usage";
        }
    }


    private static String getUsageString(MemoryUsage usage)
    {
        if (null == usage)
            return "null";

        try
        {
            StringBuffer sb = new StringBuffer();
            sb.append("init = ").append(formatInteger.format(usage.getInit()));
            sb.append("; used = ").append(formatInteger.format(usage.getUsed()));
            sb.append("; committed = ").append(formatInteger.format(usage.getCommitted()));
            sb.append("; max = ").append(formatInteger.format(usage.getMax()));
            return sb.toString();
        }
        catch (IllegalArgumentException x)
        {
            // sometime we get usage>committed exception with older verions of JRockit
            return "exception getting usage";
        }
    }


    public static class ChartForm extends FormData
    {
        private String _type;

        public String getType()
        {
            return _type;
        }

        public void setType(String type)
        {
            _type = type;
        }
    }


    private static class MemoryCategory implements Comparable<MemoryCategory>
    {
        private String _type;
        private double _mb;
        public MemoryCategory(String type, double mb)
        {
            _type = type;
            _mb = mb;
        }

        public int compareTo(MemoryCategory o)
        {
            return new Double(getMb()).compareTo(new Double(o.getMb()));
        }

        public String getType()
        {
            return _type;
        }

        public double getMb()
        {
            return _mb;
        }
    }


    @RequiresSiteAdmin
    public class MemoryChartAction extends ExportAction<ChartForm>
    {
        public void export(ChartForm form, HttpServletResponse response) throws Exception
        {
            MemoryUsage usage = null;
            boolean showLegend = false;
            if ("Heap".equals(form.getType()))
            {
                usage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
                showLegend = true;
            }
            else if ("Non Heap".equals(form.getType()))
                usage = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
            else
            {
                List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
                for (Iterator it = pools.iterator(); it.hasNext() && usage == null;)
                {
                    MemoryPoolMXBean pool = (MemoryPoolMXBean) it.next();
                    if (form.getType().equals(pool.getName()))
                        usage = pool.getUsage();
                }
            }

            if (usage == null)
                throw new NotFoundException();

            DefaultCategoryDataset dataset = new DefaultCategoryDataset();
            List<MemoryCategory> types = new ArrayList<MemoryCategory>(4);

            types.add(new MemoryCategory("Init", usage.getInit() / (1024 * 1024)));
            types.add(new MemoryCategory("Used", usage.getUsed() / (1024 * 1024)));
            types.add(new MemoryCategory("Committed", usage.getCommitted() / (1024 * 1024)));
            types.add(new MemoryCategory("Max", usage.getMax() / (1024 * 1024)));
            Collections.sort(types);

            for (int i = 0; i < types.size(); i++)
            {
                double mbPastPrevious = i > 0 ? types.get(i).getMb() - types.get(i - 1).getMb() : types.get(i).getMb();
                dataset.addValue(mbPastPrevious, types.get(i).getType(), "");
            }

            JFreeChart chart = ChartFactory.createStackedBarChart(form.getType(), null, null, dataset, PlotOrientation.HORIZONTAL, showLegend, false, false);
            response.setContentType("image/png");
            ChartUtilities.writeChartAsPNG(response.getOutputStream(), chart, showLegend ? 800 : 398, showLegend ? 100 : 70);
        }
    }


}
