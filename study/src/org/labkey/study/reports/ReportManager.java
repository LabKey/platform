/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
package org.labkey.study.reports;

import org.apache.log4j.Logger;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.commons.lang.StringUtils;
import org.labkey.api.data.*;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.query.*;
import org.labkey.api.query.snapshot.QuerySnapshotDefinition;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.ReportDB;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.view.ReportQueryView;
import org.labkey.api.reports.report.view.ChartUtil;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ACL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.module.ModuleContext;
import org.labkey.study.StudySchema;
import org.labkey.study.model.*;
import org.labkey.common.util.Pair;

import javax.servlet.ServletException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * User: migra
 * Date: Mar 6, 2006
 * Time: 8:18:58 PM
 */
public class ReportManager implements StudyManager.StudyCachableListener
{
    private static final String SCHEMA_NAME = "study";
    private static final String TABLE_NAME = "Report";

    /** records the dataset id used for plotviews (charts not tied to a specific dataset view) */
    public static final int ALL_DATASETS = -1;
    public static final String ALL_DATASETS_KEY = StudyManager.getSchemaName() + "/*";
    public static final String DATASET_CHART_PREFIX = "showWithDataset";

    private static final ReportManager instance = new ReportManager();
    private static final Logger _log = Logger.getLogger(ReportManager.class);

    public static ReportManager get()
    {
        return instance;
    }

    private ReportManager()
    {
        StudyManager.addCachableListener(this);
    }

    private DbSchema getSchema()
    {
        return DbSchema.get(SCHEMA_NAME);
    }

    public TableInfo getTable()
    {
        return getSchema().getTable(TABLE_NAME);
    }

    private static final String _datasetLabelQuery;
    static {
        StringBuilder sql = new StringBuilder();
        final SqlDialect dialect = StudyManager.getSchema().getSqlDialect();

        sql.append("(ContainerId = ?) AND (ReportOwner IS NULL OR ReportOwner = ?) ");
        sql.append("AND (ReportKey = ? ");
        sql.append("OR ReportKey ");
        sql.append(dialect.getCaseInsensitiveLikeOperator());
        sql.append(" ? " + dialect.getConcatenationOperator() + " '%')");

        _datasetLabelQuery = sql.toString();
    }

    private void _addReportLabels(Report[] reports, List<Pair<String, String>> labels, ViewContext context)
    {
        for (Report report : reports)
        {
            if (!canReadReport(context.getUser(), context.getContainer(), report))
                continue;

            String label = report.getDescriptor().getReportName();
            labels.add(new Pair(label, String.valueOf(report.getDescriptor().getReportId())));
        }
    }

    public List<Pair<String, String>> getReportLabelsForDataset(ViewContext context, DataSetDefinition def) throws Exception
    {
        SimpleFilter filter = new SimpleFilter();
        Container container = context.getContainer();
        String reportKey = ChartUtil.getReportKey(StudyManager.getSchemaName(), def.getLabel());

        List<Pair<String, String>> labels = new ArrayList<Pair<String, String>>();

        // reports in this container
        filter.addWhereClause(_datasetLabelQuery, new Object[]{
                container.getId(),
                context.getUser().getUserId(),
                ALL_DATASETS_KEY, reportKey});
        _addReportLabels(ReportService.get().getReports(filter), labels, context);

        // any inherited reports
        while (!container.isRoot())
        {
            container = container.getParent();

            filter = new SimpleFilter();
            filter.addWhereClause(_datasetLabelQuery, new Object[]{
                    container.getId(),
                    context.getUser().getUserId(),
                    ALL_DATASETS_KEY, reportKey});
            filter.addWhereClause("(((Flags) & ?) = ?)", new Object[]{ReportDescriptor.FLAG_INHERITABLE, 1});
            _addReportLabels(ReportService.get().getReports(filter), labels, context);
        }

        // look for any reports in the shared project
        filter = new SimpleFilter();
        filter.addWhereClause(_datasetLabelQuery, new Object[]{
                ContainerManager.getSharedContainer().getId(),
                context.getUser().getUserId(),
                ALL_DATASETS_KEY, reportKey});
        _addReportLabels(ReportService.get().getReports(filter), labels, context);

        // add any custom query views
        UserSchema schema = QueryService.get().getUserSchema(context.getUser(), context.getContainer(), "study");
        QueryDefinition qd = QueryService.get().getQueryDef(def.getContainer(), "study", def.getLabel());
        if (null == qd)
            qd = schema.getQueryDefForTable(def.getLabel());
        Map<String, CustomView> views = qd.getCustomViews(context.getUser(), context.getRequest());
        if (views != null)
        {
            for (CustomView view: views.values())
                if (null != view.getName() && !view.isHidden())
                    labels.add(new Pair(view.getName(), view.getName()));
        }

        Collections.sort(labels, new Comparator<Pair<String, String>>()
        {
            public int compare(Pair<String, String> o1, Pair<String, String> o2)
            {
                if (o1.getKey() == null && o2.getKey() == null) return 0;
                if (o1.getKey() == null) return -1;
                if (o2.getKey() == null) return 1;

                return o1.getKey().compareTo(o2.getKey());
            }
        });

        // add the default grid as the first element
        labels.add(0, new Pair("Default Grid View", ""));

        return labels;
    }

    /**
     * @deprecated
     */
    public static String getReportViewKey(Integer datasetId, String label)
    {
        if (datasetId != null && !datasetId.equals(0))
            return getDatasetReportKeyPrefix(datasetId) + label;
        return label;
    }

    /**
     * @deprecated
     */
    public static String getDatasetReportKeyPrefix(int datasetId)
    {
        return DATASET_CHART_PREFIX + "/" + datasetId + "/";
    }

    public Report getReport(Container c, int reportId) throws Exception
    {
        return ReportService.get().getReport(reportId);
    }
    
/*
    public Report getReport(User user, Container c, int datasetId, String label) throws Exception
    {
        return getReport(user, c, getReportViewKey(datasetId, label));
    }

    public Report getReport(User user, Container c, String label) throws Exception
    {
        Report[] reports = ReportService.get().getReports(user, c, label);
        assert(reports.length == 0 || reports.length == 1);
        if (reports.length > 0)
            return reports[0];
        return null;
    }
*/
    public void deleteReport(ViewContext context, int reportId) throws Exception
    {
        final Report report = ReportService.get().getReport(reportId);
        if (report != null)
            ReportService.get().deleteReport(context, report);
    }

    public void deleteReport(ViewContext context, Report report) throws Exception
    {
        ReportService.get().deleteReport(context, report);
    }

    public ResultSet getReportResultSet(ViewContext context, ActionURL url, DataSetDefinition def) throws Exception
    {
        UserSchema schema = QueryService.get().getUserSchema(context.getUser(), context.getContainer(), "study");
        QuerySettings settings = new QuerySettings(url.getPropertyValues(), "Dataset");
        settings.setSchemaName(schema.getSchemaName());
        //Otherwise get from the URL somehow...
        if (null != def)
            settings.setQueryName(def.getLabel());

        ReportQueryView qv = new ReportQueryView(schema, settings);
        return qv.getResultSet(0);
    }


    public ResultSet getReportResultSet(ViewContext ctx, int datasetId, int visitRowId) throws ServletException, SQLException
    {
        Study study = StudyManager.getInstance().getStudy(ctx.getContainer());
        DataSetDefinition def = study.getDataSet(datasetId);
        if (def == null)
        {
            HttpView.throwNotFound();
            return null; // silence intellij warnings
        }
        if (!def.canRead(ctx.getUser()))
            HttpView.throwUnauthorized();
        Visit visit = null;
        if (visitRowId != 0)
        {
            visit = StudyManager.getInstance().getVisitForRowId(study, visitRowId);
            if (null == visit)
                HttpView.throwNotFound();
        }

        SimpleFilter filter = new SimpleFilter("DatasetId", datasetId);
        if (visit != null)
            visit.addVisitFilter(filter);
        filter.addCondition("container", ctx.getContainer().getId());

        filter.addUrlFilters(ctx.getActionURL(), "participantdataset");

        String typeURI = def.getTypeURI();
        if (typeURI == null)
            throw new IllegalStateException("Could not find type for dataset " + datasetId);

        // UNDONE: use def.getTableInfo()
        TableInfo tinfo = StudySchema.getInstance().getTableInfoStudyData();
        PropertyColumn[] propertyColumns = OntologyManager.getColumnsForType(typeURI, tinfo, "LSID", ctx.getContainer());
        if (propertyColumns == null || propertyColumns.length == 0)
            throw new IllegalArgumentException("No columns for type: " + typeURI);

        ArrayList<ColumnInfo> columns = new ArrayList<ColumnInfo>();
        columns.add(tinfo.getColumn("ParticipantId"));
        columns.add(tinfo.getColumn("SequenceNum"));
        columns.addAll(Arrays.asList(propertyColumns));

        ResultSet rs;
        rs = Table.selectForDisplay(tinfo, columns, filter, null, 0, 0);
        return rs;
    }


    public void deleteReports(Container c, Set<TableInfo> set) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("ContainerId", c.getId());
        Table.delete(getTable(), filter);
        assert set.add(getTable());
    }

    public Report createReport(String reportType)
    {
        return ReportService.get().createReportInstance(reportType);
    }

    public boolean canDeleteReport(User user, Container c, int reportId)
    {
        if (!user.isAdministrator())
        {
            try {
                // a non-admin can delete reports they own (non admins are not allowed to create shared reports).
                Report report = ReportService.get().getReport(reportId);
                if (report != null)
                {
                    Integer owner = report.getDescriptor().getOwner();
                    return (owner != null && owner.equals(user.getUserId()));
                }
                return false;
            }
            catch (Exception e)
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks both dataset and explicit permissions on a report to determine if a user has read
     * access.
     */
    public boolean canReadReport(User user, Container c, Report report)
    {
        ACL acl = report.getDescriptor().getACL();
        if (acl == null || acl.isEmpty())
        {
            // dataset permissions
            String datasetId = report.getDescriptor().getProperty(DataSetDefinition.DATASETKEY);
            String queryName = report.getDescriptor().getProperty(QueryParam.queryName.toString());
            Study study = StudyManager.getInstance().getStudy(c);

            if (NumberUtils.isNumber(datasetId))
            {
                DataSetDefinition dsDef = StudyManager.getInstance().getDataSetDefinition(study, NumberUtils.toInt(datasetId));
                if (dsDef != null)
                    return dsDef.canRead(user);
            }
            else if (queryName != null)
            {
                // try query name, which is synonymous to dataset in study-land
                DataSetDefinition dsDef = StudyManager.getInstance().getDataSetDefinition(study, queryName);
                if (dsDef != null)
                    return dsDef.canRead(user);
            }
            return true;
        }
        else
            // explicit permissions
            return acl.hasPermission(user, ACL.PERM_READ);
    }

    public void upgradeStudyReports(ViewContext context)
    {
        try {
            StudyReport[] reports = Table.select(getTable(), Table.ALL_COLUMNS, null, null, StudyReport.class);
            for (StudyReport report : reports)
            {
                Report r = createReport(report);
                if (r != null)
                {
                    User user = UserManager.getUser(r.getDescriptor().getCreatedBy());
                    if (user != null)
                        ReportService.get().saveReport(context, r.getDescriptor().getReportKey(), r);
                }
            }
        }
        catch (SQLException se)
        {
            _log.error("Unable to convert study report", se);
        }
    }

    private Report createReport(StudyReport report)
    {
        final String type = report.getReportType();
        String newType = null;

        if ("ExportExcel".equals(type))
            newType = ExportExcelReport.TYPE;
        else if ("Attachment".equals(type))
            newType = AttachmentReport.TYPE;
        else if ("Query".equals(type))
            newType = StudyQueryReport.TYPE;
        else if ("External".equals(type))
            newType = ExternalReport.TYPE;

        if (newType != null)
        {
            Report newReport = ReportService.get().createReportInstance(newType);
            if (newReport != null)
            {
                ReportDescriptor descriptor = newReport.getDescriptor();

                descriptor.setEntityId(report.getEntityId());
                descriptor.setContainerId(report.getContainerId());
                descriptor.setCreatedBy(report.getCreatedBy());
                descriptor.setCreated(report.getCreated());
                descriptor.setReportName(report.getLabel());
                descriptor.initFromQueryString(report.getParams());
                descriptor.setReportKey(ChartUtil.getReportQueryKey(descriptor));

                return newReport;
            }
        }
        return null;
    }

    public static class UpgradeReport_22_23
    {
        private ModuleContext _moduleContext;
        private ViewContext _viewContext;

        public UpgradeReport_22_23(ModuleContext moduleContext, ViewContext viewContext)
        {
            _moduleContext = moduleContext;
            _viewContext = viewContext;
        }

        public void upgradeStudyReports()
        {
            try {
                Report[] reports = ReportService.get().getReports(new SimpleFilter());
                for (Report r : reports)
                {
                    final ReportDescriptor descriptor = r.getDescriptor();
                    final String containerId = descriptor.getContainerId();
                    final String label = descriptor.getProperty("reportLabel");
                    final String name = descriptor.getReportName();

                    if (StringUtils.isEmpty(name) && !StringUtils.isEmpty(label))
                        descriptor.setReportName(label);

                    if (StudyQueryReport.TYPE.equals(descriptor.getReportType()))
                        descriptor.setProperty(QueryParam.viewName.toString(), label);
                    else if (ChartReportView.TYPE.equals(descriptor.getReportType()))
                    {
                        int datasetId = NumberUtils.toInt(descriptor.getProperty("datasetId"), 0);
                        if (datasetId != 0)
                        {
                            DataSetDefinition def = getDataSetDefinition(datasetId, containerId);
                            if (def != null)
                                descriptor.setProperty(QueryParam.queryName.toString(), def.getLabel());
                        }
                    }
                    ReportDB data = new ReportDB(r);
                    data.setReportKey(getReportKey(descriptor.getReportKey(), containerId));

                    //Table.update(_viewContext.getUser(), ReportService.get().getTable(), data, descriptor.getReportId(), null);
                }
            }
            catch (Exception se)
            {
                _log.warn("Unable to convert study report", se);
            }
        }

        private String getReportKey(String key, String containerId)
        {
            if (key.startsWith(ReportManager.DATASET_CHART_PREFIX))
            {
                String[] parts = key.split("/");
                int showWithDataset = NumberUtils.toInt(parts[1]);
                if (ReportManager.ALL_DATASETS == showWithDataset)
                    return ReportManager.ALL_DATASETS_KEY;

                String queryName = null;
                DataSetDefinition def = getDataSetDefinition(showWithDataset, containerId);
                if (def != null)
                    queryName = def.getLabel();
                return ChartUtil.getReportKey(StudyManager.getSchemaName(), queryName);
            }
            return key;
        }

        private DataSetDefinition getDataSetDefinition(int datasetId, String containerId)
        {
            Container c = ContainerManager.getForId(containerId);
            DataSetDefinition def = null;
            if (c != null)
            {
                Study study = StudyManager.getInstance().getStudy(c);
                if (study != null)
                    def = StudyManager.getInstance().getDataSetDefinition(study, datasetId);
            }
            return def;
        }
    }

    /**
     * A variant of a report that can contain multiple individual reports.
     */
    public interface ReportView extends Report
    {
        public void init(ViewContext context);
        public void deleteView(ViewContext context) throws Exception;
        public void saveView(ViewContext context) throws Exception;

        public Integer getShowWithDataset();
        public void setShowWithDataset(Integer dataset);
        public Container getContainer();
        public void setContainer(Container c);
        public String getParams();
        public void setParams(String params);
        public String getReportViewType();

        public void setReports(Report[] reports);
    }

    public static class StudyReport
    {
        private int _createdBy;
        private String _entityId;
        private Date _created;
        private String _label;
        private String _params;
        private String _reportType;
        private Integer _showWithDataset;
        private String _containerId;

        public void setEntityId(String entity){_entityId = entity;}
        public String getEntityId(){return _entityId;}
        public void setCreatedBy(int id){_createdBy = id;}
        public int getCreatedBy(){return _createdBy;}
        public void setCreated(Date created){_created = created;}
        public Date getCreated(){return _created;}
        public void setLabel(String label){_label = label;}
        public String getLabel(){return _label;}
        public void setParams(String params){_params = params;}
        public String getParams(){return _params;}
        public void setReportType(String reportType){_reportType = reportType;}
        public String getReportType(){return _reportType;}
        public void setShowWithDataset(Integer datasetId){_showWithDataset = datasetId;}
        public Integer getShowWithDataset(){return _showWithDataset;}
        public void setContainerId(String id){_containerId = id;}
        public String getContainerId(){return _containerId;}
    }

    public void cacheCleared(final StudyCachable c)
    {
        int id = NumberUtils.toInt(String.valueOf(c.getPrimaryKey()), -1);
        if (id != -1)
        {
            Study study = StudyManager.getInstance().getStudy(c.getContainer());
            ViewContext context = new ViewContext();
            context.setContainer(c.getContainer());

            if (study != null)
            {
                DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, id);
                if (def != null)
                {
                    _log.debug("Cache cleared notification on dataset : " + id);
                    String reportKey = ChartUtil.getReportKey(StudyManager.getSchemaName(), def.getLabel());
                    for (Report report : ChartUtil.getReports(context, reportKey, true))
                    {
                        report.clearCache();
                    }
                }
            }
        }
    }
}
