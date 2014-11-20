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
package org.labkey.query.reports;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.message.digest.ReportAndDatasetChangeDigestProvider;
import org.labkey.api.notification.EmailMessage;
import org.labkey.api.notification.EmailService;
import org.labkey.api.query.NotificationInfoProvider;
import org.labkey.api.reports.ReportContentEmailManager;
import org.labkey.api.reports.ReportContentEmailManager.NotifyOption;
import org.labkey.api.reports.model.NotificationInfo;
import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.reports.model.ViewCategoryManager;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.emailTemplate.EmailTemplateService;
import org.labkey.query.reports.view.ReportAndDatasetChangeDigestEmailTemplate;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public class ReportAndDatasetChangeDigestProviderImpl extends ReportAndDatasetChangeDigestProvider
{
    private Set<NotificationInfoProvider> _notificationInfoProviders = new HashSet<>();

    public ReportAndDatasetChangeDigestProviderImpl()
    {
    }

    @Override
    public void addNotificationInfoProvider(NotificationInfoProvider provider)
    {
        _notificationInfoProviders.add(provider);
    }

    @Override
    public List<Container> getContainersWithNewMessages(Date start, Date end) throws SQLException
    {

        List<Container> containers = new ArrayList<>();
        for (NotificationInfoProvider provider : _notificationInfoProviders)
        {
            provider.clearNotificationInfoMap();
            Map<String, Map<Integer, List<NotificationInfo>>> reportInfoMap = provider.getNotificationInfoMap(start, end);
            for (String containerId : reportInfoMap.keySet())
            {
                Container container = ContainerManager.getForId(containerId);
                if (null != container && !containers.contains(container))
                    containers.add(container);
            }
        }
        return containers;
    }

    @Override
    public void sendDigest(Container container, Date min, Date max) throws Exception
    {
        Map<Integer, List<NotificationInfo>> reportInfosByCategory = null;
        for (NotificationInfoProvider provider : _notificationInfoProviders)
        {
            Map<Integer, List<NotificationInfo>> reportInfosForContainer = provider.getNotificationInfoMap(min, max).get(container.getId()); // could be null
            if (null == reportInfosByCategory)
                reportInfosByCategory = reportInfosForContainer;
            else if (null != reportInfosForContainer)
            {
                for (Map.Entry<Integer, List<NotificationInfo>> categoryEntry : reportInfosForContainer.entrySet())
                {
                    if (!reportInfosByCategory.containsKey(categoryEntry.getKey()))
                        reportInfosByCategory.put(categoryEntry.getKey(), new ArrayList<NotificationInfo>());
                    reportInfosByCategory.get(categoryEntry.getKey()).addAll(categoryEntry.getValue());
                }
            }

            provider.clearNotificationInfoMap();
        }

        if (null == reportInfosByCategory)
            return;

        try
        {
            Map<Integer, SortedSet<Integer>> categoriesByUser = ReportContentEmailManager.getUserCategoryMap(container);
            if (!categoriesByUser.isEmpty())
            {
                EmailService.I emailService = EmailService.get();
                List<EmailMessage> messages = new ArrayList<>();

                for (Map.Entry<Integer, SortedSet<Integer>> userEntry : categoriesByUser.entrySet())
                {
                    User user = UserManager.getUser(userEntry.getKey());
                    if (null != user)
                    {
                        Map<Integer, List<NotificationInfo>> reportsForUserInitial = new HashMap<>();
                        SortedSet<Integer> categories = userEntry.getValue();
                        NotifyOption notifyOption = ReportContentEmailManager.removeNotifyOption(categories);
                        if (!NotifyOption.NONE.equals(notifyOption))
                        {
                            if (NotifyOption.ALL.equals(notifyOption))
                            {
                                categories = new TreeSet<>();
                                for (ViewCategory viewCategory : ViewCategoryManager.getInstance().getAllCategories(container))
                                    categories.add(viewCategory.getRowId());
                            }
                            for (Integer category : categories)
                            {
                                if (null != category)
                                {
                                    List<NotificationInfo> reportsForCategory = reportInfosByCategory.get(category);
                                    if (null != reportsForCategory)
                                        reportsForUserInitial.put(category, reportsForCategory);
                                }
                            }

                            if (!reportsForUserInitial.isEmpty())
                            {
                                // Make email
                                Map<Integer, ViewCategory> viewCategoryMap = getViewCategoryMap(container, user);
                                Map<ViewCategory, List<NotificationInfo>> reportsForUser = new LinkedHashMap<>();
                                List<ViewCategory> viewCategories = getSortedViewCategories(viewCategoryMap);
                                for (ViewCategory viewCategory : viewCategories)
                                    if (reportsForUserInitial.containsKey(viewCategory.getRowId()))
                                        reportsForUser.put(viewCategory, sortNotificationInfoList(reportsForUserInitial.get(viewCategory.getRowId())));

                                ReportAndDatasetDigestForm form = new ReportAndDatasetDigestForm(user, container, reportsForUser);
                                messages.add(createDigestMessage(emailService, form));
                            }
                        }
                    }
                }

                if (!messages.isEmpty())
                    emailService.sendMessage(messages.toArray(new EmailMessage[messages.size()]), null, container);
            }
        }
        catch (Exception e)
        {
            // Don't fail the request because of this error
            ExceptionUtil.logExceptionToMothership(null, e);
        }
    }

    private EmailMessage createDigestMessage(EmailService.I ems, ReportAndDatasetDigestForm form) throws Exception
    {
        ReportAndDatasetChangeDigestEmailTemplate template = EmailTemplateService.get().getEmailTemplate(ReportAndDatasetChangeDigestEmailTemplate.class);
        template.init(form.getContainer(), form.getReports());

        EmailMessage msg = ems.createMessage(LookAndFeelProperties.getInstance(form.getContainer()).getSystemEmailAddress(),
                new String[]{form.getUser().getEmail()}, template.renderSubject(form.getContainer()));
        msg.addContent(EmailMessage.contentType.HTML, template.renderBody(form.getContainer()));

        return msg;
    }

    public static class ReportAndDatasetDigestForm
    {
        Map<ViewCategory, List<NotificationInfo>> _reports;
        User _user;
        Container _container;

        public ReportAndDatasetDigestForm(User user, Container container, Map<ViewCategory, List<NotificationInfo>> reports)
        {
            _user = user;
            _container = container;

            // Split reports and datasets
            _reports = reports;
        }

        public Map<ViewCategory, List<NotificationInfo>> getReports()
        {
            return _reports;
        }

        public User getUser()
        {
            return _user;
        }

        public Container getContainer()
        {
            return _container;
        }
    }

    private Map<Integer, ViewCategory> getViewCategoryMap(Container container, User user)
    {
        Map<Integer, ViewCategory> viewCategoryMap = new HashMap<>();
        ViewCategory uncategoriedCategory = ReportUtil.getDefaultCategory(container, null, null);
        uncategoriedCategory.setRowId(ViewCategoryManager.UNCATEGORIZED_ROWID);
        viewCategoryMap.put(ViewCategoryManager.UNCATEGORIZED_ROWID, uncategoriedCategory);

        for (ViewCategory viewCategory : ViewCategoryManager.getInstance().getAllCategories(container))
        {
            viewCategoryMap.put(viewCategory.getRowId(), viewCategory);
        }
        return viewCategoryMap;
    }

    private List<ViewCategory> getSortedViewCategories(Map<Integer, ViewCategory> viewCategoryMap)
    {
        List<ViewCategory> sortedViewCategories = new ArrayList<>();
        for (ViewCategory viewCategory : viewCategoryMap.values())
            sortedViewCategories.add(viewCategory);
        ViewCategoryManager.sortViewCategories(sortedViewCategories);
        return sortedViewCategories;
    }

    private List<NotificationInfo> sortNotificationInfoList(List<NotificationInfo> notificationInfos)
    {
        // It's ok to sort in place
        Collections.sort(notificationInfos, new Comparator<NotificationInfo>()
        {
            @Override
            public int compare(NotificationInfo o1, NotificationInfo o2)
            {
                int ret = o1.getDisplayOrder() - o2.getDisplayOrder();
                if (0 == ret)
                    ret = o1.getName().compareToIgnoreCase(o2.getName());
                return ret;
            }
        });
        return notificationInfos;
    }
}
