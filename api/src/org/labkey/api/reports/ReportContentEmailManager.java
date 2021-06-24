/*
 * Copyright (c) 2014-2015 LabKey Corporation
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
import org.labkey.api.reports.model.NotificationInfo;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class ReportContentEmailManager
{
    // We always store one of these as the first "category" in the delimited category string
    public enum NotifyOption
    {
        NONE
        {
            @Override
            public int getSpecialCategoryId() {return -1;}
            @Override
            public Map<Integer, List<NotificationInfo>> getReportsForUserByCategory(Map<Integer, List<NotificationInfo>> reportInfosByCategory,
                                                                                    SortedSet<Integer> categories, SortedSet<Integer> allCategories)
            {
                return Collections.emptyMap();
            }
        },
        ALL
        {
            @Override
            public int getSpecialCategoryId() {return -2;}
            @Override
            public Map<Integer, List<NotificationInfo>> getReportsForUserByCategory(Map<Integer, List<NotificationInfo>> reportInfosByCategory,
                                                                                    SortedSet<Integer> categories, SortedSet<Integer> allCategories)
            {
                return CATEGORY.getReportsForUserByCategory(reportInfosByCategory, allCategories, allCategories);
            }
        },
        CATEGORY
        {
            @Override
            public int getSpecialCategoryId() {return -3;}
            @Override
            public Map<Integer, List<NotificationInfo>> getReportsForUserByCategory(Map<Integer, List<NotificationInfo>> reportInfosByCategory,
                                                                                    SortedSet<Integer> categories, SortedSet<Integer> allCategories)
            {
                Map<Integer, List<NotificationInfo>> reportsForUserInitial = new HashMap<>();
                for (Integer category : categories)
                {
                    if (null != category)
                    {
                        List<NotificationInfo> reportsForCategory = reportInfosByCategory.get(category);
                        if (null != reportsForCategory)
                            reportsForUserInitial.put(category, reportsForCategory);
                    }
                }
                return reportsForUserInitial;
            }
        },
        DATASET
        {
            @Override
            public int getSpecialCategoryId() {return -4;}
            @Override
            public Map<Integer, List<NotificationInfo>> getReportsForUserByCategory(Map<Integer, List<NotificationInfo>> reportInfosByCategory,
                                                                                    SortedSet<Integer> datasets, SortedSet<Integer> allCategories)
            {
                Map<Integer, List<NotificationInfo>> reports = new HashMap<>();
                for (Map.Entry<Integer, List<NotificationInfo>> entry : reportInfosByCategory.entrySet())
                {
                    List<NotificationInfo> matchedReports = entry.getValue().stream()
                            .filter(ni -> datasets.contains(ni.getRowId()))
                            .collect(Collectors.toList());

                    if (!matchedReports.isEmpty())
                        reports.put(entry.getKey(), matchedReports);
                }
                return reports;
            }
        };

        public abstract int getSpecialCategoryId();
        public static NotifyOption getNotifyOption(String str)
        {
            for (NotifyOption option : NotifyOption.values())
            {
                if (option.name().equalsIgnoreCase(str))
                    return option;
            }
            return NONE;        // default to NONE even if str is bad
        }

        public abstract Map<Integer, List<NotificationInfo>> getReportsForUserByCategory(Map<Integer, List<NotificationInfo>> reportInfosByCategory,
                                                                                         SortedSet<Integer> categories, SortedSet<Integer> allCategories);
    }

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

    public static NotifyOption removeNotifyOption(SortedSet<Integer> subscriptionSet)
    {
        for (NotifyOption option : NotifyOption.values())
        {
            if (subscriptionSet.remove(option.getSpecialCategoryId()))
                return option;
        }
        return NotifyOption.NONE;
    }

    // Get set of report/dataset categories in the container that user has subscribed to
    public static SortedSet<Integer> getSubscriptionSet(Container container, User user)
    {
        // string is semicolon-delimited list or rowIds for which this user is subscribed
        String prefString = EmailService.get().getEmailPref(user, container, new ReportContentEmailPref());
        return makeIntegerSetFromDelimitedString(prefString, ";");
    }

    public static void setSubscriptionSet(Container container, User user, Set<Integer> selections)
    {
        String prefString = makeDelimitedStringFromIntegerSet(selections, ";");
        EmailService.get().setEmailPref(user, container, new ReportContentEmailPref(), prefString);
        boolean addUser = selections.size() > 1 || (1 == selections.size() && !selections.contains(NotifyOption.NONE.getSpecialCategoryId()));
        updateSubscriptionUserList(container, user, addUser);
    }

    public static Map<Integer, SortedSet<Integer>> getUserCategoryMap(Container container)
    {
        String userListString = EmailService.get().getDefaultEmailPref(container, new ReportContentUserList());
        SortedSet<Integer> userList = makeIntegerSetFromDelimitedString(userListString, ";");

        Map<Integer, SortedSet<Integer>> userCategoryMap = new HashMap<>();
        for (Integer userId : userList)
        {
            if (null != userId)
            {
                User user = UserManager.getUser(userId);
                if (null != user)
                {
                    SortedSet<Integer> categories = getSubscriptionSet(container, user);
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
        SortedSet<Integer> userList = makeIntegerSetFromDelimitedString(userListString, ";");

        if (addUser)
            userList.add(user.getUserId());
        else    // this user has no more subscriptions
            userList.remove(user.getUserId());

        String newUserListString = makeDelimitedStringFromIntegerSet(userList, ";");
        EmailService.get().setDefaultEmailPref(container, reportContentUserList, newUserListString);
    }

    private static SortedSet<Integer> makeIntegerSetFromDelimitedString(String delimitedString, String delim)
    {
        SortedSet<Integer> subscriptionSet = new TreeSet<>();
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
