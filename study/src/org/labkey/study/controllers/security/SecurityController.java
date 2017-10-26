/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
package org.labkey.study.controllers.security;

import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.xmlbeans.XmlOptions;
import org.labkey.api.action.ExportAction;
import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleErrorView;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.ReportIdentifier;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.security.Group;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.roles.EditorRole;
import org.labkey.api.security.roles.NoPermissionsRole;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.RestrictedReaderRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Study;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.TabStripView;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.study.controllers.BaseStudyController;
import org.labkey.study.model.GroupSecurityType;
import org.labkey.study.model.SecurityType;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.security.permissions.StudyPermissionExporter;
import org.labkey.studySecurityPolicy.xml.StudySecurityPolicyDocument;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * User: Matthew
 * Date: Apr 24, 2006
 * Time: 5:31:02 PM
 */
public class SecurityController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(SecurityController.class);

    public SecurityController() throws Exception
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermission(AdminPermission.class)
    public class BeginAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            setHelpTopic(new HelpTopic("studySecurity"));
            StudyImpl study = BaseStudyController.getStudyRedirectIfNull(getContainer());
            return new Overview(study);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Study Security");
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class SaveStudyPermissionsAction extends FormHandlerAction
    {
        public void validateCommand(Object target, Errors errors)
        {
        }

        public boolean handlePost(Object o, BindException errors) throws Exception
        {
            Study study = BaseStudyController.getStudyThrowIfNull(getContainer());
            HttpServletRequest request = getViewContext().getRequest();
            List<Group> groups = SecurityManager.getGroups(study.getContainer().getProject(), true);
            HashSet<Integer> set = new HashSet<>(groups.size()*2);

            for (Group g : groups)
                set.add(g.getUserId());

            MutableSecurityPolicy policy = policyFromPost(request, set, study);

            // Explicitly give site admins read permission, so they can never be locked out
            Group siteAdminGroup = SecurityManager.getGroup(Group.groupAdministrators);
            policy.clearAssignedRoles(siteAdminGroup);
            policy.addRoleAssignment(siteAdminGroup, ReaderRole.class);

            study.savePolicy(policy, getUser());
            return true;
        }

        public ActionURL getSuccessURL(Object o)
        {
            String redirect = (String)getViewContext().get("redirect");
            if (redirect != null)
                return new ActionURL(redirect);

            return new ActionURL(SecurityController.BeginAction.class, getContainer());
        }

        private MutableSecurityPolicy policyFromPost(HttpServletRequest request, HashSet<Integer> set, Study study)
        {
            MutableSecurityPolicy policy = new MutableSecurityPolicy(study);
            IteratorUtils.asIterator(request.getParameterNames()).forEachRemaining(name -> {
                if (!name.startsWith("group."))
                    return;
                String s = name.substring("group.".length());
                int groupid;
                try
                {
                    groupid = Integer.parseInt(s);
                }
                catch (NumberFormatException x)
                {
                    return;
                }
                if (!set.contains(groupid))
                    return;
                Group group = SecurityManager.getGroup(groupid);
                if (null == group)
                    return;

                s = request.getParameter(name);
                if (s.equals(GroupSecurityType.UPDATE_ALL.getParamName()))
                    policy.addRoleAssignment(group, EditorRole.class);
                else if (s.equals(GroupSecurityType.READ_ALL.getParamName()))
                    policy.addRoleAssignment(group, ReaderRole.class);
                else if (s.equals(GroupSecurityType.PER_DATASET.getParamName()))
                    policy.addRoleAssignment(group, RestrictedReaderRole.class);
                else
                    policy.addRoleAssignment(group, NoPermissionsRole.class);
            });
            return policy;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class ExportSecurityPolicyAction extends ExportAction<Object>
    {
        public void export(Object form, HttpServletResponse response, BindException errors) throws Exception
        {
            try
            {
                StudyImpl study = StudyManager.getInstance().getStudy(getContainer());
                if (study == null)
                {
                    errors.reject(ERROR_MSG, "No study in this folder");
                    return;
                }

                StudyPermissionExporter exporter = new StudyPermissionExporter();
                StudySecurityPolicyDocument doc = exporter.getStudySecurityPolicyDocument(study);

                XmlOptions xmlOptions = new XmlOptions();
                xmlOptions.setSavePrettyPrint();

                String fileName = "studyPolicy.xml";
                PageFlowUtil.prepareResponseForFile(response, Collections.emptyMap(), fileName, true);
                doc.save(response.getOutputStream(), xmlOptions);
            }
            catch (IOException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
            }
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class ImportSecurityPolicyAction extends FormViewAction<ReturnUrlForm>
    {
        private String _messageText = null;

        public ModelAndView getView(ReturnUrlForm form, boolean reshow, BindException errors) throws Exception
        {
            if (errors.hasErrors())
            {
                return new SimpleErrorView(errors);
            }
            else if (_messageText != null)
            {
                String btn = PageFlowUtil.button("continue")
                        .dropdown(true) // does this need to be a dropdown?
                        .href(new ActionURL(BeginAction.class, getContainer()))
                        .toString();

                return new HtmlView(_messageText + "<br>" + btn);
            }
            else
            {
                throw new RedirectException(new ActionURL(BeginAction.class, getContainer()));
            }
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Study Policy Import");
        }

        public void validateCommand(ReturnUrlForm form, Errors errors)
        {

        }

        public boolean handlePost(ReturnUrlForm form, BindException errors) throws Exception
        {
            List<String> messages = new ArrayList<>();
            StudyPermissionExporter exporter = new StudyPermissionExporter();
            StudyImpl study = StudyManager.getInstance().getStudy(getContainer());
            if (study == null)
            {
                errors.reject(ERROR_MSG, "Error: there is no study in this folder");
                return false;
            }

            try
            {
                Map<String, MultipartFile> map = getFileMap();
                if (map.isEmpty())
                {
                    errors.reject(ERROR_MSG, "You must select a valid XML file");
                    return false;
                }
                else if (map.size() > 1)
                {
                    errors.reject(ERROR_MSG, "Only one file is allowed.");
                    return false;
                }
                else
                {
                    MultipartFile file = map.values().iterator().next();

                    if (0 == file.getSize() || StringUtils.isBlank(file.getOriginalFilename()))
                    {
                        errors.reject(ERROR_MSG, "You must select a valid XML file");
                        return false;
                    }
                    else if (!file.getOriginalFilename().endsWith(".xml"))
                    {
                        errors.reject("folderImport", "You must select a valid XML file");
                        return false;
                    }
                    else
                    {
                        InputStream is = file.getInputStream();
                        File tmpFile = File.createTempFile("studyPolicy", ".xml");
                        tmpFile.deleteOnExit();
                        FileUtil.copyData(is, tmpFile);
                        exporter.loadFromXmlFile(study, getUser(), tmpFile, messages);

                        StringBuilder sb = new StringBuilder();
                        if (messages.size() == 0)
                        {
                            sb.append("The import was successful.");
                        }
                        else
                        {
                            sb.append("The import was successful, but the following messages were generated:<br><br>");
                            sb.append(StringUtils.join(messages, "<br>"));
                        }
                        _messageText = sb.toString();
                    }
                }

                return false;  //force text to always show
            }
            catch (IllegalArgumentException e)
            {
                errors.reject(ERROR_MSG, "Error importing XML file: " + e.getMessage());
                return false;
            }
            catch (Exception e)
            {
                ExceptionUtil.logExceptionToMothership(getViewContext().getRequest(), e);
                errors.reject(ERROR_MSG, "Error importing XML file: " + e.getMessage());
                return false;
            }
        }

        public ActionURL getSuccessURL(ReturnUrlForm form)
        {
            ActionURL returnUrl = form.getReturnActionURL();
            if (returnUrl != null)
                return returnUrl;

            if (_messageText == null)
                return new ActionURL(SecurityController.BeginAction.class, getContainer());
            else
                return null;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class ApplyDatasetPermissionsAction extends FormHandlerAction
    {
        public void validateCommand(Object target, Errors errors)
        {
        }

        public boolean handlePost(Object o, BindException errors) throws Exception
        {
            Study study = BaseStudyController.getStudyThrowIfNull(getContainer());
            List<Group> groups = SecurityManager.getGroups(study.getContainer().getProject(), true);
            Set<Integer> groupsInProject = new HashSet<>(groups.size() * 2);
            for (Group g : groups)
                groupsInProject.add(g.getUserId());

            for (Dataset dsDef : study.getDatasets())
            {
                // Data that comes back is a list of permissions and groups separated by underscores.
                // e.g. "NONE_1182" or "READ_-1"
                List<String> permsAndGroups = getViewContext().getList("dataset." + dsDef.getDatasetId());
                Map<Integer, String> group2Perm = convertToGroupsAndPermissions(permsAndGroups);

                if (group2Perm != null)
                {
                    dsDef.savePolicy(policyFromPost(group2Perm, groupsInProject, dsDef), getUser());
                }
            }
            return true;
        }

        /**
         * convert list of "perm_groupid" strings to a map of groupid -> perm
         */
        private Map<Integer, String> convertToGroupsAndPermissions(List<String> permsAndGroups)
        {
            if (permsAndGroups == null)
                return null;

            Map<Integer, String> groupToPermission = new HashMap<>();

            for (String permAndGroup : permsAndGroups)
            {
                int underscoreIndex = permAndGroup.indexOf("_");

                if (underscoreIndex <= 0 || underscoreIndex == permAndGroup.length() - 1)
                    continue;

                String gIdString = permAndGroup.substring(0, underscoreIndex);
                String perm = permAndGroup.substring(underscoreIndex + 1);
                int gid;

                try
                {
                    gid = Integer.parseInt(gIdString);
                }
                catch (NumberFormatException nfe)
                {
                    continue;
                }

                groupToPermission.put(gid, perm);
            }
            return groupToPermission;
        }

        private MutableSecurityPolicy policyFromPost(Map<Integer, String> group2Perm, Set<Integer> groupsInProject, Dataset dsDef)
        {
            MutableSecurityPolicy policy = new MutableSecurityPolicy(dsDef);

            for (Map.Entry<Integer, String> entry : group2Perm.entrySet())
            {
                int gid = entry.getKey().intValue();
                if (groupsInProject.contains(gid))
                {
                    Group group = SecurityManager.getGroup(gid);
                    if (null == group)
                        continue;

                    String perm = entry.getValue();
                    for (Role role : RoleManager.getAllRoles())
                    {
                        if (role.getClass().getName().equals(perm))
                        {
                            policy.addRoleAssignment(group, role);
                            break;
                        }
                    }
                }
            }
            return policy;
        }

        public ActionURL getSuccessURL(Object o)
        {
            String redirect = (String) getViewContext().get("redirect");
            if (redirect != null)
                return new ActionURL(redirect);

            return new ActionURL(SecurityController.BeginAction.class, getContainer());
        }
    }

    private static class ReportPermissionsTabStrip extends TabStripView
    {
        private PermissionsForm _bean;

        public ReportPermissionsTabStrip(PermissionsForm bean)
        {
            _bean = bean;
        }

        public List<NavTree> getTabList()
        {
            List<NavTree> tabs = new ArrayList<>();
            tabs.add(new TabInfo("Permissions", SecurityController.TAB_REPORT, getViewContext().getActionURL()));
            if (StudyManager.getInstance().getStudy(getViewContext().getContainer()) != null)
                tabs.add(new TabInfo("Study Security", SecurityController.TAB_STUDY, getViewContext().getActionURL()));
            return tabs;
        }

        public HttpView getTabView(String tabId) throws Exception
        {
            if (TAB_STUDY.equals(tabId))
            {
                StudyImpl study = BaseStudyController.getStudyRedirectIfNull(getViewContext().getContainer());
                return new Overview(study, getViewContext().getActionURL());
            }
            else
            {
                ReportIdentifier reportId = _bean.getReportId();
                if (reportId != null && (reportId.getReport(getViewContext()) != null))
                    return new JspView<>("/org/labkey/study/view/reportPermission.jsp", reportId.getReport(getViewContext()));
                else
                    return new HtmlView("<span class=\"labkey-error\">The specified report ID is invalid or does not exist</span>");
            }
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class ReportPermissionsAction extends FormViewAction<PermissionsForm>
    {
        public ModelAndView getView(PermissionsForm form, boolean reshow, BindException errors) throws Exception
        {
            setHelpTopic(new HelpTopic("reportPermissions"));
            return new ReportPermissionsTabStrip(form);
        }

        public void validateCommand(PermissionsForm target, Errors errors)
        {
        }

        public boolean handlePost(PermissionsForm form, BindException errors) throws Exception
        {
            Report report = null;
            if (form.getReportId() != null)
                report = form.getReportId().getReport(getViewContext());

            if (null == report)
            {
                throw new NotFoundException();
            }

            if (form.getPermissionType().equals(PermissionType.privatePermission.toString()))
            {
                ReportUtil.resetReportSecurityPolicy(getViewContext(), report, getUser());
            }
            else if (form.getPermissionType().equals(PermissionType.defaultPermission.toString()))
            {
                ReportUtil.resetReportSecurityPolicy(getViewContext(), report, null);
            }
            else
            {
                // modify one at a time
                if (form.getRemove() != 0 || form.getAdd() != 0)
                {
                    if (form.getRemove() != 0)
                        ReportUtil.updateReportSecurityPolicy(getViewContext(), report, form.getRemove(), false);

                    if (form.getAdd() != 0)
                        ReportUtil.updateReportSecurityPolicy(getViewContext(), report, form.getAdd(), true);
                }
                // set all at once
                else
                {
                    ReportUtil.setReportSecurityPolicy(getViewContext(), report, form.getGroups(), form.getUsers());
                }
            }
            return true;
        }

        public ActionURL getSuccessURL(PermissionsForm form)
        {
            return form.getReturnActionURL();
        }

        public NavTree appendNavTrail(NavTree root)
        {
            try
            {
                Study study = BaseStudyController.getStudyRedirectIfNull(getContainer());
                root.addChild(study.getLabel(), BaseStudyController.getStudyOverviewURL(getContainer()));

                if (getContainer().hasPermission(getUser(), AdminPermission.class))
                    root.addChild("Manage Views", PageFlowUtil.urlProvider(ReportUrls.class).urlManageViews(getContainer()).getLocalURIString());
            }
            catch (Exception e)
            {
                return root.addChild("Report and View Permissions");
            }
            return root.addChild("Report and View Permissions");
        }
    }

    private static class StudySecurityForm
    {
        private SecurityType _securityType;

        public SecurityType getSecurityType()
        {
            return _securityType;
        }

        public void setSecurityString(String s)
        {
            _securityType = SecurityType.valueOf(s);
        }

        public String getSecurityString()
        {
            return _securityType == null ? null : _securityType.name();
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class StudySecurityAction extends FormHandlerAction<StudySecurityForm>
    {
        public void validateCommand(StudySecurityForm target, Errors errors)
        {
        }

        public boolean handlePost(StudySecurityForm form, BindException errors) throws Exception
        {
            StudyImpl study = BaseStudyController.getStudy(getContainer());
            if (study != null && form.getSecurityType() != study.getSecurityType())
            {
                StudyImpl updated = study.createMutable();
                updated.setSecurityType(form.getSecurityType());
                StudyManager.getInstance().updateStudy(getUser(), updated);
            }
            return true;
        }

        public ActionURL getSuccessURL(StudySecurityForm studySecurityForm)
        {
            String redirect = (String) getViewContext().get("redirect");
            if (redirect != null)
                return new ActionURL(redirect);

            return new ActionURL(SecurityController.BeginAction.class, getContainer());
        }
    }

    public enum PermissionType
    {
        defaultPermission,
        customPermission,
        privatePermission,
    }

    public static final String TAB_REPORT = "tabReport";
    public static final String TAB_STUDY = "tabStudy";

    public static class PermissionsForm extends ReturnUrlForm
    {
        private ReportIdentifier reportId;
        private Integer remove = 0;
        private Integer add = 0;
        private Set<Integer> groups = null;
        private Set<Integer> users = null;
        private String _permissionType;
        private String _tabId;

        // Not used, but needed for spring binding
        public int[] getGroup()
        {
            return null;
        }

        // use group (multi values) to set acl all at once
        public void setGroup(int[] groupArray)
        {
            groups = new TreeSet<>();

            for (int group : groupArray)
                groups.add(group);
        }

        public Set<Integer> getGroups()
        {
            return groups;
        }

        // Not used, but needed for spring binding
        public int[] getUser()
        {
            return null;
        }

        // use users (multi values) to set acl all at once
        public void setUser(int[] usersArray)
        {
            users = new TreeSet<>();
            for (int user : usersArray)
                users.add(user);
        }

        public Set<Integer> getUsers()
        {
            return users;
        }

        public ReportIdentifier getReportId()
        {
            return reportId;
        }

        public void setReportId(ReportIdentifier reportId)
        {
            this.reportId = reportId;
        }

        /* add and remove can be used to add/remove single principal */

        public int getRemove()
        {
            return remove == null ? 0 : remove.intValue();
        }

        public void setRemove(Integer remove)
        {
            this.remove = remove;
        }

        public int getAdd()
        {
            return add == null ? 0 : remove.intValue();
        }

        public void setAdd(Integer add)
        {
            this.add = add;
        }

        public void setPermissionType(String type)
        {
            _permissionType = type;
        }

        public String getPermissionType()
        {
            return _permissionType;
        }

        public void setTabId(String id)
        {
            _tabId = id;
        }

        public String getTabId()
        {
            return _tabId;
        }
    }

    static class Overview extends WebPartView
    {
        private final HttpView _vbox;

        Overview(StudyImpl study)
        {
            this(study, null);
            setFrame(FrameType.NONE);
        }

        Overview(StudyImpl study, ActionURL returnUrl)
        {
            super(FrameType.DIV);

            JspView<StudyImpl> studySecurityView = new JspView<>("/org/labkey/study/security/studySecurity.jsp", study);

            Pair<StudyImpl, ActionURL> pair = new Pair<>(study, returnUrl);
            JspView<Pair<StudyImpl, ActionURL>> studyView = new JspView<>("/org/labkey/study/security/study.jsp", pair);
            studyView.setTitle("Study Security");

            JspView<Pair<StudyImpl, ActionURL>> dsView = new JspView<>("/org/labkey/study/security/datasets.jsp", pair);
            dsView.setTitle("Per Dataset Permissions");

            JspView<StudyImpl> siteView = new JspView<>("/org/labkey/study/security/locations.jsp", study);
            siteView.setTitle("Restricted Dataset Permissions (per Location)");

            VBox v = new VBox();
            v.addView(studySecurityView);
            if (study.getSecurityType() == SecurityType.ADVANCED_READ || study.getSecurityType() == SecurityType.ADVANCED_WRITE)
            {
                v.addView(studyView);
                v.addView(dsView);
            }

            if (!study.isDataspaceStudy())
            {
                JspView<StudyImpl> exportView = new JspView<>("/org/labkey/study/security/importExport.jsp", study);
                exportView.setTitle("Import/Export Policy");
                v.addView(exportView);
            }

            _vbox = v;
        }


        protected void renderView(Object model, PrintWriter out) throws Exception
        {
            include(_vbox, out);
        }
    }


    public static class StudySecurityViewFactory implements SecurityManager.ViewFactory
    {
        public HttpView createView(ViewContext context)
        {
            if (null != BaseStudyController.getStudy(context.getContainer()))
                return new StudySecurityPermissionsView();
            return null;
        }
    }


    private static class StudySecurityPermissionsView extends WebPartView
    {
        private StudySecurityPermissionsView()
        {
            super("Study Security");
        }

        @Override
        protected void renderView(Object model, PrintWriter out) throws Exception
        {
            ActionURL urlStudy = new ActionURL(BeginAction.class, getViewContext().getContainer());
            out.print("<p>Permissions for datasets in a Study are managed separately.<br/>");
            out.print(PageFlowUtil.button("Study Security").href(urlStudy));
            out.print("<br/>&nbsp;</p>");
        }
    }
}
