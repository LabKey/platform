/*
 * Copyright (c) 2005-2008 LabKey Corporation
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
package org.labkey.pipeline;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.action.*;
import org.labkey.api.data.Container;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.pipeline.*;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.*;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.util.*;
import org.labkey.api.view.*;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.AdminConsole;
import org.labkey.pipeline.api.PipelineEmailPreferences;
import org.labkey.pipeline.api.PipelineRoot;
import org.labkey.pipeline.api.GlobusKeyPairImpl;
import org.labkey.pipeline.status.StatusController;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.*;
import java.security.GeneralSecurityException;

public class PipelineController extends SpringActionController
{
    private static Logger _log = Logger.getLogger(PipelineController.class);
    private static DefaultActionResolver _resolver = new DefaultActionResolver(PipelineController.class);

    public enum RefererValues { protal, pipeline }

    public enum Params { referer, rootset, overrideRoot }

    private void saveReferer()
    {
        getViewContext().getRequest().getSession().setAttribute(Params.referer.toString(),
                getViewContext().getRequest().getParameter(Params.referer.toString()));
    }

    private String getSavedReferer()
    {
        return (String) getViewContext().getRequest().getSession().getAttribute(Params.referer.toString());
    }

    private static HelpTopic getHelpTopic(String topic)
    {
        return new HelpTopic(topic, HelpTopic.Area.SERVER);
    }

    public PipelineController()
    {
        super();
        setActionResolver(_resolver);
    }

    public PageConfig defaultPageConfig()
    {
        PageConfig p = super.defaultPageConfig();
        p.setHelpTopic(getHelpTopic("pipeline"));
        return p;
    }

    @RequiresPermission(ACL.PERM_READ)
    public class BeginAction extends SimpleRedirectAction
    {
        public ActionURL getRedirectURL(Object o)
        {
            return StatusController.urlShowList(getContainer(), false);
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class ReturnToRefererAction extends SimpleRedirectAction
    {
        public ActionURL getRedirectURL(Object o)
        {
            if (RefererValues.pipeline.toString().equals(getSavedReferer()))
                return StatusController.urlShowList(getContainer(), true);
            else
                return PageFlowUtil.urlProvider(ProjectUrls.class).urlStart(getContainer());
        }
    }

    public static ActionURL urlSetup(Container c)
    {
        return urlSetup(c, null);
    }
    
    public static ActionURL urlSetup(Container c, String referer)
    {
        return urlSetup(c, referer, false, false);
    }

    public static ActionURL urlSetup(Container c, String referer, boolean rootSet, boolean overrideRoot)
    {
        ActionURL url = new ActionURL(SetupAction.class, c);
        if (referer != null && referer.length() > 0)
            url.addParameter(Params.referer, referer);
        if (rootSet)
            url.addParameter(Params.rootset, "1");
        if (overrideRoot)
            url.addParameter(Params.overrideRoot, "1");
        return url;
    }

    @RequiresSiteAdmin
    public class SetupAction extends AbstractSetupAction<SetupForm>
    {
        protected SetupField getFormField()
        {
            return SetupField.path;
        }

        public void validateCommand(SetupForm target, Errors errors)
        {
        }

        public boolean handlePost(SetupForm form, BindException errors) throws Exception
        {
            URI root = null;

            String path = form.getPath();
            if (path != null && path.length() > 0)
            {
                File fileRoot = new File(path);
                if (!NetworkDrive.exists(fileRoot))
                {
                    error(errors, "The directory '" + fileRoot + "' does not exist.");
                    return false;
                }
                else if (!fileRoot.isDirectory())
                {
                    error(errors, "The file '" + fileRoot + "' is not a directory.");
                    return false;
                }

                root = fileRoot.toURI();
                if (URIUtil.resolve(root, root, "test") == null)
                {
                    error(errors, "The pipeline root '" + fileRoot + "' is not valid.");
                    return false;
                }
            }

            Map<String, MultipartFile> files = getFileMap();
            byte[] keyBytes = null;
            String keyPassword = form.getKeyPassword();
            byte[] certBytes = null;
            if (files.get("keyFile") != null)
            {
                keyBytes = files.get("keyFile").getBytes();
            }
            if (files.get("certFile") != null)
            {
                certBytes = files.get("certFile").getBytes();
            }
            GlobusKeyPair keyPair = null;
            if (!form.isUploadNewGlobusKeys())
            {
                PipeRoot pipeRoot = PipelineService.get().findPipelineRoot(getContainer());
                if (pipeRoot != null)
                {
                    keyPair = pipeRoot.getGlobusKeyPair();
                }
            }
            else if ((keyBytes != null && keyBytes.length > 0) || (certBytes != null && certBytes.length > 0) || keyPassword != null)
            {
                keyPair = new GlobusKeyPairImpl(keyBytes, keyPassword, certBytes);
                try
                {
                    keyPair.validateMatch();
                }
                catch (GeneralSecurityException e)
                {
                    errors.addError(new LabkeyError("Invalid Globus SSL configration: " + e.getMessage()));
                    return false;
                }
            }

            PipelineService.get().setPipelineRoot(getUser(), getContainer(), root, PipelineRoot.PRIMARY_ROOT,
                    keyPair, form.isPerlPipeline());
            return true;
        }

        public ActionURL getSuccessURL(SetupForm form)
        {
            return urlSetup(getContainer(), getSavedReferer(), true, false);
        }
    }

    enum SetupField { path, email }

    public static class SetupBean
    {
        private String _confirmMessage;
        private String _strValue;
        private ActionURL _doneURL;
        private GlobusKeyPair _globusKeyPair;
        private boolean _perlPipeline;

        public SetupBean(String confirmMessage, String strValue, ActionURL doneURL,
                         GlobusKeyPair globusKeyPair, boolean perlPipeline)
        {
            _confirmMessage = confirmMessage;
            _strValue = strValue;
            _doneURL = doneURL;
            _globusKeyPair = globusKeyPair;
            _perlPipeline = perlPipeline;
        }

        public ActionURL getDoneURL()
        {
            return _doneURL;
        }

        public String getConfirmMessage()
        {
            return _confirmMessage;
        }

        public String getStrValue()
        {
            return _strValue;
        }

        public GlobusKeyPair getGlobusKeyPair()
        {
            return _globusKeyPair;
        }

        public boolean isPerlPipeline()
        {
            return _perlPipeline;
        }
    }

    abstract public class AbstractSetupAction<FORM> extends FormViewAction<FORM>
    {
        abstract protected SetupField getFormField();

        protected void error(BindException errors, String message)
        {
            errors.rejectValue(getFormField().toString(), ERROR_MSG, message);
        }

        public ModelAndView getView(FORM form, boolean reshow, BindException errors) throws Exception
        {
            setHelpTopic(getHelpTopic("pipelineSetup"));

            if (getViewContext().getRequest().getParameter(Params.overrideRoot.toString()) == null)
            {
                PipeRoot root = PipelineService.get().findPipelineRoot(getContainer());
                if (root != null && !getContainer().equals(root.getContainer()))
                {
                    ActionURL urlOverride = getViewContext().cloneActionURL();
                    urlOverride.addParameter(Params.overrideRoot, "1");
                    ActionURL urlEditParent = urlSetup(root.getContainer());
                    StringBuilder html = new StringBuilder();
                    html.append("<p>This folder inherits its pipeline root settings from the folder '");
                    html.append(PageFlowUtil.filter(root.getContainer().getPath()));
                    html.append("'.<br>You can either [<a href=\"");
                    html.append(PageFlowUtil.filter(urlOverride));
                    html.append("\">override</a>] the inherited settings in this folder,<br>or ");
                    html.append("[<a href=\"");
                    html.append(PageFlowUtil.filter(urlEditParent));
                    html.append("\">modify the setting for all folders</a>] by setting the value in the folder '");
                    html.append(PageFlowUtil.filter(root.getContainer().getPath()));
                    html.append("'.</p>");
                    return new HtmlView(html.toString());
                }
            }

            Container c = getContainer();
            VBox view = new VBox();
            String strValue = "";
            PipeRoot pipeRoot = null;

            if (!c.isRoot())
            {
                saveReferer();

                File fileRoot = null;
                URI root = null;
                try
                {
                    pipeRoot = getPipelineRoot(getContainer());
                    if (pipeRoot != null)
                    {
                        root = pipeRoot.getUri();
                        if (root != null)
                        {
                            fileRoot = pipeRoot.getRootPath();
                            strValue = fileRoot.getPath();
                        }
                    }
                }
                catch (Exception e)
                {
                    _log.error("Error", e);
                    // TODO: Redirect somewhere, or show error
                }

                if (root != null && fileRoot != null)
                {
                    if (!NetworkDrive.exists(fileRoot))
                    {
                        errors.addError(new LabkeyError("Pipeline root does not exist."));
                        root = null;
                    }
                    else if (URIUtil.resolve(root, root, "test") == null)
                    {
                        errors.addError(new LabkeyError("Pipeline root is invalid."));
                        root = null;
                    }
                }

                ActionURL doneURL = new ActionURL(ReturnToRefererAction.class, getContainer());

                String confirmMessage = null;
                if (root != null && fileRoot != null && getViewContext().getRequest().getParameter(PipelineController.Params.rootset.toString()) != null)
                {
                    confirmMessage = "The pipeline root was set to '" + fileRoot.getPath() + "'.";
                }

                GlobusKeyPair keyPair = null;
                boolean usePerlPipeline;
                if (pipeRoot == null)
                {
                    usePerlPipeline = AppProps.getInstance().isPerlPipelineEnabled();
                    if (usePerlPipeline)
                    {
                        // If Globus properties are set, assume system is transitioning away
                        // from Perl pipeline.
                        usePerlPipeline = !PipelineService.get().isEnterprisePipeline() ||
                            PipelineJobService.get().getGlobusClientProperties() == null;
                    }
                }
                else
                {
                    keyPair = pipeRoot.getGlobusKeyPair();
                    usePerlPipeline = pipeRoot.isPerlPipeline();
                }
                
                SetupBean bean = new SetupBean(confirmMessage, strValue, doneURL, keyPair, usePerlPipeline);                
                JspView<SetupBean> jspView = new JspView<SetupBean>("/org/labkey/pipeline/setup.jsp", bean, errors);

                PipelineService service = PipelineService.get();

                HBox main = new HBox();
                VBox leftBox = new VBox(jspView);

                main.addView(leftBox);

                if (pipeRoot != null && root != null) // && StringUtils.trimToNull(AppProps.getInstance().getPipelineFTPHost()) != null)
                {
                    ACL acl = pipeRoot.getACL();
                    main.addView(new PermissionView(acl));
                }
                main.setTitle("Data Pipeline Setup");
                main.setFrame(WebPartView.FrameType.PORTAL);

                view.addView(main);

                if (root != null)
                {
                    for (PipelineProvider provider : service.getPipelineProviders())
                    {
                        HttpView part = provider.getSetupWebPart(c);
                        if (part != null)
                            leftBox.addView(part);
                    }
                }
            }

            view.addView(new JspView<FORM>("/org/labkey/pipeline/emailNotificationSetup.jsp", form));
            return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Data Pipeline Setup");
        }        
    }

    public static ActionURL urlBrowse(Container container, String referer)
    {
        ActionURL url = new ActionURL(BrowseAction.class, container);
        url.addParameter(Params.referer, referer);
        return url;
    }

    @RequiresPermission(ACL.PERM_READ)
    public class BrowseAction extends SimpleViewAction<PathForm>
    {
        private boolean _fileView;

        // Necessary for nav tree
        private String _path;
        private PipelineProvider.FileEntry[] _entries = new PipelineProvider.FileEntry[0];

        public boolean isFileView()
        {
            return _fileView;
        }

        public void setFileView(boolean fileView)
        {
            _fileView = fileView;
        }

        public void validate(PathForm form, BindException errors)
        {
        }

        // CONSIDER: PipelineProvider.FileEntry should proabably extend NavTree
        public ModelAndView getView(PathForm form, BindException errors) throws Exception
        {
            Container c = getContainer();

            PipeRoot pr = PipelineService.get().findPipelineRoot(c);

            if (pr == null || !URIUtil.exists(pr.getUri()))
                return HttpView.throwNotFoundMV("Pipeline root not set or does not exist on disk");

            URI uriRoot = pr.getUri(c);

            _path = form.getPath();
            if ("./".equals(_path))
                _path = "";
            if (_path == null)
                _path = pr.getStartingPath(getContainer(), getUser());
            else
                pr.rememberStartingPath(getContainer(), getUser(), _path);

            URI current = URIUtil.resolve(uriRoot, _path);
            if (current == null)
                return HttpView.throwNotFoundMV();

            File fileCurrent = new File(current);
            if (!fileCurrent.exists())
                HttpView.throwNotFoundMV("File not found: " + current.getPath());
            setHelpTopic(getHelpTopic("pipeline/browse"));

            saveReferer();

            URI uriCurrent = URIUtil.resolve(uriRoot, this._path);

            ActionURL browseURL = isFileView() ?
                    new ActionURL(FilesAction.class, c) :
                    new ActionURL(BrowseAction.class, c);

            List<PipelineProvider.FileEntry> parents = new ArrayList<PipelineProvider.FileEntry>();
            List<PipelineProvider.FileEntry> dirEntries = new ArrayList<PipelineProvider.FileEntry>();
            for (URI parent = uriCurrent; parent != null; parent = URIUtil.getParentURI(uriRoot, parent))
            {
                browseURL.replaceParameter("path", toRelativePath(uriRoot, parent));
                PipelineProvider.FileEntry entry = new PipelineProvider.FileEntry(parent, browseURL, true);
                entry.setLabel(new File(parent).getName());
                parents.add(0, entry);

                if (parents.size() == 1)
                {
                    File[] directories = entry.listFiles(new PipelineProvider.FileEntryFilter() {
                        public boolean accept(File f)
                        {
                            return f.isDirectory() && !f.getName().startsWith(".");
                        }
                    });
                    Arrays.sort(directories, new Comparator<File>()
                    {
                        public int compare(File f1, File f2)
                        {
                            return f1.getPath().compareToIgnoreCase(f2.getPath());
                        }
                    });

                    for (File dir : directories)
                    {
                        URI uri = dir.toURI();
                        browseURL.replaceParameter("path", toRelativePath(uriRoot, uri));
                        dirEntries.add(new PipelineProvider.FileEntry(uri, browseURL, false));
                    }

                    List<PipelineProvider> providers;
                    if (isFileView())
                    {
                        providers = new ArrayList<PipelineProvider>();
                        providers.add(new FileContentPipelineProvider());
                    }
                    else
                    {
                        providers = PipelineService.get().getPipelineProviders();
                    }

                    for (PipelineProvider provider : providers)
                        provider.updateFileProperties(getViewContext(), pr, parents);

                    // keep actions in consistent order for display
                    entry.orderActions();
                }
            }

            if (c == pr.getContainer())
                parents.get(0).setLabel("root");

            _entries = parents.toArray(new PipelineProvider.FileEntry[parents.size()]);

            JspView<PathForm> directoryView = new JspView<PathForm>("/org/labkey/pipeline/directory.jsp",form);
            directoryView.setFrame(WebPartView.FrameType.DIV);
            //directoryView.setTitle("Process and Import Data");
            directoryView.addObject("pipeRoot", pr);
            directoryView.addObject("parents", parents);
            directoryView.addObject("entries", dirEntries);
            directoryView.addObject("showCheckboxes", false);

            return directoryView;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            String caption = isFileView() ?
                    "Manage files" :
                    "Process and Import Data";

            if (_entries.length == 0)
                return root.addChild(caption);

            NavTree added = root.addChild(caption + " (root)", _entries[0].getHref());
            for (int i = 1 ; i < _entries.length; i++)
                added = root.addChild(_entries[i].getLabel(), _entries[i].getHref());

            if (_path.length() > 0 && !_path.equals("./"))
            {
                String title = caption;
                try
                {
                    title += " - /" + URLDecoder.decode(_path, "UTF-8");
                    if (title.endsWith("/"))
                        title = title.substring(0,title.length()-1);
                }
                catch (UnsupportedEncodingException e)
                {
                }
                added = root.addChild(title);
            }

            return added;
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class FilesAction extends BrowseAction
    {
        public FilesAction()
        {
            setFileView(true);
        }
    }

    @RequiresSiteAdmin
    public class UpdateRootPermissionsAction extends RedirectAction<PermissionForm>
    {
        public ActionURL getSuccessURL(PermissionForm permissionForm)
        {
            return new ActionURL(SetupAction.class, getContainer());
        }

        public boolean doAction(PermissionForm form, BindException errors) throws Exception
        {
            Container c = getContainer();
            PipeRoot pipeRoot = getPipelineRoot(c);
            assert null == pipeRoot || pipeRoot.getContainer().getId().equals(c.getId());

            if (null != pipeRoot && null != pipeRoot.getUri())
            {
                ACL acl = new ACL();
                if (form.isEnable())
                {
                    acl.setPermission(Group.groupGuests, 0); // force isEmpty() == false
                    Group[] groupsAll = SecurityManager.getGroups(c.getProject(), true);
                    Map<Integer,Group> map = new HashMap<Integer,Group>(groupsAll.length * 2);
                    for (Group g : groupsAll)
                        map.put(g.getUserId(),g);

                    int count = form.getSize();
                    for (int i=0 ; i<count ; i++)
                    {
                        Integer groupId = form.getGroups().get(i);
                        Group g = map.get(groupId);
                        if (null == g)
                            continue;
                        Integer perm = form.getPerms().get(i);
                        if (perm == null || perm.intValue() == 0)
                            continue;
                        acl.setPermission(groupId.intValue(), perm.intValue());
                    }
                }

                // UNDONE: move setACL() to PipelineManager
                SecurityManager.updateACL(c, pipeRoot.getEntityId(), acl);
                ContainerManager.ContainerPropertyChangeEvent evt = new ContainerManager.ContainerPropertyChangeEvent(
                        c, ContainerManager.Property.PipelineRoot, pipeRoot, pipeRoot);
                ContainerManager.firePropertyChangeEvent(evt);
            }

            return true;
        }

        public void validateCommand(PermissionForm target, Errors errors)
        {
        }
    }

    public static class PermissionForm
    {
        private ArrayList<Integer> groups = new FormArrayList<Integer>(Integer.class)
        {
            protected Integer newInstance() throws IllegalAccessException, InstantiationException
            {
                return Integer.valueOf(Integer.MIN_VALUE);
            }
        };
        private ArrayList<Integer> perms = new FormArrayList<Integer>(Integer.class)
        {
            protected Integer newInstance() throws IllegalAccessException, InstantiationException
            {
                return Integer.valueOf(0);
            }
        };
        private boolean enable = false;

        public int getSize()
        {
            return Math.min(groups.size(),perms.size());
        }

        public boolean isEnable()
        {
            return enable;
        }

        public void setEnable(boolean enable)
        {
            this.enable = enable;
        }

        public ArrayList<Integer> getGroups()
        {
            return groups;
        }

        public void setGroups(ArrayList<Integer> groups)
        {
            this.groups = groups;
        }

        public ArrayList<Integer> getPerms()
        {
            return perms;
        }

        public void setPerms(ArrayList<Integer> perms)
        {
            this.perms = perms;
        }
    }

    /////////////////////////////////////////////////////////////////////////
    //  FTP support

    public class PermissionView extends JspView<ACL>
    {
        PermissionView(ACL acl)
        {
            super(PipelineController.class, "permission.jsp", acl);
        }
    }

    public class TestFtpLoginAction extends SimpleStreamAction
    {
        public String getMimeType()
        {
            return "text/html";
        }

        public void setResponseProperties(HttpServletResponse response)
        {
            response.setHeader("Cache-Control", "no-cache");
        }

        public void render(Object o, BindException errors, PrintWriter out) throws Exception
        {
            if (!AppProps.getInstance().isDevMode())
                HttpView.throwUnauthorized();

            out.write("<html><body>\n");
            out.write("<form method=\"POST\" action=\"ftpLogin.post\">\n");
            out.write("<input type=\"text\" name=\"user\"/><br/>\n");
            out.write("<input type=\"password\" name=\"password\"/><br/>\n");
            out.write("<input type=\"hidden\" name=\"devMode\" value=\"true\"/>\n");
            out.write("<input type=\"submit\"/>\n");
            out.write("</form>\n");
            out.write("</body></html>\n");
        }
    }

    public class FtpLoginAction extends SimpleStreamAction<FtpLoginForm>
    {
        public void setResponseProperties(HttpServletResponse response)
        {
            // No caching, since this really RPC
            response.setHeader("Cache-Control", "no-cache");
            response.setCharacterEncoding("UTF-8");            
        }

        public void render(FtpLoginForm form, BindException errors, PrintWriter out) throws Exception
        {
            boolean isDevMode = AppProps.getInstance().isDevMode() && form.isDevMode();
            try
            {
                User u = AuthenticationManager.authenticate(form.getUser(), form.getPassword());

                if ((getViewContext().getACL().getPermissions(u) & ACL.PERM_READ) != ACL.PERM_READ)
                {
                    HttpView.throwUnauthorized();
                    return;
                }

                PipelineService service = PipelineService.get();
                Container c = getContainer();

                PipeRoot root = service.findPipelineRoot(c);
                URI uriRoot = (root != null) ? root.getUri(c) : null;
                if (uriRoot == null || !URIUtil.exists(root.getUri(c)))
                {
                    HttpView.throwNotFound();
                    return;
                }

                File f = new File(uriRoot);
                NetworkDrive drive = NetworkDrive.getNetworkDrive(f.getPath());

                out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
                out.write("<!DOCTYPE properties SYSTEM \"http://java.sun.com/dtd/properties.dtd\">\n");
                out.write("<properties version=\"1.0\">");
                if (drive != null)
                {
                    out.write("<entry key=\"drive.path\">"+drive.getPath()+"</entry>\n");
                    out.write("<entry key=\"drive.user\">"+drive.getUser()+"</entry>\n");
                    out.write("<entry key=\"drive.password\">"+drive.getPassword()+"</entry>\n");
                }
                out.write("<entry key=\"home\">"+f.getAbsolutePath()+"</entry>\n");
                out.write("</properties>");
            }
            catch(Exception e)
            {
                //on any exception pretend like the page doesn't exist if this isn't dev mode
                if (isDevMode) throw e;
                HttpView.throwNotFound();
            }
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    public static class FtpLoginForm
    {
        String user;
        String password;
        boolean devMode;

        public String getUser()
        {
            return user;
        }

        public void setUser(String user)
        {
            this.user = user;
        }

        public String getPassword()
        {
            return password;
        }

        public void setPassword(String password)
        {
            this.password = password;
        }

        public boolean isDevMode()
        {
            return devMode;
        }

        public void setDevMode(boolean devMode)
        {
            this.devMode = devMode;
        }
    }

/*    public static ActionURL urlFtpDetails(Container c, String path)
    {
        ActionURL url = new ActionURL("ftp", "info", c);
        url.addParameter("pipeline", path);
        return url;
    }

 replaced with ftpcontroller info.view

    @RequiresPermission(ACL.PERM_READ)
    public class FtpDetailsAction extends SimpleViewAction<PathForm>
    {
        public ModelAndView getView(PathForm form, BindException errors) throws Exception
        {
            Container c = getContainer();
            String path = form.getPath();
            if (StringUtils.trimToNull(path) == null)
                return HttpView.throwNotFoundMV("Pipeline root not set or does not exist on disk");

            String fileSystemViewURL = "";
            String host = AppProps.getInstance().getPipelineFTPHost();
            String port = AppProps.getInstance().getPipelineFTPPort();
            String ftpScheme = AppProps.getInstance().isPipelineFTPSecure() ?
                                "ftps://" : "ftp://";
            String ftpUser = getUser().getEmail();
            String ftpPath = host;
            if (port.length() != 0)
                ftpPath += ":"+port;
            ftpPath += c.getPath() + "/" + FtpConnector.PIPELINE_LINK + "/" + path;

            fileSystemViewURL = ftpScheme+PageFlowUtil.encode(ftpUser)+"@"+ftpPath;

            GroovyView detailsView = new GroovyView("/org/labkey/pipeline/ftpdetails.gm");
            detailsView.setTitle("FTP Instructions");
            detailsView.addObject("ftpUser", ftpUser);
            detailsView.addObject("ftpString", ftpScheme+ftpPath);
            detailsView.addObject("fileSystemViewURL", fileSystemViewURL);

            detailsView.render(getViewContext().getRequest(), getViewContext().getResponse());
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    } */
    
    /////////////////////////////////////////////////////////////////////////
    //  Email notifications

    @RequiresPermission(ACL.PERM_ADMIN)
    public class UpdateEmailNotificationAction extends AbstractSetupAction<EmailNotificationForm>
    {
        protected SetupField getFormField()
        {
            // Note: This is never used because the <labkey:errors /> tag is used,
            //       and it does not allow for field errors.
            return SetupField.email;
        }

        public void validateCommand(EmailNotificationForm target, Errors errors)
        {
        }

        public boolean handlePost(EmailNotificationForm form, BindException errors) throws Exception
        {
            if (!form.getNotifyOnSuccess())
            {
                form.setNotifyOwnerOnSuccess(false);
                form.setNotifyUsersOnSuccess("");
                form.setSuccessNotifyInterval("");
                form.setSuccessNotifyStart("");
            }

            if (!form.getNotifyOnError())
            {
                form.setNotifyOwnerOnError(false);
                form.setNotifyUsersOnError("");
                form.setEscalationUsers("");
                form.setFailureNotifyInterval("");
                form.setFailureNotifyStart("");
            }

            validateStartTime(form.getSuccessNotifyStart(), errors);
            validateStartTime(form.getFailureNotifyStart(), errors);

            Container c = getContainer();
            PipelineEmailPreferences pref = PipelineEmailPreferences.get();
            pref.setNotifyOwnerOnSuccess(form.getNotifyOwnerOnSuccess(), c);
            pref.setNotifyUsersOnSuccess(getValidEmailList(form.getNotifyUsersOnSuccess(), errors), c);
            pref.setSuccessNotificationInterval(
                    form.getSuccessNotifyInterval(),
                    form.getSuccessNotifyStart(),
                    c);
            pref.setNotifyOwnerOnError(form.getNotifyOwnerOnError(), c);
            pref.setNotifyUsersOnError(getValidEmailList(form.getNotifyUsersOnError(), errors), c);
            pref.setEscalationUsers(getValidEmailList(form.getEscalationUsers(), errors), c);
            pref.setFailureNotificationInterval(
                    form.getFailureNotifyInterval(),
                    form.getFailureNotifyStart(),
                    c);

            return errors.getGlobalErrorCount() == 0;
        }

        public ActionURL getSuccessURL(EmailNotificationForm form)
        {
            return urlSetup(getContainer());
        }

        private void validateStartTime(String startTime, BindException errors)
        {
            try {
                if (!StringUtils.isEmpty(startTime))
                    DateUtil.parseDateTime(startTime, "H:mm");
            }
            catch (ParseException pe)
            {
                errors.reject(ERROR_MSG, "Invalid time format: " + startTime);
            }
        }

        private String getValidEmailList(String emailString, BindException errors)
        {
            String[] rawEmails = StringUtils.trimToEmpty(emailString).split("\n");
            List<String> invalidEmails = new ArrayList<String>();
            List<ValidEmail> emails = org.labkey.api.security.SecurityManager.normalizeEmails(rawEmails, invalidEmails);
            StringBuilder builder = new StringBuilder();

            for (ValidEmail email : emails)
            {
                builder.append(email.getEmailAddress());
                builder.append(';');
            }
            for (String rawEmail : invalidEmails)
            {
                String e = StringUtils.trimToNull(rawEmail);
                if (null != e)
                {
                    errors.reject(ERROR_MSG, "Invalid email address: " + e);
                }
            }
            return builder.toString();
        }
    }

    public static class EmailNotificationForm
    {
        boolean _notifyOnSuccess;
        boolean _notifyOnError;
        boolean _notifyOwnerOnSuccess;
        boolean _notifyOwnerOnError;
        String _notifyUsersOnSuccess;
        String _notifyUsersOnError;
        String _escalationUsers;
        String _successNotifyInterval;
        String _failureNotifyInterval;
        String _successNotifyStart;
        String _failureNotifyStart;

        public boolean getNotifyOnSuccess()
        {
            return _notifyOnSuccess;
        }

        public void setNotifyOnSuccess(boolean notifyOnSuccess)
        {
            _notifyOnSuccess = notifyOnSuccess;
        }

        public boolean getNotifyOnError()
        {
            return _notifyOnError;
        }

        public void setNotifyOnError(boolean notifyOnError)
        {
            _notifyOnError = notifyOnError;
        }

        public boolean getNotifyOwnerOnSuccess()
        {
            return _notifyOwnerOnSuccess;
        }

        public void setNotifyOwnerOnSuccess(boolean notifyOwnerOnSuccess)
        {
            _notifyOwnerOnSuccess = notifyOwnerOnSuccess;
        }

        public boolean getNotifyOwnerOnError()
        {
            return _notifyOwnerOnError;
        }

        public void setNotifyOwnerOnError(boolean notifyOwnerOnError)
        {
            _notifyOwnerOnError = notifyOwnerOnError;
        }

        public String getNotifyUsersOnSuccess()
        {
            return _notifyUsersOnSuccess;
        }

        public void setNotifyUsersOnSuccess(String notifyUsersOnSuccess)
        {
            _notifyUsersOnSuccess = notifyUsersOnSuccess;
        }

        public String getNotifyUsersOnError()
        {
            return _notifyUsersOnError;
        }

        public void setNotifyUsersOnError(String notifyUsersOnError)
        {
            _notifyUsersOnError = notifyUsersOnError;
        }

        public String getEscalationUsers()
        {
            return _escalationUsers;
        }

        public void setEscalationUsers(String escalationUsers)
        {
            _escalationUsers = escalationUsers;
        }

        public String getSuccessNotifyInterval()
        {
            return _successNotifyInterval;
        }

        public void setSuccessNotifyInterval(String interval)
        {
            _successNotifyInterval = interval;
        }

        public String getFailureNotifyInterval()
        {
            return _failureNotifyInterval;
        }

        public void setFailureNotifyInterval(String interval)
        {
            _failureNotifyInterval = interval;
        }

        public String getSuccessNotifyStart()
        {
            return _successNotifyStart;
        }

        public void setSuccessNotifyStart(String start)
        {
            _successNotifyStart = start;
        }

        public String getFailureNotifyStart()
        {
            return _failureNotifyStart;
        }

        public void setFailureNotifyStart(String start)
        {
            _failureNotifyStart = start;
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class ResetEmailNotificationAction extends SimpleRedirectAction
    {
        public ActionURL getRedirectURL(Object o) throws Exception
        {
            PipelineEmailPreferences.get().deleteAll(getContainer());

            return new ActionURL(SetupAction.class, getContainer());
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class CompleteUserAction extends AjaxCompletionAction<CompleteUserForm>
    {
        public List<AjaxCompletion> getCompletions(CompleteUserForm form, BindException errors) throws Exception
        {
            return UserManager.getAjaxCompletions(form.getPrefix(), getViewContext());
        }
    }

    public static class CompleteUserForm
    {
        private String _prefix;

        public String getPrefix()
        {
            return _prefix;
        }

        public void setPrefix(String prefix)
        {
            _prefix = prefix;
        }
    }

/////////////////////////////////////////////////////////////////////////////
//  Direct access to the PipelineQueue

    public enum StatusParams { allcontainers }

    public static ActionURL urlStatus(Container container, boolean allContainers)
    {
        ActionURL url = new ActionURL(StatusAction.class, container);
        if (allContainers)
            url.addParameter(StatusParams.allcontainers, "1");
        return url;
    }

    /**
     * Use the current container and the current "allcontainers" value to
     * produce a URL for the status action.
     *
     * @return URL to the status action
     */
    private ActionURL urlStatus()
    {
        boolean allContainers = (getViewContext().getRequest().getParameter(StatusParams.allcontainers.toString()) != null);
        
        return urlStatus(getContainer(), allContainers);
    }

    @RequiresSiteAdmin
    public class StatusAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            setHelpTopic(getHelpTopic("pipeline/status"));

            PipelineQueue queue = PipelineService.get().getPipelineQueue();
            return new JspView<StatusModel>("/org/labkey/pipeline/pipelineStatus.jsp",
                    new StatusModel(queue.getJobData(getJobDataContainer())));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Pipeline Status");
        }
    }

    public class StatusModel
    {
        private PipelineJobData _jobData;

        private StatusModel(PipelineJobData jobData)
        {
            _jobData = jobData;
        }

        public PipelineJobData getJobData()
        {
            return _jobData;
        }
    }

    @RequiresPermission(ACL.PERM_DELETE)
    public class CancelJobAction extends SimpleRedirectAction<JobIdForm>
    {
        public ActionURL getRedirectURL(JobIdForm form) throws Exception
        {
            PipelineQueue queue = PipelineService.get().getPipelineQueue();
            boolean success = queue.cancelJob(getJobDataContainer(), form.getJobId());
            return urlStatus();
        }
    }

    public static class JobIdForm
    {
        private int _jobId;

        public int getJobId()
        {
            return _jobId;
        }

        public void setJobId(int jobId)
        {
            _jobId = jobId;
        }
    }

    protected Container getJobDataContainer() throws Exception
    {
        if (getUser().isAdministrator() &&
                getViewContext().getRequest().getParameter(StatusParams.allcontainers.toString()) != null)
        {
            return null;
        }
        return getContainer();
    }

    protected PipeRoot getPipelineRoot(Container c)
    {
        try
        {
            PipeRoot p = PipelineService.get().findPipelineRoot(c);
            if (p != null && p.getContainer() != null && p.getContainer().getId().equals(c.getId()))
                return p;
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        return null;
    }

    private String toRelativePath(URI uriRoot, URI parent)
    {
        String path = URIUtil.relativize(uriRoot, parent).toString();
        // don't return "" since we can't tell the difference between
        // browse.view? and browse.view?path=
        return path.equals("") ? "./" : path;
    }

/////////////////////////////////////////////////////////////////////////////
//  File download support

    @RequiresPermission(ACL.PERM_READ)
    public class DownloadAction extends SimpleViewAction<PathForm>
    {
        public ModelAndView getView(PathForm form, BindException errors) throws Exception
        {
            PipeRoot pipeRoot = getPipelineRoot(getContainer());
            if (null == pipeRoot || null == StringUtils.trimToNull(form.getPath()))
                return HttpView.throwNotFoundMV();

            // check pipeline ACL
            ACL acl = pipeRoot.getACL();
            if (!acl.hasPermission(getUser(), ACL.PERM_READ))
                return HttpView.throwUnauthorizedMV();

            File file = new File(pipeRoot.getRootPath(),form.getPath());
            if (!file.exists() || !file.isFile())
                return HttpView.throwNotFoundMV();

            PageFlowUtil.streamFile(getViewContext().getResponse(), file, true);
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    public static class ActionDownloadFile extends PipelineProvider.FileAction
    {
        public ActionDownloadFile(ActionURL href, File file)
        {
            super("download", href, new File[] {file});
        }

        @Override
        public String getDisplay()
        {
            StringBuilder sb = new StringBuilder();
            sb.append("[<a class=\"labkey-message\" href=\"").append(PageFlowUtil.filter(getHref())).append("\">")
                    .append(PageFlowUtil.filter(getLabel())).append("</a>]");
            return sb.toString();
        }
    }

    public static class ActionDeleteFile extends PipelineProvider.FileAction
    {
        public ActionDeleteFile(ActionURL href, File file)
        {
            super("delete " + file.getName(), href, new File[] {file});
        }

        @Override
        public String getButtonSrc()
        {
            return AppProps.getInstance().getContextPath() + "/_images/delete.gif";
        }
    }

    /**
     * <code>PathForm</code> is heavily used in browsing a pipeline root.
     */
    public static class PathForm
    {
        // TODO: Action forms also depend on the path parameter.  Move it to API?
        enum Params { path }

        private String _path;

        public String getPath()
        {
            return _path;
        }

        public void setPath(String path)
        {
            _path = path;
        }
    }

    public static class SetupForm extends PathForm
    {
        private String _keyPassword;
        private boolean _uploadNewGlobusKeys;
        private boolean _perlPipeline;

        public boolean isUploadNewGlobusKeys()
        {
            return _uploadNewGlobusKeys;
        }

        public void setUploadNewGlobusKeys(boolean uploadNewGlobusKeys)
        {
            _uploadNewGlobusKeys = uploadNewGlobusKeys;
        }

        public String getKeyPassword()
        {
            return _keyPassword;
        }

        public void setKeyPassword(String keyPassword)
        {
            _keyPassword = keyPassword;
        }

        public boolean isPerlPipeline()
        {
            return _perlPipeline;
        }

        public void setPerlPipeline(boolean perlPipeline)
        {
            _perlPipeline = perlPipeline;
        }
    }

/////////////////////////////////////////////////////////////////////////////
//  Public URL interface to this controller

    public static void registerAdminConsoleLinks()
    {
        ActionURL url = PageFlowUtil.urlProvider(PipelineUrls.class).urlSetup(ContainerManager.getRoot());
        AdminConsole.addLink(AdminConsole.SettingsLinkType.Management, "pipeline email notification", url);
    }

    public static class PipelineUrlsImp implements PipelineUrls
    {
        public ActionURL urlBrowse(Container container, String referer)
        {
            return PipelineController.urlBrowse(container, referer);
        }

        public ActionURL urlReferer(Container container)
        {
            return new ActionURL(ReturnToRefererAction.class, container);
        }

        public ActionURL urlSetup(Container container)
        {
            return PipelineController.urlSetup(container);
        }

        public ActionURL urlBegin(Container container)
        {
            return PipelineController.urlBegin(container);
        }
    }

    public static ActionURL urlBegin(Container container)
    {
        return new ActionURL(BeginAction.class, container); 
    }
}
