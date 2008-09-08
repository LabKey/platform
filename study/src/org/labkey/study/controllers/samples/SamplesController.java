/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

package org.labkey.study.controllers.samples;

import jxl.Range;
import jxl.Workbook;
import jxl.WorkbookSettings;
import org.apache.beehive.netui.pageflow.FormData;
import org.apache.beehive.netui.pageflow.Forward;
import org.apache.beehive.netui.pageflow.annotations.Jpf;
import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.lang.StringUtils;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.labkey.api.attachments.*;
import org.labkey.api.data.*;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusUrls;
import org.labkey.api.security.*;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.util.*;
import org.labkey.api.view.*;
import org.labkey.common.tools.TabLoader;
import org.labkey.common.util.Pair;
import org.labkey.study.SampleManager;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.BaseController;
import org.labkey.study.designer.MapArrayExcelWriter;
import org.labkey.study.importer.SimpleSpecimenImporter;
import org.labkey.study.model.*;
import org.labkey.study.pipeline.SpecimenBatch;
import org.labkey.study.query.SpecimenQueryView;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.net.URISyntaxException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;


@Jpf.Controller(longLived=true,messageBundles = {@Jpf.MessageBundle(bundlePath = "messages.Validation")})
public class SamplesController extends BaseController
{
    @Jpf.Action
    /**
     * This method represents the point of entry into the pageflow
     */
    protected Forward begin() throws Exception
    {
        return new ViewForward(getActionURL().relativeUrl("samples", null));
    }

    public static class UpdateGroupForm extends IdForm
    {
        private Integer _siteId;
        private String[] _delete;
        private boolean _sendEmail;
        private String _names;
        private String _returnUrl;

        public Integer getSiteId()
        {
            return _siteId;
        }

        public void setSiteId(Integer siteId)
        {
            _siteId = siteId;
        }

        public String getNames()
        {
            return _names;
        }

        public void setNames(String names)
        {
            _names = names;
        }

        public boolean isSendEmail()
        {
            return _sendEmail;
        }

        public void setSendEmail(boolean sendEmail)
        {
            _sendEmail = sendEmail;
        }

        public String[] getDelete()
        {
            return _delete;
        }

        public void setDelete(String[] delete)
        {
            _delete = delete;
        }

        public String getReturnUrl()
        {
            return _returnUrl;
        }

        public void setReturnUrl(String returnUrl)
        {
            _returnUrl = returnUrl;
        }
    }

    @Jpf.Action
    protected Forward showGroupMembers(UpdateGroupForm form) throws Exception
    {
        requiresAdmin();
        SampleRequestActor actor = SampleManager.getInstance().getRequirementsProvider().getActor(getContainer(), form.getId());
        if (actor == null)
            return HttpView.throwNotFound();

        Site site = null;
        if (form.getSiteId() != null)
            site = StudyManager.getInstance().getSite(getContainer(), form.getSiteId());

        StringBuilder message = new StringBuilder();
        if ("POST".equalsIgnoreCase(getRequest().getMethod()))
        {
            String[] emailsToDelete = form.getDelete();
            if (emailsToDelete != null && emailsToDelete.length > 0)
            {
                List<String> invalidEmails = new ArrayList<String>();
                List<ValidEmail> emails = SecurityManager.normalizeEmails(emailsToDelete, invalidEmails);

                if (invalidEmails.isEmpty())
                {
                    User[] users = new User[emails.size()];
                    int i = 0;
                    for (ValidEmail email : emails)
                    {
                        users[i++] = UserManager.getUser(email);
                        assert users[i - 1] != null : email.getEmailAddress() + " is not associated with a user account.";
                    }
                    actor.removeMembers(site, users);
                }
                else
                    throw new ServletException("Invalid email address" + (invalidEmails.size() > 1 ? "es: " : ": ") + StringUtils.join(invalidEmails.toArray()));
            }

            if (form.getNames() != null)
            {
                String[] names = form.getNames().split("\n");
                List<String> invalidEmails = new ArrayList<String>();
                List<ValidEmail> emails = SecurityManager.normalizeEmails(names, invalidEmails);

                for (String rawEmail : invalidEmails)
                {
                    // Ignore lines of all whitespace, otherwise show an error.
                    if (!"".equals(rawEmail.trim()))
                    {
                        message.append("Could not add user ");
                        message.append(rawEmail.trim());
                        message.append(": Invalid email address<br>");
                    }
                }

                List<User> newMembers = new ArrayList<User>();
                for (ValidEmail email : emails)
                {
                    if (getUser().isAdministrator())
                    {
                        String result = SecurityManager.addUser(getViewContext(), email, form.isSendEmail(), null, null);
                        if (result != null)
                            message.append(result).append("<br>");
                        newMembers.add(UserManager.getUser(email));
                    }
                    else
                    {
                        User user = UserManager.getUser(email);
                        if (user == null)
                            message.append(email.getEmailAddress()).append(" is not a registered system user.<br>");
                        else
                        {
                            newMembers.add(UserManager.getUser(email));
                        }
                    }
                }
                actor.addMembers(site, newMembers.toArray(new User[newMembers.size()]));
            }
            if (message.length() == 0)
            {
                String returnURL = form.getReturnUrl();
                if (returnURL != null && returnURL.length() > 0)
                    return new ViewForward(returnURL);
                else
                {
                    ActionURL manageURL = cloneActionURL();
                    manageURL.deleteParameters();
                    manageURL.setAction("manageActors");
                    return new ViewForward(manageURL);
                }
            }
        }

        User[] members = actor.getMembers(site);
        String title = actor.getLabel();
        if (site != null)
            title += ", " + site.getLabel();
        JspView<GroupMembersBean> view = new JspView<GroupMembersBean>("/org/labkey/study/view/samples/groupMembers.jsp",
                new GroupMembersBean(actor, site, members, message.toString(), form.getReturnUrl()));

        NavTree[] navTrail = new NavTree[]{
                new NavTree(getStudy().getLabel(), getActionURL().relativeUrl("overview", null, "Study")),
                new NavTree("Manage Study", getActionURL().relativeUrl("manageStudy", null, "Study")),
                new NavTree("Manage Actors", getActionURL().relativeUrl("manageActors",
                        site != null ? "showMemberSites=" + actor.getRowId() : "")),
                new NavTree(title)};

        return _renderInTemplate(view, title, navTrail);

    }

    public static class GroupMembersBean
    {
        private SampleRequestActor _actor;
        private Site _site;
        private User[] _members;
        private String _messages;
        private String _ldapDomain;
        private String _returnUrl;

        public GroupMembersBean(SampleRequestActor actor, Site site, User[] members, String message, String returnUrl)
        {
            _actor = actor;
            _site = site;
            _members = members;
            _messages = message;
            _ldapDomain = AuthenticationManager.getLdapDomain();
            _returnUrl = returnUrl;
        }

        public SampleRequestActor getActor()
        {
            return _actor;
        }

        public User[] getMembers()
        {
            return _members;
        }

        public Site getSite()
        {
            return _site;
        }

        public String getMessages()
        {
            return _messages;
        }

        public String getLdapDomain()
        {
            return _ldapDomain;
        }

        public String getReturnUrl()
        {
            return _returnUrl;
        }

        public String getCompleteUsersPrefix()
        {
            return PageFlowUtil.urlProvider(SecurityUrls.class).getCompleteUserURLPrefix(_actor.getContainer());
        }
    }


    @Jpf.Action
    protected Forward download(AttachmentForm form) throws IOException, ServletException, SQLException
    {
        requiresPermission(ACL.PERM_READ);

        SampleRequestEvent event = new SampleRequestEvent();  // TODO: Need to verify that entityId represents a valid SampleRequestEvent
        event.setContainer(getContainer());
        event.setEntityId(form.getEntityId());

        AttachmentService.get().download(getResponse(), event, form.getName());

        return null;
    }


    public static class ManageRepositorySettingsForm extends FormData
    {
        private boolean _simple;
        private boolean _enableRequests;

        public boolean isSimple()
        {
            return _simple;
        }

        public void setSimple(boolean simple)
        {
            _simple = simple;
        }

        public boolean isEnableRequests()
        {
            return _enableRequests;
        }

        public void setEnableRequests(boolean enableRequests)
        {
            _enableRequests = enableRequests;
        }
    }

    @Jpf.Action
    protected Forward showManageRepositorySettings() throws Exception
    {
        JspView<SampleManager.RepositorySettings> view = new JspView<SampleManager.RepositorySettings>("/org/labkey/study/view/samples/manageRepositorySettings.jsp", SampleManager.getInstance().getRepositorySettings(getContainer()));
        return _renderInTemplate(view, "Manage Repository Settings",
                new NavTree("Study", new ActionURL("Study", "begin.view", getContainer())),
                new NavTree("Manage Study", new ActionURL("Study", "manageStudy.view", getContainer())));
    }

    @Jpf.Action
    protected Forward manageRepositorySettings(ManageRepositorySettingsForm form) throws Exception
    {
        SampleManager.RepositorySettings settings = SampleManager.getInstance().getRepositorySettings(getContainer());
        settings.setSimple(form.isSimple());
        settings.setEnableRequests(!form.isSimple()); //We only expose one setting for now...
        SampleManager.getInstance().saveRepositorySettings(getContainer(), settings);

        return new ViewForward("Study", "manageStudy", getContainer());
    }

    private Map<Integer, SampleRequestActor> getIdToRequestActorMap(Container container) throws SQLException
    {
        SampleRequestActor[] actors = SampleManager.getInstance().getRequirementsProvider().getActors(container);
        Map<Integer, SampleRequestActor> idToStatus = new HashMap<Integer, SampleRequestActor>();
        for (SampleRequestActor actor : actors)
            idToStatus.put(actor.getRowId(), actor);
        return idToStatus;
    }

    public static class ManageRequestForm extends IdForm
    {
        private Integer _newSite;
        private Integer _newActor;
        private String _newDescription;
        private String _export;
        private Boolean _submissionResult;

        public Integer getNewActor()
        {
            return _newActor;
        }

        public void setNewActor(Integer newActor)
        {
            _newActor = newActor;
        }

        public Integer getNewSite()
        {
            return _newSite;
        }

        public void setNewSite(Integer newSite)
        {
            _newSite = newSite;
        }

        public String getNewDescription()
        {
            return _newDescription;
        }

        public void setNewDescription(String newDescription)
        {
            _newDescription = newDescription;
        }

        public String getExport()
        {
            return _export;
        }

        public void setExport(String export)
        {
            _export = export;
        }

        public Boolean isSubmissionResult()
        {
            return _submissionResult;
        }

        public void setSubmissionResult(Boolean submissionResult)
        {
            _submissionResult = submissionResult;
        }
    }

    @Jpf.Action
    protected Forward manageActorOrder(BulkEditForm form) throws Exception
    {
        requiresAdmin();
        if ("POST".equalsIgnoreCase(getRequest().getMethod()))
        {
            String order = form.getOrder();
            if (order != null && order.length() > 0)
            {
                String[] rowIds = order.split(",");
                // get a map of id to actor objects before starting our updates; this prevents us from
                // blowing then repopulating the cache with each update:
                Map<Integer, SampleRequestActor> idToActor = getIdToRequestActorMap(getContainer());
                for (int i = 0; i < rowIds.length; i++)
                {
                    int rowId = Integer.parseInt(rowIds[i]);
                    SampleRequestActor actor = idToActor.get(rowId);
                    if (actor != null && actor.getSortOrder() != i)
                    {
                        actor = actor.createMutable();
                        actor.setSortOrder(i);
                        actor.update(getUser());
                    }
                }
            }
            return new ViewForward(getActionURL().relativeUrl("manageActors", null));
        }
        return displayManagementSubpage("manageActorOrder", "Manage Actor Order", "specimenRequest");
    }

    public static class StatusEditForm extends BulkEditForm
    {
        private int[] _finalStateIds = new int[0];
        private int[] _specimensLockedIds = new int[0];
        private boolean _newSpecimensLocked;
        private boolean _newFinalState;
        private boolean _useShoppingCart;

        public int[] getFinalStateIds()
        {
            return _finalStateIds;
        }

        public void setFinalStateIds(int[] finalStateIds)
        {
            _finalStateIds = finalStateIds;
        }

        public boolean isNewFinalState()
        {
            return _newFinalState;
        }

        public void setNewFinalState(boolean newFinalState)
        {
            _newFinalState = newFinalState;
        }

        public boolean isNewSpecimensLocked()
        {
            return _newSpecimensLocked;
        }

        public void setNewSpecimensLocked(boolean newSpecimensLocked)
        {
            _newSpecimensLocked = newSpecimensLocked;
        }

        public int[] getSpecimensLockedIds()
        {
            return _specimensLockedIds;
        }

        public void setSpecimensLockedIds(int[] specimensLockedIds)
        {
            _specimensLockedIds = specimensLockedIds;
        }

        public boolean isUseShoppingCart()
        {
            return _useShoppingCart;
        }

        public void setUseShoppingCart(boolean useShoppingCart)
        {
            _useShoppingCart = useShoppingCart;
        }
    }

    public static class ActorEditForm extends BulkEditForm
    {
        boolean _newPerSite;

        public boolean isNewPerSite()
        {
            return _newPerSite;
        }

        public void setNewPerSite(boolean newPerSite)
        {
            _newPerSite = newPerSite;
        }
    }

    @Jpf.Action
    protected Forward deleteActor(IdForm form) throws Exception
    {
        requiresAdmin();
        SampleRequestActor actor = SampleManager.getInstance().getRequirementsProvider().getActor(getContainer(), form.getId());
        if (actor != null)
            actor.delete();

        ActionURL manageURL = cloneActionURL();
        manageURL.deleteParameters();
        manageURL.setAction("manageActors");
        return new ViewForward(manageURL);
    }

    @Jpf.Action
    protected Forward manageActors(ActorEditForm form) throws Exception
    {
        requiresAdmin();
        if ("POST".equalsIgnoreCase(getRequest().getMethod()))
        {
            ActionErrors errors = PageFlowUtil.getActionErrors(getRequest(), true);
            int[] rowIds = form.getIds();
            String[] labels = form.getLabels();
            if (labels != null)
            {
                for (String label : labels)
                {
                    if (label == null || label.length() == 0)
                        errors.add("main", new ActionMessage("Error", "Actor name cannot be empty."));
                }
            }
            if (errors.isEmpty())
            {
                if (rowIds != null && rowIds.length > 0)
                {
                    // get a map of id to actor objects before starting our updates; this prevents us from
                    // blowing then repopulating the cache with each update:
                    Map<Integer, SampleRequestActor> idToActor = getIdToRequestActorMap(getContainer());
                    for (int i = 0; i < rowIds.length; i++)
                    {
                        int rowId = rowIds[i];
                        String label = labels[i];
                        SampleRequestActor actor = idToActor.get(rowId);
                        if (actor != null && !nullSafeEqual(label, actor.getLabel()))
                        {
                            actor = actor.createMutable();
                            actor.setLabel(label);
                            actor.update(getUser());
                        }
                    }
                }

                if (form.getNewLabel() != null && form.getNewLabel().length() > 0)
                {
                    SampleRequestActor actor = new SampleRequestActor();
                    actor.setLabel(form.getNewLabel());
                    SampleRequestActor[] actors = SampleManager.getInstance().getRequirementsProvider().getActors(getContainer());
                    actor.setSortOrder(actors.length);
                    actor.setContainer(getContainer());
                    actor.setPerSite(form.isNewPerSite());
                    actor.create(getUser());
                }
                if (form.getNextPage() != null && form.getNextPage().length() > 0)
                    return new ViewForward(getActionURL().relativeUrl(form.getNextPage(), null));
                else
                    return new ViewForward(ActionURL.toPathString("Study", "manageStudy", getContainer()));
            }
        }
        return displayManagementSubpage("manageActors", "Manage Actors", "specimenRequest");
    }

    private Map<Integer, SampleRequestStatus> getIdToRequestStatusMap(Container container) throws SQLException
    {
        SampleRequestStatus[] statuses = SampleManager.getInstance().getRequestStatuses(container, getUser());
        Map<Integer, SampleRequestStatus> idToStatus = new HashMap<Integer, SampleRequestStatus>();
        for (SampleRequestStatus status : statuses)
            idToStatus.put(status.getRowId(), status);
        return idToStatus;
    }

    @Jpf.Action
    protected Forward manageStatusOrder(BulkEditForm form) throws Exception
    {
        requiresAdmin();
        if ("POST".equalsIgnoreCase(getRequest().getMethod()))
        {
            String order = form.getOrder();
            if (order != null && order.length() > 0)
            {
                String[] rowIdStrings = order.split(",");
                int[] rowIds = new int[rowIdStrings.length];
                for (int i = 0; i < rowIdStrings.length; i++)
                    rowIds[i] = Integer.parseInt(rowIdStrings[i]);
                updateRequestStatusOrder(getContainer(), rowIds);
            }
            return new ViewForward(getActionURL().relativeUrl("manageStatuses", null));
        }
        return displayManagementSubpage("manageStatusOrder", "Manage Status Order", "specimenRequest");
    }


    private void updateRequestStatusOrder(Container container, int[] rowIds) throws SQLException
    {
        // get a map of id to status objects before starting our updates; this prevents us from
        // blowing then repopulating the cache with each update:
        Map<Integer, SampleRequestStatus> idToStatus = getIdToRequestStatusMap(container);
        for (int i = 0; i < rowIds.length; i++)
        {
            int rowId = rowIds[i];
            SampleRequestStatus status = idToStatus.get(rowId);
            if (status != null && !status.isSystemStatus() && status.getSortOrder() != i)
            {
                status = status.createMutable();
                status.setSortOrder(i);
                SampleManager.getInstance().updateRequestStatus(getUser(), status);
            }
        }
    }

    @Jpf.Action
    protected Forward deleteStatus(IdForm form) throws Exception
    {
        requiresAdmin();
        SampleRequestStatus[] statuses = SampleManager.getInstance().getRequestStatuses(getContainer(), getUser());
        SampleRequestStatus status = SampleManager.getInstance().getRequestStatus(getContainer(), form.getId());
        if (status != null)
        {
            SampleManager.getInstance().deleteRequestStatus(getUser(), status);
            int[] remainingIds = new int[statuses.length - 1];
            int idx = 0;
            for (SampleRequestStatus remainingStatus : statuses)
            {
                if (remainingStatus.getRowId() != form.getId())
                    remainingIds[idx++] = remainingStatus.getRowId();
            }
            updateRequestStatusOrder(getContainer(), remainingIds);
        }
        ActionURL manageURL = cloneActionURL();
        manageURL.deleteParameters();
        manageURL.setAction("manageStatuses");
        return new ViewForward(manageURL);
    }

    @Jpf.Action
    protected Forward manageStatuses(StatusEditForm form) throws Exception
    {
        requiresAdmin();
        if ("POST".equalsIgnoreCase(getRequest().getMethod()))
        {
            int[] rowIds = form.getIds();
            String[] labels = form.getLabels();
            if (rowIds != null && rowIds.length > 0)
            {
                // get a map of id to status objects before starting our updates; this prevents us from
                // blowing then repopulating the cache with each update:
                Map<Integer, SampleRequestStatus> idToStatus = getIdToRequestStatusMap(getContainer());
                Set<Integer> finalStates = new HashSet<Integer>(form.getFinalStateIds().length);
                for (int id : form.getFinalStateIds())
                    finalStates.add(id);
                Set<Integer> lockedSpecimenStates = new HashSet<Integer>(form.getSpecimensLockedIds().length);
                for (int id : form.getSpecimensLockedIds())
                    lockedSpecimenStates.add(id);

                for (int i = 0; i < rowIds.length; i++)
                {
                    int rowId = rowIds[i];
                    SampleRequestStatus status = idToStatus.get(rowId);
                    if (status != null && !status.isSystemStatus())
                    {
                        String label = labels[i];
                        boolean isFinalState = finalStates.contains(rowId);
                        boolean specimensLocked = lockedSpecimenStates.contains(rowId);
                        if (!nullSafeEqual(label, status.getLabel()) ||
                                isFinalState != status.isFinalState() ||
                                specimensLocked != status.isSpecimensLocked())
                        {
                            status = status.createMutable();
                            status.setLabel(label);
                            status.setFinalState(isFinalState);
                            status.setSpecimensLocked(specimensLocked);
                            SampleManager.getInstance().updateRequestStatus(getUser(), status);
                        }
                    }
                }
            }

            if (form.getNewLabel() != null && form.getNewLabel().length() > 0)
            {
                SampleRequestStatus status = new SampleRequestStatus();
                status.setLabel(form.getNewLabel());
                SampleRequestStatus[] statuses = SampleManager.getInstance().getRequestStatuses(getContainer(), getUser());
                status.setSortOrder(statuses.length);
                status.setContainer(getContainer());
                status.setFinalState(form.isNewFinalState());
                status.setSpecimensLocked(form.isNewSpecimensLocked());
                SampleManager.getInstance().createRequestStatus(getUser(), status);
            }

            SampleManager.StatusSettings settings = SampleManager.getInstance().getStatusSettings(getContainer());
            if (settings.isUseShoppingCart() != form.isUseShoppingCart())
            {
                settings.setUseShoppingCart(form.isUseShoppingCart());
                SampleManager.getInstance().saveStatusSettings(getContainer(), settings);
            }

            if (form.getNextPage() != null && form.getNextPage().length() > 0)
                return new ViewForward(getActionURL().relativeUrl(form.getNextPage(), null));
            else
                return new ViewForward(ActionURL.toPathString("Study", "manageStudy", getContainer()));
        }

        return displayManagementSubpage("manageStatuses", "Manage Statuses", "specimenRequest");
    }


    public static class ManageRequestInputsBean
    {
        private SampleManager.SpecimenRequestInput[] _inputs;
        private Container _container;
        private String _contextPath;

        public ManageRequestInputsBean(ViewContext context) throws SQLException
        {
            _container = context.getContainer();
            _inputs = SampleManager.getInstance().getNewSpecimenRequestInputs(_container);
            _contextPath = context.getContextPath();
        }

        public SampleManager.SpecimenRequestInput[] getInputs()
        {
            return _inputs;
        }

        public Container getContainer()
        {
            return _container;
        }

        public String getContextPath()
        {
            return _contextPath;
        }
    }

    public static class ManageNotificationsForm extends BeanViewForm<SampleManager.RequestNotificationSettings>
    {
        public ManageNotificationsForm()
        {
            super(SampleManager.RequestNotificationSettings.class);
        }

        @Override
        public ActionErrors validate(ActionMapping actionMapping, HttpServletRequest servletRequest)
        {
            ActionErrors errors = null;
            String replyTo = getBean().getReplyTo();
            if (replyTo == null || replyTo.length() == 0)
            {
                errors = new ActionErrors();
                errors.add("main", new ActionMessage("Error", "Reply-to cannot be empty."));
            }
            else if (!SampleManager.RequestNotificationSettings.REPLY_TO_CURRENT_USER_VALUE.equals(replyTo))
            {
                try
                {
                    new ValidEmail(replyTo);
                }
                catch(ValidEmail.InvalidEmailException e)
                {
                    errors = new ActionErrors();
                    errors.add("main", new ActionMessage("Error", replyTo + " is not a valid email address."));
                }
            }

            String subjectSuffix = getBean().getSubjectSuffix();
            if (subjectSuffix == null || subjectSuffix.length() == 0)
            {
                errors = new ActionErrors();
                errors.add("main", new ActionMessage("Error", "Subject suffix cannot be empty."));
            }

            try
            {
                getBean().getNewRequestNotifyAddresses();
            }
            catch (ValidEmail.InvalidEmailException e)
            {
                errors = new ActionErrors();
                errors.add("main", new ActionMessage("Error", e.getBadEmail() + " is not a valid email address."));
            }

            try
            {
                getBean().getCCAddresses();
            }
            catch (ValidEmail.InvalidEmailException e)
            {
                errors = new ActionErrors();
                errors.add("main", new ActionMessage("Error", e.getBadEmail() + " is not a valid email address."));
            }

            return errors;
        }
    }

    public static final class ManageRequestInputsForm extends FormData
    {
        private String[] _title;
        private String[] _helpText;
        private int[] _multiline;
        private int[] _required;
        private int[] _rememberSiteValue;

        public String[] getHelpText()
        {
            return _helpText;
        }

        public void setHelpText(String[] helpText)
        {
            _helpText = helpText;
        }

        public String[] getTitle()
        {
            return _title;
        }

        public void setTitle(String[] title)
        {
            _title = title;
        }

        public int[] getMultiline()
        {
            return _multiline;
        }

        public void setMultiline(int[] multiline)
        {
            _multiline = multiline;
        }

        public int[] getRememberSiteValue()
        {
            return _rememberSiteValue;
        }

        public void setRememberSiteValue(int[] rememberSiteValue)
        {
            _rememberSiteValue = rememberSiteValue;
        }

        public int[] getRequired()
        {
            return _required;
        }

        public void setRequired(int[] required)
        {
            _required = required;
        }
    }

    @Jpf.Action
    protected Forward handleUpdateRequestInputs(ManageRequestInputsForm form) throws Exception
    {
        requiresAdmin();

        SampleManager.SpecimenRequestInput[] inputs = new SampleManager.SpecimenRequestInput[form.getTitle().length];
        for (int i = 0; i < form.getTitle().length; i++)
        {
            String title = form.getTitle()[i];
            String helpText = form.getHelpText()[i];
            inputs[i] = new SampleManager.SpecimenRequestInput(title, helpText, i);
        }

        for (int index : form.getMultiline())
            inputs[index].setMultiLine(true);
        for (int index : form.getRequired())
            inputs[index].setRequired(true);
        for (int index : form.getRememberSiteValue())
            inputs[index].setRememberSiteValue(true);

        SampleManager.getInstance().saveNewSpecimenRequestInputs(getContainer(), inputs);
        return new ViewForward(getActionURL().relativeUrl("manageStudy", null, "Study"));
    }

    @Jpf.Action
    protected Forward manageRequestInputs(PipelineForm form) throws Exception
    {
        requiresAdmin();
        JspView<ManageRequestInputsBean> view = new JspView<ManageRequestInputsBean>("/org/labkey/study/view/samples/manageRequestInputs.jsp",
                new ManageRequestInputsBean(getViewContext()));
        NavTree[] navTrail = new NavTree[]{
                new NavTree(getStudy().getLabel(), getActionURL().relativeUrl("overview", null, "Study")),
                new NavTree("Manage Study", getActionURL().relativeUrl("manageStudy", null, "Study")),
                new NavTree("Manage New Request Form")};
        return _renderInTemplate(view, "Manage New Request Form", "specimenRequest", navTrail);
    }

    @Jpf.Action(validationErrorForward = @Jpf.Forward(path = "manageNotifications.do", name = "validate"))
    protected Forward handleUpdateNotifications(ManageNotificationsForm form) throws Exception
    {
        SampleManager.RequestNotificationSettings settings = form.getBean();
        if (!settings.isNewRequestNotifyCheckbox())
            settings.setNewRequestNotify(null);
        else
        {
            if (isNullOrBlank(settings.getNewRequestNotify()))
                addError("New request notify is blank and send email is checked");
        }
        if (!settings.isCcCheckbox())
            settings.setCc(null);
        else
        {
            if (isNullOrBlank(settings.getCc()))
                addError("Always CC is blank and send email is checked");
        }
        if (PageFlowUtil.getActionErrors(getRequest(), true).isEmpty())
        {
            SampleManager.getInstance().saveRequestNotificationSettings(getContainer(), settings);
            return new ViewForward(getActionURL().relativeUrl("manageStudy", null, "Study"));
        }
        return manageNotifications(form);

    }

    public static class UploadSpecimensForm extends FormData
    {
        private String tsv;
        private String redir;

        public String getTsv()
        {
            return tsv;
        }

        public void setTsv(String tsv)
        {
            this.tsv = tsv;
        }


        @Override
        public ActionErrors validate(ActionMapping mapping, HttpServletRequest request)
        {
            ActionErrors errors = PageFlowUtil.getActionErrors(request, true);
            if (null == StringUtils.trimToNull(tsv))
                errors.add("main", new ActionMessage("Error", "Please supply data to upload"));

            return errors;
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

    @Jpf.Action
    protected Forward getSpecimenExcel() throws Exception
    {
        requiresPermission(ACL.PERM_ADMIN);
        Container c = getContainer();
        //Search for a template in all folders up to root.
        Workbook inputWorkbook = null;
        while (!c.equals(ContainerManager.getRoot()))
        {
            AttachmentDirectory dir = AttachmentService.get().getMappedAttachmentDirectory(c, false);
            if (null != dir && dir.getFileSystemDirectory().exists())
            {
                if (new File(dir.getFileSystemDirectory(), "Samples.xls").exists())
                {
                    WorkbookSettings settings = new WorkbookSettings();
                    settings.setGCDisabled(true);
                    inputWorkbook = Workbook.getWorkbook(new File(dir.getFileSystemDirectory(), "Samples.xls"), settings);
                }
            }
            c = c.getParent();
        }
        int startRow = 0;
        if (null != inputWorkbook)
        {
            Range[] range = inputWorkbook.findByName("specimen_headers");
            if (null != range && range.length > 0)
                startRow = range[0].getTopLeft().getRow();
            else
                inputWorkbook = null;
        }

        Map<String,Object>[] defaultSpecimens = new Map[0];
        SimpleSpecimenImporter importer = new SimpleSpecimenImporter(getStudy().isDateBased(), "ParticipantId");
        MapArrayExcelWriter xlWriter = new MapArrayExcelWriter(defaultSpecimens, importer.getSimpleSpecimenColumns());
        for (ExcelColumn col : xlWriter.getColumns())
            col.setCaption(importer.label(col.getName()));
        xlWriter.setCurrentRow(startRow);
        if (null != inputWorkbook)
            xlWriter.setTemplate(inputWorkbook);

        xlWriter.write(getResponse());

        return null;
    }

    @Jpf.Action
    protected Forward showUploadSpecimens(UploadSpecimensForm form) throws Exception
    {
        requiresAdmin();
        SampleManager.RepositorySettings settings =  SampleManager.getInstance().getRepositorySettings(getContainer());
        if (!settings.isSimple())
            return new ViewForward("Pipeline", "browse", getContainer());
        
        JspView<UploadSpecimensForm> view = new JspView<UploadSpecimensForm>("/org/labkey/study/view/samples/uploadSimpleSpecimens.jsp", form);
        NavTree[] navTrail = new NavTree[]{
                new NavTree(getStudy().getLabel(), getActionURL().relativeUrl("overview", null, "Study")),
                new NavTree("Specimens", getActionURL().relativeUrl("begin.view", null)),
                new NavTree("Upload Specimens")};
        return _renderInTemplate(view, "Upload Specimens", navTrail);
    }

    @Jpf.Action(validationErrorForward = @Jpf.Forward(path = "showUploadSpecimens.do", name = "validate"))
    protected Forward handleUploadSpecimens(UploadSpecimensForm form) throws Exception
    {
        requiresAdmin();
        SimpleSpecimenImporter importer = new SimpleSpecimenImporter();
        String specimenTSV = StringUtils.trimToNull(form.getTsv());

        TabLoader loader = new TabLoader(specimenTSV, true);
        Map<String,String> columnAliases = new CaseInsensitiveHashMap<String>();
        //Make sure we accept the labels
        for (Map.Entry<String,String> entry : importer.getColumnLabels().entrySet())
            columnAliases.put(entry.getValue(), entry.getKey());
        //And a few more aliases
        columnAliases.put("ParticipantId", SimpleSpecimenImporter.PARTICIPANT_ID);
        columnAliases.put("Subject", SimpleSpecimenImporter.PARTICIPANT_ID);
        columnAliases.put("SequenceNum", SimpleSpecimenImporter.VISIT);
        columnAliases.put("Visit", SimpleSpecimenImporter.VISIT);
        columnAliases.put("specimenNumber", SimpleSpecimenImporter.SAMPLE_ID);
        columnAliases.put("totalVolume", SimpleSpecimenImporter.VOLUME);
        columnAliases.put("volumeUnits", SimpleSpecimenImporter.UNITS);
        columnAliases.put("primaryType", SimpleSpecimenImporter.PRIMARY_SPECIMEN_TYPE);
        columnAliases.put("additiveType", SimpleSpecimenImporter.ADDITIVE_TYPE);
        columnAliases.put("derivativeType", SimpleSpecimenImporter.DERIVIATIVE_TYPE);
        columnAliases.put("Visit", SimpleSpecimenImporter.VISIT);
        columnAliases.put("drawTimestamp", SimpleSpecimenImporter.DRAW_TIMESTAMP);

        //Remember whether we used a different header so we can put up error messages that make sense
        Map<String,String> labels = new HashMap();
        for (TabLoader.ColumnDescriptor c : loader.getColumns())
        {
            if (columnAliases.containsKey(c.name))
            {
                labels.put(columnAliases.get(c.name), c.name);
                c.name = columnAliases.get(c.name);
            }
            else
                labels.put(c.name, c.name);
        }
        importer.fixupSpecimenColumns(loader);
        Map<String,Object>[] specimenRows;
        try
        {
            loader.setThrowOnErrors(true);
            specimenRows = (Map<String,Object>[]) loader.load();
        }
        catch (ConversionException x)
        {
            PageFlowUtil.getActionErrors(getRequest(), true).add("main", new ActionMessage("Error", x.getMessage() + " NOTE: Numbers must contain only digits and decimal separators."));
            return showUploadSpecimens(form);
        }


        ActionErrors errors = PageFlowUtil.getActionErrors(getRequest(), true);
        Set<String> participants = new HashSet<String>();
        Set<Object> vialIds = new HashSet<Object>();
        Map<Object, Pair<Object,Object>> sampleIdMap = new HashMap<Object, Pair<Object, Object>>();
        String visitKey = getStudy().isDateBased() ? SimpleSpecimenImporter.DRAW_TIMESTAMP : SimpleSpecimenImporter.VISIT;
        int rowNum = 1;
        for (Map<String,Object> row : specimenRows)
        {
            String participant = (String) row.get(SimpleSpecimenImporter.PARTICIPANT_ID);
            if (null == participant)
                errors.add("main", new ActionMessage("Error", "Error, Row " + rowNum + " field " + (null == labels.get(SimpleSpecimenImporter.PARTICIPANT_ID) ? SimpleSpecimenImporter.PARTICIPANT_ID : labels.get(SimpleSpecimenImporter.PARTICIPANT_ID)) + " is not supplied"));
            else
                participants.add((String) row.get(SimpleSpecimenImporter.PARTICIPANT_ID));

            Object sampleId = row.get(SimpleSpecimenImporter.SAMPLE_ID);
            if (null == sampleId)
                errors.add("main", new ActionMessage("Error", "Error, Row " + rowNum + " missing " + (null == labels.get(SimpleSpecimenImporter.SAMPLE_ID) ? SimpleSpecimenImporter.SAMPLE_ID : labels.get(SimpleSpecimenImporter.SAMPLE_ID))));
            else
            {
                Pair<Object,Object> participantVisit = new Pair<Object,Object>(participant, row.get(visitKey));
                if (sampleIdMap.containsKey(sampleId))
                {
                    if (!participantVisit.equals(sampleIdMap.get(sampleId)))
                        errors.add("main", new ActionMessage("Error", "Error, Row " + rowNum + " same sample id has multiple participant/visits."));
                }
                else
                    sampleIdMap.put(sampleId, participantVisit);
            }

            Object vialId = row.get(SimpleSpecimenImporter.VIAL_ID);
            if (null == vialId)
                vialId = sampleId;
            if (!vialIds.add(vialId))
                errors.add("main", new ActionMessage("Error", "Error, Row " + rowNum + " duplicate vial id " + vialId));

            Set<String> requiredFields = PageFlowUtil.set(SimpleSpecimenImporter.DRAW_TIMESTAMP);
            if (!getStudy().isDateBased())
                requiredFields.add(SimpleSpecimenImporter.VISIT);
            for (String col : requiredFields)
                if (null == row.get(col))
                    errors.add("main", new ActionMessage("Error", "Error, Row " + rowNum + " does not contain a value for field " + (null == labels.get(col) ? col : labels.get(col))));

            if (errors.size() >= 3)
                break;

            rowNum++;
        }

        if (errors.size() > 0)
            return showUploadSpecimens(form);

        importer.process(getUser(), getStudy().getContainer(), specimenRows);

        String redir = form.getRedir();
        if (null != StringUtils.trimToNull(redir))
            return new ViewForward(new ActionURL(redir));
        else
            return _renderInTemplate(new HtmlView("Samples uploaded successfully."), "Sample Import Complete",
                new NavTree(getStudy().getLabel(), getActionURL().relativeUrl("overview", null, "Study")),
                new NavTree("Specimens", getActionURL().relativeUrl("begin.view", null)));
    }

    private boolean isNullOrBlank(String toCheck)
    {
        return ((toCheck == null) || toCheck.equals(""));
    }

    public static class DisplaySettingsForm extends BeanViewForm<SampleManager.DisplaySettings>
    {
        public DisplaySettingsForm()
        {
            super(SampleManager.DisplaySettings.class);
        }
    }

    @Jpf.Action
    protected Forward manageDisplaySettings(DisplaySettingsForm form) throws Exception
    {
        requiresAdmin();
        // try to get the settings from the form, just in case this is a reshow:
        SampleManager.DisplaySettings settings = form.getBean();
        if (settings == null || settings.getLastVialEnum() == null)
            settings = SampleManager.getInstance().getDisplaySettings(getContainer());

        JspView<SampleManager.DisplaySettings> view =
                new JspView<SampleManager.DisplaySettings>("/org/labkey/study/view/samples/manageDisplay.jsp", settings);
        NavTree[] navTrail = new NavTree[]{
                new NavTree(getStudy().getLabel(), getActionURL().relativeUrl("overview", null, "Study")),
                new NavTree("Manage Study", getActionURL().relativeUrl("manageStudy", null, "Study")),
                new NavTree("Manage Display Settings")};
        return _renderInTemplate(view, "Manage Display Settings", "specimenRequest", navTrail);
    }

    @Jpf.Action
    protected Forward handleUpdateDisplaySettings(DisplaySettingsForm form) throws SQLException, ServletException, URISyntaxException
    {
        SampleManager.DisplaySettings settings = form.getBean();
        SampleManager.getInstance().saveDisplaySettings(getContainer(), settings);
        return new ViewForward(getActionURL().relativeUrl("manageStudy", null, "Study"));
    }

    @Jpf.Action
    protected Forward manageNotifications(ManageNotificationsForm form) throws Exception
    {
        requiresAdmin();
        // try to get the settings from the form, just in case this is a reshow:
        SampleManager.RequestNotificationSettings settings = form.getBean();
        if (settings == null || settings.getReplyTo() == null)
            settings = SampleManager.getInstance().getRequestNotificationSettings(getContainer());

        JspView<SampleManager.RequestNotificationSettings> view =
                new JspView<SampleManager.RequestNotificationSettings>("/org/labkey/study/view/samples/manageNotifications.jsp",
                        settings);
        NavTree[] navTrail = new NavTree[]{
                new NavTree(getStudy().getLabel(), getActionURL().relativeUrl("overview", null, "Study")),
                new NavTree("Manage Study", getActionURL().relativeUrl("manageStudy", null, "Study")),
                new NavTree("Manage Notifications")};
        return _renderInTemplate(view, "Manage Notifications", "specimenRequest", navTrail);
    }


    @Jpf.Action
    protected Forward submitSpecimenImport(PipelineForm form) throws Exception
    {
        requiresPermission(ACL.PERM_ADMIN);

        Container c = getContainer();
        String path = form.getPath();
        File f = null;

        if (path != null)
        {
            PipeRoot root = PipelineService.get().findPipelineRoot(c);
            if (root != null)
                f = root.resolvePath(path);
        }

        if (null == f || !f.exists() || !f.isFile())
        {
            HttpView.throwNotFound();
            return null;
        }
        File logFile = new File(f.getPath() + ".log");
        if (logFile.exists() && logFile.isFile())
            return importSpecimenData(form);

        SpecimenBatch batch = new SpecimenBatch(new ViewBackgroundInfo(this), f);
        batch.submit();

        return HttpView.throwRedirect(PageFlowUtil.urlProvider(PipelineStatusUrls.class).urlBegin(c));
    }

    @Jpf.Action
    protected Forward importSpecimenData(PipelineForm form) throws Exception
    {
        requiresAdmin();
        String path = form.getPath();
        File dataFile = null;

        if (path != null)
        {
            PipeRoot root = PipelineService.get().findPipelineRoot(getContainer());
            if (root != null)
                dataFile = root.resolvePath(path);
        }

        if (null == dataFile || !dataFile.exists() || !dataFile.isFile())
        {
            HttpView.throwNotFound();
            return null;
        }

        List<String> errors = new ArrayList<String>();
        if (!dataFile.canRead())
            errors.add("Can't read data file: " + path);

        boolean previouslyRun = false;
        File logFile = new File(dataFile.getPath() + ".log");
        if (logFile.exists() && logFile.isFile())
        {
            if (form.isDeleteLogfile())
                logFile.delete();
            else
                previouslyRun = true;
        }

        SpecimenBatch batch = new SpecimenBatch(new ViewBackgroundInfo(this), dataFile);
        if (errors.size() == 0)
        {
            List<String> parseErrors = new ArrayList<String>();
            batch.prepareImport(parseErrors);
            for (String error : parseErrors)
                errors.add(error);
        }

        JspView<ImportSpecimensBean> view = new JspView<ImportSpecimensBean>("/org/labkey/study/view/samples/importSpecimens.jsp",
                new ImportSpecimensBean(getContainer(), batch, path, errors, previouslyRun));
        return renderInTemplate(view, getContainer(), "Import Study Batch - " + path);

    }

    @Jpf.Action
    protected Forward autoComplete(AutoCompletionForm form) throws Exception
    {
        requiresPermission(ACL.PERM_READ);
        String column;
        TableInfo tinfo;
        if (SpecimenService.CompletionType.ParticipantId.name().equals(form.getType()))
        {
            tinfo = StudySchema.getInstance().getTableInfoParticipantVisit();
            column = "ParticipantId";
        }
        else if (SpecimenService.CompletionType.SpecimenGlobalUniqueId.name().equals(form.getType()))
        {
            tinfo = StudySchema.getInstance().getTableInfoSpecimen();
            column = "GlobalUniqueId";
        }
        else if (SpecimenService.CompletionType.VisitId.name().equals(form.getType()))
        {
            tinfo = StudySchema.getInstance().getTableInfoParticipantVisit();
            column = "SequenceNum";
        }
        else
            throw new IllegalArgumentException("Completion type " + form.getType() + " not recognized.");

        List<AjaxCompletion> completions = new ArrayList<AjaxCompletion>();
        ResultSet rs = null;
        try
        {
            String sql = "SELECT DISTINCT " + column + " FROM " +
                    tinfo.getSchema().getName() + "." + tinfo.getName() +
                    " WHERE Container = ? AND " + column + " LIKE '" + form.getPrefix() + "%' ORDER BY " + column;
            rs = Table.executeQuery(tinfo.getSchema(), sql, new Object[] { getContainer().getId() });
            while (rs.next())
                completions.add(new AjaxCompletion(rs.getObject(1).toString()));
        }
        finally
        {
            if (rs != null) try { rs.close(); } catch (SQLException e) {}
        }

        return sendAjaxCompletions(completions);
    }


    public static class AutoCompletionForm extends FormData
    {
        private String _prefix;
        private String _type;

        public String getPrefix()
        {
            return _prefix;
        }

        public void setPrefix(String prefix)
        {
            _prefix = prefix;
        }

        public String getType()
        {
            return _type;
        }

        public void setType(String type)
        {
            _type = type;
        }
    }

    public static class ImportSpecimensBean
    {
        private String _path;
        private SpecimenBatch _batch;
        private List<String> _errors;
        private boolean _previouslyRun;
        private Container _container;

        public ImportSpecimensBean(Container container, SpecimenBatch batch,
                                   String path, List<String> errors, boolean previouslyRun)
        {
            _path = path;
            _batch = batch;
            _errors = errors;
            _previouslyRun = previouslyRun;
            _container = container;
        }

        public SpecimenBatch getBatch()
        {
            return _batch;
        }

        public String getPath()
        {
            return _path;
        }

        public List<String> getErrors()
        {
            return _errors;
        }

        public boolean isPreviouslyRun()
        {
            return _previouslyRun;
        }

        public Container getContainer()
        {
            return _container;
        }
    }

    private Forward displayManagementSubpage(String jsp, String title, String helpTopic) throws Exception
    {
        requiresAdmin();
        JspView<Study> view = new JspView<Study>("/org/labkey/study/view/samples/" + jsp + ".jsp", getStudy());
        NavTree[] navTrail = new NavTree[]{
            new NavTree(getStudy().getLabel(), getActionURL().relativeUrl("overview", null, "Study")),
            new NavTree("Manage Study", getActionURL().relativeUrl("manageStudy", null, "Study")),
            new NavTree(title)};
        return _renderInTemplate(view, title, helpTopic, navTrail);
    }

    @Jpf.Action
    protected Forward search(SearchForm form) throws Exception
    {
        requiresPermission(ACL.PERM_READ);
        ActionURL url = cloneActionURL();
        url.setAction("samples");
        url.deleteParameters();
        url.addParameter("showVials", Boolean.toString(form.isShowVials()));
        for (SearchForm.SearchParam param : form.getSearchParams())
        {
            if (param.getCompareType() != null && param.getCompareType().length() > 0)
            {
                CompareType compare = CompareType.valueOf(param.getCompareType());
                if (!compare.isDataValueRequired() || (param.getValue() != null && param.getValue().length() > 0))
                    url.addParameter(param.getColumnName() + "~" + compare.getUrlKey(), param.getValue());
            }
        }
        return new ViewForward(url);
    }

    @Jpf.Action
    protected Forward showSearch(ShowSearchForm form) throws Exception
    {
        requiresPermission(ACL.PERM_READ);
        Study study = getStudy();
        if (null == study)
            HttpView.throwNotFound("No study exists in this folder.");
        NavTree[] navTrail = new NavTree[]{
                new NavTree(study.getLabel(), getActionURL().relativeUrl("overview", null, "Study")),
                new NavTree("Specimens", getActionURL().relativeUrl("samples", null)),
                new NavTree((form.isShowVials() ? "Vial" : "Specimen") + " Search")};
        JspView<SearchBean> view = new JspView<SearchBean>("/org/labkey/study/view/samples/search.jsp",
                new SearchBean(getViewContext(), form.isShowVials()));
        return _renderInTemplate(view, (form.isShowVials() ? "Vial" : "Specimen") + " Search", navTrail);
    }

    public static class ShowSearchForm extends FormData
    {
        private boolean _showVials;

        public boolean isShowVials()
        {
            return _showVials;
        }

        public void setShowVials(boolean showVials)
        {
            _showVials = showVials;
        }
    }
    public static class SearchForm extends ShowSearchForm
    {
        private SearchParam[] _searchParams;
        public static class SearchParam
        {
            private String _compareType;
            private String _value;
            private String _columnName;

            public String getCompareType()
            {
                return _compareType;
            }

            public void setCompareType(String compareType)
            {
                _compareType = compareType;
            }

            public String getValue()
            {
                return _value;
            }

            public void setValue(String value)
            {
                _value = value;
            }

            public String getColumnName()
            {
                return _columnName;
            }

            public void setColumnName(String columnName)
            {
                _columnName = columnName;
            }
        }
        public SearchForm()
        {
            _searchParams = new SearchParam[100];
            for (int i = 0; i < 100; i++)
                _searchParams[i] = new SearchParam();
        }

        public SearchParam[] getSearchParams()
        {
            return _searchParams;
        }

        public void setSearchParams(SearchParam[] searchParams)
        {
            _searchParams = searchParams;
        }

    }

    public static class SearchBean
    {
        private boolean _detailsView;
        private List<DisplayColumn> _displayColumns;
        private ActionURL _baseViewURL;
        private String _dataRegionName;
        private Container _container;
        private Map<String, DisplayColumnInfo> _defaultDetailCols;
        private Map<String, DisplayColumnInfo> _defaultSummaryCols;

        private static class DisplayColumnInfo
        {
            private boolean _displayByDefault;
            private boolean _displayAsPickList;
            private boolean _forceDistinctQuery;

            public DisplayColumnInfo(boolean displayByDefault, boolean displayAsPickList)
            {
                this(displayByDefault, displayAsPickList, false);
            }

            public DisplayColumnInfo(boolean displayByDefault, boolean displayAsPickList, boolean forceDistinctQuery)
            {
                _displayByDefault = displayByDefault;
                _displayAsPickList = displayAsPickList;
                _forceDistinctQuery = forceDistinctQuery;
            }

            public boolean isDisplayAsPickList()
            {
                return _displayAsPickList;
            }

            public boolean isDisplayByDefault()
            {
                return _displayByDefault;
            }

            public boolean isForceDistinctQuery()
            {
                return _forceDistinctQuery;
            }
        }


        public SearchBean(ViewContext context, boolean detailsView)
        {
            _container = context.getContainer();
            _detailsView = detailsView;
            SpecimenQueryView view = SpecimenQueryView.createView(context, detailsView ? SpecimenQueryView.ViewType.VIALS :
                    SpecimenQueryView.ViewType.SUMMARY);
            _displayColumns = view.getDisplayColumns();
            _dataRegionName = view.getDataRegionName();
            _baseViewURL = view.getBaseViewURL();

            _defaultDetailCols = new HashMap<String, DisplayColumnInfo>();
            _defaultDetailCols.put("PrimaryType", new DisplayColumnInfo(true, true));
            _defaultDetailCols.put("AdditiveType", new DisplayColumnInfo(true, true));
            _defaultDetailCols.put("SiteName", new DisplayColumnInfo(true, true, true));
            _defaultDetailCols.put("Visit", new DisplayColumnInfo(true, true));
            _defaultDetailCols.put("ParticipantId", new DisplayColumnInfo(true, true, true));
            _defaultDetailCols.put("Available", new DisplayColumnInfo(true, true));
            _defaultDetailCols.put("SiteLdmsCode", new DisplayColumnInfo(false, true));
            _defaultDetailCols.put("DerivativeType", new DisplayColumnInfo(true, true));
            _defaultDetailCols.put("VolumeUnits", new DisplayColumnInfo(false, true));
            _defaultDetailCols.put("GlobalUniqueId", new DisplayColumnInfo(true, false));
            _defaultDetailCols.put("Clinic", new DisplayColumnInfo(true, true));

            _defaultSummaryCols = new HashMap<String, DisplayColumnInfo>();
            _defaultSummaryCols.put("PrimaryType", new DisplayColumnInfo(true, true));
            _defaultSummaryCols.put("AdditiveType", new DisplayColumnInfo(true, true));
            _defaultSummaryCols.put("DerivativeType", new DisplayColumnInfo(true, true));
            _defaultSummaryCols.put("SiteName", new DisplayColumnInfo(true, true, true));
            _defaultSummaryCols.put("Visit", new DisplayColumnInfo(true, true));
            _defaultSummaryCols.put("ParticipantId", new DisplayColumnInfo(true, true, true));
            _defaultSummaryCols.put("Available", new DisplayColumnInfo(true, false));
            _defaultSummaryCols.put("VolumeUnits", new DisplayColumnInfo(false, true));
            _defaultSummaryCols.put("SpecimenNumber", new DisplayColumnInfo(true, false));
            _defaultSummaryCols.put("Clinic", new DisplayColumnInfo(true, true));
        }

        public List<DisplayColumn> getDisplayColumns()
        {
            return _displayColumns;
        }

        public boolean isDetailsView()
        {
            return _detailsView;
        }

        public ActionURL getBaseViewURL()
        {
            return _baseViewURL;
        }

        public String getDataRegionName()
        {
            return _dataRegionName;
        }

        public boolean isDefaultColumn(ColumnInfo info)
        {
            Map<String, DisplayColumnInfo> defaultColumns = isDetailsView() ? _defaultDetailCols : _defaultSummaryCols;
            DisplayColumnInfo colInfo = defaultColumns.get(info.getName());
            return colInfo != null && colInfo.isDisplayByDefault();
        }

        public boolean isPickListColumn(ColumnInfo info)
        {
            Map<String, DisplayColumnInfo> defaultColumns = isDetailsView() ? _defaultDetailCols : _defaultSummaryCols;
            DisplayColumnInfo colInfo = defaultColumns.get(info.getName());
            return colInfo != null && colInfo.isDisplayAsPickList();
        }

        public List<String> getPickListValues(ColumnInfo info) throws SQLException
        {
            Map<String, DisplayColumnInfo> defaultColumns = isDetailsView() ? _defaultDetailCols : _defaultSummaryCols;
            DisplayColumnInfo colInfo = defaultColumns.get(info.getName());
            assert colInfo != null : info.getName() + " is not a picklist column.";
            return SampleManager.getInstance().getDistinctColumnValues(_container, info, colInfo.isForceDistinctQuery());
        }
    }
}
