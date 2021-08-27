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
package org.labkey.pipeline;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.BaseViewAction;
import org.labkey.api.action.CustomApiForm;
import org.labkey.api.action.FormArrayList;
import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.GWTServiceAction;
import org.labkey.api.action.LabKeyError;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleErrorView;
import org.labkey.api.action.SimpleRedirectAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.admin.ImportException;
import org.labkey.api.admin.ImportOptions;
import org.labkey.api.compliance.ComplianceService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.FilesAdminOptions;
import org.labkey.api.files.view.FilesWebPart;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.module.Module;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineAction;
import org.labkey.api.pipeline.PipelineActionConfig;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.pipeline.PipelineStatusUrls;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.pipeline.browse.PipelinePathForm;
import org.labkey.api.pipeline.view.SetupForm;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryUrls;
import org.labkey.api.security.Group;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.AbstractActionPermissionTest;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UserManagementPermission;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.study.StudyService;
import org.labkey.api.trigger.TriggerConfiguration;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.URIUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HBox;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.PageConfig;
import org.labkey.pipeline.api.PipeRootImpl;
import org.labkey.pipeline.api.PipelineEmailPreferences;
import org.labkey.pipeline.api.PipelineManager;
import org.labkey.pipeline.api.PipelineSchema;
import org.labkey.pipeline.api.PipelineServiceImpl;
import org.labkey.pipeline.api.PipelineStatusManager;
import org.labkey.pipeline.status.StatusController;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PipelineController extends SpringActionController
{
    private static final DefaultActionResolver _resolver = new DefaultActionResolver(PipelineController.class);

    public enum Params { path, rootset, overrideRoot }

    private static HelpTopic getHelpTopic(String topic)
    {
        return new HelpTopic(topic);
    }

    public PipelineController()
    {
        setActionResolver(_resolver);
    }

    @Override
    public PageConfig defaultPageConfig()
    {
        PageConfig p = super.defaultPageConfig();
        p.setHelpTopic(getHelpTopic("pipeline"));
        return p;
    }

    @RequiresPermission(ReadPermission.class)
    public class BeginAction extends SimpleRedirectAction
    {
        @Override
        public ActionURL getRedirectURL(Object o)
        {
            return StatusController.urlShowList(getContainer(), false);
        }
    }

    public static ActionURL urlSetup(Container c)
    {
        return urlSetup(c, null);
    }

    public static ActionURL urlSetup(Container c, ActionURL returnURL)
    {
        return urlSetup(c, returnURL, false, false);
    }

    public static ActionURL urlSetup(Container c, ActionURL returnURL, boolean rootSet, boolean overrideRoot)
    {
        ActionURL url = new ActionURL(SetupAction.class, c);
        if (returnURL != null)
            url.addReturnURL(returnURL);
        if (rootSet)
            url.addParameter(Params.rootset, "1");
        if (overrideRoot)
            url.addParameter(Params.overrideRoot, "1");
        return url;
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public class SetupAction extends AbstractSetupAction<SetupForm>
    {
        @Override
        protected SetupField getFormField()
        {
            return SetupField.path;
        }

        @Override
        public void validateCommand(SetupForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(SetupForm form, BindException errors) throws Exception
        {
            return savePipelineSetup(getViewContext(), form, errors);
        }

        @Override
        public ActionURL getSuccessURL(SetupForm form)
        {
            return urlSetup(getContainer(), form.getReturnActionURL(getContainer().getStartURL(getUser())), true, false);
        }
    }

    enum SetupField { path, email }

    private static URI validatePath(String path, BindException errors)
    {
        if (path == null)
        {
            return null;
        }

        if (path.startsWith("\\\\"))
        {
            errors.reject(ERROR_MSG, "UNC paths are not supported for pipeline roots. Consider creating a Network Drive configuration in the Admin Console under Site Settings.");
            return null;
        }

        if (path.startsWith("s3://") || path.startsWith("/@cloud"))
            return URI.create(path);

        File fileRoot = new File(path);

        // Try to make sure the path is the right case. getCanonicalPath() resolves symbolic
        // links on Unix so don't replace the path if it's pointing at a different location.
        fileRoot = FileUtil.getAbsoluteCaseSensitiveFile(fileRoot);

        if (!NetworkDrive.exists(fileRoot))
        {
            errors.reject(ERROR_MSG, "The directory '" + fileRoot + "' does not exist.");
            return null;
        }
        else if (!fileRoot.isDirectory())
        {
            errors.reject(ERROR_MSG, "The file '" + fileRoot + "' is not a directory.");
            return null;
        }

        URI result = fileRoot.toURI();
        if (URIUtil.resolve(result, result, "test") == null)
        {
            errors.reject(ERROR_MSG, "The pipeline root '" + fileRoot + "' is not valid.");
            return null;
        }
        return result;
    }

    public static boolean savePipelineSetup(ViewContext context, SetupForm form, BindException errors) throws Exception
    {
        if (form.shouldRevertOverride())
        {
            PipelineService.get().setPipelineRoot(context.getUser(), context.getContainer(), PipelineService.PRIMARY_ROOT, false);
            return true;
        }

        String path = form.hasSiteDefaultPipelineRoot() ? null : form.getPath();
        URI root = validatePath(path, errors);
        String supplementalPath = form.hasSiteDefaultPipelineRoot() || form.getSupplementalPath() == null ? null : form.getSupplementalPath();
        URI supplementalRoot = supplementalPath == null ? null : validatePath(supplementalPath, errors);
        if (root == null && supplementalRoot != null)
        {
            errors.addError(new LabKeyError("Cannot set a supplemental root without also setting a primary root"));
        }
        if (errors.hasErrors())
        {
            return false;
        }

        if (supplementalRoot == null)
        {
            PipelineService.get().setPipelineRoot(context.getUser(), context.getContainer(), PipelineService.PRIMARY_ROOT, form.isSearchable(), root);
        }
        else
        {
            PipelineService.get().setPipelineRoot(context.getUser(), context.getContainer(), PipelineService.PRIMARY_ROOT, form.isSearchable(), root, supplementalRoot);
        }
        return true;
    }

    abstract public class AbstractSetupAction<FORM extends ReturnUrlForm> extends FormViewAction<FORM>
    {
        abstract protected SetupField getFormField();

        protected void error(BindException errors, String message)
        {
            errors.rejectValue(getFormField().toString(), ERROR_MSG, message);
        }

        @Override
        public ModelAndView getView(FORM form, boolean reshow, BindException errors)
        {
            setHelpTopic(getHelpTopic("pipelineSetup"));

            if (getViewContext().getRequest().getParameter(Params.overrideRoot.toString()) == null && !reshow)
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
                    html.append("'.<br>You can either ");
                    html.append(PageFlowUtil.textLink("override", urlOverride));
                    html.append(" the inherited settings in this folder,<br>or ");
                    html.append(PageFlowUtil.textLink("modify the setting for all folders", urlEditParent));
                    html.append(" by setting the value in the folder '");
                    html.append(PageFlowUtil.filter(root.getContainer().getPath()));
                    html.append("'.</p>");
                    return new HtmlView(html.toString());
                }
            }

            Container c = getContainer();
            VBox left = new VBox();
            HBox result = new HBox(left);

            if (!c.isRoot())
            {
                SetupForm bean = SetupForm.init(c);
                bean.setReturnUrl(form.getReturnUrl());
                bean.setErrors(errors);

                PipeRoot pipeRoot = SetupForm.getPipelineRoot(c);

                if (pipeRoot != null)
                {
                    for (String errorMessage : pipeRoot.validate())
                    {
                        errors.addError(new LabKeyError(errorMessage));
                    }

                    if (!errors.hasErrors() && getViewContext().getRequest().getParameter(PipelineController.Params.rootset.toString()) != null)
                    {
                        bean.setConfirmMessage("The pipeline root was set to " + pipeRoot.toString());
                    }
                }

                VBox main = new VBox(PipelineService.get().getSetupView(bean));

                if (pipeRoot != null && !errors.hasErrors())
                {
                    PermissionView permissionView = new PermissionView(pipeRoot);
                    permissionView.setTitle("File Permissions");
                    permissionView.setFrame(WebPartView.FrameType.PORTAL);
                    result.addView(permissionView);
                }

                main.setTitle("Data Processing Pipeline Setup");
                main.setFrame(WebPartView.FrameType.PORTAL);

                if (!errors.hasErrors())
                {
                    Set<Module> activeModules = c.getActiveModules();
                    for (PipelineProvider provider : PipelineService.get().getPipelineProviders())
                    {
                        if (activeModules.contains(provider.getOwningModule()))
                        {
                            HttpView part = provider.getSetupWebPart(c);
                            if (part != null)
                                main.addView(part);
                        }
                    }
                }

                left.addView(main);
            }

            JspView<FORM> emailView = new JspView<>("/org/labkey/pipeline/emailNotificationSetup.jsp", form);
            emailView.setFrame(WebPartView.FrameType.PORTAL);
            emailView.setTitle("Email Notification");
            left.addView(emailView);
            return result;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            if (getContainer().isRoot())
            {
                urlProvider(AdminUrls.class).addAdminNavTrail(root, "Data Processing Pipeline Setup", getClass(), getContainer());
            }
            else
            {
                root.addChild("Data Pipeline", new ActionURL(BeginAction.class, getContainer()));
                root.addChild("Data Processing Pipeline Setup");
            }
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class BrowseAction extends SimpleViewAction<PathForm>
    {
        public BrowseAction()
        {
        }

        @Override
        public ModelAndView getView(PathForm pathForm, BindException errors)
        {
            Path path = null;
            if (pathForm.getPath() != null)
            try { path = Path.parse(pathForm.getPath()); } catch (Exception x) { }
            BrowseWebPart wp = new BrowseWebPart(path);
            wp.getModelBean().setAutoResize(true);
            wp.setFrame(WebPartView.FrameType.NONE);
            getPageConfig().setHelpTopic(new HelpTopic("fileSharing"));
            return wp;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Files");
        }
    }

    public static class BrowseWebPart extends FilesWebPart
    {
        public BrowseWebPart()
        {
            this(Path.rootPath);
        }

        public BrowseWebPart(Path startPath)
        {
            super(getContextContainer(), null, null);

            FilesForm bean = getModelBean();
            ViewContext context = getViewContext();

            bean.setDirectory(startPath);
            bean.setFolderTreeCollapsed(false);
            bean.setShowDetails(true);
            bean.setAutoResize(false);
            bean.setStatePrefix(context.getContainer().getId() + "#fileContent");

            bean.setExpandFileUpload(false);
            bean.setDisableGeneralAdminSettings(true);

            // pipeline is always enabled
            bean.setEnabled(true);

            PipeRoot root = PipelineService.get().findPipelineRoot(context.getContainer());
            if (root != null)
            {
                bean.setRootPath(root.getWebdavURL());
                bean.setRootDirectory(root.getRootNioPath());
            }

            setTitle("Pipeline Files");
            setTitleHref(new ActionURL(BrowseAction.class, HttpView.getContextContainer()));
        }

        @Override
        protected SecurableResource getSecurableResource()
        {
            PipeRoot pipeRoot = PipelineService.get().findPipelineRoot(getViewContext().getContainer());
            if (pipeRoot != null && !pipeRoot.isFileRoot())
                return pipeRoot;
            return super.getSecurableResource();
        }

        @Override
        protected boolean canDisplayPipelineActions()
        {
            return true;
        }
    }

    public static class PipelineActionsForm extends PathForm
    {
        boolean _allActions;

        public boolean isAllActions()
        {
            return _allActions;
        }

        public void setAllActions(boolean allActions)
        {
            _allActions = allActions;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ActionsAction extends ReadOnlyApiAction<PipelineActionsForm>
    {
        @Override
        public ApiResponse execute(PipelineActionsForm form, BindException errors)
        {
            Container c = getContainer();

            PipeRootImpl pr = PipelineServiceImpl.get().findPipelineRoot(c);
            if (pr == null || !pr.isValid())
            {
                throw new NotFoundException("Pipeline root not set or does not exist on disk");
            }

            String relativePath = form.getPath();
            if (null == relativePath || "./".equals(relativePath))
                relativePath = "";
            if (relativePath.startsWith("/"))
                relativePath = relativePath.substring(1);

            if (pr.isCloudRoot() && null != pr.getCloudStoreName())
            {
                if (relativePath.startsWith(pr.getCloudStoreName()))
                    relativePath = relativePath.replace(pr.getCloudStoreName(), "");
                if (relativePath.startsWith("//"))
                    relativePath = relativePath.substring(1);
            }

            java.nio.file.Path fileCurrent = pr.resolveToNioPath(relativePath);
            // S3-backed storage may not have an entry for the root if there are no children, see issue 38377
            if (!("".equals(relativePath) && pr.isCloudRoot()) && (fileCurrent == null || !Files.exists(fileCurrent)))
            {
                errors.reject(ERROR_MSG, "File not found: " + form.getPath());
            }
            else
            {
                ActionURL browseURL = new ActionURL(BrowseAction.class, c);
                String browseParam = pr.relativePath(fileCurrent);
                if ("".equals(browseParam))
                {
                    browseParam = "./";
                }
                browseURL.replaceParameter("path", browseParam);

                PipelineDirectoryImpl entry = new PipelineDirectoryImpl(pr, relativePath, browseURL);
                List<PipelineProvider> providers = PipelineService.get().getPipelineProviders();
                Set<Module> activeModules = c.getActiveModules(getUser());
                for (PipelineProvider provider : providers)
                {
                    boolean showAllActions = form.isAllActions();
                    if (provider.isShowActionsIfModuleInactive() || activeModules.contains(provider.getOwningModule()))
                    {
                        if (!pr.isCloudRoot() || provider.supportsCloud())      // Don't include non-cloud providers if this is cloud pipeline root
                            provider.updateFileProperties(getViewContext(), pr, entry, showAllActions);
                    }
                }

                // keep actions in consistent order for display
                entry.orderActions();
                JSONArray actions = new JSONArray();
                for (PipelineAction action : entry.getActions())
                {
                    actions.put(action.toJSON());
                }
                ApiSimpleResponse resp = new ApiSimpleResponse();
                resp.put("success", true);
                resp.put("actions", actions);
                return resp;
            }
            return new ApiSimpleResponse("success", false);
        }
    }

    public static class SaveOptionsForm implements CustomApiForm
    {
        private Map<String,Object> _props;

        @Override
        public void bindProperties(Map<String, Object> props)
        {
            _props = props;
        }

        public Map<String,Object> getProps()
        {
            return _props;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class UpdatePipelineActionConfigAction extends MutatingApiAction<SaveOptionsForm>
    {
        @Override
        public ApiResponse execute(SaveOptionsForm form, BindException errors)
        {
            FileContentService svc = FileContentService.get();
            FilesAdminOptions options = svc.getAdminOptions(getContainer());

            Map<String, Object> props = form.getProps();
            options.updateFromJSON(props);
            svc.setAdminOptions(getContainer(), options);

            return new ApiSimpleResponse("success", true);
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class GetPipelineActionConfigAction extends ReadOnlyApiAction
    {
        @Override
        public ApiResponse execute(Object form, BindException errors) throws Exception
        {
            Container container = getContainer();

            PipeRoot pr = PipelineService.get().findPipelineRoot(container);
            if (pr == null || !pr.isValid())
            {
                throw new NotFoundException("Pipeline root not set or does not exist on disk");
            }
            FileContentService svc = FileContentService.get();
            FilesAdminOptions options = svc.getAdminOptions(container);

            for (PipelineProvider provider : PipelineService.get().getPipelineProviders())
            {
                for (PipelineActionConfig config : provider.getDefaultActionConfig(container))
                    options.addDefaultPipelineConfig(config);
            }
            ApiSimpleResponse resp = new ApiSimpleResponse();
            resp.put("config", options.toJSON());
            resp.putBeanList("fileProperties", getFileProperties(getContainer(), options.getFileConfig()));
            resp.put("canSeeFilePaths", SecurityManager.canSeeFilePaths(getContainer(), getUser()));
            resp.put("success", true);

            return resp;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class GetPipelineFilePropertiesAction extends ReadOnlyApiAction
    {
        @Override
        public ApiResponse execute(Object form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();
            FilesAdminOptions.fileConfig config = FilesAdminOptions.fileConfig.valueOf(getViewContext().getActionURL().getParameter("fileConfig"));

            resp.putBeanList("fileProperties", getFileProperties(getContainer(), config));
            resp.put("configOption", config.name());
            resp.put("success", true);

            return resp;
        }
    }

    private List<GWTPropertyDescriptor> getFileProperties(Container container, FilesAdminOptions.fileConfig config)
    {
        FileContentService svc = FileContentService.get();
        List<GWTPropertyDescriptor> properties = new ArrayList<>();

        switch (config)
        {
            case useCustom:
                String uri = svc.getDomainURI(container, config);
                GWTDomain domain = DomainUtil.getDomainDescriptor(getUser(), uri, container);

                if (domain != null)
                {
                    for (Object o : domain.getFields())
                    {
                        if (o instanceof GWTPropertyDescriptor)
                            properties.add((GWTPropertyDescriptor)o);
                    }
                }
                break;
            case useDefault:
                break;
            case useParent:
                while (container != container.getProject())
                {
                    container = container.getParent();
                    FilesAdminOptions options = svc.getAdminOptions(container);

                    if (options.getFileConfig() != FilesAdminOptions.fileConfig.useParent)
                        return getFileProperties(container, options.getFileConfig());
                }
                FilesAdminOptions.fileConfig cfg = svc.getAdminOptions(container).getFileConfig();
                cfg = cfg != FilesAdminOptions.fileConfig.useParent ? cfg : FilesAdminOptions.fileConfig.useDefault;
                return getFileProperties(container, cfg);
        }

        return properties;
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public class UpdateRootPermissionsAction extends FormHandlerAction<PermissionForm>
    {
        @Override
        public void validateCommand(PermissionForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(PermissionForm form, BindException errors) throws Exception
        {
            Container c = getContainer();
            PipeRoot pipeRoot = getPipelineRoot(c);
            assert null == pipeRoot || pipeRoot.getContainer().getId().equals(c.getId());

            if (null != pipeRoot)
            {
                MutableSecurityPolicy policy = new MutableSecurityPolicy(pipeRoot);
                if (form.isEnable())
                {
                    List<Group> groupsAll = SecurityManager.getGroups(c.getProject(), true);
                    Map<Integer,Group> map = new HashMap<>(groupsAll.size() * 2);
                    for (Group g : groupsAll)
                        map.put(g.getUserId(),g);

                    int count = form.getSize();
                    for (int i=0 ; i<count ; i++)
                    {
                        Integer groupId = form.getGroups().get(i);
                        Group g = map.get(groupId);
                        if (null == g)
                            continue;
                        String roleName = form.getPerms().get(i);
                        if (roleName == null)
                            continue;
                        Role role = RoleManager.getRole(roleName);
                        if (null == role)
                            continue;
                        policy.addRoleAssignment(g, role);
                    }
                }

                // UNDONE: move setACL() to PipelineManager
                SecurityPolicyManager.savePolicy(policy);
                ContainerManager.ContainerPropertyChangeEvent evt = new ContainerManager.ContainerPropertyChangeEvent(
                        c, ContainerManager.Property.PipelineRoot, pipeRoot, pipeRoot);
                ContainerManager.firePropertyChangeEvent(evt);
            }

            return true;
        }

        @Override
        public ActionURL getSuccessURL(PermissionForm permissionForm)
        {
            return permissionForm.getReturnActionURL(new ActionURL(SetupAction.class, getContainer()));
        }

    }

    public static class PermissionForm extends ReturnUrlForm
    {
        private List<Integer> groups = new FormArrayList<Integer>(Integer.class)
        {
            @Override
            protected Integer newInstance()
            {
                return Integer.valueOf(Integer.MIN_VALUE);
            }
        };

        private List<String> perms = new FormArrayList<>(String.class);

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

        public List<Integer> getGroups()
        {
            return groups;
        }

        public void setGroups(List<Integer> groups)
        {
            this.groups = groups;
        }

        public List<String> getPerms()
        {
            return perms;
        }

        public void setPerms(List<String> perms)
        {
            this.perms = perms;
        }
    }

    public class PermissionView extends JspView<PipeRoot>
    {
        PermissionView(PipeRoot pipeRoot)
        {
            super("/org/labkey/pipeline/permission.jsp", pipeRoot);
        }
    }

    /////////////////////////////////////////////////////////////////////////
    //  Email notifications

    @RequiresPermission(AdminPermission.class)
    public class UpdateEmailNotificationAction extends AbstractSetupAction<EmailNotificationForm>
    {
        @Override
        protected SetupField getFormField()
        {
            // Note: This is never used because the <labkey:errors /> tag is used,
            //       and it does not allow for field errors.
            return SetupField.email;
        }

        @Override
        public void validateCommand(EmailNotificationForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(EmailNotificationForm form, BindException errors)
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
            pref.setNotifyUsersOnSuccess(getValidUserList(form.getNotifyUsersOnSuccess(), errors), c);
            pref.setSuccessNotificationInterval(
                    form.getSuccessNotifyInterval(),
                    form.getSuccessNotifyStart(),
                    c);
            pref.setNotifyOwnerOnError(form.getNotifyOwnerOnError(), c);
            pref.setNotifyUsersOnError(getValidUserList(form.getNotifyUsersOnError(), errors), c);
            pref.setFailureNotificationInterval(
                    form.getFailureNotifyInterval(),
                    form.getFailureNotifyStart(),
                    c);

            return errors.getGlobalErrorCount() == 0;
        }

        @Override
        public ActionURL getSuccessURL(EmailNotificationForm form)
        {
            return form.getReturnActionURL(urlSetup(getContainer()));
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

        private String getValidUserList(String emailString, BindException errors)
        {
            String[] rawEmails = StringUtils.trimToEmpty(emailString).split("\n");
            List<String> invalidEmails = new ArrayList<>();
            List<ValidEmail> emails = SecurityManager.normalizeEmails(rawEmails, invalidEmails);
            StringBuilder builder = new StringBuilder();

            for (ValidEmail email : emails)
            {
                User u = UserManager.getUser(email);
                if (u != null)
                {
                    builder.append(u.getAutocompleteName(getContainer(), getUser())).append(';');
                }
                else if (getContainer().hasPermission(getUser(), UserManagementPermission.class))
                {
                    try
                    {
                        SecurityManager.addUser(getViewContext(), email, true, null);
                        u = UserManager.getUser(email);
                        if (u != null)
                            builder.append(email).append(';');
                        else
                            errors.reject(ERROR_MSG, "Unable to create user for email: " + email.toString());
                    }
                    catch (Exception e)
                    {
                        errors.reject(ERROR_MSG, "Unable to create user for email: " + email.toString());
                    }
                }
                else
                    errors.reject(ERROR_MSG, "Unable to find user or create user for email: " + email.toString());
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

    public static class EmailNotificationForm extends ReturnUrlForm
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

    @RequiresPermission(AdminPermission.class)
    public class ResetEmailNotificationAction extends FormHandlerAction
    {
        @Override
        public void validateCommand(Object target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(Object o, BindException errors)
        {
            PipelineEmailPreferences.get().deleteAll(getContainer());
            return true;
        }

        @Override
        public URLHelper getSuccessURL(Object o)
        {
            return new ActionURL(SetupAction.class, getContainer());
        }
    }

    @RequiresPermission(DeletePermission.class)
    public class CancelJobAction extends FormHandlerAction<StatusController.RowIdForm>
    {
        private ActionURL _successURL;

        @Override
        public void validateCommand(StatusController.RowIdForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(StatusController.RowIdForm form, BindException errors) throws Exception
        {
            try
            {
                PipelineStatusManager.cancelStatus(getViewBackgroundInfo(), Collections.singleton(form.getRowId()));
                _successURL = form.getReturnActionURL(StatusController.urlShowList(getContainer(), true));
            }
            catch (PipelineProvider.HandlerException e)
            {
                _successURL = StatusController.urlDetails(getContainer(), form.getRowId(), e.getMessage());
            }
            return true;
        }

        @Override
        public URLHelper getSuccessURL(StatusController.RowIdForm rowIdForm)
        {
            return _successURL;
        }
    }

    protected PipeRoot getPipelineRoot(Container c)
    {
        PipeRoot p = PipelineService.get().findPipelineRoot(c);
        if (p != null && p.getContainer() != null && p.getContainer().getId().equals(c.getId()))
            return p;
        return null;
    }

/////////////////////////////////////////////////////////////////////////////
//  File download support

    @RequiresPermission(ReadPermission.class)
    public class DownloadAction extends SimpleViewAction<PathForm>
    {
        @Override
        public ModelAndView getView(PathForm form, BindException errors) throws Exception
        {
            PipeRoot pipeRoot = getPipelineRoot(getContainer());
            if (null == pipeRoot || null == StringUtils.trimToNull(form.getPath()))
                throw new NotFoundException();

            // check pipeline ACL
            if (!SecurityPolicyManager.getPolicy(pipeRoot).hasPermission(getUser(), ReadPermission.class))
                throw new UnauthorizedException();

            File file = pipeRoot.resolvePath(form.getPath());
            if (!file.exists() || !file.isFile())
                throw new NotFoundException();

            PageFlowUtil.streamFile(getViewContext().getResponse(), file, true);
            return null;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
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

    @RequiresPermission(ReadPermission.class)
    public class GetPipelineContainerAction extends ReadOnlyApiAction
    {
        @Override
        public ApiResponse execute(Object form, BindException errors)
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();
            PipeRoot root = PipelineService.get().findPipelineRoot(getContainer());
            resp.put("containerPath", null != root ? root.getContainer().getPath() : null);
            resp.put("webDavURL", null != root ? FileUtil.encodeForURL(root.getWebdavURL()) : null);
            return resp;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class ImportFolderFromPipelineAction extends SimpleRedirectAction<PipelinePathForm>
    {
        @Override
        public ActionURL getRedirectURL(PipelinePathForm form)
        {
            Container c = getContainer();
            File folderFile = form.getValidatedSingleFile(c);

            ActionURL url = new ActionURL(StartFolderImportAction.class, getContainer());
            url.addParameter("filePath", folderFile.getAbsolutePath());
            url.addParameter("validateQueries", true);
            url.addParameter("createSharedDatasets", true);
            return url;
        }

        @Override
        protected ModelAndView getErrorView(Exception e, BindException errors) throws Exception
        {
            try
            {
                throw e;
            }
            catch (ImportException sie)
            {
                errors.reject("folderImport", e.getMessage());
                return new SimpleErrorView(errors);
            }
        }
    }

    /**
     * Landing page for ImportFolderFromPipelineAction and  StudyController.ImportStudyFromPipelineAction
     */
    @RequiresPermission(AdminPermission.class)
    public class StartFolderImportAction extends FormViewAction<StartFolderImportForm>
    {
        private String _navTrail = "Import Folder";
        private File _archiveFile;
        private List<Container> _importContainers = new ArrayList<>();

        @Override
        public void validateCommand(StartFolderImportForm form, Errors errors)
        {
            PipeRoot currentPipelineRoot = PipelineService.get().findPipelineRoot(getContainer());
            if (!PipelineService.get().hasValidPipelineRoot(getContainer()) || null == currentPipelineRoot)
            {
                errors.reject(ERROR_MSG, "Pipeline root not found.");
            }
            else if (form.getFilePath() == null)
            {
                errors.reject(ERROR_MSG, "No filePath provided.");
            }
            else
            {
                _archiveFile = PipelineManager.validateFolderImportFilePath(form.getFilePath(), currentPipelineRoot, errors);

                // Be sure that the set of folder to apply the import to match the setting to enable/disable them
                if (form.isApplyToMultipleFolders() && (form.getFolderRowIds() == null || form.getFolderRowIds().size() == 0))
                {
                    errors.reject(ERROR_MSG, "At least one folder must be selected when 'apply to multiple folders' is enabled.");
                }
                else if (!form.isApplyToMultipleFolders() && form.getFolderRowIds() != null)
                {
                    errors.reject(ERROR_MSG, "Folder RowIds provided when 'apply to multiple folders' not enabled.");
                }

                // Be sure that the user has admin permissions to all selected folders and that all selected folders exist
                if (form.getFolderRowIds() != null)
                {
                    for (Integer rowId : form.getFolderRowIds())
                    {
                        Container selectedContainer = ContainerManager.getForRowId(rowId);
                        if (selectedContainer == null)
                            errors.reject(ERROR_MSG, "Folder does not exist for selected RowId: " + rowId + ".");
                        else if (!selectedContainer.hasPermission(getUser(), AdminPermission.class))
                            errors.reject(ERROR_MSG, "You do not have the required permissions for the selected folder: " + selectedContainer.getTitle() + ".");
                        else
                            _importContainers.add(selectedContainer);
                    }
                }
                else
                {
                    // default to importing the archive to the current container
                    _importContainers.add(getContainer());
                }

                // Be sure that each import container has a valid pipeline root
                for (Container container : _importContainers)
                {
                    PipeRoot pipelineRoot = PipelineService.get().findPipelineRoot(container);
                    if (!PipelineService.get().hasValidPipelineRoot(container) || null == pipelineRoot)
                    {
                        errors.reject(ERROR_MSG, "Pipeline root not found for selected container: " + container.getTitle() + ".");
                    }
                }

                // Be sure that the provided data types to import match the setting to enable/disable them
                if (form.isSpecificImportOptions() && (form.getDataTypes() == null || form.getDataTypes().size() == 0))
                {
                    errors.reject(ERROR_MSG, "At least one folder data type must be selected when 'select specific objects to import' is enabled.");
                }
                else if (!form.isSpecificImportOptions() && form.getDataTypes() != null)
                {
                    errors.reject(ERROR_MSG, "Folder data types provided when 'select specific objects to import' not enabled.");
                }
            }
        }

        @Override
        public ModelAndView getView(StartFolderImportForm form, boolean reshow, BindException errors)
        {
            if (form.isAsStudy())
                _navTrail = "Import Study";
            _navTrail += form.isFromTemplateSourceFolder() ? " from Existing Folder" : form.isFromZip() ? " from Zip Archive" : " from Pipeline";

            return new JspView<>("/org/labkey/pipeline/startPipelineImport.jsp", form, errors);
        }

        @Override
        public boolean handlePost(StartFolderImportForm form, BindException errors) throws Exception
        {
            User user = getUser();
            boolean success = true;
            Map<Container, File> containerArchiveXmlMap = new HashMap<>();

            if (_archiveFile.exists())
            {
                // iterate over the selected containers, or just the current container in the default case, and unzip the archive if necessary
                for (Container container : _importContainers)
                {
                    File archiveXml = PipelineManager.getArchiveXmlFile(container, _archiveFile, form.isAsStudy() ? "study.xml" : "folder.xml", errors);
                    if (errors.hasErrors())
                        return false;

                    containerArchiveXmlMap.put(container, archiveXml);
                }

                // create a new pipeline import job for applying the imported archive to each selected container
                for (Container container : _importContainers)
                {
                    ImportOptions options = new ImportOptions(container.getId(), getUser().getUserId());
                    options.setSkipQueryValidation(!form.isValidateQueries());
                    options.setCreateSharedDatasets(form.isCreateSharedDatasets());
                    options.setFailForUndefinedVisits(form.isFailForUndefinedVisits());
                    options.setDataTypes(form.getDataTypes());
                    options.setIncludeSubfolders(!form.isApplyToMultipleFolders());

                    ComplianceService complianceService = ComplianceService.get();
                    if (null != complianceService)
                        options.setActivity(complianceService.getCurrentActivity(getViewContext()));

                    success = success && createImportPipelineJob(container, user, options, containerArchiveXmlMap.get(container), form.isAsStudy(), errors);
                }
            }

            // the original archive file would have been placed in the current container unzip dir, clean that up
            // if the current container was not one of the target containers
            if (!containerArchiveXmlMap.containsKey(getContainer()))
            {
                PipeRoot pipelineRoot = PipelineService.get().findPipelineRoot(getContainer());
                if (pipelineRoot != null)
                    pipelineRoot.getImportDirectoryPathAndEnsureDeleted();
            }

            return success;
        }

        private boolean createImportPipelineJob(Container container, User user, ImportOptions options, File archiveXml, boolean asStudy, BindException errors)
        {
            PipeRoot pipelineRoot = PipelineService.get().findPipelineRoot(container);
            ActionURL url = getViewContext().getActionURL();

            if (asStudy)
                return StudyService.get().runStudyImportJob(container, user, url, archiveXml, _archiveFile.getName(), errors, pipelineRoot, options);
            else
                return PipelineService.get().runFolderImportJob(container, user, url, archiveXml, _archiveFile.getName(), errors, pipelineRoot, options);
        }

        @Override
        public URLHelper getSuccessURL(StartFolderImportForm form)
        {
            // default case, go to the pipeline jobs page for the current container
            // otherwise go to the pipeline jobs page for the project and show all subfolders
            Container c = getContainer();
            if (_importContainers.size() == 1 && _importContainers.get(0).equals(c))
            {
                return urlProvider(PipelineStatusUrls.class).urlBegin(c);
            }
            else
            {
                ActionURL url = urlProvider(PipelineStatusUrls.class).urlBegin(c.getProject());
                url.addParameter("StatusFiles.containerFilterName", ContainerFilter.Type.CurrentAndSubfolders.name());
                return url;
            }
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild(_navTrail);
        }
    }

    public static class StartFolderImportForm
    {
        private boolean _fromZip;
        private boolean _fromTemplateSourceFolder;
        private boolean _asStudy;
        private String _filePath;
        private boolean _validateQueries;
        private boolean _createSharedDatasets;
        private boolean _specificImportOptions;
        private boolean _applyToMultipleFolders;
        private boolean _failForUndefinedVisits;
        private Set<String> _dataTypes;
        private List<Integer> _folderRowIds;

        public String getFilePath()
        {
            return _filePath;
        }

        public void setFilePath(String filePath)
        {
            _filePath = filePath;
        }

        public boolean isValidateQueries()
        {
            return _validateQueries;
        }

        public void setValidateQueries(boolean validateQueries)
        {
            _validateQueries = validateQueries;
        }

        public boolean isCreateSharedDatasets()
        {
            return _createSharedDatasets;
        }

        public void setCreateSharedDatasets(boolean createSharedDatasets)
        {
            _createSharedDatasets = createSharedDatasets;
        }

        public boolean isSpecificImportOptions()
        {
            return _specificImportOptions;
        }

        public void setSpecificImportOptions(boolean specificImportOptions)
        {
            _specificImportOptions = specificImportOptions;
        }

        public boolean isApplyToMultipleFolders()
        {
            return _applyToMultipleFolders;
        }

        public void setApplyToMultipleFolders(boolean applyToMultipleFolders)
        {
            _applyToMultipleFolders = applyToMultipleFolders;
        }

        public boolean isFailForUndefinedVisits()
        {
            return _failForUndefinedVisits;
        }

        public void setFailForUndefinedVisits(boolean failForUndefinedVisits)
        {
            _failForUndefinedVisits = failForUndefinedVisits;
        }

        public Set<String> getDataTypes()
        {
            return _dataTypes;
        }

        public void setDataTypes(Set<String> dataTypes)
        {
            _dataTypes = dataTypes;
        }

        public List<Integer> getFolderRowIds()
        {
            return _folderRowIds;
        }

        public void setFolderRowIds(List<Integer> folderRowIds)
        {
            _folderRowIds = folderRowIds;
        }

        public boolean isAsStudy()
        {
            return _asStudy;
        }

        public void setAsStudy(boolean asStudy)
        {
            _asStudy = asStudy;
        }

        public boolean isFromZip()
        {
            return _fromZip;
        }

        public void setFromZip(boolean fromZip)
        {
            _fromZip = fromZip;
        }

        public boolean isFromTemplateSourceFolder()
        {
            return _fromTemplateSourceFolder;
        }

        public void setFromTemplateSourceFolder(boolean fromTemplateSourceFolder)
        {
            _fromTemplateSourceFolder = fromTemplateSourceFolder;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class CreatePipelineTriggerAction extends SimpleViewAction<PipelineTriggerForm>
    {
        String _title = "Create Pipeline Trigger";

        @Override
        public ModelAndView getView(PipelineTriggerForm form, BindException errors) throws Exception
        {
            if (form.getRowId() != null)
            {
                _title = "Update Pipeline Trigger";
                Integer rowId = form.getRowId();
                String returnURL = form.getReturnUrl();
                SimpleFilter filter = SimpleFilter.createContainerFilter(getContainer());
                filter.addCondition(FieldKey.fromParts("RowId"), form.getRowId());
                PipelineTriggerForm savedForm = new TableSelector(PipelineSchema.getInstance().getTableInfoTriggerConfigurations(), filter, null).getObject(PipelineTriggerForm.class);

                if (savedForm != null)
                {
                    form = savedForm;
                    form.setRowId(rowId);
                    form.setReturnUrl(returnURL);
                }
                else
                {
                    errors.reject(ERROR_MSG, "Form with id " + rowId + " could not be found");
                }
            }

            if (form.getReturnUrl() == null)
                form.setReturnUrl(getContainer().getStartURL(getUser()).toString());

            return new JspView<>("/org/labkey/pipeline/createPipelineTrigger.jsp", form, errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("fileWatcher");
            root.addChild("Pipeline Trigger Configurations", urlProvider(QueryUrls.class).urlExecuteQuery(getContainer(), "pipeline", "TriggerConfigurations"));
            root.addChild(_title);
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class SavePipelineTriggerAction extends MutatingApiAction<PipelineTriggerForm>
    {
        @Override
        public void validateForm(PipelineTriggerForm form, Errors errors)
        {
            if (StringUtils.isBlank(form.getLocation()))
                form.setLocation("./");

            PipelineManager.validateTriggerConfiguration(form, getContainer(), getUser(), errors);
        }

        @Override
        public Object execute(PipelineTriggerForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            URLHelper url;

            PipelineService.get().saveTriggerConfig(getContainer(), getUser(), form);
            if (form.getReturnActionURL() != null)
            {
                url = form.getReturnActionURL();
            }
            else
            {
                url = getContainer().getStartURL(getUser());
            }

            response.put("success", true);
            response.put(ActionURL.Param.returnUrl.name(), url.toString());

            return response;
        }
    }

    public static class PipelineTriggerForm extends TriggerConfiguration implements CustomApiForm
    {
        private final ReturnUrlForm urlForm = new ReturnUrlForm();
        private String _pipelineTask;

        @Override
        public void bindProperties(Map<String, Object> props)
        {
            MutablePropertyValues params = new MutablePropertyValues(props);
            BaseViewAction.defaultBindParameters(this, "form", params);

            Object assayProvider = props.get("assay provider");
            if (assayProvider != null)
                setAssayProvider(String.valueOf(assayProvider));

            Object customParamKey = props.get("customParamKey");
            if (customParamKey instanceof JSONArray)
            {
                for (Object o : ((JSONArray)customParamKey).toArray())
                    _customParamKey.add(String.valueOf(o));
            }
            Object customParamValue = props.get("customParamValue");
            if (customParamValue instanceof JSONArray)
            {
                for (Object o : ((JSONArray)customParamValue).toArray())
                    _customParamValue.add(String.valueOf(o));
            }
        }

        @Nullable
        public String getReturnUrl()
        {
            return urlForm.getReturnUrl();
        }

        public void setReturnUrl(String returnUrl)
        {
            urlForm.setReturnUrl(returnUrl);
        }

        public ActionURL getReturnActionURL()
        {
            return urlForm.getReturnActionURL();
        }

        public String getPipelineTask()
        {
            return _pipelineTask;
        }

        public void setPipelineTask(String pipelineTask)
        {
            _pipelineTask = pipelineTask;
        }
    }

/////////////////////////////////////////////////////////////////////////////
//  Public URL interface to this controller

    public static void registerAdminConsoleLinks()
    {
        ActionURL url = urlProvider(PipelineUrls.class).urlSetup(ContainerManager.getRoot());
        AdminConsole.addLink(AdminConsole.SettingsLinkType.Management, "pipeline email notification", url, AdminOperationsPermission.class);
    }

    public static class PipelineUrlsImp implements PipelineUrls
    {
        @Override
        public ActionURL urlBrowse(Container container)
        {
            return urlBrowse(container, null, null);
        }

        @Override
        public ActionURL urlBrowse(Container container, @Nullable URLHelper returnUrl)
        {
            return urlBrowse(container, returnUrl, null);
        }

        @Override
        public ActionURL urlBrowse(Container container, @Nullable URLHelper returnUrl, @Nullable String path)
        {
            ActionURL url = new ActionURL(BrowseAction.class, container);

            if (null != returnUrl)
                url.addReturnURL(returnUrl);

            if (path != null)
                url.addParameter(Params.path, path);

            return url;
        }

        @Override
        @Nullable
        public ActionURL urlBrowse(@Nullable PipelineStatusFile sf, @Nullable URLHelper returnUrl)
        {
            if (sf == null)
                return null;

            File logFile = new File(sf.getFilePath());
            File dir = logFile.getParentFile();
            PipeRoot pipeRoot = PipelineService.get().findPipelineRoot(sf.lookupContainer());
            if (NetworkDrive.exists(dir) && pipeRoot != null && pipeRoot.isUnderRoot(dir))
            {
                String relativePath = pipeRoot.relativePath(dir);

                // Issue 14693: changing the pipeline root or symlinks can result in bad paths.  if we cant locate the file, just dont display the browse button.
                if (relativePath != null)
                {
                    relativePath = relativePath.replace("\\", "/");
                    if (relativePath.equals("."))
                    {
                        relativePath = "/";
                    }
                    return urlBrowse(sf.lookupContainer(), returnUrl, relativePath);
                }
            }

            return null;
        }

        @Override
        public ActionURL urlSetup(Container container)
        {
            return PipelineController.urlSetup(container);
        }

        @Override
        public ActionURL urlBegin(Container container)
        {
            return PipelineController.urlBegin(container);
        }

        @Override
        public ActionURL urlActions(Container container)
        {
            return new ActionURL(ActionsAction.class, container);
        }

        @Override
        public ActionURL urlStartFolderImport(Container container, @NotNull File archiveFile, boolean asStudy, @Nullable ImportOptions options, boolean fromTemplateSourceFolder)
        {
            ActionURL url = new ActionURL(StartFolderImportAction.class, container);
            if (asStudy)
                url.addParameter("asStudy", true);

            return addStartImportParameters(url, archiveFile, options, fromTemplateSourceFolder);
        }

        @Override
        public ActionURL urlCreatePipelineTrigger(Container container, String pipelineId, @Nullable ActionURL returnUrl)
        {
            ActionURL url = new ActionURL(CreatePipelineTriggerAction.class, container);

            if (pipelineId != null && !pipelineId.isEmpty())
                url.addParameter("pipelineTask", pipelineId);

            if (returnUrl != null)
                url.addReturnURL(returnUrl);

            return url;
        }

        private ActionURL addStartImportParameters(ActionURL url, @NotNull File file, @Nullable ImportOptions options, boolean fromTemplateSourceFolder)
        {
            url.addParameter("filePath", file.getAbsolutePath());
            url.addParameter("validateQueries", options == null || !options.isSkipQueryValidation());
            url.addParameter("createSharedDatasets", options == null || options.isCreateSharedDatasets());
            if (options != null)
            {
                url.addParameter("advancedImportOptions", options.isAdvancedImportOptions());
                url.addParameter("fromZip", true);
                url.addParameter("fromTemplateSourceFolder", fromTemplateSourceFolder);
            }

            return url;
        }

        @Override
        public ActionURL statusDetails(Container container, int jobRowId)
        {
            return new ActionURL(StatusController.DetailsAction.class, container).addParameter("rowId", jobRowId);
        }

        @Override
        public ActionURL statusList(Container container)
        {
            return new ActionURL(StatusController.DetailsAction.class, container);
        }
    }

    public static ActionURL urlBegin(Container container)
    {
        return new ActionURL(BeginAction.class, container);
    }


    @RequiresPermission(ReadPermission.class)
    public class PipelineConfigurationAction extends GWTServiceAction
    {
        @Override
        protected BaseRemoteService createService()
        {
            return new PipelineGWTServiceImpl(getViewContext());
        }
    }

    public static class TestCase extends AbstractActionPermissionTest
    {
        @Override
        public void testActionPermissions()
        {
            User user = TestContext.get().getUser();
            assertTrue(user.hasSiteAdminPermission());

            PipelineController controller = new PipelineController();

            // @RequiresPermission(ReadPermission.class)
            assertForReadPermission(user, false,
                controller.new BeginAction(),
                controller.new BrowseAction(),
                controller.new ActionsAction(),
                controller.new GetPipelineActionConfigAction(),
                controller.new GetPipelineFilePropertiesAction(),
                controller.new DownloadAction(),
                controller.new GetPipelineContainerAction(),
                controller.new PipelineConfigurationAction()
            );

            // @RequiresPermission(DeletePermission.class)
            assertForUpdateOrDeletePermission(user,
                controller.new CancelJobAction()
            );

            // @RequiresPermission(AdminPermission.class)
            assertForAdminPermission(user,
                controller.new UpdatePipelineActionConfigAction(),
                controller.new UpdateEmailNotificationAction(),
                controller.new ResetEmailNotificationAction(),
                controller.new ImportFolderFromPipelineAction(),
                controller.new StartFolderImportAction()
            );

            // @RequiresPermission(AdminOperationsPermission.class)
            assertForAdminOperationsPermission(user,
                controller.new UpdateRootPermissionsAction(),
                controller.new SetupAction()
            );
        }
    }
}
