/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
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

import org.apache.beehive.netui.pageflow.FormData;
import org.apache.beehive.netui.pageflow.Forward;
import org.apache.beehive.netui.pageflow.annotations.Jpf;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateUtils;
import org.apache.commons.collections15.MultiMap;
import org.apache.log4j.Logger;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.upload.FormFile;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.action.SpringActionController.ActionDescriptor;
import org.labkey.api.action.SpringActionController.ActionResolver;
import org.labkey.api.attachments.*;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.*;
import org.labkey.api.data.ContainerManager.RootContainer;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.jsp.FormPage;
import org.labkey.api.module.*;
import org.labkey.api.ms2.MS2Service;
import org.labkey.api.ms2.SearchClient;
import org.labkey.api.pipeline.PipelineStatusUrls;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.security.*;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.util.*;
import org.labkey.api.util.emailTemplate.EmailTemplate;
import org.labkey.api.util.emailTemplate.EmailTemplateService;
import org.labkey.api.util.preferences.PreferenceService;
import org.labkey.api.view.*;
import org.labkey.api.view.template.DialogTemplate;
import org.labkey.api.view.template.HomeTemplate;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.wiki.WikiRenderer;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiService;
import org.labkey.common.util.Pair;
import org.labkey.core.login.LoginController;
import org.springframework.web.servlet.mvc.Controller;

import javax.net.ssl.HttpsURLConnection;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
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

@Jpf.Controller(messageBundles = {@Jpf.MessageBundle(bundlePath = "messages.Validation")})
public class AdminController extends ViewController
{
    private static Logger _log = Logger.getLogger(AdminController.class);

    private static long _errorMark = 0;


    @Jpf.Action @RequiresPermission(ACL.PERM_NONE)
    protected Forward begin() throws Exception
    {
        return new ViewForward("admin", "showAdmin", "");
    }


    @Jpf.Action @RequiresPermission(ACL.PERM_NONE)
    protected Forward maintenance() throws Exception
    {
        WikiRenderer formatter = WikiService.get().getRenderer(WikiRendererType.RADEOX);
        String content = formatter.format(ModuleLoader.getInstance().getAdminOnlyMessage()).getHtml();
        HtmlView errorView = new HtmlView("The site is currently undergoing maintenance", content);
        HttpView template = new DialogTemplate(errorView);
        template.render(getRequest(), getResponse());
        return null;
    }

    @Jpf.Action @RequiresPermission(ACL.PERM_NONE)
    public Forward containerId() throws Exception
    {
        HttpView view = new HtmlView(
                getContainer().getName() + "<br>" +
                getContainer().getId() + "<br>" +
                getContainer().getRowId()
                );
        return includeView(view);
    }


    private static ActionURL getUrl(String action)
    {
        return new ActionURL("admin", action, "");
    }


    public static ActionURL getShowAdminUrl()
    {
        return getUrl("showAdmin");
    }


    @Jpf.Action @RequiresSiteAdmin
    protected Forward showAdmin() throws Exception
    {
        ActionURL url;
        Container c = getContainer();

        JspView view = new JspView("/org/labkey/core/admin/admin.jsp");

        view.addObject("Modules", ModuleLoader.getInstance().getModules());

        view.addObject("javaVersion", System.getProperty("java.version"));
        view.addObject("userName", System.getProperty("user.name"));
        view.addObject("osName", System.getProperty("os.name"));
        view.addObject("mode", AppProps.getInstance().isDevMode() ? "Development" : "Production");
        view.addObject("servletContainer", ModuleLoader.getServletContext().getServerInfo());

        view.addObject("schema", CoreSchema.getInstance().getSchema());

        List<String> emails = UserManager.getUserEmailList();
        List<Pair<String, Long>> active = UserManager.getActiveUsers(System.currentTimeMillis() - DateUtils.MILLIS_PER_HOUR);

        view.addObject("userEmail", getUser().getEmail());
        view.addObject("emails", emails);
        view.addObject("active", active);

        // Configuration
        view.addObject("customizeSiteUrl", ActionURL.toPathString("admin", "showCustomizeSite.view", ""));
        view.addObject("authenticationUrl", new ActionURL("login", "configure.view", "").addParameter("returnUrl", getActionURL().getLocalURIString()));
        view.addObject("FlowAdminUrl", ActionURL.toPathString("Flow", "flowAdmin.view", ""));
        view.addObject("customizeEmailUrl", ActionURL.toPathString("admin", "customizeEmail.view", ""));
        view.addObject("reorderProjectsUrl", ActionURL.toPathString("admin", "reorderFolders.view", ""));
        view.addObject("configureRReportUrl", ActionURL.toPathString("reports", "configureRReport.view", ""));

        // Management
        view.addObject("MS1AdminUrl", ActionURL.toPathString("ms1", "showAdmin.view", ""));
        view.addObject("MS2AdminUrl", ActionURL.toPathString("MS2", "showMS2Admin.view", ""));
        url = PageFlowUtil.urlProvider(PipelineStatusUrls.class).urlBegin(c);
        view.addObject("PipeAdminUrl", url.getLocalURIString() + "StatusFiles.Status%7Eneqornull=COMPLETE");
        url = PageFlowUtil.urlProvider(PipelineUrls.class).urlSetup(c);
        view.addObject("pipelineSetupUrl", url.getLocalURIString());
        view.addObject("ProteinAdminUrl", ActionURL.toPathString("MS2", "showProteinAdmin.view", ""));
        view.addObject("auditLogUrl", ActionURL.toPathString("admin", "showAuditLog.view", ""));

        // Diagnostics
        view.addObject("threadsUrl", ActionURL.toPathString("admin", "showThreads.view", ""));
        view.addObject("memoryUrl", ActionURL.toPathString("admin", "memTracker.view", ""));
        view.addObject("actionsUrl", ActionURL.toPathString("admin", "actions.view", ""));
        view.addObject("scriptsUrl", ActionURL.toPathString("admin", "scripts.view", ""));
        view.addObject("groovyUrl", ActionURL.toPathString("admin", "groovy.view", ""));
        view.addObject("allErrorsUrl", ActionURL.toPathString("admin", "showAllErrors.view", ""));
        view.addObject("recentErrorsUrl", ActionURL.toPathString("admin", "showErrorsSinceMark.view", ""));
        view.addObject("resetErrorsUrl", ActionURL.toPathString("admin", "resetErrorMark.view", ""));
        view.addObject("testLdapUrl", ActionURL.toPathString("admin", "showTestLdap.view", ""));
        view.addObject("dbCheck", ActionURL.toPathString("admin", "dbChecker.view", ""));
        view.addObject("creditsUrl", ActionURL.toPathString("admin", "credits.view", ""));

        return _renderInTemplate(view, "Admin Console");
    }


    private static final String _libPath = "/WEB-INF/lib/";


    @Jpf.Action @RequiresPermission(ACL.PERM_NONE)
    protected Forward credits() throws Exception
    {
        // No security checks... anyone (even guests) can view the credits page

        HttpView jars = new CreditsView(getCreditsFile("jars.txt"), getWebInfJars(true), "JAR", "webapp", "^[\\w|-]+\\.jar\\|");
        HttpView scripts = new CreditsView(getCreditsFile("scripts.txt"), null, "javascript", "/internal/webapp directory", null);
        HttpView bins = new CreditsView(getCreditsFile("executables.txt"), getBinFilenames(), "executable", "/external/bin directory", null);

        return _renderInTemplate(new VBox(jars, scripts, bins), "Credits");
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


    private List<String> getWebInfJars(boolean removeInternalJars)
    {
        if (!AppProps.getInstance().isDevMode())
            return null;

        Set<String> resources = getServletContext().getResourcePaths(_libPath);
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


    @Jpf.Action @RequiresSiteAdmin
    protected Forward showTestLdap(TestLdapForm form) throws Exception
    {
        HttpView view = new GroovyView("/org/labkey/core/admin/testLdap.gm");
        view.addObject("form", form);
        PageConfig page = new PageConfig();
        if (null == form.getMessage() || form.getMessage().length() < 200)
            page.setFocusId("server");
        return includeView(new DialogTemplate(view, page));
    }


    @Jpf.Action @RequiresSiteAdmin
    protected Forward testLdap(TestLdapForm form) throws Exception
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
        return showTestLdap(form);
    }


    @Jpf.Action @RequiresSiteAdmin
    protected Forward resetFavicon() throws Exception
    {
        deleteExistingFavicon();

        return new ViewForward(getCustomizeSiteURL());
    }


    @Jpf.Action @RequiresSiteAdmin
    protected Forward resetLogo() throws Exception
    {
        deleteExistingLogo();
        return new ViewForward(getCustomizeSiteURL());
    }


    @Jpf.Action @RequiresSiteAdmin
    protected Forward showCustomizeSite(CustomizeSiteForm form) throws Exception
    {
        HttpView view = new CustomizeSiteView(false, form.getThemeName(), form.isTestInPage());
        return _renderInTemplate(view, "Customize Site");
    }


    @Jpf.Action @RequiresSiteAdmin
    protected Forward showUpgradeCustomizeSite(CustomizeSiteForm form) throws Exception
    {
        HttpView view = new CustomizeSiteView(true, form.getThemeName(), form.isTestInPage());
        return includeView(new DialogTemplate(view));
    }


    private static class CustomizeSiteView extends GroovyView
    {
        public CustomizeSiteView(boolean upgradeInProgress, String newThemeName, boolean testInPage) throws SQLException
        {
            super("/org/labkey/core/admin/customizeSite.gm");
            addObject("Themes", WebTheme.getWebThemes ());
            addObject("CurrentTheme", WebTheme.getTheme());
            addObject("ThemeFonts", ThemeFont.getThemeFonts ());
            addObject("CurrentThemeFont", ThemeFont.getThemeFont());
            addObject("appProps", AppProps.getInstance());
            addObject("upgradeInProgress", upgradeInProgress);
            addObject("customLogo", AttachmentCache.lookupLogoAttachment());
            addObject("customFavIcon", AttachmentCache.lookupFavIconAttachment());
            addObject("helpLink", "<a href=\"" + (new HelpTopic( "configAdmin", HelpTopic.Area.SERVER)).getHelpTopicLink() + "\" target=\"labkey\">more info...</a>");
            addObject("ftpHelpLink", "<a href=\"" + (new HelpTopic( "configureFtp", HelpTopic.Area.SERVER)).getHelpTopicLink() + "\" target=\"labkey\">help configuring ftp...</a>");
            addObject("testInPage", testInPage);

            //if new color scheme defined, get new theme name from url
            WebTheme newTheme = null;
            if (newThemeName != null)
                newTheme = WebTheme.getTheme(newThemeName);
            addObject("NewTheme", newTheme);
        }
    }


    @Jpf.Action @RequiresSiteAdmin
    protected Forward showDefineWebThemes(WebThemeForm form) throws Exception
    {
        HttpView view = new DefineWebThemesView(form);
        // UNDONE: showCustomizeSite.view should be on nav trail
        return _renderInTemplate(view, "Web Themes");
    }


    @Jpf.Action @RequiresSiteAdmin
    protected Forward showUpgradeDefineWebThemes(WebThemeForm form) throws Exception
    {
        includeView(new DialogTemplate(new DefineWebThemesView(form)));
        return null;
    }


    private static class DefineWebThemesView extends GroovyView
    {
        public DefineWebThemesView(WebThemeForm form) throws SQLException
        {
            super("/org/labkey/core/admin/webTheme.gm");
            addObject("Themes", WebTheme.getWebThemes ());
            String themeName = form.getThemeName();
            WebTheme currentTheme = WebTheme.getTheme(themeName);
            addObject("selectedTheme", currentTheme);
            addObject("form", form);
            addObject("upgradeInProgress", form.isUpgradeInProgress());
        }
    }

    @RequiresSiteAdmin
    @Jpf.Action(validationErrorForward = @Jpf.Forward(path = "showDefineWebThemes.do", name = "showDefineWebThemes"))
    protected Forward defineWebThemes(WebThemeForm form) throws Exception
    {
        String themeName = form.getThemeName();
        String friendlyName = form.getFriendlyName();

        //we should only receive posts to this method now....
        //but do we need this anymore since page is admin only anyhow
        if (!("POST".equalsIgnoreCase(getRequest().getMethod())))
        {
            throw new IllegalAccessException();
        }

        ActionURL url = cloneActionURL();
        url.deleteParameters();

        if (form.isUpgradeInProgress())
            url.setAction("showUpgradeCustomizeSite");
        else
            url.setAction("showCustomizeSite");

        if (null != getRequest().getParameter("Delete.x"))
        {
            // delete the web theme
            WebTheme.deleteWebTheme (themeName);
        }
        else
        {
            //new theme
            if (null == themeName || 0 == themeName.length())
                themeName = friendlyName;

            //add new theme or save existing theme
            WebTheme.updateWebTheme (
                themeName
                , form.getNavBarColor(), form.getHeaderLineColor()
                , form.getEditFormColor(), form.getFullScreenBorderColor()
                , form.getGradientLightColor(), form.getGradientDarkColor()
                );

            AttachmentCache.clearGradientCache();
            ButtonServlet.resetColorScheme();
            //parameter to use to set customize page drop-down to user's last choice on define themes page
            url.addParameter("themeName", themeName);
        }

        WriteableAppProps appProps = AppProps.getWriteableInstance();
        appProps.incrementLookAndFeelRevision();
        appProps.save();

        return new ViewForward(url);
    }


    @Jpf.Action @RequiresSiteAdmin
    protected Forward showThreads() throws Exception
    {
        HttpView view = new JspView<ThreadsBean>("/org/labkey/core/admin/threads.jsp", new ThreadsBean());

        return _renderInTemplate(view, "Current Threads");
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


    @Jpf.Action @RequiresPermission(ACL.PERM_NONE)
    public Forward guid() throws Exception
    {
        getResponse().getWriter().write(GUID.makeGUID());
        return null;
    }


    private void handleLogoFile(FormFile file) throws ServletException, SQLException, IOException, AttachmentService.DuplicateFilenameException
    {
        User user = getViewContext().getUser();

        // Set the name to something we'll recognize as a logo file
        String uploadedFileName = file.getFileName();
        int index = uploadedFileName.lastIndexOf(".");
        if (index == -1)
        {
            throw new ServletException("No file extension on the uploaded image");
        }

        // Get rid of any existing logos
        deleteExistingLogo();

        AttachmentFile renamed = new StrutsAttachmentFile(file);
        renamed.setFilename(AttachmentCache.LOGO_FILE_NAME_PREFIX + uploadedFileName.substring(index));
        AttachmentService.get().addAttachments(user, RootContainer.get(), Collections.<AttachmentFile>singletonList(renamed));
        AttachmentCache.clearLogoCache();
    }

    private void deleteExistingLogo()
            throws SQLException
    {
        RootContainer rootContainer = RootContainer.get();
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

    private void handleIconFile(FormFile file) throws SQLException, IOException, ServletException, AttachmentService.DuplicateFilenameException
    {
        User user = getViewContext().getUser();

        if (!file.getFileName().toLowerCase().endsWith(".ico"))
        {
            throw new ServletException("FavIcon must be a .ico file");
        }

        deleteExistingFavicon();

        AttachmentFile renamed = new StrutsAttachmentFile(file);
        renamed.setFilename(AttachmentCache.FAVICON_FILE_NAME);
        AttachmentService.get().addAttachments(user, RootContainer.get(), Collections.<AttachmentFile>singletonList(renamed));
        AttachmentCache.clearFavIconCache();
    }

    private void deleteExistingFavicon()
            throws SQLException
    {
        // TODO: Simplify this whole thing to: AttachmentService.get().deleteAttachment(RootContainer.get(), AttachmentCache.FAVICON_FILE_NAME); AttachmentCache.clearFavIconCache();

        RootContainer rootContainer = RootContainer.get();
        Attachment[] attachments = AttachmentService.get().getAttachments(rootContainer);
        for (Attachment attachment : attachments)
        {
            if (attachment.getName().equals(AttachmentCache.FAVICON_FILE_NAME))
            {
                AttachmentService.get().deleteAttachment(rootContainer, attachment.getName());
                AttachmentCache.clearFavIconCache();
            }
        }
    }

    private ActionURL getCustomizeSiteURL() throws ServletException
    {
        return new ActionURL("admin", "showCustomizeSite.view", getContainer());
    }


    @Jpf.Action @RequiresSiteAdmin
    protected Forward showNetworkDriveTest(SiteAdminForm form) throws Exception
    {
        NetworkDrive testDrive = new NetworkDrive();
        testDrive.setPassword(form.getNetworkDrivePassword());
        testDrive.setPath(form.getNetworkDrivePath());
        testDrive.setUser(form.getNetworkDriveUser());

        ActionErrors errors = validateNetworkDrive(form);

        TestNetworkDriveBean bean = new TestNetworkDriveBean();

        if (errors.isEmpty())
        {
            char driveLetter = form.getNetworkDriveLetter().trim().charAt(0);
            try
            {
                String mountError = testDrive.mount(driveLetter);
                if (mountError != null)
                {
                    errors.add("main", new ActionMessage("Error", mountError));
                }
                else
                {
                    File f = new File(driveLetter + ":\\");
                    if (!f.exists())
                    {
                        errors.add("main", new ActionMessage("Error", "Could not access network drive"));
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
                errors.add("main", new ActionMessage("Error", "Error mounting drive: " + e));
            }
            catch (InterruptedException e)
            {
                errors.add("main", new ActionMessage("Error", "Error mounting drive: " + e));
            }
            try
            {
                testDrive.unmount(driveLetter);
            }
            catch (IOException e)
            {
                errors.add("main", new ActionMessage("Error", "Error mounting drive: " + e));
            }
            catch (InterruptedException e)
            {
                errors.add("main", new ActionMessage("Error", "Error mounting drive: " + e));
            }
        }

        JspView<TestNetworkDriveBean> v = new JspView<TestNetworkDriveBean>("/org/labkey/core/admin/testNetworkDrive.jsp", bean);
        return includeView(new DialogTemplate(v));
    }

    @Jpf.Action @RequiresSiteAdmin
    protected Forward updatePreferences(SiteAdminForm form) throws Exception
    {
        ModuleLoader.getInstance().setDeferUsageReport(false);

        // We only need to check that SSL is running if the user isn't already using SSL
        if (form.isSslRequired() && !(getRequest().isSecure() && (form.getSslPort() == getRequest().getServerPort())))
        {
            URL testURL = new URL("https", getRequest().getServerName(), form.getSslPort(), AppProps.getInstance().getContextPath());
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
                ActionErrors errors = PageFlowUtil.getActionErrors(getRequest(), true);
                errors.add("main", new ActionMessage("Error", error));
                return new ViewForward(getCustomizeSiteURL(), false);
            }
        }

        if (null != form.getMultipartRequestHandler())
        {
            Map<String, FormFile> fileMap = form.getMultipartRequestHandler().getFileElements();
            FormFile logoFile = fileMap.get("logoImage");
            if (logoFile != null && !"".equals(logoFile.getFileName()) && logoFile.getFileSize() > 0)
            {
                try
                {
                    handleLogoFile(logoFile);
                }
                catch (Exception e)
                {
                    ActionErrors errors = PageFlowUtil.getActionErrors(getRequest(), true);
                    errors.add("main", new ActionMessage("Error", e.getMessage()));
                    return new ViewForward(getCustomizeSiteURL(), false);
                }
            }

            FormFile iconFile = fileMap.get("iconImage");
            if (logoFile != null && !"".equals(iconFile.getFileName()) && iconFile.getFileSize() > 0)
            {
                try
                {
                    handleIconFile(iconFile);
                }
                catch (Exception e)
                {
                    ActionErrors errors = PageFlowUtil.getActionErrors(getRequest(), true);
                    errors.add("main", new ActionMessage("Error", e.getMessage()));
                    return new ViewForward(getCustomizeSiteURL(), false);
                }
            }
        }

        // Make sure we can parse the system maintenance time
        Date systemMaintenanceTime = SystemMaintenance.parseSystemMaintenanceTime(form.getSystemMaintenanceTime());

        if (null == systemMaintenanceTime)
        {
            ActionErrors errors = PageFlowUtil.getActionErrors(getRequest(), true);
            errors.add("main", new ActionMessage("Error", "Invalid format for System Maintenance Time - please enter time in 24-hour format (e.g., 0:30 for 12:30AM, 14:00 for 2:00PM)"));
            return new ViewForward(getCustomizeSiteURL(), false);
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
                ActionErrors errors = PageFlowUtil.getActionErrors(getRequest(), true);
                errors.add("main", new ActionMessage("Error", mascotClient.getErrorString()));
                return new ViewForward(getCustomizeSiteURL(), false);
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
                ActionErrors errors = PageFlowUtil.getActionErrors(getRequest(), true);
                errors.add("main", new ActionMessage("Error", sequestClient.getErrorString()));
                return new ViewForward(getCustomizeSiteURL(), false);
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
            ValidEmail email = new ValidEmail(form.getSystemEmailAddress());
            props.setSystemEmailAddresses(email.getEmailAddress());
        }
        catch (ValidEmail.InvalidEmailException e)
        {
            ActionErrors errors = PageFlowUtil.getActionErrors(getRequest(), true);
            errors.add("main", new ActionMessage("Error", "Invalid System Email Address. Please enter a valid email address."));
            return new ViewForward(getCustomizeSiteURL(), false);
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
            props.setFolderDisplayMode(folderDisplayMode);
        }
        catch (IllegalArgumentException e)
        {
        }


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
            ActionErrors errors = validateNetworkDrive(form);

            if (errors.size() > 0)
            {
                return new ViewForward(getCustomizeSiteURL(), false);
            }
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
                ActionErrors errors = PageFlowUtil.getActionErrors(getRequest(), true);
                errors.add("main", new ActionMessage("Error", "Invalid Base Server URL, " + e.getMessage() + ".  Please enter a valid URL, for example: http://www.labkey.org, https://www.labkey.org, or http://www.labkey.org:8080"));
                return new ViewForward(getCustomizeSiteURL(), false);
            }
        }

        props.save();

        if (null != level)
            level.scheduleUpgradeCheck();

        if (setSystemMaintenanceTimer)
            SystemMaintenance.setTimer();

        if (form.isUpgradeInProgress())
        {
            return new ViewForward("Project", "begin.view", "/home");
        }
        else
        {
            return new ViewForward(getCustomizeSiteURL());
        }
    }

    private ActionErrors validateNetworkDrive(SiteAdminForm form)
    {
        ActionErrors errors = PageFlowUtil.getActionErrors(getRequest(), true);
        if (form.getNetworkDriveLetter() == null || form.getNetworkDriveLetter().trim().length() > 1)
        {
            errors.add("main", new ActionMessage("Error", "Network drive letter must be a single character"));
        }
        char letter = form.getNetworkDriveLetter().trim().toLowerCase().charAt(0);
        if (letter < 'a' || letter > 'z')
        {
            errors.add("main", new ActionMessage("Error", "Network drive letter must be a letter"));
        }
        if (form.getNetworkDrivePath() == null || form.getNetworkDrivePath().trim().length() == 0)
        {
            errors.add("main", new ActionMessage("Error", "If you specify a network drive letter, you must also specify a path"));
        }
        return errors;
    }

    public static class WebThemeForm extends FormData
    {
        String _themeName;
        String _friendlyName;
        String _navBarColor;
        String _headerLineColor;
        String _editFormColor;
        String _fullScreenBorderColor;
        String _gradientLightColor;
        String _gradientDarkColor;

        private boolean upgradeInProgress;

        ArrayList<String> _errorList = new ArrayList<String>();

        public boolean isUpgradeInProgress()
        {
            return upgradeInProgress;
        }

        public void setUpgradeInProgress(boolean upgradeInProgress)
        {
            this.upgradeInProgress = upgradeInProgress;
        }

        public String getEditFormColor()
        {
            return _editFormColor;
        }

        public void setEditFormColor(String editFormColor)
        {
            _editFormColor = editFormColor;
        }

        public String getFriendlyName()
        {
            return _friendlyName;
        }

        public void setFriendlyName(String friendlyName)
        {
            _friendlyName = friendlyName;
        }

        public String getFullScreenBorderColor()
        {
            return _fullScreenBorderColor;
        }

        public void setFullScreenBorderColor(String fullScreenBorderColor)
        {
            _fullScreenBorderColor = fullScreenBorderColor;
        }

        public String getGradientDarkColor()
        {
            return _gradientDarkColor;
        }

        public void setGradientDarkColor(String gradientDarkColor)
        {
            _gradientDarkColor = gradientDarkColor;
        }

        public String getGradientLightColor()
        {
            return _gradientLightColor;
        }

        public void setGradientLightColor(String gradientLightColor)
        {
            _gradientLightColor = gradientLightColor;
        }

        public String getHeaderLineColor()
        {
            return _headerLineColor;
        }

        public void setHeaderLineColor(String headerLineColor)
        {
            _headerLineColor = headerLineColor;
        }

        public String getNavBarColor()
        {
            return _navBarColor;
        }

        public void setNavBarColor(String navBarColor)
        {
            _navBarColor = navBarColor;
        }

        public String getThemeName()
        {
            return _themeName;
        }

        public void setThemeName(String themeName)
        {
            _themeName = themeName;
        }

        private boolean isValidColor(String s)
        {
            if (s.length() != 6) return false;
            int r = -1;
            int g = -1;
            int b = -1;
            try
            {
              r = Integer.parseInt(s.substring(0, 2), 16);
            }
            catch (NumberFormatException e)
            {
                // fall through
            }
            try
            {
              g = Integer.parseInt(s.substring(2, 4), 16);
            }
            catch (NumberFormatException e)
            {
                // fall through
            }
            try
            {
              b = Integer.parseInt(s.substring(4, 6), 16);
            }
            catch (NumberFormatException e)
            {
                // fall through
            }
            if (r<0 || r>255) return false;
            if (g<0 || g>255) return false;
            if (b<0 || b>255) return false;
            return true;
        }
    
        public ActionErrors validate(ActionMapping arg0, HttpServletRequest arg1)
        {
            ActionErrors actionErrors = new ActionErrors();

            //check for nulls on submit
            if ((null == _friendlyName || "".equals(_friendlyName)) && 
                (null == _themeName || "".equals(_themeName)))
            {
                actionErrors.add("web theme", new ActionMessage("Error", "Please choose a theme name."));
            }

            if (_navBarColor == null || _headerLineColor == null || _editFormColor == null ||
                    _fullScreenBorderColor == null || _gradientLightColor == null ||
                    _gradientDarkColor == null || 
                    !isValidColor(_navBarColor) || !isValidColor(_headerLineColor) || !isValidColor(_editFormColor) ||
                    !isValidColor(_fullScreenBorderColor) || !isValidColor(_gradientLightColor) || 
                    !isValidColor(_gradientDarkColor))
            {
                actionErrors.add("web theme", new ActionMessage("Error", "You must provide a valid 6-character hexadecimal value for each field."));
            }
            
            return (actionErrors.size() > 0 ? actionErrors : null);
        }

    }

    public static class SiteAdminForm extends ViewForm
    {
        private boolean _upgradeInProgress;

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
            this._pipelineFTPHost = pipelineFTPHost;
        }

        public String getPipelineFTPPort()
        {
            return _pipelineFTPPort;
        }

        public void setPipelineFTPPort(String pipelineFTPPort)
        {
            this._pipelineFTPPort = pipelineFTPPort;
        }

        public boolean isPipelineFTPSecure()
        {
            return _pipelineFTPSecure;
        }

        public void setPipelineFTPSecure(boolean pipelineFTPSecure)
        {
            this._pipelineFTPSecure = pipelineFTPSecure;
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
            this._themeName = themeName;
        }

        public String getThemeFont()
        {
            return _themeFont;
        }

        public void setThemeFont(String themeFont)
        {
            this._themeFont = themeFont;
        }

        public String getCompanyName()
        {
            return _companyName;
        }

        public void setCompanyName(String companyName)
        {
            this._companyName = companyName;
        }

        public boolean isPipelineCluster()
        {
            return _pipelineCluster;
        }

        public void setPipelineCluster(boolean pipelineCluster)
        {
            this._pipelineCluster = pipelineCluster;
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
            this._sequest = sequest;
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
    }


    @Jpf.Action @RequiresSiteAdmin
    protected Forward impersonate() throws Exception
    {
        String rawEmail = getActionURL().getParameter("email");
        ValidEmail email = new ValidEmail(rawEmail);

        if (UserManager.userExists(email))
        {
            final User impersonatedUser = UserManager.getUser(email);
            SecurityManager.setAuthenticatedUser(getRequest(), impersonatedUser);
            AuditLogService.get().addEvent(getViewContext(), UserManager.USER_AUDIT_EVENT, getUser().getUserId(),
                    getUser().getEmail() + " impersonated user: " + email);
            AuditLogService.get().addEvent(getViewContext(), UserManager.USER_AUDIT_EVENT, impersonatedUser.getUserId(),
                    email + " was impersonated by user: " + getUser().getEmail());
            return new ViewForward(AppProps.getInstance().getHomePageUrl());
        }

        return _renderInTemplate(new HtmlView("User doesn't exist"), "Impersonate User");
    }


    private Forward _renderInTemplate(HttpView view, String title) throws Exception
    {
        return _renderInTemplate(view, title, false);
    }

    private Forward _renderInTemplate(HttpView view, String title, boolean navTrailEndsAtProject) throws Exception
    {
        NavTrailConfig trailConfig = new NavTrailConfig(getViewContext());
        if (title != null)
            trailConfig.setTitle(title);
        if (!"showAdmin".equals(getActionURL().getAction()))
            trailConfig.setExtraChildren(new NavTree("Admin Console", getShowAdminUrl()));

        HomeTemplate template = new HomeTemplate(getViewContext(), getContainer(), view, trailConfig);
        includeView(template);
        return null;
    }


    private Forward _renderInDialogTemplate(HttpView view, String title) throws Exception
    {
        DialogTemplate template = new DialogTemplate(view);
        template.setTitle(title);
        includeView(template);
        return null;
    }


    @Jpf.Action @RequiresSiteAdmin
    protected Forward moduleUpgrade(UpgradeStatusForm form) throws Exception
    {
        Forward f = null;
        User u = getViewContext().getUser();

        if (form.getExpress())
            ModuleLoader.getInstance().setExpress(true);

        //Make sure we are the upgrade user before upgrading...
        User upgradeUser = ModuleLoader.getInstance().setUpgradeUser(u, form.getForce());
        if (u.equals(upgradeUser))
        {
            Module module;
            String moduleName = form.getModuleName();
            //Already have a module to upgrade
            if (null != moduleName)
            {
                module = ModuleLoader.getInstance().getModule(moduleName);
                ModuleLoader.ModuleState state = ModuleLoader.ModuleState.valueOf(form.getState());
                ModuleContext ctx = ModuleLoader.getInstance().getModuleContext(module);
                if (state.equals(ModuleLoader.ModuleState.InstallComplete))
                    ctx.upgradeComplete(form.getNewVersion());
                else //Continue calling until we're done
                    f = module.versionUpdate(ctx, getViewContext());
            }
            else
            {
                //Get next available
                module = ModuleLoader.getInstance().getNextUpgrade();
                if (null != module)
                {
                    ModuleContext ctx = ModuleLoader.getInstance().getModuleContext(module);
                    ctx.setExpress(ModuleLoader.getInstance().getExpress());
                    //Make sure we haven't started. If so, just reshow module status again
                    f = module.versionUpdate(ctx, getViewContext());
                }
            }
        }
        else
        {
            //Make sure status doesn't force user switching..
            form.setForce(false);
        }

        if (null == f)
            f = moduleStatus(form);

        return f;
    }


    @Jpf.Action @RequiresSiteAdmin
    protected Forward startupFailure() throws Throwable
    {
        Throwable failure = ModuleLoader.getInstance().getStartupFailure();
        if (failure != null)
        {
            throw failure;
        }
        else
            return new ViewForward(AppProps.getInstance().getHomePageUrl());
    }


    @Jpf.Action @RequiresPermission(ACL.PERM_NONE)
    protected Forward moduleStatus(UpgradeStatusForm form) throws Exception
    {
        //This is first UI at startup.  Create first admin account, if necessary.
        if (UserManager.hasNoUsers())
            HttpView.throwRedirect(LoginController.getInitialUserUrl());
        requiresAdmin();

        VBox vbox = new VBox();
        vbox.addView(new ModuleStatusView());

        if (ModuleLoader.getInstance().isUpgradeRequired())
            vbox.addView(new StartUpgradingView(ModuleLoader.getInstance().getUpgradeUser(), form.getForce(), ModuleLoader.getInstance().isNewInstall()));
        else
        {
            String url = getActionURL().relativeUrl("showUpgradeCustomizeSite", null, "admin");
            SqlScriptRunner.stopBackgroundThread();

            vbox.addView(new HtmlView("All modules are up-to-date.<br><br>" +
                    "<a href='" + url + "'><img border=0 src='" + PageFlowUtil.buttonSrc("Next") + "'></a>"));
        }

        includeView(new DialogTemplate(vbox));
        return null;
    }

    public static class UpgradeStatusForm extends ViewForm
    {
        private double oldVersion;
        private double newVersion;
        private String moduleName = null;
        private String state = ModuleLoader.ModuleState.InstallRequired.name();
        boolean force = false;
        boolean express = false;

        /**
         * Should we force current user to become the upgrader
         */
        public boolean getForce()
        {
            return force;
        }

        public void setForce(boolean force)
        {
            this.force = force;
        }

        public double getOldVersion()
        {
            return oldVersion;
        }

        public void setOldVersion(double oldVersion)
        {
            this.oldVersion = oldVersion;
        }

        public double getNewVersion()
        {
            return newVersion;
        }

        public void setNewVersion(double newVersion)
        {
            this.newVersion = newVersion;
        }

        public String getModuleName()
        {
            return moduleName;
        }

        public void setModuleName(String moduleName)
        {
            this.moduleName = moduleName;
        }

        public void setState(String state)
        {
            this.state = state;
        }

        public String getState()
        {
            return state;
        }

        public boolean getExpress()
        {
            return express;
        }

        public void setExpress(boolean express)
        {
            this.express = express;
        }
    }


    public static class ModuleStatusView extends HttpView
    {
        @Override
        protected void renderInternal(Object model, PrintWriter out) throws Exception
        {
            List<Module> modules = ModuleLoader.getInstance().getModules();
            out.write("<table><tr><td><b>Module</b></td><td><b>Status</b></td></tr>");
            for (Module module : modules)
            {
                ModuleContext ctx = ModuleLoader.getInstance().getModuleContext(module);
                out.write("<tr><td>");
                out.write(ctx.getName());
                out.write("</td><td>");
                out.write(ctx.getMessage());
                out.write("</td></tr>\n");
            }
            out.write("</table>");
        }
    }


    public static class StartUpgradingView extends HttpView
    {
        User user = null;
        boolean force = false;
        boolean newInstall = false;

        public StartUpgradingView(User currentUser, boolean force, boolean newInstall)
        {
            this.force = force;
            user = currentUser;
            this.newInstall = newInstall;
        }

        @Override
        protected void renderInternal(Object model, PrintWriter out) throws Exception
        {
            String upgradeURL = getViewContext().getActionURL().relativeUrl("moduleUpgrade", force ? "force=1" : "");
            User upgradeUser = ModuleLoader.getInstance().getUpgradeUser();
            String action = newInstall ? "Install" : "Upgrade";
            String ing = newInstall ? "Installing" : "Upgrading";

            //Upgrade is not started
            if (null == upgradeUser || force)
            {
                out.write("<a href=\"" + upgradeURL + "&express=1" + "\"><img border=0 src='" + PageFlowUtil.buttonSrc("Express " + action) + "'></a>&nbsp;");
                out.write("<a href=\"" + upgradeURL + "\"><img border=0 src='" + PageFlowUtil.buttonSrc("Advanced " + action) + "'></a>");
            }
            //I'm already upgrading upgrade next module after showing status
            else if (getViewContext().getUser().equals(upgradeUser))
            {
                out.write("<script type=\"text/javascript\">var timeout = window.setTimeout(\"doRefresh()\", 1000);" +
                        "function doRefresh() {\n" +
                        "   window.clearTimeout(timeout);\n" +
                        "   window.location = '" + upgradeURL + "';\n" +
                        "}\n</script>");
                out.write("<p>");
                out.write(ing + "...");
                out.write("<p>This page should refresh automatically. If the page does not refresh <a href=\"");
                out.write(upgradeURL);
                out.write("\">Click Here</a>");
            }
            //Somebody else is installing/upgrading
            else
            {
                ActionURL url = getViewContext().cloneActionURL();
                url.deleteParameter("express");
                String retryUrl = url.relativeUrl("moduleStatus.view", "force=1");

                out.print("<p>");
                out.print(user.getEmail());
                out.print(" is already " + ing.toLowerCase() + ". <p>");

                out.print("Refresh this page to see " + action.toLowerCase() + " progress.<p>");
                out.print("If " + action.toLowerCase() + " was cancelled, <a href='");
                out.print(retryUrl);
                out.print("'>Try Again</a>");
            }
        }
    }


    @Jpf.Action @RequiresSiteAdmin
    protected Forward memTracker() throws Exception
    {
        HttpView memView = new GroovyView("/org/labkey/core/admin/memTracker.gm");
        String clearCaches = getActionURL().getParameter("clearCaches");

        if (Boolean.valueOf(clearCaches))
        {
            Introspector.flushCaches();
            CacheMap.purgeAllCaches();
        }

        String gc = getActionURL().getParameter("gc");
        if (Boolean.valueOf(gc))
            System.gc();

        List<MemTracker.HeldReference> all = MemTracker.getReferences();
        List<MemTracker.HeldReference> list = new ArrayList<MemTracker.HeldReference>(all.size());
        // removeCache recentely allocated
        long threadId = Thread.currentThread().getId();
        long start = ViewServlet.getRequestStartTime(getRequest());
        for (MemTracker.HeldReference r : all)
        {
            if (r.getThreadId() == threadId && r.getAllocationTime() >= start)
                continue;
            list.add(r);
        }
        memView.addObject("references", list);


        List<Pair<String,Object>> systemProperties = new ArrayList<Pair<String,Object>>();

        // memory:
        List<String> graphNames = new ArrayList<String>();
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

        memView.addObject("systemProperties", systemProperties);
        memView.addObject("graphNames", graphNames);
        memView.addObject("assertsEnabled", Boolean.FALSE);
        assert memView.addObject("assertsEnabled", Boolean.TRUE) != null;
        return _renderInTemplate(memView, "Memory usage -- " + DateUtil.formatDateTime(), false);
    }


    @Jpf.Action @RequiresSiteAdmin
    protected Forward groovy() throws Exception
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

        return _renderInTemplate(new HtmlView(sb.toString()), "Groovy Templates", false);
    }


    @Jpf.Action @RequiresSiteAdmin
    protected Forward scripts() throws Exception
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

        return _renderInTemplate(new HtmlView(html.toString()), "SQL Scripts", false);
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


    @Jpf.Action @RequiresSiteAdmin
    protected Forward runSystemMaintenance() throws Exception
    {
        SystemMaintenance sm = new SystemMaintenance(false);
        sm.run();

        return _renderInTemplate(new HtmlView("System maintenance task started"), "System Maintenance", false);
    }


    private static ActionURL getActionsURL()
    {
        return new ActionURL("admin", "actions", "");
    }


    @Jpf.Action @RequiresSiteAdmin
    protected Forward actions() throws Exception
    {
        return _renderInTemplate(new ActionsTabStrip(), "Spring Actions");
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


    static NumberFormat formatInteger = DecimalFormat.getIntegerInstance();

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

    public static class CustomizeSiteForm extends FormData
    {
        private boolean _testInPage = false;
        private String _themeName;
//        private boolean upgradeInProgress;

        public boolean isTestInPage()
        {
            return _testInPage;
        }

        public void setTestInPage(boolean testInPage)
        {
            _testInPage = testInPage;
        }

        public String getThemeName()
        {
            return _themeName;
        }

        public void setThemeName(String themeName)
        {
            _themeName = themeName;
        }

//        public boolean isUpgradeInProgress()
//        {
//            return upgradeInProgress;
//        }
//
//        public void setUpgradeInProgress(boolean upgradeInProgress)
//        {
//            this.upgradeInProgress = upgradeInProgress;
//        }
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


    @Jpf.Action @RequiresSiteAdmin
    protected Forward memoryChart(ChartForm form) throws Exception
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
            return HttpView.throwNotFound();

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
        getResponse().setContentType("image/png");
        ChartUtilities.writeChartAsPNG(getResponse().getOutputStream(), chart, showLegend ? 800 : 398, showLegend ? 100 : 70);
        return null;
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


    @Jpf.Action @RequiresSiteAdmin
    protected Forward dbChecker() throws Exception
    {
        ActionURL currentUrl = cloneActionURL();
        String fixRequested = currentUrl.getParameter("_fix");
        StringBuffer contentBuffer = new StringBuffer();

        if (null != fixRequested)
        {
            String sqlcheck=null;
            if (fixRequested.equalsIgnoreCase("container"))
                   sqlcheck = DbSchema.checkAllContainerCols(true);
            if (fixRequested.equalsIgnoreCase("descriptor"))
                   sqlcheck = OntologyManager.doProjectColumnCheck(true);
            contentBuffer.append(sqlcheck);
        }
        else
        {
            contentBuffer.append("\n<br/><br/>Checking Container Column References...");
            String strTemp = DbSchema.checkAllContainerCols(false);
            if (strTemp.length() > 0)
            {
                contentBuffer.append(strTemp);
                currentUrl = cloneActionURL();
                currentUrl.addParameter("_fix", "container");
                contentBuffer.append("<br/><br/>&nbsp;&nbsp;&nbsp;&nbsp; click <a href=\""
                        + currentUrl + "\" >here</a> to attempt recovery .");
            }
            
            contentBuffer.append("\n<br/><br/>Checking PropertyDescriptor and DomainDescriptor consistency...");
            strTemp = OntologyManager.doProjectColumnCheck(false);
            if (strTemp.length() > 0)
            {
                contentBuffer.append(strTemp);
                currentUrl = cloneActionURL();
                currentUrl.addParameter("_fix", "descriptor");
                contentBuffer.append("<br/><br/>&nbsp;&nbsp;&nbsp;&nbsp; click <a href=\""
                        + currentUrl + "\" >here</a> to attempt recovery .");
            }



            contentBuffer.append("\n<br/><br/>Checking Schema consistency with tableXML...");
            Set<DbSchema> schemas = new HashSet<DbSchema>();
            List<Module> modules = ModuleLoader.getInstance().getModules();
            String sOut=null;

             for (Module module : modules)
                 schemas.addAll(module.getSchemasToTest());

            for (DbSchema schema : schemas)
            {
                sOut = TableXmlUtils.compareXmlToMetaData(schema.getName(), false, false);
                if (null!=sOut)
                    contentBuffer.append("<br/>&nbsp;&nbsp;&nbsp;&nbsp;ERROR: Inconsistency in Schema "+ schema.getName()
                            + "<br/>"+ sOut);
            }

            contentBuffer.append("\n<br/><br/>Database Consistency checker complete");

        }
        HtmlView htmlView = new HtmlView("<table class=\"DataRegion\"><tr><td>" + contentBuffer.toString() + "</td></tr></table>");
        htmlView.setTitle("Database Consistency Checker");
        return _renderInTemplate(htmlView, "Database Consistency Checker");


    }


    @Jpf.Action @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward manageFolders(ManageFoldersForm form) throws Exception
    {
        if (getContainer().isRoot())
            HttpView.throwNotFound();
        
        JspView v = FormPage.getView(AdminController.class, form, "manageFolders.jsp");

        return _renderInTemplate(v, "Manage Folders", true);
    }

    public static class FolderReorderForm extends FormData
    {
        private String _order;
        private boolean _resetToAlphabetical;

        public String getOrder()
        {
            return _order;
        }

        public void setOrder(String order)
        {
            _order = order;
        }

        public boolean isResetToAlphabetical()
        {
            return _resetToAlphabetical;
        }

        public void setResetToAlphabetical(boolean resetToAlphabetical)
        {
            _resetToAlphabetical = resetToAlphabetical;
        }
    }

    @Jpf.Action
    @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward reorderFolders(FolderReorderForm form) throws Exception
    {
        if ("POST".equalsIgnoreCase(getRequest().getMethod()))
        {
            Container parent = getContainer().isRoot() ? getContainer() : getContainer().getParent();
            if (form.isResetToAlphabetical())
                ContainerManager.setChildOrderToAlphabetical(parent);
            else if (form.getOrder() != null)
            {
                List<Container> children = parent.getChildren();
                String[] order = form.getOrder().split(";");
                Map<String,Container> nameToContainer = new HashMap<String, Container>();
                for (Container child : children)
                    nameToContainer.put(child.getName(), child);
                List<Container> sorted = new ArrayList<Container>(children.size());
                for (String childName : order)
                {
                    Container child = nameToContainer.get(childName);
                    sorted.add(child);
                }
                ContainerManager.setChildOrder(parent, sorted);
            }
            if (getContainer().isRoot())
                return new ViewForward(getActionURL().relativeUrl("showAdmin", null));
            else
                return new ViewForward(getActionURL().relativeUrl("manageFolders", null));
        }
        JspView<ViewContext> v = new JspView<ViewContext>("/org/labkey/core/admin/reorderFolders.jsp");
        return _renderInTemplate(v, "Reorder " + (getContainer().isRoot() || getContainer().getParent().isRoot() ? "Projects" : "Folders"), true);
    }

    /**
     * Shows appropriate UI for renaming, moving, deleting, and creating folders & projects.  These actions
     * share a lot of common code, so use a single JPF action with an "action" parameter.
     */
    @Jpf.Action @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward modifyFolder(ManageFoldersForm form) throws Exception
    {
        Container c = getContainer();

        JspView<ManageFoldersForm> view = new JspView<ManageFoldersForm>("/org/labkey/core/admin/modifyFolder.jsp", form);

        String containerDescription;

        if ("create".equals(form.getAction()))
            containerDescription = (c.isRoot() ? "Project" : "Folder");
        else
            containerDescription = (c.isProject() ? "Project" : "Folder");

        String title = toProperCase(form.getAction()) + " " + containerDescription;

        if ("delete".equals(form.getAction()))
            return _renderInDialogTemplate(view, title);
        else
            return _renderInTemplate(view, title, true);
    }


    private static String toProperCase(String s)
    {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }


    public static class MoveContainerTree extends ContainerTree
    {
        private Container ignore;

        public MoveContainerTree(String rootPath, User user, int perm, ActionURL url)
        {
            super(rootPath, user, perm, url);
        }

        public void setIgnore(Container c)
        {
            ignore = c;
        }

        @Override
        protected boolean renderChildren(StringBuilder html, MultiMap<Container, Container> mm, Container parent, int level)
        {
            if (!parent.equals(ignore))
                return super.renderChildren(html, mm, parent, level);
            else
                return false;
        }
    }


    @Jpf.Action @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward moveFolder(ManageFoldersForm form) throws Exception
    {
        Container c = ContainerManager.getForPath(form.getFolder());  // Folder to move
        if (null == c)
            HttpView.throwNotFound("Folder does not exist");
        if (c.isRoot())
            return errorForward("move", "Error: Can't move the root folder.", c.getPath());

        Container newParent = form.getContainer();
        if (!newParent.hasPermission(getUser(), ACL.PERM_ADMIN))
            HttpView.throwUnauthorized();

        if (newParent.hasChild(c.getName()))
            return errorForward("move", "Error: The selected folder already has a folder with that name.  Please select a different location (or Cancel).", c.getPath());

        Container oldProject = c.getProject();
        Container newProject = newParent.isRoot() ? c : newParent.getProject();
        if (!oldProject.getId().equals(newProject.getId()) && !form.isConfirmed())
        {
            HttpView v = new GroovyView("/org/labkey/core/admin/confirmProjectMove.gm");
            v.addObject("form", form);
            return includeView(new DialogTemplate(v));
        }

        ContainerManager.move(c, newParent);

        if (form.isAddAlias())
        {
            String[] originalAliases = ContainerManager.getAliasesForContainer(c);
            List<String> newAliases = new ArrayList<String>(Arrays.asList(originalAliases));
            newAliases.add(c.getPath());
            ContainerManager.saveAliasesForContainer(c, newAliases);
        }

        c = ContainerManager.getForId(c.getId());                     // Reload container to populate new location
        return new ViewForward("admin", "manageFolders", c.getPath());
    }


    @Jpf.Action @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward renameFolder(ManageFoldersForm form) throws SQLException, ServletException, URISyntaxException
    {
        Container c = getContainer();
        String folderName = form.getName();
        StringBuffer error = new StringBuffer();

        if (Container.isLegalName(folderName, error))
        {
            if (c.getParent().hasChild(folderName))
                error.append("The parent folder already has a folder with this name.");
            else
            {
                ContainerManager.rename(c, folderName);
                if (form.isAddAlias())
                {
                    String[] originalAliases = ContainerManager.getAliasesForContainer(c);
                    List<String> newAliases = new ArrayList<String>(Arrays.asList(originalAliases));
                    newAliases.add(c.getPath());
                    ContainerManager.saveAliasesForContainer(c, newAliases);
                }
                c = ContainerManager.getForId(c.getId());  // Reload container to populate new name
                return new ViewForward("admin", "manageFolders", c.getPath());
            }
        }

        return errorForward("rename", "Error: " + error + "  Please enter a different folder name (or Cancel).");
    }


    @Jpf.Action @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward createFolder(ManageFoldersForm form)
            throws IOException, ServletException, SQLException, URISyntaxException
    {
        Container parent = getContainer();
        String folderName = form.getName();
        StringBuffer error = new StringBuffer();

        if (Container.isLegalName(folderName, error))
        {
            if (parent.hasChild(folderName))
                error.append("The parent folder already has a folder with this name.");
            else
            {
                Container c = ContainerManager.createContainer(parent, folderName);
                String folderType = form.getFolderType();
                assert null != folderType;
                FolderType type = ModuleLoader.getInstance().getFolderType(folderType);
                c.setFolderType(type);


                ActionURL next;
                if (c.isProject())
                {
                    SecurityManager.createNewProjectGroups(c);
                    next = new ActionURL("Security", "project", c);
                }
                else
                {
                    //If current user is NOT a site or folder admin, we'll inherit permissions (otherwise they would not be able to see the folder)
                    Integer adminGroupId = null;
                    if (null != c.getProject())
                        adminGroupId = SecurityManager.getGroupId(c.getProject(), "Administrators", false);
                    boolean isProjectAdmin = (null != adminGroupId) && getUser().isInGroup(adminGroupId.intValue());
                    if (!isProjectAdmin && !getUser().isAdministrator())
                        SecurityManager.setInheritPermissions(c);

                    if (type.equals(FolderType.NONE))
                        next = new ActionURL("admin", "customize", c);
                    else
                        next = new ActionURL("Security", "container", c);
                }
                next.addParameter("wizard", Boolean.TRUE.toString());

                return new ViewForward(next);
            }
        }

        return errorForward("create", "Error: " + error + "  Please enter a different folder name (or Cancel).");
    }

    @Jpf.Action @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward deleteFolder(ManageFoldersForm form) throws SQLException, ServletException, URISyntaxException
    {
        Container c = getContainer();

        if (!isPost())
            return new ViewForward("admin", "manageFolder", c);

        // Must be site admin to delete a project
        if (c.isProject())
            requiresGlobalAdmin();

        if (form.getRecurse())
        {
            ContainerManager.deleteAll(c, getUser());
        }
        else
        {
            if (c.getChildren().isEmpty())
                ContainerManager.delete(c, getUser());
            else
                throw new IllegalStateException("This container has children");  // UI should prevent this case
        }

        // If we just deleted a project then redirect to the home page, otherwise back to managing the project folders
        if (c.isProject())
            return new ViewForward("Project", "begin.view", "home");
        else
            return new ViewForward("admin", "manageFolders", c.getParent().getPath());
    }


    private Forward errorForward(String action, String error)
    {
        return errorForward(action, error, null);
    }


    // Forward back to modifyFolder action with appropriate error.  Used for SQL Exceptions (e.g., constraint
    // violations when renaming, creating, or moving a folder somewhere that already has a folder of that name)
    private Forward errorForward(String action, String error, String extraPath)
    {
        ActionURL currentUrl = cloneActionURL();
        currentUrl.setAction("modifyFolder");
        currentUrl.addParameter("action", action);
        PageFlowUtil.getActionErrors(getRequest(), true).add("main", new ActionMessage("Error", error));
        currentUrl.deleteParameter("x");
        currentUrl.deleteParameter("y");

        if (null != extraPath)
            currentUrl.setExtraPath(extraPath);

        return new ViewForward(currentUrl, false);
    }


    @Jpf.Action @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward customize(UpdateFolderForm form) throws Exception
    {
        Container c = getContainer();
        if (c.isRoot())
            HttpView.throwNotFound();
            
        JspView<UpdateFolderForm> view = new JspView<UpdateFolderForm>("/org/labkey/core/admin/customizeFolder.jsp", form);
        NavTrailConfig config = new NavTrailConfig(getViewContext()).setTitle("Customize folder " + c.getPath());
        HttpView template = new HomeTemplate(getViewContext(), c, view, config);
        includeView(template);
        return null;
    }


    @Jpf.Action(validationErrorForward = @Jpf.Forward(path = "customize.do", name = "customize"))
    @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward updateFolder(UpdateFolderForm form) throws Exception
    {
        Container c = getContainer();
        if (c.isRoot())
            HttpView.throwNotFound();
        
        String[] modules = form.getActiveModules();
        Set<Module> activeModules = new HashSet<Module>();
        for (String moduleName : modules)
        {
            Module module = ModuleLoader.getInstance().getModule(moduleName);
            if (module != null)
                activeModules.add(module);
        }

        if (null == StringUtils.trimToNull(form.getFolderType()) || FolderType.NONE.getName().equals(form.getFolderType()))
        {
            c.setFolderType(FolderType.NONE, activeModules);
            Module defaultModule = ModuleLoader.getInstance().getModule(form.getDefaultModule());
            c.setDefaultModule(defaultModule);
        }
        else
        {
            FolderType folderType= ModuleLoader.getInstance().getFolderType(form.getFolderType());
            c.setFolderType(folderType, activeModules);
        }

        ActionURL url;
        if (form.isWizard())
        {
            url = new ActionURL("Security", "container", c);
            url.addParameter("wizard", Boolean.TRUE.toString());
        }
        else
            url = c.getFolderType().getStartURL(c, getUser());

        return new ViewForward(url);
    }

    private File getErrorLogFile()
    {
        File tomcatHome = new File(System.getProperty("catalina.home"));
        return new File(tomcatHome, "logs/labkey-errors.log");
    }


    @Jpf.Action @RequiresSiteAdmin
    public Forward resetErrorMark() throws Exception
    {
        File errorLogFile = getErrorLogFile();
        _errorMark = errorLogFile.length();
        return begin();
    }


    public Forward showErrors(long startingOffset) throws Exception
    {
        File errorLogFile = getErrorLogFile();
        if (errorLogFile.exists())
        {
            FileInputStream fIn = null;
            try
            {
                fIn = new FileInputStream(errorLogFile);
                fIn.skip(startingOffset);
                OutputStream out = getResponse().getOutputStream();
                getResponse().setContentType("text/plain");
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
        return null;
    }


    @Jpf.Action @RequiresSiteAdmin
    public Forward showErrorsSinceMark() throws Exception
    {
        return showErrors(_errorMark);
    }


    @Jpf.Action @RequiresSiteAdmin
    public Forward showAllErrors() throws Exception
    {
        return showErrors(0);
    }


    public static class UpdateFolderForm extends ViewForm
    {
        private String[] activeModules;
        private String defaultModule;
        private String folderType;
        private boolean wizard;

        public String[] getActiveModules()
        {
            return activeModules;
        }

        public void setActiveModules(String[] activeModules)
        {
            this.activeModules = activeModules;
        }

        public String getDefaultModule()
        {
            return defaultModule;
        }

        public void setDefaultModule(String defaultModule)
        {
            this.defaultModule = defaultModule;
        }

        public void reset(ActionMapping mapping, HttpServletRequest request)
        {
            activeModules = new String[ModuleLoader.getInstance().getModules().size()];
        }

        public ActionErrors validate(ActionMapping arg0, HttpServletRequest arg1)
        {
            boolean fEmpty = true;
            for(String module : activeModules)
            {
                if(module != null)
                {
                    fEmpty = false;
                    break;
                }
            }
            if(fEmpty && "None".equals(getFolderType()))
            {
                ActionErrors actionErrors = new ActionErrors();
                String error = "Error: Please select at least one tab to display.";
                actionErrors.add("tabs", new ActionMessage("Error", error));
                return actionErrors;
            }
            return null;
        }

        public String getFolderType()
        {
            return folderType;
        }

        public void setFolderType(String folderType)
        {
            this.folderType = folderType;
        }

        public boolean isWizard()
        {
            return wizard;
        }

        public void setWizard(boolean wizard)
        {
            this.wizard = wizard;
        }
    }


    public static class ManageFoldersForm extends ViewForm
    {
        private String name;
        private String folder;
        private String action;
        private String folderType;
        private boolean showAll;
        private boolean confirmed = false;
        private boolean addAlias;
        private boolean recurse = false;


        public void reset(ActionMapping actionMapping, HttpServletRequest request)
        {
            super.reset(actionMapping, request);
            addAlias = false;
        }

        public boolean isShowAll()
        {
            return showAll;
        }

        public void setShowAll(boolean showAll)
        {
            this.showAll = showAll;
        }

        public String getAction()
        {
            return action;
        }

        public void setAction(String action)
        {
            this.action = action;
        }

        public String getFolder()
        {
            return folder;
        }

        public void setFolder(String folder)
        {
            this.folder = folder;
        }

        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        public String getProjectName()
        {
            String extraPath = getContainer().getPath();

            int i = extraPath.indexOf("/", 1);

            if (-1 == i)
                return extraPath;
            else
                return extraPath.substring(0, i);
        }

        public boolean isConfirmed()
        {
            return confirmed;
        }

        public void setConfirmed(boolean confirmed)
        {
            this.confirmed = confirmed;
        }

        public String getFolderType()
        {
            return folderType;
        }

        public void setFolderType(String folderType)
        {
            this.folderType = folderType;
        }

        public boolean isAddAlias()
        {
            return addAlias;
        }

        public void setAddAlias(boolean addAlias)
        {
            this.addAlias = addAlias;
        }

        public boolean getRecurse()
        {
            return recurse;
        }

        public void setRecurse(boolean recurse)
        {
            this.recurse = recurse;
        }
    }


    @Jpf.Action @RequiresSiteAdmin
    protected Forward customizeEmail(CustomEmailForm form) throws Exception
    {
        JspView<CustomEmailForm> view = new JspView<CustomEmailForm>("/org/labkey/core/admin/customizeEmail.jsp", form);

        NavTree[] navTrail = new NavTree[] {
                new NavTree("Admin Console", new ActionURL("admin", "begin", getViewContext().getContainer())),
                new NavTree("Customize Email")};

        NavTrailConfig config = new NavTrailConfig(getViewContext());

        config.setTitle("Customize Email", false);
        config.setExtraChildren(navTrail);

        return _renderInTemplate(view, "Customize Email");
    }


    @Jpf.Action @RequiresSiteAdmin
    protected Forward updateCustomEmail(CustomEmailForm form) throws Exception
    {
        if (form.getTemplateClass() != null)
        {
            EmailTemplate template = EmailTemplateService.get().createTemplate(form.getTemplateClass());

            template.setSubject(form.getEmailSubject());
            template.setBody(form.getEmailMessage());

            String[] errors = new String[1];
            if (template.isValid(errors))
                EmailTemplateService.get().saveEmailTemplate(template);
            else
                PageFlowUtil.getActionErrors(getRequest(), true).add("main", new ActionMessage("Error", errors[0]));
        }
        return customizeEmail(form);
    }

    @Jpf.Action
    @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward folderAliases() throws Exception
    {
        JspView<ViewContext> view = new JspView<ViewContext>("/org/labkey/core/admin/folderAliases.jsp");
        return _renderInTemplate(view, "Folder Aliases: " + getViewContext().getContainer().getPath());
    }

    @Jpf.Action @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward saveAliases(UpdateAliasesForm form) throws Exception
    {
        List<String> aliases = new ArrayList<String>();
        if (form.getAliases() != null)
        {
            StringTokenizer st = new StringTokenizer(form.getAliases(), "\n\r", false);
            while (st.hasMoreTokens())
            {
                String alias = st.nextToken().trim();
                if (!alias.startsWith("/"))
                {
                    alias = "/" + alias;
                }
                while (alias.endsWith("/"))
                {
                    alias = alias.substring(0, alias.lastIndexOf('/'));
                }
                aliases.add(alias);
            }
        }
        ContainerManager.saveAliasesForContainer(getContainer(), aliases);
        ActionURL url = cloneActionURL();
        url.setAction("manageFolders.view");
        return new ViewForward(url);
    }

    public static class UpdateAliasesForm extends ViewForm
    {
        private String _aliases;


        public String getAliases()
        {
            return _aliases;
        }

        public void setAliases(String aliases)
        {
            _aliases = aliases;
        }
    }


    @Jpf.Action @RequiresPermission(ACL.PERM_READ)
    protected Forward setAdminMode(UserPrefsForm form) throws Exception
    {
        PreferenceService.get().setProperty("adminMode", form.isAdminMode() ? Boolean.TRUE.toString() : null, getUser());
        return new ViewForward(form.getRedir());
    }

    @Jpf.Action @RequiresPermission(ACL.PERM_READ)
    protected Forward setShowFolders(UserPrefsForm form) throws Exception
    {
        PreferenceService.get().setProperty("showFolders", Boolean.toString(form.isShowFolders()), getUser());
        return new ViewForward(form.getRedir());
    }

    public static class UserPrefsForm extends FormData
    {
        private boolean adminMode;
        private boolean showFolders;
        private String redir;

        public boolean isAdminMode()
        {
            return adminMode;
        }

        public void setAdminMode(boolean adminMode)
        {
            this.adminMode = adminMode;
        }

        public boolean isShowFolders()
        {
            return showFolders;
        }

        public void setShowFolders(boolean showFolders)
        {
            this.showFolders = showFolders;
        }

        public String getRedir()
        {
            return redir;
        }

        public void setRedir(String redir)
        {
            this.redir = redir;
        }
    }
    @Jpf.Action @RequiresSiteAdmin
    protected Forward deleteCustomEmail(CustomEmailForm form) throws Exception
    {
        if (form.getTemplateClass() != null)
        {
            EmailTemplate template = EmailTemplateService.get().createTemplate(form.getTemplateClass());
            template.setSubject(form.getEmailSubject());
            template.setBody(form.getEmailMessage());

            EmailTemplateService.get().deleteEmailTemplate(template);
        }
        return customizeEmail(form);
    }

    public static class CustomEmailForm extends ViewForm
    {
        private String _templateClass;
        private String _emailSubject;
        private String _emailMessage;

        public void setTemplateClass(String name){_templateClass = name;}
        public String getTemplateClass(){return _templateClass;}
        public void setEmailSubject(String subject){_emailSubject = subject;}
        public String getEmailSubject(){return _emailSubject;}
        public void setEmailMessage(String body){_emailMessage = body;}
        public String getEmailMessage(){return _emailMessage;}
    }
}
