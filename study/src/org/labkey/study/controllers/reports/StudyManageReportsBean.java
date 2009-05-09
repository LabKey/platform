/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

package org.labkey.study.controllers.reports;

import org.apache.commons.lang.StringUtils;
import org.labkey.api.query.*;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.QueryReport;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.RReport;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.reports.report.view.ManageReportsBean;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.Study;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.DataSetQueryView;
import org.labkey.study.reports.*;
import org.labkey.study.controllers.StudyController;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Mar 1, 2007
 */
public class StudyManageReportsBean extends ManageReportsBean
{
    public static final String EXTERNAL_REPORT = "ExternalReport";
    public static final String EXCEL_EXPORT_REPORT = "Site View - Exported Excel";
    public static final String SHARED_DATASET_REPORT = "Available to all Datasets";
    public static final String QUERY_REPORT = "Query Views";
    private static final String ALL_DATASETS_LABEL = "Available to all Datasets";
    private static final String PUBLIC_QUERY_VIEW = "This grid view is available to all users. To make this view private, customize the view and uncheck the checkbox: 'Make this grid view available to all users'.";
    private static final String PRIVATE_QUERY_VIEW = "This grid view is not available to all users. To make this view public, customize the view and check the checkbox: 'Make this grid view available to all users'.";
    private static final String PUBLIC_REPORT = "This view is available to all users who have read access to the dataset. To modify this setting, navigate to view <a href=\"%s\">permissions</a> and select the appropriate radio button.";
    private static final String RESTRICTED_REPORT = "This view has explicit permissions applied. To modify this setting, navigate to view <a href=\"%s\">permissions</a> and select the appropriate radio button.";
    private static final String PRIVATE_REPORT = "This view is private and only visible to you. To modify this setting, navigate to view <a href=\"%s\">permissions</a> and select the appropriate radio button.";

    private Study _study;
    private boolean _isAdminView;
    private List<ReportRecord> _staticReports;
    private boolean _isWideView;
    private String _restrictedImg;
    private String _publicImg;
    private String _privateImg;
    private List<String> _unauthorizedQueryRpt = new ArrayList<String>();

    public StudyManageReportsBean(ViewContext context, boolean isAdminView, boolean isWide)
    {
        super(context);

        _isAdminView = isAdminView;
        _isWideView = isWide;
        _study = StudyManager.getInstance().getStudy(context.getContainer());
        _restrictedImg = "<img src='" + context.getContextPath() + "/_images/restricted.gif'>";
        _publicImg = "<img src='" + context.getContextPath() + "/_images/public.gif'>";
        _privateImg = "<img src='" + context.getContextPath() + "/_images/private.gif'>";
    }

    public boolean getAdminView(){return _isAdminView;}
    public void setAdminView(boolean admin){_isAdminView = admin;}
    public boolean getIsWideView(){return _isWideView;}

    public int getReportCount() throws Exception
    {
        int numberOfReports = super.getReportCount();

        numberOfReports += getStaticReports().size();
        try {
            if (getEnrollmentReport(false) != null)
                numberOfReports++;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return numberOfReports;
    }

    public List<ReportRecord> getStaticReports() throws Exception
    {
        if (_staticReports == null)
        {
            _staticReports = new ArrayList<ReportRecord>();
            for (Report r : ReportService.get().getReports(_context.getUser(), _context.getContainer()))
            {
                if (AttachmentReport.TYPE.equals(r.getDescriptor().getReportType()))
                {
                    if (isReadable(r))
                    {
                        String permissionURL = _context.cloneActionURL().relativeUrl("reportPermissions.view", "reportId=" + r.getDescriptor().getReportId(), "Study-Security");
                        StudyReportRecordImpl rec = new StudyReportRecordImpl(r, r.getDescriptor().getReportName(),
                                ((AttachmentReport)r).getUrl(_context),
                                _context.cloneActionURL().relativeUrl("deleteReport.view", "reportId=" + r.getDescriptor().getReportId(), "Study-Reports"),
                                permissionsLink(permissionURL),
                                getSharedURL(r, permissionURL));

                        _staticReports.add(rec);
                    }
                }
            }
        }
        return _staticReports;
    }

    protected String getSharedURL(Report r, String permissionURL)
    {
        if (r.getDescriptor().getOwner() != null)
            return PageFlowUtil.helpPopup("Private View", String.format(PRIVATE_REPORT, permissionURL), true, _privateImg);
        else if (!SecurityManager.getPolicy(r.getDescriptor()).isEmpty())
            return PageFlowUtil.helpPopup("Explicit Permissions", String.format(RESTRICTED_REPORT, permissionURL), true, _restrictedImg);
        else
            return PageFlowUtil.helpPopup("Shared View", String.format(PUBLIC_REPORT, permissionURL), true, _publicImg);
    }

    private String getSharedURL(CustomView view)
    {
        if (view.getOwner() == null)
            return PageFlowUtil.helpPopup("Shared View", PUBLIC_QUERY_VIEW, false, _publicImg);
        else
            return PageFlowUtil.helpPopup("Private View", PRIVATE_QUERY_VIEW, false, _privateImg);
    }

    protected Map<String, List<ReportRecord>> createReportRecordMap()
    {
        return new TreeMap<String, List<ReportRecord>>(new Comparator<String>()
        {
            public int compare(String o1, String o2)
            {
                if (ALL_DATASETS_LABEL.equals(o1) && !ALL_DATASETS_LABEL.equals(o2)) return 1;
                if (ALL_DATASETS_LABEL.equals(o2) && !ALL_DATASETS_LABEL.equals(o1)) return -1;

                return o1.compareTo(o2);
            }
        });
    }

    public ActionURL getCustomizeParticipantViewURL()
    {
        ActionURL customizeParticipantURL = new ActionURL(StudyController.CustomizeParticipantViewAction.class, _study.getContainer());
        // add a sample participant to our URL so that users can see the results of their customization.  This needs to be on the URL
        // since the default custom script reads the participant ID parameter from the URL:
        String[] participantIds = StudyManager.getInstance().getParticipantIds(_study, 1);
        if (participantIds != null && participantIds.length > 0)
            customizeParticipantURL.addParameter("participantId", participantIds[0]);
        return customizeParticipantURL;
    }

    protected void createReportRecord(Report r, Map<String, List<ReportRecord>> views)
    {
        if (!isReadable(r))
        {
            if (r instanceof QueryReport)
                _unauthorizedQueryRpt.add(r.getDescriptor().getReportName());
            return;
        }

        final String reportType = r.getDescriptor().getReportType();

        if (AttachmentReport.TYPE.equals(reportType) || EnrollmentReport.TYPE.equals(reportType))
            return;
        else if (ExportExcelReport.TYPE.equals(reportType))
        {
            String permissionURL = _context.cloneActionURL().relativeUrl("reportPermissions.view", "reportId=" + r.getDescriptor().getReportId().toString(), "Study-Security");
            StudyReportRecordImpl rec = new StudyReportRecordImpl(r, r.getDescriptor().getReportName(),
                    _context.cloneActionURL().relativeUrl("exportExcel.view", "reportId=" + r.getDescriptor().getReportId().toString(), "Study-Reports"),
                    _context.cloneActionURL().relativeUrl("deleteReport.view", "reportId=" + r.getDescriptor().getReportId().toString(), "Study-Reports"),
                    permissionsLink(permissionURL),
                    getSharedURL(r, permissionURL));
            getList(EXCEL_EXPORT_REPORT, views).add(rec);
        }
        else if (StudyRReport.TYPE.equals(reportType) || RReport.TYPE.equals(reportType))
        {
            String permissionURL = _context.cloneActionURL().relativeUrl("reportPermissions.view", "reportId=" + r.getDescriptor().getReportId().toString(), "Study-Security");
            StudyReportRecordImpl rec = new StudyReportRecordImpl(r, r.getDescriptor().getProperty(ReportDescriptor.Prop.reportName),
                    r.getRunReportURL(_context).getLocalURIString(),
                    _context.cloneActionURL().relativeUrl("deleteReport.view", "reportId=" + r.getDescriptor().getReportId().toString(), "Study-Reports"),
                    permissionsLink(permissionURL),
                    getSharedURL(r, permissionURL));

            ActionURL editURL = r.getEditReportURL(_context);
            if (editURL != null)
                rec.setEditURL(editURL.getLocalURIString());
            String queryName = r.getDescriptor().getProperty(QueryParam.queryName.toString());
            
            if (!StringUtils.isEmpty(queryName))
                getList(queryName, views).add(rec);
        }
        else if (isDatasetReport(r))
        {
            // dataset report key encoding is: <schemaName>/<queryName>/<viewName>
            String key = r.getDescriptor().getReportKey();
            String[] parts = ReportUtil.splitReportKey(key);

            if (ReportManager.ALL_DATASETS_KEY.equals(key))
            {
                getList(SHARED_DATASET_REPORT, views).add(getDatasetReportRecord(r, r.getDescriptor().getProperty(QueryParam.queryName.toString())));
            }
            else if (parts != null && parts.length >= 2)
            {
                String queryName = parts[1];
                getList(queryName, views).add(getDatasetReportRecord(r, queryName));
            }
            else
            {
                String permissionURL = _context.cloneActionURL().relativeUrl("reportPermissions.view", "reportId=" + r.getDescriptor().getReportId(), "Study-Security");

                StudyReportRecordImpl rec = new StudyReportRecordImpl(r, r.getDescriptor().getReportName(),
                        _context.cloneActionURL().relativeUrl("showReport.view", "reportId=" + r.getDescriptor().getReportId(), "Study-Reports"),
                        _context.cloneActionURL().relativeUrl("deleteReport.view", "reportId=" + r.getDescriptor().getReportId(), "Study-Reports"),
                        permissionsLink(permissionURL),
                        getSharedURL(r, permissionURL));
                getList(QUERY_REPORT, views).add(rec);
            }
        }
    }

    protected void afterGetViews(Map<String, List<ManageReportsBean.ReportRecord>> views)
    {
        // finally see if there are any custom query views created outside of the study report management
        for (Map.Entry<String, List<ReportRecord>> entry : getCustomQueryViews().entrySet())
        {
            List<ReportRecord> reports = getList(entry.getKey(), views);
            for (ReportRecord r : entry.getValue())
            {
                if (!reports.contains(r) && !_unauthorizedQueryRpt.contains(r.getName()))
                    reports.add(r);
            }
        }
    }

    private boolean isReadable(Report r)
    {
        return ReportManager.get().canReadReport(_context.getUser(), _context.getContainer(), r);
    }

    private ReportRecord getDatasetReportRecord(Report r, String queryName)
    {
        String name = r.getDescriptor().getReportName();
        String displayURL;

        displayURL = r.getRunReportURL(_context).toString();
        ActionURL deleteURL = new ActionURL("Study-Reports", "deleteReport.view", _context.getContainer());
        ActionURL permissionsURL = new ActionURL("Study-Security", "reportPermissions.view", _context.getContainer());

        deleteURL.addParameter("reportId", r.getDescriptor().getReportId().toString());
        permissionsURL.addParameter("reportId", r.getDescriptor().getReportId().toString());

        return new StudyReportRecordImpl(r, name, displayURL, deleteURL.toString(), permissionsLink(permissionsURL.toString()),
                getSharedURL(r, permissionsURL.toString()));
    }

    private String permissionsLink(String url)
    {
        return "[<a href='" + url + "'>permissions</a>]";
    }

    /**
     * Gets any custom query views that may have been created outside of the reporting infrastructure.
     */
    private Map<String, List<ReportRecord>> getCustomQueryViews()
    {
        Map<String, List<ReportRecord>> customViews = new HashMap<String, List<ReportRecord>>();
        UserSchema schema = QueryService.get().getUserSchema(_context.getUser(), _context.getContainer(), "study");
        if (_study != null)
        {
            for (DataSetDefinition dsd : _study.getDataSets())
            {
                if (dsd.getLabel() != null)
                {
                    QueryDefinition qd = QueryService.get().getQueryDef(_study.getContainer(), "study", dsd.getLabel());
                    if (qd == null)
                        qd = schema.getQueryDefForTable(dsd.getLabel());
                    Map<String, CustomView> views = qd.getCustomViews(_context.getUser(), _context.getRequest());

                    // we don't display any customized default views
                    views.remove(null);
                    if (views.size() > 0)
                    {
                        List<ReportRecord> rpts = getList(dsd.getLabel(), customViews);
                        ActionURL displayURL = new ActionURL("Study", "datasetReport.view", _context.getContainer());
                        displayURL.addParameter("datasetId", String.valueOf(dsd.getDataSetId()));
                        ActionURL deleteURL = new ActionURL("Study-Reports", "deleteCustomQuery.view", _context.getContainer());
                        deleteURL.addParameter("defName", dsd.getLabel());

                        for (Map.Entry<String, CustomView> entry : views.entrySet())
                        {
                            if (entry.getValue().isHidden())
                                continue;

                            displayURL.replaceParameter("Dataset.viewName", entry.getKey());
                            deleteURL.replaceParameter("reportView", entry.getKey());
                            ActionURL redirect = new ActionURL("Study-Security", "reportPermissions", _context.getContainer());
                            final ActionURL permissionURL = getQueryConversionURL(dsd.getLabel(), entry.getKey(), dsd.getDataSetId(), redirect.getLocalURIString());

                            StudyReportRecordImpl rec = new StudyReportRecordImpl(null, entry.getKey(),
                                    displayURL.toString(),
                                    deleteURL.toString(),
                                    //PageFlowUtil.helpPopup("Permissions Unavailable", STUDY_SECURITY_UNSUPPORTED),
                                    null,
                                    getSharedURL(entry.getValue()));

                            rec.setConversionURL(permissionURL.getLocalURIString());
                            rpts.add(rec);
                        }
                    }
                }
            }
        }
        return customViews;
    }

    private ActionURL getQueryConversionURL(String queryName, String viewName, int datasetId, String redirectURL)
    {
        ActionURL url = new ActionURL("Study-Reports", "queryConversion.view", _context.getContainer());

        url.addParameter("reportType", StudyQueryReport.TYPE);
        url.addParameter("showWithDataset", datasetId);
        url.addParameter("redirect", redirectURL);
        url.addParameter(QueryParam.schemaName.toString(), StudyManager.getSchemaName());
        url.addParameter(QueryParam.viewName.toString(), viewName);
        url.addParameter(QueryParam.queryName.toString(), queryName);
        url.addParameter(QueryParam.dataRegionName.toString(), DataSetQueryView.DATAREGION);
        url.addParameter("datasetId", datasetId);

        return url;
    }

    private boolean isDatasetReport(Report report)
    {
        final String reportType = report.getDescriptor().getReportType();

        if (EnrollmentReport.TYPE.equals(reportType))
            return false;
        if (AttachmentReport.TYPE.equals(reportType))
            return false;
        if (ExportExcelReport.TYPE.equals(reportType))
            return true;
        if (ExternalReport.TYPE.equals(reportType))
            return true;
        if (StudyCrosstabReport.TYPE.equals(reportType))
            return true;
        if (QueryReport.TYPE.equals(reportType) || StudyQueryReport.TYPE.equals(reportType))
            return true;

        String key = report.getDescriptor().getReportKey();
        if (key != null && key.startsWith(StudyManager.getSchemaName()))
            return true;

        return false;
    }

    public Report getEnrollmentReport(boolean create) throws Exception
    {
        Report report = EnrollmentReport.getEnrollmentReport(_context.getUser(), _study, create);
        if (report != null && isReadable(report))
        {
            return report;
        }
        return null;
    }

    public interface StudyReportRecord extends ReportRecord
    {
        public String getPermissionsURL();
        public void setConversionURL(String url);
        public String getConversionURL();
        public String getSharedURL();
    }

    protected static class StudyReportRecordImpl extends ReportRecordImpl implements StudyReportRecord
    {
        private String _permissionsURL;
        private String _conversionURL;
        private String _sharedURL;

        public StudyReportRecordImpl(Report report, String name, String displayURL, String deleteURL, String permissionsURL, String sharedURL)
        {
            super(report, name, displayURL, deleteURL);
            _permissionsURL = permissionsURL;
            _sharedURL = sharedURL;
        }
        public String getPermissionsURL(){return _permissionsURL;}
        public void setConversionURL(String url){_conversionURL = url;}
        public String getConversionURL(){return _conversionURL;}
        public String getSharedURL(){return _sharedURL;}
    }
}
