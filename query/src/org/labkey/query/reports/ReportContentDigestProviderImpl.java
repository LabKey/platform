package org.labkey.query.reports;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.message.digest.ReportContentDigestProvider;
import org.labkey.api.notification.EmailMessage;
import org.labkey.api.notification.EmailService;
import org.labkey.api.query.ReportInfoProvider;
import org.labkey.api.reports.ReportContentEmailManager;
import org.labkey.api.reports.model.ReportInfo;
import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.reports.model.ViewCategoryManager;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.view.JspView;

import javax.servlet.http.HttpServletRequest;
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

public class ReportContentDigestProviderImpl implements ReportContentDigestProvider
{
    private Set<ReportInfoProvider> _reportInfoProviders = new HashSet<>();

    public ReportContentDigestProviderImpl()
    {
    }

    @Override
    public void addReportInfoProvider(ReportInfoProvider provider)
    {
        _reportInfoProviders.add(provider);
    }

    @Override
    public List<Container> getContainersWithNewMessages(Date start, Date end) throws SQLException
    {

        List<Container> containers = new ArrayList<>();
        for (ReportInfoProvider provider : _reportInfoProviders)
        {
            provider.clearReportInfoMap();
            Map<String, Map<Integer, List<ReportInfo>>> reportInfoMap = provider.getReportInfoMap(start, end);
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
        Map<Integer, List<ReportInfo>> reportInfosByCategory = null;
        for (ReportInfoProvider provider : _reportInfoProviders)
        {
            Map<Integer, List<ReportInfo>> reportInfosForContainer = provider.getReportInfoMap(min, max).get(container.getId()); // could be null
            if (null == reportInfosByCategory)
                reportInfosByCategory = reportInfosForContainer;
            else if (null != reportInfosForContainer)
            {
                for (Map.Entry<Integer, List<ReportInfo>> categoryEntry : reportInfosForContainer.entrySet())
                {
                    if (!reportInfosByCategory.containsKey(categoryEntry.getKey()))
                        reportInfosByCategory.put(categoryEntry.getKey(), new ArrayList<ReportInfo>());
                    reportInfosByCategory.get(categoryEntry.getKey()).addAll(categoryEntry.getValue());
                }
            }

            provider.clearReportInfoMap();
        }

        if (null == reportInfosByCategory)
            return;

        try
        {
            Map<Integer, Set<Integer>> categoriesByUser = ReportContentEmailManager.getUserCategoryMap(container);
            if (!categoriesByUser.isEmpty())
            {
                EmailService.I emailService = EmailService.get();
                HttpServletRequest request = AppProps.getInstance().createMockRequest();
                String subject = "Report/Dataset Change Notification";
                List<EmailMessage> messages = new ArrayList<>();

                for (Map.Entry<Integer, Set<Integer>> userEntry : categoriesByUser.entrySet())
                {
                    User user = UserManager.getUser(userEntry.getKey());
                    if (null != user)
                    {
                        Map<Integer, List<ReportInfo>> reportsForUserInitial = new HashMap<>();
                        for (Integer category : userEntry.getValue())
                        {
                            if (null != category)
                            {
                                List<ReportInfo> reportsForCategory = reportInfosByCategory.get(category);
                                if (null != reportsForCategory)
                                    reportsForUserInitial.put(category, reportsForCategory);
                            }
                        }

                        if (!reportsForUserInitial.isEmpty())
                        {
                            // Make email
                            Map<Integer, ViewCategory> viewCategoryMap = getViewCategoryMap(container, user);
                            Map<ViewCategory, List<ReportInfo>> reportsForUser = new LinkedHashMap<>();
                            List<ViewCategory> viewCategories = getSortedViewCategories(viewCategoryMap);
                            for (ViewCategory viewCategory : viewCategories)
                                if (reportsForUserInitial.containsKey(viewCategory.getRowId()))
                                    reportsForUser.put(viewCategory, sortReportInfoList(reportsForUserInitial.get(viewCategory.getRowId())));

                            ReportDigestForm form = new ReportDigestForm(user, container, reportsForUser);
                            EmailMessage msg = emailService.createMessage(LookAndFeelProperties.getInstance(container).getSystemEmailAddress(),
                                    new String[]{user.getEmail()}, subject);

                            msg.addContent(EmailMessage.contentType.HTML, request,
                                    new JspView<>("/org/labkey/query/reports/view/reportDigestNotify.jsp", form));
                            msg.addContent(EmailMessage.contentType.PLAIN, request,
                                    new JspView<>("/org/labkey/query/reports/view/reportDigestNotifyPlain.jsp", form));

                            messages.add(msg);
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
            //_log.warn("Unable to send email for report/dataset notification: " + e.getMessage());
        }
    }

    public static class ReportDigestForm
    {
        Map<ViewCategory, List<ReportInfo>> _reports;
        User _user;
        Container _container;

        public ReportDigestForm(User user, Container container, Map<ViewCategory, List<ReportInfo>> reports)
        {
            _user = user;
            _container = container;

            // Split reports and datasets
            _reports = reports;
        }

        public Map<ViewCategory, List<ReportInfo>> getReports()
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

        for (ViewCategory viewCategory : ViewCategoryManager.getInstance().getCategories(container, user))
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

    private List<ReportInfo> sortReportInfoList(List<ReportInfo> reportInfos)
    {
        // It's ok to sort in place
        Collections.sort(reportInfos, new Comparator<ReportInfo>()
        {
            @Override
            public int compare(ReportInfo o1, ReportInfo o2)
            {
                int ret = o1.getDisplayOrder() - o2.getDisplayOrder();
                if (0 == ret)
                    ret = o1.getName().compareToIgnoreCase(o2.getName());
                return ret;
            }
        });
        return reportInfos;
    }
}
