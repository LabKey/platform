/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.announcements.model;

import org.labkey.api.announcements.CommSchema;
import org.labkey.api.data.Container;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.Table;
import org.labkey.api.message.settings.MessageConfigService;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.ReadPermission;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Jan 19, 2011
 * Time: 5:08:44 PM
 */
public class MessageConfigManager
{
    private static CommSchema _comm = CommSchema.getInstance();
    public static final int EMAIL_PREFERENCE_DEFAULT = -1;
    public static final int EMAIL_FORMAT_HTML = 1;
    public static final int PAGE_TYPE_MESSAGE = 0;

    //returns explicit record from EmailPrefs table for this user if there is one.
    public static MessageConfigService.UserPreference[] getUserEmailPrefRecord(Container c, User user) throws SQLException
    {
        return _getUserEmailPrefRecord(c, user, null);
    }

    public static EmailPref getUserEmailPrefRecord(Container c, User user, String type) throws SQLException
    {
        EmailPref[] prefs = _getUserEmailPrefRecord(c, user, type);

        if (prefs != null)
            return prefs[0];
        else
            return null;
    }

    private static EmailPref[] _getUserEmailPrefRecord(Container c, User user, String type) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition("Container", c.getId());
        filter.addCondition("UserId", user.getUserId());

        if (type != null)
            filter.addCondition("Type", type);

        //return records only for those users who have explicitly set a preference for this container.
        EmailPref[] emailPrefs = Table.select(
                _comm.getTableInfoEmailPrefs(),
                Table.ALL_COLUMNS,
                filter,
                null,
                EmailPref.class
                );

        if (emailPrefs.length == 0)
            return null;
        else
            return emailPrefs;
    }

    public static EmailPref[] getUserEmailPrefs(Container c, String type) throws SQLException
    {
        SQLFragment sql = new SQLFragment();
        List<EmailPref> prefs = new ArrayList<EmailPref>();

        sql.append("SELECT u.userId, email, firstName, lastName, displayName, emailOptionId, emailFormatId, type, lastModifiedBy, container FROM ");
        sql.append(CoreSchema.getInstance().getTableInfoUsers(), "u").append(" LEFT JOIN ");
        sql.append(_comm.getTableInfoEmailPrefs(), "prefs").append(" ON u.userId = prefs.userId ");
        sql.append("AND type = ? AND container = ?");

        EmailPref[] p = Table.executeQuery(_comm.getSchema(), sql.getSQL(), new Object[]{type, c.getId()}, EmailPref.class);

        for (EmailPref ep : p)
        {
            User user = ep.getUser();
            if (c.hasPermission(user, ReadPermission.class) && user.isActive())
                prefs.add(ep);
        }

        return prefs.toArray(new EmailPref[prefs.size()]);
    }

    public static void saveEmailPreference(User currentUser, Container c, User projectUser, String type, int emailPreference) throws SQLException
    {
        //determine whether user has record in EmailPrefs table.
        EmailPref emailPref = getUserEmailPrefRecord(c, projectUser, type);

        //insert new if user preference record does not yet exist
        if (null == emailPref && emailPreference != EMAIL_PREFERENCE_DEFAULT)
        {
            emailPref = new EmailPref();
            emailPref.setContainer(c.getId());
            emailPref.setUserId(projectUser.getUserId());
            emailPref.setEmailFormatId(EMAIL_FORMAT_HTML);
            emailPref.setEmailOptionId(emailPreference);
            emailPref.setPageTypeId(PAGE_TYPE_MESSAGE);
            emailPref.setLastModifiedBy(currentUser.getUserId());
            emailPref.setType(type);
            Table.insert(currentUser, _comm.getTableInfoEmailPrefs(), emailPref);
        }
        else
        {
            if (emailPreference == EMAIL_PREFERENCE_DEFAULT)
            {
                //if preference has been set back to default, delete user's email pref record
                SimpleFilter filter = new SimpleFilter();
                filter.addCondition("UserId", projectUser.getUserId());
                filter.addCondition("Type", type);
                Table.delete(_comm.getTableInfoEmailPrefs(), filter);
            }
            else
            {
                //otherwise update if it already exists
                emailPref.setEmailOptionId(emailPreference);
                emailPref.setLastModifiedBy(currentUser.getUserId());
                Table.update(currentUser, _comm.getTableInfoEmailPrefs(), emailPref,
                        new Object[]{c.getId(), projectUser.getUserId(), type});
            }
        }
    }

    //delete all user records regardless of container
    public static void deleteUserEmailPref(User user, List<Container> containerList) throws SQLException
    {
        if (containerList == null)
        {
            Table.delete(_comm.getTableInfoEmailPrefs(),
                    new SimpleFilter("UserId", user.getUserId()));
        }
        else
        {
            SimpleFilter filter = new SimpleFilter();
            filter.addCondition("UserId", user.getUserId());
            StringBuilder whereClause = new StringBuilder("Container IN (");
            for (int i = 0; i < containerList.size(); i++)
            {
                Container c = containerList.get(i);
                whereClause.append("'");
                whereClause.append(c.getId());
                whereClause.append("'");
                if (i < containerList.size() - 1)
                    whereClause.append(", ");
            }
            whereClause.append(")");
            filter.addWhereClause(whereClause.toString(), null);

            Table.delete(_comm.getTableInfoEmailPrefs(), filter);
        }
    }

    public static EmailOption[] getEmailOptions(String type) throws SQLException
    {
        SimpleFilter filter = null;

        if (type != null)
            filter = new SimpleFilter("Type", type);

        return Table.select(_comm.getTableInfoEmailOptions(), Table.ALL_COLUMNS, filter, new Sort("EmailOptionId"), EmailOption.class);
    }

    public static EmailOption getEmailOption(int optionId) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("EmailOptionId", optionId);
        EmailOption[] options = Table.select(_comm.getTableInfoEmailOptions(), Table.ALL_COLUMNS, filter, null, EmailOption.class);

        assert (options.length <= 1);
        if (options.length == 0)
            return null;
        else
            return options[0];
    }

    public static class EmailPref implements MessageConfigService.UserPreference
    {
        String _container;
        int _userId;
        String _email;
        String _firstName;
        String _lastName;
        String _displayName;
        Integer _emailOptionId;
        Integer _emailFormatId;
        String _emailOption;
        Integer _lastModifiedBy;
        String _lastModifiedByName;
        String _type;

        boolean _projectMember;

        int _pageTypeId;

        public String getLastModifiedByName()
        {
            return _lastModifiedByName;
        }

        public void setLastModifiedByName(String lastModifiedByName)
        {
            _lastModifiedByName = lastModifiedByName;
        }

        public Integer getLastModifiedBy()
        {
            return _lastModifiedBy;
        }

        public void setLastModifiedBy(Integer lastModifiedBy)
        {
            _lastModifiedBy = lastModifiedBy;
        }

        public String getEmail()
        {
            return _email;
        }

        public void setEmail(String email)
        {
            _email = email;
        }

        public String getContainer()
        {
            return _container;
        }

        public void setContainer(String container)
        {
            _container = container;
        }

        public String getEmailOption()
        {
            return _emailOption;
        }

        public void setEmailOption(String emailOption)
        {
            _emailOption = emailOption;
        }

        public String getFirstName()
        {
            return _firstName;
        }

        public void setFirstName(String firstName)
        {
            _firstName = firstName;
        }

        public String getLastName()
        {
            return _lastName;
        }

        public void setLastName(String lastName)
        {
            _lastName = lastName;
        }

        public String getDisplayName()
        {
            return _displayName;
        }

        public void setDisplayName(String displayName)
        {
            _displayName = displayName;
        }

        public Integer getEmailFormatId()
        {
            return _emailFormatId;
        }

        public void setEmailFormatId(Integer emailFormatId)
        {
            _emailFormatId = emailFormatId;
        }

        public Integer getEmailOptionId()
        {
            return _emailOptionId;
        }

        public void setEmailOptionId(Integer emailOptionId)
        {
            _emailOptionId = emailOptionId;
        }

        public int getUserId()
        {
            return _userId;
        }

        public void setUserId(int userId)
        {
            _userId = userId;
        }

        public int getPageTypeId()
        {
            return _pageTypeId;
        }

        public void setPageTypeId(int pageTypeId)
        {
            _pageTypeId = pageTypeId;
        }

        public boolean isProjectMember()
        {
            return _projectMember;
        }

        public void setProjectMember(boolean projectMember)
        {
            _projectMember = projectMember;
        }

        public User getUser()
        {
            return UserManager.getUser(_userId);
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


    public static class EmailOption implements MessageConfigService.NotificationOption
    {
        int _emailOptionId;
        String _emailOption;
        String _type;

        public String getEmailOption()
        {
            return _emailOption;
        }

        public void setEmailOption(String emailOption)
        {
            _emailOption = emailOption;
        }

        public int getEmailOptionId()
        {
            return _emailOptionId;
        }

        public void setEmailOptionId(int emailOptionId)
        {
            _emailOptionId = emailOptionId;
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
}
