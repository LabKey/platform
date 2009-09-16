/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

import org.apache.commons.lang.StringUtils;
import org.labkey.api.collections.ResultSetRowMapFactory;
import org.labkey.api.data.*;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;
import org.labkey.study.SampleManager;
import org.labkey.study.security.permissions.RequestSpecimensPermission;
import org.labkey.study.samples.settings.DisplaySettings;
import org.labkey.study.samples.settings.RepositorySettings;
import org.labkey.study.controllers.samples.SpecimenUtils;
import org.labkey.study.controllers.samples.SpringSpecimenController;
import org.labkey.study.controllers.reports.ReportsController;
import org.labkey.study.model.ParticipantDataset;
import org.labkey.study.model.Specimen;
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
    private static class SpecimenDataRegion extends DataRegion
    {
        private Map<String, ColumnInfo> _requiredColumns = new HashMap<String, ColumnInfo>();

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
            for (Iterator<ColumnInfo> it = currentColumns.iterator(); it.hasNext() && foundCol == null;)
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
            List<String> cols = new ArrayList<String>();
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
        private String _historyLinkBase = null;

        protected boolean isRecordSelectorEnabled(RenderContext ctx)
        {
            return isAvailable(ctx);
        }

        @Override
        protected List<String> getRequiredColumns()
        {
            List<String> required = super.getRequiredColumns();
            required.add("GlobalUniqueId");
            required.add("AtRepository");
            required.add("LockedInRequest");
            required.add("Available");
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
                if (!isAtRepository(ctx))
                {
                    out.write(PageFlowUtil.helpPopup("Specimen Unavailable",
                            "This specimen is unavailable because it is not currently held by a repository.<br><br>" +
                                    "Click [<a href=\"" + getHistoryLink(ctx) + "\">history</a>] for more information.", true));
                }
                else if (isInActiveRequest(ctx))
                {
                    out.write(PageFlowUtil.helpPopup("Specimen Unavailable",
                            "This specimen is unavailable because it is part of an active specimen request.<br><br>" +
                                    "Click [<a href=\"" + getHistoryLink(ctx) + "\">history</a>] for more information.", true));
                }
                else
                {
                    out.write(PageFlowUtil.helpPopup("Specimen Unavailable",
                            "This specimen is unavailable an administrator has overridden its availability status.<br><br>" +
                                    "Contact a system administrator for more information.", true));
                }
            }
        }

        private String getHistoryLink(RenderContext ctx)
        {
            if (_historyLinkBase == null)
                _historyLinkBase = getHistoryLinkBase(ctx.getViewContext());
            Integer specimenId = (Integer) ctx.getRow().get("RowId");
            return _historyLinkBase + specimenId;
        }

        private boolean isAtRepository(RenderContext ctx)
        {
            return SpecimenUtils.isFieldTrue(ctx, getRequiredColumnAlias("AtRepository"));
        }

        private boolean isInActiveRequest(RenderContext ctx)
        {
            return SpecimenUtils.isFieldTrue(ctx, getRequiredColumnAlias("LockedInRequest"));
        }

        private boolean isAvailable(RenderContext ctx)
        {
            return SpecimenUtils.isFieldTrue(ctx, getRequiredColumnAlias("Available"));
        }
    }

    protected static String getHistoryLinkBase(ViewContext ctx)
    {
        ActionURL historyLink = new ActionURL(SpringSpecimenController.SampleEventsAction.class, ctx.getContainer());
        historyLink.addParameter("returnUrl", ctx.getActionURL().getLocalURIString());
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
                count = ((Number)getRequiredColumnValue(ctx, "AvailableCount")).intValue();
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
            }},
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
            }},
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
            }};

        public abstract String getQueryName();
        public abstract String getViewName();
        public abstract boolean isVialView();
    }

    protected SpecimenQueryView(ViewContext context, UserSchema schema, QuerySettings settings,
                            SimpleFilter filter, Sort sort, ViewType viewType, boolean participantVisitFiltered)
    {
        super(context, schema, settings, filter, sort);
        _viewType = viewType;
        _participantVisitFiltered = participantVisitFiltered;
    }

    public static SpecimenQueryView createView(ViewContext context, ViewType viewType)
    {
        SimpleFilter filter = new SimpleFilter();
        return createView(context, filter, createDefaultSort(viewType), viewType, false);
    }

    public static SpecimenQueryView createView(ViewContext context, Specimen[] samples, ViewType viewType)
    {
        SimpleFilter filter = new SimpleFilter();
        addFilterClause(filter, samples, viewType);
        return createView(context, filter, createDefaultSort(viewType), viewType, false);
    }

    public static SpecimenQueryView createView(ViewContext context, ParticipantDataset[] participantDatasets, ViewType viewType)
    {
        SimpleFilter filter = new SimpleFilter();
        addFilterClause(filter, participantDatasets);
        return createView(context, filter, createDefaultSort(viewType), viewType, true);
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
            addPreviouslyRequestedEnrollmentClause(filter, context.getContainer(), Integer.parseInt(showRequestedByEnrollmentSite), false);
        else
        {
            showRequestedByEnrollmentSite = StringUtils.trimToNull((String) context.get(PARAMS.showCompleteRequestedByEnrollmentSite.name()));
            if (null != showRequestedByEnrollmentSite)
                addPreviouslyRequestedEnrollmentClause(filter, context.getContainer(), Integer.parseInt(showRequestedByEnrollmentSite), true);
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

    private static SpecimenQueryView createView(ViewContext context, SimpleFilter filter, Sort sort, ViewType viewType, boolean participantVisitFiltered)
    {
        StudyImpl study = StudyManager.getInstance().getStudy(context.getContainer());
        StudyQuerySchema schema = new StudyQuerySchema(study, context.getUser(), true);
        String queryName = viewType.getQueryName();
        QuerySettings qs = new QuerySettings(context, queryName);
        qs.setSchemaName(schema.getSchemaName());
        qs.setQueryName(queryName);
        String viewName = viewType.getViewName();
        if (qs.getViewName() == null && viewName != null)
            qs.setViewName(viewName);
        qs.setAllowCustomizeView(false);

        String excludeRequested = StringUtils.trimToNull((String) context.get(PARAMS.excludeRequestedBySite.name()));
        if (null != excludeRequested)
            addNotPreviouslyRequestedClause(filter, context.getContainer(), Integer.parseInt(excludeRequested));
        addOnlyPreviouslyRequestedFilter(context, filter);
        addEnrollmentSiteRequestFilter(context, filter);
        addRequestFilter(context, filter);
        return new SpecimenQueryView(context, schema, qs, filter, sort, viewType, participantVisitFiltered);
    }

    public Map<String, Integer> getSampleCounts(RenderContext ctx)
    {
        if (null  == _availableSpecimenCounts)
            try
            {
                _availableSpecimenCounts = new HashMap<String, Integer>();
                ResultSet rs = ctx.getResultSet();
                Set<String> specimenHashes = new HashSet<String>();
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
                            _availableSpecimenCounts.putAll(SampleManager.getInstance().getSampleCounts(ctx.getContainer(), specimenHashes));
                            specimenHashes = new HashSet<String>();
                        }
                    }
                    while(rs.next());
                    if (!specimenHashes.isEmpty())
                    {
                        _availableSpecimenCounts.putAll(SampleManager.getInstance().getSampleCounts(ctx.getContainer(), specimenHashes));
                    }
                }
                else
                {
                    _availableSpecimenCounts.putAll(SampleManager.getInstance().getSampleCounts(ctx.getContainer(), null));
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
            return new Sort("ParticipantId,Visit,PrimaryType,DerivativeType,AdditiveType");
    }

    protected static SimpleFilter addFilterClause(SimpleFilter filter, Specimen[] specimens, ViewType viewType)
    {
        if (specimens != null && specimens.length > 0)
        {
            StringBuilder whereClause = new StringBuilder();
            if (viewType.isVialView())
                whereClause.append("RowId IN (");
            else
                whereClause.append("SpecimenHash IN (");
            for (int i = 0; i < specimens.length; i++)
            {
                if (viewType.isVialView())
                    whereClause.append(specimens[i].getRowId());
                else
                    whereClause.append(specimens[i].getSpecimenHash());
                if (i < specimens.length - 1)
                    whereClause.append(", ");
            }
            whereClause.append(")");
            filter = filter.addWhereClause(whereClause.toString(), null);
        }
        return filter;
    }

    protected static SimpleFilter addFilterClause(SimpleFilter filter, ParticipantDataset[] participantDatasets)
    {
        if (participantDatasets != null && participantDatasets.length > 0)
        {
            StringBuilder whereClause = new StringBuilder();
            Object[] params = new Object[2 * participantDatasets.length];
            int param = 0;
            for (ParticipantDataset pd : participantDatasets)
            {
                if (param > 0)
                    whereClause.append(" OR ");
                whereClause.append("(Visit = ? AND ParticipantId = ?)");
                params[param++] = pd.getSequenceNum();
                params[param++] = pd.getParticipantId();
            }
            filter = filter.addWhereClause(whereClause.toString(), params);
        }
        return filter;
    }

    protected static SimpleFilter addNotPreviouslyRequestedClause(SimpleFilter filter, Container container, int siteId)
    {
        String sql = "SpecimenHash NOT IN (" +
        "SELECT s.SpecimenHash from study.SampleRequestSpecimen rs join study.SampleRequest r on rs.SamplerequestId=r.RowId "
                + "join study.Specimen s on rs.SpecimenGlobalUniqueId=s.GlobalUniqueId "
                + "join study.SamplerequestStatus status ON r.StatusId=status.RowId "
                +  "where r.DestinationSiteId=? AND s.Container=? AND status.SpecimensLocked=?)";

        filter.addWhereClause(sql, new Object[] {siteId, container.getId(), Boolean.TRUE}, "SpecimenHash");

        return filter;
    }

    protected static SimpleFilter addOnlyPreviouslyRequestedClause(SimpleFilter filter, Container container, int siteId, boolean completedRequestsOnly)
    {
        String sql = "GlobalUniqueId IN ("
                + "SELECT rs.SpecimenGlobalUniqueId FROM study.SampleRequestSpecimen rs join study.SampleRequest r on rs.SamplerequestId=r.RowId "
                + "join study.SamplerequestStatus status ON r.StatusId=status.RowId "
                +  "where r.DestinationSiteId=? AND rs.Container=? AND status.SpecimensLocked=?" +
                (completedRequestsOnly ? " AND status.FinalState=?" : "") + ")";

        Object[] params;
        if (completedRequestsOnly)
            params = new Object[] {siteId, container.getId(), Boolean.TRUE, Boolean.TRUE};
        else
            params = new Object[] {siteId, container.getId(), Boolean.TRUE};

        filter.addWhereClause(sql, params, "GlobalUniqueId");

        return filter;
    }

    protected static SimpleFilter addPreviouslyRequestedEnrollmentClause(SimpleFilter filter, Container container, int siteId, boolean completedRequestsOnly)
    {
        String sql = "GlobalUniqueId IN (SELECT Specimen.GlobalUniqueId FROM study.Specimen AS Specimen,\n" +
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
                        (completedRequestsOnly ? "     Status.FinalState = ? AND\n" : "") +
                        "     Specimen.Container = ? AND\n" +
                        "     Participant.EnrollmentSiteId ";

        Object[] params;
        if (siteId == -1)
        {
            sql += "IS NULL)";
            if (completedRequestsOnly)
                params = new Object[] { Boolean.TRUE, Boolean.TRUE, container.getId()};
            else
                params = new Object[] { Boolean.TRUE, container.getId()};
        }
        else
        {
            sql += "= ?)";
            if (completedRequestsOnly)
                params = new Object[] { Boolean.TRUE, Boolean.TRUE, container.getId(), siteId };
            else
                params = new Object[] { Boolean.TRUE, container.getId(), siteId };
        }

        filter.addWhereClause(sql, params, "GlobalUniqueId");

        return filter;
    }

    protected static SimpleFilter addPreviouslyRequestedClause(SimpleFilter filter, Container container, boolean showRequested, boolean completedRequestsOnly)
    {
        String sql = "GlobalUniqueId " + (showRequested ? "" : "NOT ") + "IN ("
                + "SELECT rs.SpecimenGlobalUniqueId FROM study.SampleRequestSpecimen rs join study.SampleRequest r on rs.SamplerequestId=r.RowId "
                + "join study.SamplerequestStatus status ON r.StatusId=status.RowId "
                +  "where rs.Container=? AND status.SpecimensLocked=?" + (completedRequestsOnly ? " AND status.FinalState=?" : "") +
                ")";
        Object[] params;
        if (completedRequestsOnly)
            params = new Object[] {container.getId(), Boolean.TRUE, Boolean.TRUE};
        else
            params = new Object[] {container.getId(), Boolean.TRUE};

        filter.addWhereClause(sql, params, "GlobalUniqueId");

        return filter;
    }

    protected DataRegion createDataRegion()
    {
        DataRegion rgn;
        if (_viewType.isVialView())
        {
            if (_showRecordSelectors && _restrictRecordSelectors)
                rgn = new VialRestrictedDataRegion();
            else
                rgn = new SpecimenDataRegion();
            rgn.setName(getDataRegionName());
            rgn.setDisplayColumns(getDisplayColumns());
            rgn.setShowRecordSelectors(_showRecordSelectors);
            rgn.setRecordSelectorValueColumns("RowId");
            if (_showHistoryLinks)
            {
                String eventsBase = getHistoryLinkBase(getViewContext());
                rgn.addDisplayColumn(0, new SimpleDisplayColumn("<a href=\"" + eventsBase + "${rowid}&selected=" +
                        Boolean.toString(_participantVisitFiltered) + "\">[history]</a>"));
            }
            rgn.setAggregates(new Aggregate(getTable().getColumn("Volume"), Aggregate.Type.SUM),
                    new Aggregate(getTable().getColumn("GlobalUniqueId"), Aggregate.Type.COUNT));
        }
        else
        {
            if (_showRecordSelectors && _restrictRecordSelectors)
                rgn = new SpecimenRestrictedDataRegion();
            else
                rgn = new SpecimenDataRegion();
            rgn.setName(getDataRegionName());
            rgn.setDisplayColumns(getDisplayColumns());
            rgn.setShowRecordSelectors(_showRecordSelectors);

            rgn.setRecordSelectorValueColumns("SpecimenHash");
            if (_showRecordSelectors)
            {
                if (null == _hiddenFormFields)
                    _hiddenFormFields = new HashMap<String, String>();
                _hiddenFormFields.put("fromGroupedView", Boolean.TRUE.toString());
            }
            rgn.setAggregates(new Aggregate(getTable().getColumn("TotalVolume"), Aggregate.Type.SUM),
                    new Aggregate(getTable().getColumn("LockedInRequestCount"), Aggregate.Type.SUM),
                    new Aggregate(getTable().getColumn("AtRepositoryCount"), Aggregate.Type.SUM),
                    new Aggregate(getTable().getColumn("VialCount"), Aggregate.Type.SUM),
                    new Aggregate(getTable().getColumn("AvailableVolume"), Aggregate.Type.SUM),
                    new Aggregate(getTable().getColumn("AvailableCount"), Aggregate.Type.SUM));
        }
        try
        {
            boolean oneVialIndicator = false;
            boolean zeroVialIndicator = false;
            if (!_disableLowVialIndicators)
            {
                DisplaySettings settings =  SampleManager.getInstance().getDisplaySettings(getContainer());
                oneVialIndicator = settings.getLastVialEnum() == DisplaySettings.DisplayOption.ALL_USERS ||
                    (settings.getLastVialEnum() == DisplaySettings.DisplayOption.ADMINS_ONLY &&
                        getUser().isAdministrator());
                zeroVialIndicator = settings.getZeroVialsEnum() == DisplaySettings.DisplayOption.ALL_USERS ||
                        (settings.getZeroVialsEnum() == DisplaySettings.DisplayOption.ADMINS_ONLY &&
                                getUser().isAdministrator());
            }
            RepositorySettings settings = SampleManager.getInstance().getRepositorySettings(getContainer());
            if (!settings.isSimple() && getViewContext().getContainer().hasPermission(getUser(), RequestSpecimensPermission.class))
            {
                // Only add this column if we're using advanced specimen management
                rgn.addDisplayColumn(0, new SpecimenRequestDisplayColumn(this, getTable(), zeroVialIndicator, oneVialIndicator,
                    SampleManager.getInstance().isSpecimenShoppingCartEnabled(getContainer()) && _showRecordSelectors));
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

        DisplayColumn rowIdCol = rgn.getDisplayColumn("RowId");
        if (rowIdCol != null)
            rowIdCol.setVisible(false);
        DisplayColumn containerIdCol = rgn.getDisplayColumn("Container");
        if (containerIdCol != null)
            containerIdCol.setVisible(false);

        rgn.setShadeAlternatingRows(true);
        rgn.setShowBorders(true);
        rgn.setMaxRows(getMaxRows());
        rgn.setOffset(getOffset());
        rgn.setShowRows(getShowRows());
        rgn.setSelectionKey(getSelectionKey());
        StudyManager.getInstance().applyDefaultFormats(getContainer(), rgn.getDisplayColumns());

        if (_hiddenFormFields != null)
        {
            for (Map.Entry<String,String> param : _hiddenFormFields.entrySet())
                rgn.addHiddenFormField(param.getKey(), param.getValue());
        }
        
        rgn.addHiddenFormField("referrer", getViewContext().getActionURL().getLocalURIString());

        return rgn;
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
            _hiddenFormFields = new HashMap<String, String>();
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
        DataView view = createDataView();
        DataRegion rgn = view.getDataRegion();
        rgn.setMaxRows(0);
        RenderContext renderContext = view.getRenderContext();
        ResultSet rs = null;
        try
        {
            rs = rgn.getResultSet(renderContext);
            List<DisplayColumn> allColumns = getExportColumns(rgn.getDisplayColumns());
            List<DisplayColumn> columns = new ArrayList<DisplayColumn>();
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
                    Object value = col.getDisplayValue(renderContext);
                    builder.append("    <td style=\"border: 1px solid #BBBBBB\">").append(PageFlowUtil.filter(value)).append("</td>\n");
                }
                builder.append("  </tr>\n");
            }
            builder.append("</table>");
            return builder.toString();
        }
        finally
        {
            if (rs != null) try { rs.close(); } catch (SQLException e) {}
        }
    }

    @Override
    public void addManageViewItems(MenuButton button, Map<String, String> params)
    {
        ActionURL url = new ActionURL(ReportsController.ManageReportsAction.class, getContainer());
        for (Map.Entry<String, String> entry : params.entrySet())
            url.addParameter(entry.getKey(), entry.getValue());

        button.addMenuItem("Manage Views", url);
    }
}
