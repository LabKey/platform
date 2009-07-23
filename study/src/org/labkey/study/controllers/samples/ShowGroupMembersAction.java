/*
 * Copyright (c) 2009 LabKey Corporation
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

import org.apache.commons.lang.StringUtils;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.security.*;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.study.Study;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.study.SampleManager;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.SampleRequestActor;
import org.labkey.study.model.SiteImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.security.permissions.ManageSpecimenActorsPermission;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletException;
import java.util.ArrayList;
import java.util.List;
import java.sql.SQLException;

@RequiresPermissionClass(ManageSpecimenActorsPermission.class)
public class ShowGroupMembersAction extends FormViewAction<ShowGroupMembersAction.UpdateGroupForm>
{
    private SampleRequestActor _actor;
    private SiteImpl _site;

    public void validateCommand(UpdateGroupForm target, Errors errors)
    {
    }

    public ModelAndView getView(UpdateGroupForm form, boolean reshow, BindException errors) throws Exception
    {
        SampleRequestActor actor = getActor(form);
        SiteImpl site = getSite(form);

        if (actor == null)
            return HttpView.throwNotFound();

        User[] members = actor.getMembers(site);
        JspView<GroupMembersBean> view = new JspView<GroupMembersBean>("/org/labkey/study/view/samples/groupMembers.jsp",
                new GroupMembersBean(actor, site, members, form.getReturnUrl()));

        return view;
    }

    public boolean handlePost(UpdateGroupForm form, BindException errors) throws Exception
    {
        String[] emailsToDelete = form.getDelete();
        SampleRequestActor actor = getActor(form);
        SiteImpl site = getSite(form);

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
                    errors.rejectValue(SpringSpecimenController.ERROR_MSG, "Could not add user " + rawEmail.trim() + ": Invalid email address");
                }
            }

            List<User> newMembers = new ArrayList<User>();
            for (ValidEmail email : emails)
            {
                if (getViewContext().getUser().isAdministrator())
                {
                    String result = SecurityManager.addUser(getViewContext(), email, form.isSendEmail(), null, null);
                    newMembers.add(UserManager.getUser(email));
                }
                else
                {
                    User user = UserManager.getUser(email);
                    if (user == null)
                        errors.rejectValue(SpringSpecimenController.ERROR_MSG, email.getEmailAddress() + " is not a registered system user.");
                    else
                    {
                        newMembers.add(UserManager.getUser(email));
                    }
                }
            }
            actor.addMembers(site, newMembers.toArray(new User[newMembers.size()]));
        }

        return !errors.hasErrors();
    }

    public ActionURL getSuccessURL(UpdateGroupForm form)
    {
        String returnURL = form.getReturnUrl();
        if (returnURL != null && returnURL.length() > 0)
            return new ActionURL(returnURL);
        else
        {
            return new ActionURL(SpringSpecimenController.ManageActorsAction.class, getViewContext().getContainer());
        }
    }

    public NavTree appendNavTrail(NavTree root)
    {
        Study study = StudyManager.getInstance().getStudy(getViewContext().getContainer());

        root.addChild(study.getLabel(), new ActionURL(StudyController.OverviewAction.class, getViewContext().getContainer()));
        root.addChild("Manage Study", new ActionURL(StudyController.ManageStudyAction.class, getViewContext().getContainer()));

        if (_site != null)
            root.addChild("Manage Actors", new ActionURL(SpringSpecimenController.ManageActorsAction.class, getViewContext().getContainer()).addParameter("showMemberSites", _actor.getRowId()));
        else
            root.addChild("Manage Actors", new ActionURL(SpringSpecimenController.ManageActorsAction.class, getViewContext().getContainer()));

        String title = _actor.getLabel();
        if (_site != null)
            title += ", " + _site.getLabel();

        root.addChild(title);

        return root;
    }

    private SampleRequestActor getActor(UpdateGroupForm form)
    {
        if (_actor == null)
            _actor = SampleManager.getInstance().getRequirementsProvider().getActor(getViewContext().getContainer(), form.getId());

        return _actor;
    }

    private SiteImpl getSite(UpdateGroupForm form) throws SQLException
    {
        if (_site == null && form.getSiteId() != null)
            _site = StudyManager.getInstance().getSite(getViewContext().getContainer(), form.getSiteId());

        return _site;
    }

    public static class UpdateGroupForm extends SpringSpecimenController.IdForm
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

    public static class GroupMembersBean
    {
        private SampleRequestActor _actor;
        private SiteImpl _site;
        private User[] _members;
        private String _ldapDomain;
        private String _returnUrl;

        public GroupMembersBean(SampleRequestActor actor, SiteImpl site, User[] members, String returnUrl)
        {
            _actor = actor;
            _site = site;
            _members = members;
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

        public SiteImpl getSite()
        {
            return _site;
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
}