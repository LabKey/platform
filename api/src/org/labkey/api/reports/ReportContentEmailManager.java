/*
 * Copyright (c) 2014 LabKey Corporation
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
package org.labkey.api.reports;

import org.labkey.api.data.Container;
import org.labkey.api.notification.EmailPref;
import org.labkey.api.notification.EmailService;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ReportContentEmailManager
{
    public static class ReportContentEmailPref extends EmailPref
    {
        public static final String NONE = "";
        public static final String FOLDER_DEFAULT = NONE;

        @Override
        public String getId()
        {
            return "ReportContentEmailPref";
        }

        @Override
        public String getDefaultValue()
        {
            return FOLDER_DEFAULT;
        }

        @Override
        public String getValue(String value, String defaultValue)
        {
            if (getDefaultValue().equals(value))
                return defaultValue;
            else
                return value;
        }
    }

    // Not really an EmailPref but a way to record the set of users subscribed to at least 1 category in a container
    public static class ReportContentUserList extends EmailPref
    {
        public static final String NONE = "";
        public static final String FOLDER_DEFAULT = NONE;

        @Override
        public String getId()
        {
            return "ReportContentUserList";
        }

        @Override
        public String getDefaultValue()
        {
            return FOLDER_DEFAULT;
        }

        @Override
        public String getValue(String value, String defaultValue)
        {
            if (getDefaultValue().equals(value))
                return defaultValue;
            else
                return value;
        }
    }

    // Get set of report/dataset categories in the container that user has subscribed to
    public static Set<Integer> getSubscriptionSet(Container container, User user)
    {
        // string is semicolon-delimited list or rowIds for which this user is subscribed
        String prefString = EmailService.get().getEmailPref(user, container, new ReportContentEmailPref());
        return makeIntegerSetFromDelimitedString(prefString, ";");
    }

    public static void setSubscriptionSet(Container container, User user, Set<Integer> subscriptionSet)
    {
        String prefString = makeDelimitedStringFromIntegerSet(subscriptionSet, ";");
        EmailService.get().setEmailPref(user, container, new ReportContentEmailPref(), prefString);
        updateSubscriptionUserList(container, user, !subscriptionSet.isEmpty());
    }

    public static Map<Integer, Set<Integer>> getUserCategoryMap(Container container)
    {
        String userListString = EmailService.get().getDefaultEmailPref(container, new ReportContentUserList());
        Set<Integer> userList = makeIntegerSetFromDelimitedString(userListString, ";");

        Map<Integer, Set<Integer>> userCategoryMap = new HashMap<>();
        for (Integer userId : userList)
        {
            if (null != userId)
            {
                User user = UserManager.getUser(userId);
                if (null != user)
                {
                    Set<Integer> categories = getSubscriptionSet(container, user);
                    if (!categories.isEmpty())
                        userCategoryMap.put(userId, categories);
                }
            }
        }
        return userCategoryMap;
    }

    private static void updateSubscriptionUserList(Container container, User user, boolean addUser)
    {
        ReportContentUserList reportContentUserList = new ReportContentUserList();
        String userListString = EmailService.get().getDefaultEmailPref(container, reportContentUserList);
        Set<Integer> userList = makeIntegerSetFromDelimitedString(userListString, ";");

        if (addUser)
            userList.add(user.getUserId());
        else    // this user has no more subscriptions
            userList.remove(user.getUserId());

        String newUserListString = makeDelimitedStringFromIntegerSet(userList, ";");
        EmailService.get().setDefaultEmailPref(container, reportContentUserList, newUserListString);
    }

    private static Set<Integer> makeIntegerSetFromDelimitedString(String delimitedString, String delim)
    {
        Set<Integer> subscriptionSet = new HashSet<>();
        String[] items = delimitedString.split(delim);
        for (String item : items)
        {
            if (!item.isEmpty())
            {
                Integer rowId = Integer.valueOf(item);
                if (null != rowId)
                    subscriptionSet.add(rowId);
            }
        }
        return subscriptionSet;
    }

    private static String makeDelimitedStringFromIntegerSet(Set<Integer> inputSet, String delim)
    {
        String resultString = "";
        String localDelim = "";
        for (Integer rowId : inputSet)
        {
            resultString += localDelim + rowId.toString();
            localDelim = delim;
        }
        return resultString;
    }
}
