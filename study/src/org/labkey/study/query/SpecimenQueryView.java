/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

package org.labkey.study.query;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.collections.ResultSetRowMapFactory;
import org.labkey.api.data.*;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.security.User;
import org.labkey.api.study.Study;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.reports.ReportService;
import org.labkey.api.security.permissions.EditSpecimenDataPermission;
import org.labkey.api.study.StudyService;
import org.labkey.study.SpecimenManager;
import org.labkey.study.CohortFilter;
import org.labkey.study.StudySchema;
import org.labkey.study.reports.StudyCrosstabReport;
import org.labkey.study.security.permissions.RequestSpecimensPermission;
import org.labkey.study.specimen.settings.DisplaySettings;
import org.labkey.study.specimen.settings.RepositorySettings;
import org.labkey.study.controllers.specimen.SpecimenUtils;
import org.labkey.study.controllers.specimen.SpecimenController;
import org.labkey.study.model.ParticipantDataset;
import org.labkey.study.model.Vial;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.StudyImpl;

import java.io.IOException;
import java.io.Writer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * User: brittp
 * Date: Aug 23, 2006
 * Time: 3:38:44 PM
 */
public class SpecimenQueryView extends BaseStudyQueryView
{
    public enum Mode
    {
        COMMENTS,
        REQUESTS,
        DEFAULT
    }

    private boolean _requireSequenceNum;
    private boolean _enableRequests;

    private static class SpecimenDataRegion extends DataRegion
    {
        private Map<String, ColumnInfo> _requiredColumns = new HashMap<>();

        @Override
        protected boolean isErrorRow(RenderContext ctx, int rowIndex)
        {
            return SpecimenUtils.isFieldTrue(ctx, "QualityControlFlag");
        }

        protected void addColumnIfMissing(Set<ColumnInfo> currentColumns, String colName)
        {
            ColumnInfo col = getTable().getColumn(colName);
            if (col == null)
                throw new IllegalStateException("Failed to find expected column " + colName);
            ColumnInfo foundCol = null;
            for (Iterator<ColumnInfo> it = currentColumns.iterator(); it.hasNext() && foundCol == null; )
            {
                ColumnInfo current = it.next();
                if (colName.equalsIgnoreCase(current.getName()))
                    foundCol = current;
            }
            if (foundCol == null)
            {
                currentColumns.add(col);
                foundCol = col;
            }
            _requiredColumns.put(colName, foundCol);
        }

        @Override
        public void addQueryColumns(Set<ColumnInfo> columns)
        {
            for (String requiredColumn : getRequiredColumns())
                addColumnIfMissing(columns, requiredColumn);
        }

        protected List<String> getRequiredColumns()
        {
            List<String> cols = new ArrayList<>();
            cols.add("QualityControlFlag");
            return cols;
        }

        protected String getRequiredColumnAlias(String columnName)
        {
            ColumnInfo col = _requiredColumns.get(columnName);
            if (col == null)
                throw new IllegalStateException("Failed to find expected column " + columnName);
            return col.getAlias();
        }

        protected Object getRequiredColumnValue(RenderContext ctx, String columnName)
        {
            return ctx.getRow().get(getRequiredColumnAlias(columnName));
        }
    }

    private static class VialRestrictedDataRegion extends SpecimenDataRegion
    {
        protected boolean isRecordSelectorEnabled(RenderContext ctx)
        {
            return isAvailable(ctx);
        }

        @Override
        protected List<String> getRequiredColumns()
        {
            List<String> required = super.getRequiredColumns();
            required.add("GlobalUniqueId");
            required.add("Available");
            required.add("AvailabilityReason");
            return required;
        }

        @Override
        protected String getRecordSelectorId(RenderContext ctx)
        {
            return "check_" + getRequiredColumnValue(ctx, "GlobalUniqueId");
        }

        protected void renderExtraRecordSelectorContent(RenderContext ctx, Writer out) throws IOException
        {
            if (!isAvailable(ctx))
            {
                String reasonAlias = getRequiredColumnAlias("AvailabilityReason");
                Object reason = ctx.getRow().get(reasonAlias);

                out.write(PageFlowUtil.helpPopup("Specimen Unavailable",
                        (reason instanceof String ? reason : "Specimen Unavailable.") + "<br><br>" +
                                "Click " + PageFlowUtil.textLink("history", getHistoryLink(ctx)) + " for more information.", true));
            }
        }

        private String getHistoryLink(RenderContext ctx)
        {
            String containerId = (String) ctx.getRow().get("Container");
            String historyLinkBase = getHistoryLinkBase(ctx.getViewContext(), containerId);
            Long specimenId = (Long) ctx.getRow().get("RowId");
            return historyLinkBase + specimenId;
        }

        private boolean isAvailable(RenderContext ctx)
        {
            return SpecimenUtils.isFieldTrue(ctx, getRequiredColumnAlias("Available"));
        }
    }

    protected static String getHistoryLinkBase(ViewContext ctx, String containerId)
    {
        Container container = null != containerId ? ContainerManager.getForId(containerId) : ctx.getContainer();
        ActionURL historyLink = new ActionURL(SpecimenController.SampleEventsAction.class, container);
        historyLink.addParameter(ActionURL.Param.returnUrl, ctx.getActionURL().getLocalURIString());
        return historyLink.toString() + "&id=";
    }

    private class SpecimenRestrictedDataRegion extends SpecimenDataRegion
    {
        protected boolean isRecordSelectorEnabled(RenderContext ctx)
        {
            return isAvailable(ctx);
        }

        @Override
        protected List<String> getRequiredColumns()
        {
            List<String> required = super.getRequiredColumns();
            required.add("SpecimenHash");
            required.add("AvailableCount");
            return required;
        }

        @Override
        protected String getRecordSelectorId(RenderContext ctx)
        {
            return "check_" + getRequiredColumnValue(ctx, "SpecimenHash");
        }

        protected void renderExtraRecordSelectorContent(RenderContext ctx, Writer out) throws IOException
        {
            if (!isAvailable(ctx))
            {
                out.write(PageFlowUtil.helpPopup("Specimens Unavailable",
                        "No vials of this primary specimen are available.  All vials are either part of an active specimen request, " +
                                "locked by an administrator, or not currently held by a repository."));
            }
        }

        private boolean isAvailable(RenderContext ctx)
        {
            Integer count;
            if (getRequiredColumnValue(ctx, "AvailableCount") != null)
            {
                count = ((Number) getRequiredColumnValue(ctx, "AvailableCount")).intValue();
            }
            else
            {
                String hash = (String) getRequiredColumnValue(ctx, "SpecimenHash");
                count = getSampleCounts(ctx).get(hash);
            }
            return (count != null && count.intValue() > 0);
        }
    }

    private ViewType _viewType;
    private boolean _showHistoryLinks;
    private boolean _disableLowVialIndicators;
    private boolean _showRecordSelectors;
    private boolean _restrictRecordSelectors = true;
    private Map<String, String> _hiddenFormFields;
    // key of _availableSpecimenCounts is a specimen hash, which includes ptid, type, etc.
    private Map<String, Integer> _availableSpecimenCounts;
    private boolean _participantVisitFiltered = false;


    public enum ViewType
    {
        SUMMARY()
                {
                    public String getQueryName()
                    {
                        return "SpecimenSummary";
                    }

                    public String getViewName()
                    {
                        return null;
                    }

                    public boolean isVialView()
                    {
                        return false;
                    }

                    public boolean isForExport()
                    {
                        return false;
                    }
                },
        VIALS()
                {
                    public String getQueryName()
                    {
                        return "SpecimenDetail";
                    }

                    public String getViewName()
                    {
                        return null;
                    }

                    public boolean isVialView()
                    {
                        return true;
                    }

                    public boolean isForExport()
                    {
                        return false;
                    }
                },
        VIALS_EMAIL()
                {
                    public String getQueryName()
                    {
                        return "SpecimenDetail";
                    }

                    public String getViewName()
                    {
                        return "SpecimenEmail";
                    }

                    public boolean isVialView()
                    {
                        return true;
                    }

                    public boolean isForExport()
                    {
                        return true;
                    }
                };

        public abstract String getQueryName();

        public abstract String getViewName();

        public abstract boolean isVialView();

        public abstract boolean isForExport();
    }

    protected SpecimenQueryView(UserSchema schema, QuerySettings settings, SimpleFilter filter, Sort sort, ViewType viewType,
                                boolean participantVisitFiltered, CohortFilter cohortFilter, boolean requireSequenceNum)
    {
        super(schema, settings, filter, sort);
        _viewType = viewType;
        _cohortFilter = cohortFilter;
        _participantVisitFiltered = participantVisitFiltered;
        _requireSequenceNum = requireSequenceNum;

        RepositorySettings repositorySettings = SpecimenManager.getInstance().getRepositorySettings(schema.getContainer());
        _enableRequests = repositorySettings.isEnableRequests();
        boolean isEditable = ViewType.VIALS == viewType && repositorySettings.isSpecimenDataEditable() && getContainer().hasPermission(getUser(), EditSpecimenDataPermission.class);
        setShowUpdateColumn(isEditable);

        setShowInsertNewButton(isEditable);
        setShowDeleteButton(isEditable);
        setShowImportDataButton(false);
        if (isEditable)
        {
            AbstractTableInfo tableInfo = (AbstractTableInfo) getTable();
            ActionURL updateActionURL = new ActionURL(SpecimenController.UpdateSpecimenQueryRowAction.class, getContainer());
            updateActionURL.addParameter("schemaName", "study");
            updateActionURL.addParameter(QueryView.DATAREGIONNAME_DEFAULT + "." + QueryParam.queryName, tableInfo.getName());
            tableInfo.setUpdateURL(new DetailsURL(updateActionURL, Collections.singletonMap("RowId", "RowId")));
            ActionURL insertActionURL = new ActionURL(SpecimenController.InsertSpecimenQueryRowAction.class, getContainer());
            insertActionURL.addParameter("schemaName", "study");
            insertActionURL.addParameter(QueryView.DATAREGIONNAME_DEFAULT + "." + QueryParam.queryName, tableInfo.getName());
            tableInfo.setInsertURL(new DetailsURL(insertActionURL));
        }

        setViewItemFilter(new ReportService.ItemFilter()
        {
            public boolean accept(String type, String label)
            {
                if (StudyCrosstabReport.TYPE.equals(type)) return true;
                return DEFAULT_ITEM_FILTER.accept(type, label);
            }
        });
    }

    public static SpecimenQueryView createView(ViewContext context, ViewType viewType)
    {
        SimpleFilter filter = new SimpleFilter();
        return createView(context, filter, createDefaultSort(viewType), viewType, false, null, false);
    }

    public static SpecimenQueryView createView(ViewContext context, QuerySettings settings, ViewType viewType)
    {
        SimpleFilter filter = new SimpleFilter();
        return createView(context, settings, filter, createDefaultSort(viewType), viewType, false, null, false);
    }

    public static SpecimenQueryView createView(ViewContext context, ViewType viewType, CohortFilter cohortFilter)
    {
        SimpleFilter filter = new SimpleFilter();
        return createView(context, filter, createDefaultSort(viewType), viewType, false, cohortFilter, false);
    }

    public static SpecimenQueryView createView(ViewContext context, List<Vial> samples, ViewType viewType)
    {
        SimpleFilter filter = new SimpleFilter();
        addFilterClause(filter, samples, viewType);
        return createView(context, filter, createDefaultSort(viewType), viewType, false, null, false);
    }

    public static SpecimenQueryView createView(ViewContext context, ParticipantDataset[] participantDatasets, ViewType viewType)
    {
        SimpleFilter filter = new SimpleFilter();
        Study study = StudyManager.getInstance().getStudy(context.getContainer());
        addFilterClause(study, filter, participantDatasets);
        return createView(context, filter, createDefaultSort(viewType), viewType, true, null, true);
    }

    public enum PARAMS
    {
        excludeRequestedBySite,
        showRequestedBySite,
        showCompleteRequestedBySite,
        showRequestedByEnrollmentSite,
        showCompleteRequestedByEnrollmentSite,
        showRequestedOnly,
        showCompleteRequestedOnly,
        showNotRequestedOnly,
        showNotCompleteRequestedOnly
    }

    private static boolean hasRequestFilterParam(ViewContext context, PARAMS param)
    {
        String paramValue = StringUtils.trimToNull((String) context.get(param.name()));
        return null != paramValue && paramValue.equalsIgnoreCase("true");
    }

    private static void addOnlyPreviouslyRequestedFilter(ViewContext context, SimpleFilter filter)
    {
        String showRequestedBySite = StringUtils.trimToNull((String) context.get(PARAMS.showRequestedBySite.name()));
        if (null != showRequestedBySite)
            addOnlyPreviouslyRequestedClause(filter, context.getContainer(), Integer.parseInt(showRequestedBySite), false);
        else
        {
            showRequestedBySite = StringUtils.trimToNull((String) context.get(PARAMS.showCompleteRequestedBySite.name()));
            if (null != showRequestedBySite)
                addOnlyPreviouslyRequestedClause(filter, context.getContainer(), Integer.parseInt(showRequestedBySite), true);
        }
    }

    private static void addEnrollmentSiteRequestFilter(ViewContext context, SimpleFilter filter)
    {
        String showRequestedByEnrollmentSite = StringUtils.trimToNull((String) context.get(PARAMS.showRequestedByEnrollmentSite.name()));
        if (null != showRequestedByEnrollmentSite)
            addPreviouslyRequestedEnrollmentClause(filter, context.getContainer(), context.getUser(), Integer.parseInt(showRequestedByEnrollmentSite), false);
        else
        {
            showRequestedByEnrollmentSite = StringUtils.trimToNull((String) context.get(PARAMS.showCompleteRequestedByEnrollmentSite.name()));
            if (null != showRequestedByEnrollmentSite)
                addPreviouslyRequestedEnrollmentClause(filter, context.getContainer(), context.getUser(), Integer.parseInt(showRequestedByEnrollmentSite), true);
        }
    }

    private static void addRequestFilter(ViewContext context, SimpleFilter filter)
    {
        if (hasRequestFilterParam(context, PARAMS.showRequestedOnly))
            addPreviouslyRequestedClause(filter, context.getContainer(), true, false);
        else if (hasRequestFilterParam(context, PARAMS.showCompleteRequestedOnly))
            addPreviouslyRequestedClause(filter, context.getContainer(), true, true);
        else if (hasRequestFilterParam(context, PARAMS.showNotRequestedOnly))
            addPreviouslyRequestedClause(filter, context.getContainer(), false, false);
        else if (hasRequestFilterParam(context, PARAMS.showNotCompleteRequestedOnly))
            addPreviouslyRequestedClause(filter, context.getContainer(), false, true);
    }

    private static SpecimenQueryView createView(ViewContext context, SimpleFilter filter, Sort sort, ViewType viewType,
                                                boolean participantVisitFiltered, CohortFilter cohortFilter, boolean requireSequenceNum)
    {
        StudyImpl study = StudyManager.getInstance().getStudy(context.getContainer());
        StudyQuerySchema schema = StudyQuerySchema.createSchema(study, context.getUser(), true);
        String queryName = viewType.getQueryName();
        QuerySettings qs = schema.getSettings(context, queryName, queryName);
        return createView(context, qs, filter, sort, viewType, participantVisitFiltered, cohortFilter, requireSequenceNum);
    }

    private static SpecimenQueryView createView(ViewContext context, QuerySettings qs, SimpleFilter filter, Sort sort, ViewType viewType,
                                                boolean participantVisitFiltered, CohortFilter cohortFilter, boolean requireSequenceNum)
    {
        StudyImpl study = StudyManager.getInstance().getStudy(context.getContainer());
        StudyQuerySchema schema = StudyQuerySchema.createSchema(study, context.getUser(), true);
        String viewName = viewType.getViewName();
        if (qs.getViewName() == null && viewName != null)
            qs.setViewName(viewName);

        String excludeRequested = StringUtils.trimToNull((String) context.get(PARAMS.excludeRequestedBySite.name()));
        if (null != excludeRequested)
            addNotPreviouslyRequestedClause(filter, context.getContainer(), Integer.parseInt(excludeRequested));
        addOnlyPreviouslyRequestedFilter(context, filter);
        addEnrollmentSiteRequestFilter(context, filter);
        addRequestFilter(context, filter);
        return new SpecimenQueryView(schema, qs, filter, sort, viewType, participantVisitFiltered, cohortFilter, requireSequenceNum);
    }

    public Map<String, Integer> getSampleCounts(RenderContext ctx)
    {
        if (null == _availableSpecimenCounts)
            try
            {
                _availableSpecimenCounts = new HashMap<>();
                ResultSet rs = ctx.getResults();
                Set<String> specimenHashes = new HashSet<>();
                int originalRow = rs.getRow();
                rs.last();
                if (rs.getRow() - originalRow < 1000)
                {
                    rs.absolute(originalRow);
                    if (rs.getRow() == 0)
                    {
                        rs.next();
                    }
                    do
                    {
                        specimenHashes.add(rs.getString("SpecimenHash"));
                        if (specimenHashes.size() > 150)
                        {
                            _availableSpecimenCounts.putAll(SpecimenManager.getInstance().getSampleCounts(ctx.getContainer(), specimenHashes));
                            specimenHashes = new HashSet<>();
                        }
                    }
                    while (rs.next());
                    if (!specimenHashes.isEmpty())
                    {
                        _availableSpecimenCounts.putAll(SpecimenManager.getInstance().getSampleCounts(ctx.getContainer(), specimenHashes));
                    }
                }
                else
                {
                    _availableSpecimenCounts.putAll(SpecimenManager.getInstance().getSampleCounts(ctx.getContainer(), null));
                }
                rs.absolute(originalRow);
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }

        return _availableSpecimenCounts;
    }

    private static Sort createDefaultSort(ViewType viewType)
    {
        if (viewType.isVialView())
            return new Sort("GlobalUniqueId");
        else
            return new Sort(StudyService.get().getSubjectColumnName(getContextContainer()) + ",Visit,PrimaryType,DerivativeType,AdditiveType");
    }

    protected static SimpleFilter addFilterClause(SimpleFilter filter, List<Vial> vials, ViewType viewType)
    {
        if (vials != null && vials.size() > 0)
        {
            StringBuilder whereClause = new StringBuilder();
            if (viewType.isVialView())
                whereClause.append("RowId IN (");
            else
                whereClause.append("SpecimenHash IN (");
            for (int i = 0; i < vials.size(); i++)
            {
                if (viewType.isVialView())
                    whereClause.append(vials.get(i).getRowId());
                else
                    whereClause.append(vials.get(i).getSpecimenHash());
                if (i < vials.size() - 1)
                    whereClause.append(", ");
            }
            whereClause.append(")");
            filter = filter.addWhereClause(whereClause.toString(), null);
        }
        return filter;
    }

    protected static SimpleFilter addFilterClause(Study study, SimpleFilter filter, ParticipantDataset[] participantDatasets)
    {
        boolean visitBased = study.getTimepointType().isVisitBased();
        if (participantDatasets != null && participantDatasets.length > 0)
        {
            StringBuilder whereClause = new StringBuilder();
            List<Object> params = new ArrayList<>();
            String sep = "";
            for (ParticipantDataset pd : participantDatasets)
            {
                whereClause.append(sep);
                whereClause.append("(");
                if (visitBased && pd.getSequenceNum() != null)
                {
                    whereClause.append("SequenceNum = ? AND ");
                    params.add(pd.getSequenceNum());
                }
                else if (pd.getVisitDate() != null)
                {
                    whereClause.append(StudySchema.getInstance().getSqlDialect().getDateTimeToDateCast("DrawTimestamp"));
                    whereClause.append(" = ");
                    whereClause.append(StudySchema.getInstance().getSqlDialect().getDateTimeToDateCast("?")).append(" AND ");
                    params.add(pd.getVisitDate());
                }
                whereClause.append(StudyService.get().getSubjectColumnName(getContextContainer())).append(" = ?)");
                params.add(pd.getParticipantId());
                sep = " OR ";
            }
            filter = filter.addWhereClause(whereClause.toString(), params.toArray(new Object[params.size()]));
        }
        return filter;
    }

    protected static SimpleFilter addNotPreviouslyRequestedClause(SimpleFilter filter, Container container, int locationId)
    {
        TableInfo tableInfoVial = StudySchema.getInstance().getTableInfoVial(container);
        if (null == tableInfoVial)
            throw new IllegalStateException("Vial table not found.");
        SQLFragment sql = new SQLFragment("SpecimenHash NOT IN (" +
                "SELECT v.SpecimenHash from study.SampleRequestSpecimen rs join study.SampleRequest r on rs.SamplerequestId=r.RowId "
                + "join ");
        sql.append(tableInfoVial.getFromSQL("v")).append(" on rs.SpecimenGlobalUniqueId=v.GlobalUniqueId "
                + "join study.SamplerequestStatus status ON r.StatusId=status.RowId "
                + "where r.DestinationSiteId=").append(locationId).append(" AND status.SpecimensLocked=").append(tableInfoVial.getSqlDialect().getBooleanTRUE()).append(")");

        assert(0 == sql.getParams().size());

        filter.addWhereClause(sql.getSQL(), null, FieldKey.fromParts("SpecimenHash"));

        return filter;
    }

    protected static SimpleFilter addOnlyPreviouslyRequestedClause(SimpleFilter filter, Container container, int locationId, boolean completedRequestsOnly)
    {
        String sql = "GlobalUniqueId IN ("
                + "SELECT rs.SpecimenGlobalUniqueId FROM study.SampleRequestSpecimen rs join study.SampleRequest r on rs.SamplerequestId=r.RowId "
                + "join study.SamplerequestStatus status ON r.StatusId=status.RowId "
                + "where r.DestinationSiteId=? AND rs.Container=? AND status.SpecimensLocked=?" +
                (completedRequestsOnly ? " AND status.FinalState=?" : "") + ")";

        Object[] params;
        if (completedRequestsOnly)
            params = new Object[]{locationId, container.getId(), Boolean.TRUE, Boolean.TRUE};
        else
            params = new Object[]{locationId, container.getId(), Boolean.TRUE};

        filter.addWhereClause(sql, params, FieldKey.fromParts("GlobalUniqueId"));

        return filter;
    }

    protected static SimpleFilter addPreviouslyRequestedEnrollmentClause(SimpleFilter filter, Container container, User user, int locationId, boolean completedRequestsOnly)
    {
        SQLFragment sql = getBaseRequestedEnrollmentSql(container, user, completedRequestsOnly);
        assert 0 == sql.getParams().size();
        if (locationId == -1)
        {
            sql.append("IS NULL)");
        }
        else
        {
            sql.append("= " + locationId + ")");
        }

        filter.addWhereClause(sql.getSQL(), null, FieldKey.fromParts("GlobalUniqueId"));

        return filter;
    }

    public static SQLFragment getBaseRequestedEnrollmentSql(Container container, User user, boolean isCompleteRequestsOnly)
    {
        StudyQuerySchema schema = StudyQuerySchema.createSchema(StudyManager.getInstance().getStudy(container), user, true);
        TableInfo tableInfoSpecimenDetail = schema.getTable(StudyQuerySchema.SPECIMEN_WRAP_TABLE_NAME);
        if (null == tableInfoSpecimenDetail)
            throw new IllegalStateException("SpecimenDetail table not found.");
        String tableInfoAlias = "Specimen";

        SQLFragment baseSql = new SQLFragment("GlobalUniqueId IN (SELECT Specimen.GlobalUniqueId FROM ");
        baseSql.append(tableInfoSpecimenDetail.getFromSQL(tableInfoAlias)).append(",\n" +
                "study.SampleRequestSpecimen AS RequestSpecimen,\n" +
                "study.SampleRequest AS Request, study.SampleRequestStatus AS Status,\n" +
                "study.Participant AS Participant\n" +
                "WHERE Request.Container = Status.Container AND\n" +
                "     Request.StatusId = Status.RowId AND\n" +
                "     RequestSpecimen.SampleRequestId = Request.RowId AND\n" +
                "     RequestSpecimen.Container = Request.Container AND\n" +
                "     Specimen.Container = RequestSpecimen.Container AND\n" +
                "     Specimen.GlobalUniqueId = RequestSpecimen.SpecimenGlobalUniqueId AND\n" +
                "     Participant.Container = Specimen.Container AND\n" +
                "     Participant.ParticipantId = Specimen.Ptid AND\n" +
                "     Status.SpecimensLocked = ? AND\n" +
                (isCompleteRequestsOnly ? "     Status.FinalState = ? AND\n" : "") +
                "     Specimen.Container = ? AND\n" +
                "     Participant.EnrollmentSiteId ");
        baseSql.add(Boolean.TRUE);
        if (isCompleteRequestsOnly)
            baseSql.add(Boolean.TRUE);
        baseSql.add(container);
        return baseSql;
    }

    protected static SimpleFilter addPreviouslyRequestedClause(SimpleFilter filter, Container container, boolean showRequested, boolean completedRequestsOnly)
    {
        String sql = "GlobalUniqueId " + (showRequested ? "" : "NOT ") + "IN ("
                + "SELECT rs.SpecimenGlobalUniqueId FROM study.SampleRequestSpecimen rs join study.SampleRequest r on rs.SamplerequestId=r.RowId "
                + "join study.SamplerequestStatus status ON r.StatusId=status.RowId "
                + "where rs.Container=? AND status.SpecimensLocked=?" + (completedRequestsOnly ? " AND status.FinalState=?" : "") +
                ")";
        Object[] params;
        if (completedRequestsOnly)
            params = new Object[]{container.getId(), Boolean.TRUE, Boolean.TRUE};
        else
            params = new Object[]{container.getId(), Boolean.TRUE};

        filter.addWhereClause(sql, params, FieldKey.fromParts("GlobalUniqueId"));

        return filter;
    }

    protected DataRegion createDataRegion()
    {
        SpecimenDataRegion rgn;
        if (_viewType.isVialView())
        {
            if (_showRecordSelectors && _restrictRecordSelectors)
                rgn = new VialRestrictedDataRegion();
            else
                rgn = new SpecimenDataRegion();
            rgn.setRecordSelectorValueColumns("RowId");

            // NOTE: Setting here instead of in table ctor because if set when SpecimenDetail is sub-query, we have too many columns in sub-query (Dave)
            if (ViewType.VIALS.equals(_viewType))
                getTable().getColumn("Container").setRequired(true);
        }
        else
        {
            if (_showRecordSelectors && _restrictRecordSelectors)
                rgn = new SpecimenRestrictedDataRegion();
            else
                rgn = new SpecimenDataRegion();

            rgn.setRecordSelectorValueColumns("SpecimenHash");
            if (_showRecordSelectors)
            {
                if (null == _hiddenFormFields)
                    _hiddenFormFields = new HashMap<>();
                _hiddenFormFields.put("fromGroupedView", Boolean.TRUE.toString());
            }
        }
        configureDataRegion(rgn);
        rgn.prepareDisplayColumns(getViewContext().getContainer());
        return rgn;
    }

    @Override
    protected void configureDataRegion(DataRegion rgn)
    {
        super.configureDataRegion(rgn);

        DisplayColumn rowIdCol = rgn.getDisplayColumn("RowId");
        if (rowIdCol != null)
            rowIdCol.setVisible(false);
        DisplayColumn containerIdCol = rgn.getDisplayColumn("Container");
        if (containerIdCol != null)
            containerIdCol.setVisible(false);

        rgn.setShowRecordSelectors(_showRecordSelectors);

        if (_hiddenFormFields != null)
        {
            for (Map.Entry<String, String> param : _hiddenFormFields.entrySet())
                rgn.addHiddenFormField(param.getKey(), param.getValue());
        }

        rgn.addHiddenFormField("referrer", getViewContext().getActionURL().getLocalURIString());

        if (_viewType.isVialView())
        {
            addAggregateIfInDisplay("Volume", Aggregate.BaseType.SUM);
            addAggregateIfInDisplay("GlobalUniqueId", Aggregate.BaseType.COUNT);
        }
        else
        {
            addAggregateIfInDisplay("TotalVolume", Aggregate.BaseType.SUM);
            addAggregateIfInDisplay("LockedInRequestCount", Aggregate.BaseType.SUM);
            addAggregateIfInDisplay("AtRepositoryCount", Aggregate.BaseType.SUM);
            addAggregateIfInDisplay("VialCount", Aggregate.BaseType.SUM);
            addAggregateIfInDisplay("AvailableVolume", Aggregate.BaseType.SUM);
            addAggregateIfInDisplay("AvailableCount", Aggregate.BaseType.SUM);
        }
    }

    // Add the aggregate clause only if the aggregated column is going to be displayed
    // (otherwise SQL is not happy)
    private void addAggregateIfInDisplay(String colName, Aggregate.Type aggregateType)
    {
        ColumnInfo columnInfo = getTable().getColumn(colName);
        if (columnInfo != null)
        {
            for (DisplayColumn displayColumn : super.getDisplayColumns())
            {
                if (displayColumn instanceof DetailsColumn)
                    continue;

                if (columnInfo.equals(displayColumn.getColumnInfo()))
                {
                    getSettings().addAggregates(new Aggregate(columnInfo, aggregateType));
                    return;
                }
            }
        }
    }

    @Override
    public List<DisplayColumn> getDisplayColumns()
    {
        List<DisplayColumn> cols = super.getDisplayColumns();

        if (_viewType.isForExport())
        {
            // Remove rowId column and update column, if present
            DisplayColumn column = cols.get(0);
            if (column.getName().equalsIgnoreCase("rowid") || column.getName().equalsIgnoreCase("update"))
                cols.remove(0);
            column = cols.get(0);
            if (column.getName().equalsIgnoreCase("rowid") || column.getName().equalsIgnoreCase("update"))
                cols.remove(0);
        }

        if (_viewType.isVialView())
        {
            if (_showHistoryLinks)
            {
                cols.add(0, new SimpleDisplayColumn("[history]")
                {
                    @Override
                    public String renderURL(RenderContext ctx)
                    {
                        StringBuilder url = new StringBuilder();
                        String containerId = (String) ctx.getRow().get("Container");
                        Long specimenId = (Long) ctx.getRow().get("RowId");
                        url.append(getHistoryLinkBase(ctx.getViewContext(), containerId))
                           .append(specimenId.toString()).append("&selected=")
                           .append(Boolean.toString(_participantVisitFiltered));
                        return url.toString();
                    }
                });
            }
        }

        boolean oneVialIndicator = false;
        boolean zeroVialIndicator = false;
        if (!_disableLowVialIndicators)
        {
            DisplaySettings settings = SpecimenManager.getInstance().getDisplaySettings(getContainer());
            oneVialIndicator = settings.getLastVialEnum() == DisplaySettings.DisplayOption.ALL_USERS ||
                    (settings.getLastVialEnum() == DisplaySettings.DisplayOption.ADMINS_ONLY &&
                            getUser().hasRootAdminPermission());
            zeroVialIndicator = settings.getZeroVialsEnum() == DisplaySettings.DisplayOption.ALL_USERS ||
                    (settings.getZeroVialsEnum() == DisplaySettings.DisplayOption.ADMINS_ONLY &&
                            getUser().hasRootAdminPermission());
        }
        RepositorySettings settings = SpecimenManager.getInstance().getRepositorySettings(getContainer());
        if (settings.isEnableRequests() && !_viewType.isForExport() && getViewContext().getContainer().hasPermission(getUser(), RequestSpecimensPermission.class))
        {
            // Only add this column if we're using advanced specimen management and not exported to email or attachment
            cols.add(0, new SpecimenRequestDisplayColumn(this, getTable(), zeroVialIndicator, oneVialIndicator,
                    SpecimenManager.getInstance().isSpecimenShoppingCartEnabled(getContainer()) && _showRecordSelectors));
        }

        // this column is normally hidden but we need it on the select for any specimen filters
        if (_requireSequenceNum)
        {
            ColumnInfo seqNumCol = getTable().getColumn("SequenceNum");
            if (seqNumCol != null)
                cols.add(seqNumCol.getRenderer());
        }

        return cols;
    }

    public boolean isShowingVials()
    {
        return _viewType.isVialView();
    }

    public boolean isShowHistoryLinks()
    {
        return _showHistoryLinks;
    }

    public void addHiddenFormField(String name, String value)
    {
        if (_hiddenFormFields == null)
            _hiddenFormFields = new HashMap<>();
        _hiddenFormFields.put(name, value);
    }

    public boolean isDisableLowVialIndicators()
    {
        return _disableLowVialIndicators;
    }

    public void setDisableLowVialIndicators(boolean disableLowVialIndicators)
    {
        _disableLowVialIndicators = disableLowVialIndicators;
    }

    public boolean isShowRecordSelectors()
    {
        return _showRecordSelectors;
    }

    public boolean isRestrictRecordSelectors()
    {
        return _restrictRecordSelectors;
    }

    public void setRestrictRecordSelectors(boolean restrictRecordSelectors)
    {
        _restrictRecordSelectors = restrictRecordSelectors;
    }

    public void setShowHistoryLinks(boolean showHistoryLinks)
    {
        _showHistoryLinks = showHistoryLinks;
    }

    public void setShowRecordSelectors(boolean showRecordSelectors)
    {
        //assert !(showRecordSelectors && !_detailsView) : "Only details view may show record selectors";
        _showRecordSelectors = showRecordSelectors;
    }

    public String getSimpleHtmlTable() throws SQLException, IOException
    {
        getSettings().setMaxRows(Table.ALL_ROWS);
        DataView view = createDataView();
        DataRegion rgn = view.getDataRegion();
        RenderContext renderContext = view.getRenderContext();
        ResultSet rs = null;
        try
        {
            rs = rgn.getResultSet(renderContext);
            List<DisplayColumn> allColumns = getExportColumns(rgn.getDisplayColumns());
            List<DisplayColumn> columns = new ArrayList<>();
            for (DisplayColumn col : allColumns)
            {
                if (col.shouldRender(renderContext))
                    columns.add(col);
            }
            StringBuilder builder = new StringBuilder();
            builder.append("<table style=\"border-collapse:collapse\" cellspacing=\"0\" cellpadding=\"2\">\n  <tr><td>&nbsp;</td>\n");
            for (DisplayColumn col : columns)
            {
                String header = PageFlowUtil.filter(col.getCaption());
                header = header.replaceAll(" ", "&nbsp;");
                builder.append("    <td style=\"font-weight:bold;border: 1px solid #BBBBBB\">").append(header).append("</td>\n");
            }
            builder.append("  </tr>\n");
            int row = 0;

            ResultSetRowMapFactory factory = ResultSetRowMapFactory.create(rs);

            while (rs.next())
            {
                renderContext.setRow(factory.getRowMap(rs));
                builder.append("  <tr style=\"background-color:").append(row++ % 2 == 0 ? "#FFFFFF" : "#DDDDDD").append("\">\n");
                builder.append("    <td style=\"border: 1px solid #BBBBBB\">").append(row).append("</td>\n");
                for (DisplayColumn col : columns)
                {
                    String value = col.getFormattedValue(renderContext);
                    builder.append("    <td style=\"border: 1px solid #BBBBBB\">").append(value).append("</td>\n");
                }
                builder.append("  </tr>\n");
            }
            builder.append("</table>");
            return builder.toString();
        }
        finally
        {
            if (rs != null) try
            {
                rs.close();
            }
            catch (SQLException e)
            {
            }
        }
    }

    @Override
    public void addManageViewItems(MenuButton button, Map<String, String> params)
    {
        ActionURL url = PageFlowUtil.urlProvider(ReportUrls.class).urlManageViews(getViewContext().getContainer());
        for (Map.Entry<String, String> entry : params.entrySet())
            url.addParameter(entry.getKey(), entry.getValue());

        button.addMenuItem("Manage Views", url);
    }

    @Override
    protected ColumnHeaderType getColumnHeaderType()
    {
        return ColumnHeaderType.Caption;
    }
}
