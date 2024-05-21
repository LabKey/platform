/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.specimen.query;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.ResultSetRowMapFactory;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.Aggregate;
import org.labkey.api.data.ColumnHeaderType;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DetailsColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.MenuButton;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.Results;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleDisplayColumn;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.security.User;
import org.labkey.api.specimen.SpecimenQuerySchema;
import org.labkey.api.specimen.SpecimenSchema;
import org.labkey.api.specimen.Vial;
import org.labkey.api.specimen.security.permissions.EditSpecimenDataPermission;
import org.labkey.api.specimen.security.permissions.RequestSpecimensPermission;
import org.labkey.api.specimen.settings.DisplaySettings;
import org.labkey.api.specimen.settings.RepositorySettings;
import org.labkey.api.specimen.settings.SettingsManager;
import org.labkey.api.study.CohortFilter;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.StudyUtils;
import org.labkey.api.study.model.ParticipantDataset;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;
import org.labkey.specimen.actions.SpecimenController;

import java.io.IOException;
import java.io.Writer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.requireNonNull;

public class SpecimenQueryView extends BaseSpecimenQueryView
{
    public enum Mode
    {
        COMMENTS,
        REQUESTS,
        DEFAULT
    }

    private final boolean _requireSequenceNum;

    private static class SpecimenDataRegion extends DataRegion
    {
        private final Map<String, ColumnInfo> _requiredColumns = new HashMap<>();

        @Override
        protected boolean isErrorRow(RenderContext ctx, int rowIndex)
        {
            return StudyUtils.isFieldTrue(ctx, "QualityControlFlag");
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
        @Override
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

        @Override
        protected void renderExtraRecordSelectorContent(RenderContext ctx, Writer out) throws IOException
        {
            if (!isAvailable(ctx))
            {
                String reasonAlias = getRequiredColumnAlias("AvailabilityReason");
                Object reason = ctx.getRow().get(reasonAlias);

                HtmlStringBuilder builder = HtmlStringBuilder.of(reason instanceof String r ? r : "Specimen Unavailable.")
                    .append(HtmlString.BR).append(HtmlString.BR)
                    .append("Click ")
                    .append(PageFlowUtil.link("[history]", getHistoryLink(ctx)).clearClasses())
                    .append(" for more information.");

                PageFlowUtil.popupHelp(builder.getHtmlString(), "Specimen Unavailable").appendTo(out);
            }
        }

        private ActionURL getHistoryLink(RenderContext ctx)
        {
            String containerId = (String) ctx.getRow().get("Container");
            ActionURL url = getHistoryLinkURL(ctx.getViewContext(), containerId);
            Long specimenId = (Long) ctx.getRow().get("RowId");

            return url.addParameter("id", specimenId);
        }

        private boolean isAvailable(RenderContext ctx)
        {
            return StudyUtils.isFieldTrue(ctx, getRequiredColumnAlias("Available"));
        }
    }

    protected static ActionURL getHistoryLinkURL(ViewContext ctx, String containerId)
    {
        Container container = null != containerId ? ContainerManager.getForId(containerId) : ctx.getContainer();
        return new ActionURL(SpecimenController.SpecimenEventsAction.class, container).addReturnURL(ctx.getActionURL());
    }

    private class SpecimenRestrictedDataRegion extends SpecimenDataRegion
    {
        @Override
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

        @Override
        protected void renderExtraRecordSelectorContent(RenderContext ctx, Writer out) throws IOException
        {
            if (!isAvailable(ctx))
            {
                PageFlowUtil.popupHelp(HtmlString.of("No vials of this primary specimen are available. All " +
                    "vials are either part of an active specimen request, locked by an administrator, or not " +
                    "currently held by a repository."), "Specimens Unavailable").appendTo(out);
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

    private boolean _showHistoryLinks;
    private boolean _disableLowVialIndicators;
    private boolean _showRecordSelectors;
    private boolean _restrictRecordSelectors = true;
    private Map<String, String> _hiddenFormFields;
    // key of _availableSpecimenCounts is a specimen hash, which includes ptid, type, etc.
    private Map<String, Integer> _availableSpecimenCounts;

    private final boolean _participantVisitFiltered;
    private final ViewType _viewType;


    public enum ViewType
    {
        SUMMARY()
        {
            @Override
            public String getQueryName()
            {
                return "SpecimenSummary";
            }

            @Override
            public String getViewName()
            {
                return null;
            }

            @Override
            public boolean isVialView()
            {
                return false;
            }

            @Override
            public boolean isForExport()
            {
                return false;
            }
        },
        VIALS()
        {
            @Override
            public String getQueryName()
            {
                return "SpecimenDetail";
            }

            @Override
            public String getViewName()
            {
                return null;
            }

            @Override
            public boolean isVialView()
            {
                return true;
            }

            @Override
            public boolean isForExport()
            {
                return false;
            }
        },
        VIALS_EMAIL()
        {
            @Override
            public String getQueryName()
            {
                return "SpecimenDetail";
            }

            @Override
            public String getViewName()
            {
                return "SpecimenEmail";
            }

            @Override
            public boolean isVialView()
            {
                return true;
            }

            @Override
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

        RepositorySettings repositorySettings = SettingsManager.get().getRepositorySettings(schema.getContainer());
        boolean isEditable = ViewType.VIALS == viewType && repositorySettings.isSpecimenDataEditable() && getContainer().hasPermission(getUser(), EditSpecimenDataPermission.class);
        setShowUpdateColumn(isEditable);

        setShowInsertNewButton(isEditable);
        setShowDeleteButton(isEditable);
        setShowImportDataButton(false);
        if (isEditable)
        {
            AbstractTableInfo tableInfo = (AbstractTableInfo) getTable();
            ActionURL updateUrl = new ActionURL(SpecimenController.UpdateSpecimenQueryRowAction.class, getContainer());
            updateUrl.addParameter("schemaName", "study");
            updateUrl.addParameter(QueryView.DATAREGIONNAME_DEFAULT + "." + QueryParam.queryName, tableInfo.getName());
            setUpdateURL(new DetailsURL(updateUrl, Collections.singletonMap("RowId", "RowId")));

            // we want a DetailsURL-like string so clear the container
            ActionURL insertActionURL = new ActionURL(SpecimenController.UpdateSpecimenQueryRowAction.class, ContainerManager.getRoot());
            insertActionURL.addParameter("schemaName", "study");
            insertActionURL.addParameter(QueryView.DATAREGIONNAME_DEFAULT + "." + QueryParam.queryName, tableInfo.getName());

            setInsertURL(insertActionURL.getLocalURIString(false));
        }

        setViewItemFilter((type, label) ->
            StudyUtils.STUDY_CROSSTAB_REPORT_TYPE.equals(type) || DEFAULT_ITEM_FILTER.accept(type, label)
        );
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

    public static SpecimenQueryView createView(ViewContext context, @NotNull Collection<? extends ParticipantDataset> participantDatasets, ViewType viewType)
    {
        SimpleFilter filter = new SimpleFilter();
        Study study = StudyService.get().getStudy(context.getContainer());
        addFilterClause(requireNonNull(study), filter, participantDatasets);
        return createView(context, filter, createDefaultSort(viewType), viewType, true, null, true);
    }

    public enum PARAMS
    {
        excludeRequestedBySite,
        showCompleteRequestedByEnrollmentSite,
        showCompleteRequestedBySite,
        showCompleteRequestedOnly,
        showNotCompleteRequestedOnly,
        showNotRequestedOnly,
        showRequestedByEnrollmentSite,
        showRequestedBySite,
        showRequestedOnly,
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
        Study study = StudyService.get().getStudy(context.getContainer());
        UserSchema schema = SpecimenQuerySchema.get(study, context.getUser());
        String queryName = viewType.getQueryName();
        QuerySettings qs = schema.getSettings(context, queryName, queryName);
        return createView(context, qs, filter, sort, viewType, participantVisitFiltered, cohortFilter, requireSequenceNum);
    }

    private static SpecimenQueryView createView(ViewContext context, QuerySettings qs, SimpleFilter filter, Sort sort, ViewType viewType,
                                                boolean participantVisitFiltered, CohortFilter cohortFilter, boolean requireSequenceNum)
    {
        Study study = StudyService.get().getStudy(context.getContainer());
        UserSchema schema = SpecimenQuerySchema.get(study, context.getUser());
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
                            _availableSpecimenCounts.putAll(getSpecimenCounts(ctx.getContainer(), specimenHashes));
                            specimenHashes = new HashSet<>();
                        }
                    }
                    while (rs.next());
                    if (!specimenHashes.isEmpty())
                    {
                        _availableSpecimenCounts.putAll(getSpecimenCounts(ctx.getContainer(), specimenHashes));
                    }
                }
                else
                {
                    _availableSpecimenCounts.putAll(getSpecimenCounts(ctx.getContainer(), null));
                }
                rs.absolute(originalRow);
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }

        return _availableSpecimenCounts;
    }

    private Map<String, Integer> getSpecimenCounts(Container container, Collection<String> specimenHashes)
    {
        TableInfo tableInfoSpecimen = SpecimenSchema.get().getTableInfoSpecimen(container);

        SQLFragment extraClause = null;
        if (specimenHashes != null)
        {
            extraClause = new SQLFragment(" WHERE SpecimenHash ");
            tableInfoSpecimen.getSqlDialect().appendInClauseSql(extraClause, specimenHashes);
        }

        final Map<String, Integer> map = new HashMap<>();

        SQLFragment sql = new SQLFragment("SELECT SpecimenHash, CAST(AvailableCount AS Integer) AS AvailableCount FROM ");
        sql.append(tableInfoSpecimen.getFromSQL(""));
        if (extraClause != null)
        {
            sql.append(extraClause);
        }
        new SqlSelector(SpecimenSchema.get().getSchema(), sql).forEach(rs -> {
            String specimenHash = rs.getString("SpecimenHash");
            map.put(specimenHash, rs.getInt("AvailableCount"));
        });

        return map;
    }

    private static Sort createDefaultSort(ViewType viewType)
    {
        if (viewType.isVialView())
            return new Sort("GlobalUniqueId");
        else
            return new Sort(requireNonNull(StudyService.get()).getSubjectColumnName(getContextContainer()) + ",Visit,PrimaryType,DerivativeType,AdditiveType");
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

    protected static SimpleFilter addFilterClause(Study study, SimpleFilter filter, @NotNull Collection<? extends ParticipantDataset> participantDatasets)
    {
        boolean visitBased = study.getTimepointType().isVisitBased();
        if (!participantDatasets.isEmpty())
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
                    whereClause.append(SpecimenSchema.get().getSqlDialect().getDateTimeToDateCast("DrawTimestamp"));
                    whereClause.append(" = ");
                    whereClause.append(SpecimenSchema.get().getSqlDialect().getDateTimeToDateCast("?")).append(" AND ");
                    params.add(pd.getVisitDate());
                }
                whereClause.append(requireNonNull(StudyService.get()).getSubjectColumnName(getContextContainer())).append(" = ?)");
                params.add(pd.getParticipantId());
                sep = " OR ";
            }
            filter = filter.addWhereClause(whereClause.toString(), params.toArray(new Object[params.size()]));
        }
        return filter;
    }

    protected static SimpleFilter addNotPreviouslyRequestedClause(SimpleFilter filter, Container container, int locationId)
    {
        TableInfo tableInfoVial = SpecimenSchema.get().getTableInfoVial(container);
        SQLFragment sql = new SQLFragment("SpecimenHash NOT IN (" +
                "SELECT v.SpecimenHash from study.SampleRequestSpecimen rs join study.SampleRequest r on rs.SamplerequestId=r.RowId "
                + "join ");
        sql.append(tableInfoVial.getFromSQL("v")).append(" on rs.SpecimenGlobalUniqueId=v.GlobalUniqueId "
                + "join study.SamplerequestStatus status ON r.StatusId=status.RowId "
                + "where r.DestinationSiteId=").appendValue(locationId).append(" AND status.SpecimensLocked=").append(tableInfoVial.getSqlDialect().getBooleanTRUE()).append(")");

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

        if (locationId == -1)
        {
            sql.append("IS NULL)");
        }
        else
        {
            sql.append("= ?)");
            sql.add(locationId);
        }

        filter.addWhereClause(sql, FieldKey.fromParts("GlobalUniqueId"));

        return filter;
    }

    public static SQLFragment getBaseRequestedEnrollmentSql(Container container, User user, boolean isCompleteRequestsOnly)
    {
        UserSchema schema = SpecimenQuerySchema.get(StudyService.get().getStudy(container), user);
        TableInfo tableInfoSpecimenDetail = schema.getTable(SpecimenQuerySchema.SPECIMEN_WRAP_TABLE_NAME);
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

    @Override
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
//            if (ViewType.VIALS.equals(_viewType))
//                ((BaseColumnInfo)getTable().getColumn("Container")).setRequired(true);
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
                        String containerId = (String) ctx.getRow().get("Container");
                        Long specimenId = (Long) ctx.getRow().get("RowId");
                        ActionURL url = getHistoryLinkURL(ctx.getViewContext(), containerId).addParameter("id", specimenId.toString()).addParameter("selected", _participantVisitFiltered);
                        return url.toString();
                    }
                });
            }
        }

        boolean oneVialIndicator = false;
        boolean zeroVialIndicator = false;
        if (!_disableLowVialIndicators)
        {
            DisplaySettings settings = SettingsManager.get().getDisplaySettings(getContainer());
            oneVialIndicator = settings.getLastVialEnum() == DisplaySettings.DisplayOption.ALL_USERS ||
                    (settings.getLastVialEnum() == DisplaySettings.DisplayOption.ADMINS_ONLY &&
                            getUser().hasRootAdminPermission());
            zeroVialIndicator = settings.getZeroVialsEnum() == DisplaySettings.DisplayOption.ALL_USERS ||
                    (settings.getZeroVialsEnum() == DisplaySettings.DisplayOption.ADMINS_ONLY &&
                            getUser().hasRootAdminPermission());
        }
        RepositorySettings settings = SettingsManager.get().getRepositorySettings(getContainer());
        if (settings.isEnableRequests() && !_viewType.isForExport() && getViewContext().getContainer().hasPermission(getUser(), RequestSpecimensPermission.class))
        {
            // Only add this column if we're using advanced specimen management and not exported to email or attachment
            cols.add(0, new SpecimenRequestDisplayColumn(this, getTable(), zeroVialIndicator, oneVialIndicator,
                    SettingsManager.get().isSpecimenShoppingCartEnabled(getContainer()) && _showRecordSelectors));
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

    @Override
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

        try (Results results = rgn.getResults(renderContext))
        {
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

            ResultSetRowMapFactory factory = ResultSetRowMapFactory.create(results);

            while (results.next())
            {
                renderContext.setRow(factory.getRowMap(results));
                builder.append("  <tr style=\"background-color:").append(row++ % 2 == 0 ? "#FFFFFF" : "#DDDDDD").append("\">\n");
                builder.append("    <td style=\"border: 1px solid #BBBBBB\">").append(row).append("</td>\n");
                for (DisplayColumn col : columns)
                {
                    HtmlString value = col.getFormattedHtml(renderContext);
                    builder.append("    <td style=\"border: 1px solid #BBBBBB\">").append(value).append("</td>\n");
                }
                builder.append("  </tr>\n");
            }
            builder.append("</table>");
            return builder.toString();
        }
    }

    @Override
    public void addManageViewItems(MenuButton button, Map<String, String> params)
    {
        ActionURL url = requireNonNull(PageFlowUtil.urlProvider(ReportUrls.class)).urlManageViews(getViewContext().getContainer());
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
