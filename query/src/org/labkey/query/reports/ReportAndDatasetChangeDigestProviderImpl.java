/*
 * Copyright (c) 2014-2017 LabKey Corporation
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
import org.labkey.api.util.MimeMap.MimeType;
import org.labkey.api.util.emailTemplate.EmailTemplateService;
import org.labkey.query.reports.view.ReportAndDatasetChangeDigestEmailTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public final class ReportAndDatasetChangeDigestProviderImpl implements ReportAndDatasetChangeDigestProvider
{
    final private List<NotificationInfoProvider> _notificationInfoProviders = new CopyOnWriteArrayList<>();

    public ReportAndDatasetChangeDigestProviderImpl()
    {
    }

    @Override
    public void addNotificationInfoProvider(NotificationInfoProvider provider)
    {
        _notificationInfoProviders.add(provider);
    }

    @Override
    public void sendDigestForAllContainers(Date start, Date end) throws Exception
    {
        Map<String, Map<Integer, List<NotificationInfo>>> allProvidersInfoMap = null;
        for (NotificationInfoProvider provider : _notificationInfoProviders)
        {
            Map<String, Map<Integer, List<NotificationInfo>>> providerReportInfoMap = provider.getNotificationInfoMap(start, end);
            if (null == allProvidersInfoMap)
                allProvidersInfoMap = providerReportInfoMap;
            else
            {
                // Merge into map for all providers
                for (Map.Entry<String, Map<Integer, List<NotificationInfo>>> infoMapEntry : providerReportInfoMap.entrySet())
                {
                    Map<Integer, List<NotificationInfo>> providerReportInfosForContainer = infoMapEntry.getValue();
                    if (null != providerReportInfosForContainer)
                    {
                        Map<Integer, List<NotificationInfo>> allProvidersInfosByCategory = allProvidersInfoMap.get(infoMapEntry.getKey());
                        if (null == allProvidersInfosByCategory)
                            allProvidersInfoMap.put(infoMapEntry.getKey(), providerReportInfosForContainer);
                        else
                        {
                            for (Map.Entry<Integer, List<NotificationInfo>> categoryEntry : providerReportInfosForContainer.entrySet())
                            {
                                if (!allProvidersInfosByCategory.containsKey(categoryEntry.getKey()))
                                    allProvidersInfosByCategory.put(categoryEntry.getKey(), new ArrayList<>());
                                allProvidersInfosByCategory.get(categoryEntry.getKey()).addAll(categoryEntry.getValue());
                            }
                        }
                    }
                }
            }
        }

        if (null != allProvidersInfoMap)
        {
            for (String containerId : allProvidersInfoMap.keySet())
            {
                Container container = ContainerManager.getForId(containerId);
                if (null != container)
                    sendDigest(container, allProvidersInfoMap.get(container.getId()));
            }
        }
    }

    private void sendDigest(Container container, Map<Integer, List<NotificationInfo>> reportInfosByCategory) throws Exception
    {
        if (null == reportInfosByCategory)
            return;

        try
        {
            Map<Integer, SortedSet<Integer>> categoriesByUser = ReportContentEmailManager.getUserCategoryMap(container);
            if (!categoriesByUser.isEmpty())
            {
                EmailService emailService = EmailService.get();
                List<EmailMessage> messages = new ArrayList<>();

                SortedSet<Integer> allCategories = ViewCategoryManager.getInstance().getAllCategories(container)
                    .stream()
                    .map(ViewCategory::getRowId)
                    .collect(Collectors.toCollection(TreeSet::new));     // In case we need ALL categories
                allCategories.add(ViewCategoryManager.UNCATEGORIZED_ROWID);

                for (Map.Entry<Integer, SortedSet<Integer>> userEntry : categoriesByUser.entrySet())
                {
                    User user = UserManager.getUser(userEntry.getKey());
                    if (null != user)
                    {
                        SortedSet<Integer> categories = userEntry.getValue();       // Set has a NotifyOption in it
                        NotifyOption notifyOption = ReportContentEmailManager.removeNotifyOption(categories);
                        Map<Integer, List<NotificationInfo>> reportsForUserInitial = notifyOption.getReportsForUserByCategory(reportInfosByCategory, categories, allCategories);

                        if (!reportsForUserInitial.isEmpty())
                        {
                            // Make email
                            Map<Integer, ViewCategory> viewCategoryMap = getViewCategoryMap(container);
                            Map<ViewCategory, List<NotificationInfo>> reportsForUser = new LinkedHashMap<>();
                            List<ViewCategory> viewCategories = getSortedViewCategories(viewCategoryMap);
                            viewCategories
                                .stream()
                                .filter(viewCategory -> reportsForUserInitial.containsKey(viewCategory.getRowId()))
                                .forEach(viewCategory -> reportsForUser.put(viewCategory, sortNotificationInfoList(reportsForUserInitial.get(viewCategory.getRowId()))));

                            ReportAndDatasetDigestBean bean = new ReportAndDatasetDigestBean(user, container, reportsForUser);
                            messages.add(createDigestMessage(emailService, bean));
                        }
                    }
                }

                if (!messages.isEmpty())
                    emailService.sendMessages(messages, null, container);
            }
        }
        catch (Exception e)
        {
            // Don't fail the request because of this error
            ExceptionUtil.logExceptionToMothership(null, e);
        }
    }

    private EmailMessage createDigestMessage(EmailService ems, ReportAndDatasetDigestBean form) throws Exception
    {
        ReportAndDatasetChangeDigestEmailTemplate template = EmailTemplateService.get().getEmailTemplate(ReportAndDatasetChangeDigestEmailTemplate.class);
        template.init(form.getContainer(), form.getReports());

        EmailMessage msg = ems.createMessage(LookAndFeelProperties.getInstance(form.getContainer()).getSystemEmailAddress(),
                Collections.singletonList(form.getUser().getEmail()), template.renderSubject(form.getContainer()));
        msg.setSenderName(template.renderSenderName(form.getContainer()));
        msg.addContent(MimeType.HTML, template.renderBody(form.getContainer()));
        String replyTo = template.renderReplyTo(form.getContainer());
        if (replyTo != null)
        {
            msg.setHeader("Reply-To", replyTo);
        }

        return msg;
    }

    private static class ReportAndDatasetDigestBean
    {
        private final Map<ViewCategory, List<NotificationInfo>> _reports;
        private final User _user;
        private final Container _container;

        private ReportAndDatasetDigestBean(User user, Container container, Map<ViewCategory, List<NotificationInfo>> reports)
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

    private Map<Integer, ViewCategory> getViewCategoryMap(Container container)
    {
        Map<Integer, ViewCategory> viewCategoryMap = new HashMap<>();
        ViewCategory uncategorizedCategory = ReportUtil.getDefaultCategory(container, null, null);
        uncategorizedCategory.setRowId(ViewCategoryManager.UNCATEGORIZED_ROWID);
        viewCategoryMap.put(ViewCategoryManager.UNCATEGORIZED_ROWID, uncategorizedCategory);

        for (ViewCategory viewCategory : ViewCategoryManager.getInstance().getAllCategories(container))
        {
            viewCategoryMap.put(viewCategory.getRowId(), viewCategory);
        }
        return viewCategoryMap;
    }

    private List<ViewCategory> getSortedViewCategories(Map<Integer, ViewCategory> viewCategoryMap)
    {
        List<ViewCategory> sortedViewCategories = new ArrayList<>();
        sortedViewCategories.addAll(viewCategoryMap.values());
        ViewCategoryManager.sortViewCategories(sortedViewCategories);
        return sortedViewCategories;
    }

    private List<NotificationInfo> sortNotificationInfoList(List<NotificationInfo> notificationInfos)
    {
        // It's ok to sort in place
        notificationInfos.sort((o1, o2) ->
        {
            int ret = o1.getDisplayOrder() - o2.getDisplayOrder();
            if (0 == ret)
                ret = o1.getName().compareToIgnoreCase(o2.getName());
            return ret;
        });
        return notificationInfos;
    }
}
