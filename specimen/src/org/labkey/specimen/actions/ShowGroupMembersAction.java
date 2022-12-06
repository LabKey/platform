/*
 * Copyright (c) 2014-2019 LabKey Corporation
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
package org.labkey.specimen.actions;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityUrls;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.UserManagementPermission;
import org.labkey.api.specimen.location.LocationImpl;
import org.labkey.api.specimen.location.LocationManager;
import org.labkey.api.study.StudyUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.specimen.actions.SpecimenController.ManageActorsAction;
import org.labkey.specimen.model.SpecimenRequestActor;
import org.labkey.specimen.requirements.SpecimenRequestRequirementProvider;
import org.labkey.specimen.security.permissions.ManageSpecimenActorsPermission;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletException;
import java.util.ArrayList;
import java.util.List;

@RequiresPermission(ManageSpecimenActorsPermission.class)
public class ShowGroupMembersAction extends FormViewAction<ShowGroupMembersAction.UpdateGroupForm>
{
    private SpecimenRequestActor _actor;
    private LocationImpl _location;

    public static ActionURL getShowGroupMembersURL(Container c, int rowId, @Nullable Integer locationId, @Nullable ActionURL returnUrl)
    {
        ActionURL url = new ActionURL(ShowGroupMembersAction.class, c);
        url.addParameter("id", Integer.toString(rowId));
        if (locationId != null)
            url.addParameter("locationId", locationId);
        if (returnUrl != null)
            url.addReturnURL(returnUrl);

        return url;
    }

    @Override
    public void validateCommand(UpdateGroupForm target, Errors errors)
    {
    }

    @Override
    public ModelAndView getView(UpdateGroupForm form, boolean reshow, BindException errors)
    {
        SpecimenRequestActor actor = getActor(form);
        LocationImpl location = getLocation(form);

        if (actor == null)
            throw new NotFoundException();

        User[] members = actor.getMembers(location);

        return new JspView<>("/org/labkey/specimen/view/groupMembers.jsp",
                new GroupMembersBean(actor, location, members, form.getReturnActionURL()), errors);
    }

    @Override
    public boolean handlePost(UpdateGroupForm form, BindException errors) throws Exception
    {
        String[] emailsToDelete = form.getDelete();
        SpecimenRequestActor actor = getActor(form);
        LocationImpl location = getLocation(form);

        if (emailsToDelete != null && emailsToDelete.length > 0)
        {
            List<String> invalidEmails = new ArrayList<>();
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
                actor.removeMembers(location, users);
            }
            else
                throw new ServletException("Invalid email address" + (invalidEmails.size() > 1 ? "es: " : ": ") + StringUtils.join(invalidEmails.toArray()));
        }

        if (form.getNames() != null)
        {
            String[] names = form.getNames().split("\n");
            List<String> invalidEmails = new ArrayList<>();
            List<ValidEmail> emails = SecurityManager.normalizeEmails(names, invalidEmails);

            for (String rawEmail : invalidEmails)
            {
                // Ignore lines of all whitespace, otherwise show an error.
                if (!"".equals(rawEmail.trim()))
                {
                    errors.reject(SpringActionController.ERROR_MSG, "Could not add user " + rawEmail.trim() + ": Invalid email address");
                }
            }

            List<User> newMembers = new ArrayList<>();

            for (ValidEmail email : emails)
            {
                if (getUser().hasRootPermission(UserManagementPermission.class))
                {
                    SecurityManager.addUser(getViewContext(), email, form.isSendEmail(), null);
                    newMembers.add(UserManager.getUser(email));
                }
                else
                {
                    User user = UserManager.getUser(email);

                    if (user == null)
                    {
                        errors.reject(SpringActionController.ERROR_MSG, email.getEmailAddress() + " is not a registered system user.");
                    }
                    else
                    {
                        newMembers.add(UserManager.getUser(email));
                    }
                }
            }

            actor.addMembers(location, newMembers.toArray(new User[newMembers.size()]));
        }

        return !errors.hasErrors();
    }


    @Override
    public ActionURL getSuccessURL(UpdateGroupForm form)
    {
        return form.getReturnActionURL(new ActionURL(ManageActorsAction.class, getContainer()));
    }

    @Override
    public void addNavTrail(NavTree root)
    {
        PageFlowUtil.urlProvider(StudyUrls.class).addManageStudyNavTrail(root, getContainer(), getUser());

        if (_location != null)
            root.addChild("Manage Actors", new ActionURL(ManageActorsAction.class, getContainer()).addParameter("showMemberSites", _actor.getRowId()));
        else
            root.addChild("Manage Actors", new ActionURL(ManageActorsAction.class, getContainer()));

        String title = _actor.getLabel();
        if (_location != null)
            title += ", " + _location.getLabel();

        root.addChild(title);
    }

    private SpecimenRequestActor getActor(UpdateGroupForm form)
    {
        if (_actor == null)
            _actor = SpecimenRequestRequirementProvider.get().getActor(getContainer(), form.getId());

        return _actor;
    }

    private LocationImpl getLocation(UpdateGroupForm form)
    {
        if (_location == null && form.getLocationId() != null)
            _location = LocationManager.get().getLocation(getContainer(), form.getLocationId());

        return _location;
    }

    public static class UpdateGroupForm extends IdForm
    {
        private Integer _locationId;
        private String[] _delete;
        private boolean _sendEmail;
        private String _names;

        public Integer getLocationId()
        {
            return _locationId;
        }

        public void setLocationId(Integer locationId)
        {
            _locationId = locationId;
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
    }

    public static class GroupMembersBean
    {
        private final SpecimenRequestActor _actor;
        private final LocationImpl _location;
        private final User[] _members;
        private final ActionURL _returnUrl;

        public GroupMembersBean(SpecimenRequestActor actor, LocationImpl location, User[] members, ActionURL returnUrl)
        {
            _actor = actor;
            _location = location;
            _members = members;
            _returnUrl = returnUrl;
        }

        public SpecimenRequestActor getActor()
        {
            return _actor;
        }

        public User[] getMembers()
        {
            return _members;
        }

        public LocationImpl getLocation()
        {
            return _location;
        }

        public ActionURL getReturnUrl()
        {
            return _returnUrl;
        }

        public ActionURL getCompleteUsersPrefix()
        {
            return PageFlowUtil.urlProvider(SecurityUrls.class).getCompleteUserURL(_actor.getContainer());
        }
    }
}