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
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
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
            Map<String, Map<Integer, Set<ReportInfo>>> reportInfoMap = provider.getReportInfoMap(start, end);
            for (String containerId : reportInfoMap.keySet())
            {
                Container container = ContainerManager.getForId(containerId);
                if (null != container)
                    containers.add(container);
            }
        }
        return containers;
    }

    @Override
    public void sendDigest(Container container, Date min, Date max) throws Exception
    {
        Map<Integer, Set<ReportInfo>> reportInfosByCategory = null;
        for (ReportInfoProvider provider : _reportInfoProviders)
        {
            Map<Integer, Set<ReportInfo>> reportInfosForContainer = provider.getReportInfoMap(min, max).get(container.getId()); // could be null
            if (null == reportInfosByCategory)
                reportInfosByCategory = reportInfosForContainer;
            else if (null != reportInfosForContainer)
            {
                for (Map.Entry<Integer, Set<ReportInfo>> categoryEntry : reportInfosForContainer.entrySet())
                {
                    if (!reportInfosByCategory.containsKey(categoryEntry.getKey()))
                        reportInfosByCategory.put(categoryEntry.getKey(), new HashSet<ReportInfo>());
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
                    Set<ReportInfo> reportsForUser = new HashSet<>();
                    for (Integer category : userEntry.getValue())
                    {
                        if (null != category)
                        {
                            Set<ReportInfo> reportsForCategory = reportInfosByCategory.get(category);
                            if (null != reportsForCategory)
                                reportsForUser.addAll(reportsForCategory);
                        }
                    }

                    User user = UserManager.getUser(userEntry.getKey());
                    if (!reportsForUser.isEmpty() && null != user)
                    {
                        // Make email
                        Map<Integer, ViewCategory> viewCategoryMap = getViewCategoryMap(container, user);
                        ReportDigestForm form = new ReportDigestForm(user, container, reportsForUser, viewCategoryMap);
                        EmailMessage msg = emailService.createMessage(LookAndFeelProperties.getInstance(container).getSystemEmailAddress(),
                                                                      new String[]{user.getEmail()}, subject);

                        msg.addContent(EmailMessage.contentType.HTML, request,
                                new JspView<>("/org/labkey/query/reports/view/reportDigestNotify.jsp", form));
                        msg.addContent(EmailMessage.contentType.PLAIN, request,
                                new JspView<>("/org/labkey/query/reports/view/reportDigestNotifyPlain.jsp", form));

                        messages.add(msg);
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
        Set<ReportInfo> _reports;
        Set<ReportInfo> _datasets;
        User _user;
        Container _container;
        private Map<Integer, ViewCategory> _viewCategoryMap;

        public ReportDigestForm(User user, Container container, Set<ReportInfo> reportInfos, Map<Integer, ViewCategory> viewCategoryMap)
        {
            _user = user;
            _container = container;
            _viewCategoryMap = viewCategoryMap;

            // Split reports and datasets
            _reports = new HashSet<>();
            _datasets = new HashSet<>();
            for (ReportInfo reportInfo : reportInfos)
            {
                if (reportInfo.getType() == ReportInfo.Type.report)
                    _reports.add(reportInfo);
                else if (reportInfo.getType() == ReportInfo.Type.dataset)
                    _datasets.add(reportInfo);
                else
                    assert false;       // Type added?
            }
        }

        public Set<ReportInfo> getReports()
        {
            return _reports;
        }

        public Set<ReportInfo> getDatasets()
        {
            return _datasets;
        }

        public User getUser()
        {
            return _user;
        }

        public Container getContainer()
        {
            return _container;
        }

        public Map<Integer, ViewCategory> getViewCategoryMap()
        {
            return _viewCategoryMap;
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
}
