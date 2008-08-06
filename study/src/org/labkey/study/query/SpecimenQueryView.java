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

package org.labkey.study.query;

import org.apache.commons.lang.StringUtils;
import org.labkey.api.data.*;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;
import org.labkey.study.SampleManager;
import org.labkey.study.model.*;

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
public class SpecimenQueryView extends StudyQueryView
{
    private static class VialRestrictedDataRegion extends DataRegion
    {
        private String _historyLinkBase = null;

        protected boolean isRecordSelectorEnabled(RenderContext ctx)
        {
            return isAvailable(ctx);
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
                _historyLinkBase = ActionURL.toPathString("Study-Samples", "sampleEvents", ctx.getContainer().getPath()) + "?id=";
            Integer specimenId = (Integer) ctx.getRow().get("RowId");
            return _historyLinkBase + specimenId;
        }

        private boolean isAtRepository(RenderContext ctx)
        {
            Object value = ctx.getRow().get("AtRepository");
            if (value instanceof Integer)
                return ((Integer) value) != 0;
            else if (value instanceof Boolean)
                return ((Boolean) value).booleanValue();
            return false;
        }

        private boolean isInActiveRequest(RenderContext ctx)
        {
            Object value = ctx.getRow().get("LockedInRequest");
            if (value instanceof Integer)
                return ((Integer) value) != 0;
            else if (value instanceof Boolean)
                return ((Boolean) value).booleanValue();
            return false;
        }

        private boolean isAvailable(RenderContext ctx)
        {
            Object value = ctx.getRow().get("Available");
            if (value instanceof Integer)
                return ((Integer) value) != 0;
            else if (value instanceof Boolean)
                return ((Boolean) value).booleanValue();
            return false;
        }
    }

    private class SpecimenRestrictedDataRegion extends DataRegion
    {
        protected boolean isRecordSelectorEnabled(RenderContext ctx)
        {
            return isAvailable(ctx);
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
            if (ctx.getRow().get("AvailableCount") != null)
            {
                count = ((Number)ctx.getRow().get("AvailableCount")).intValue();
            }
            else
            {
                SpecimenSummary current = new SpecimenSummary(ctx.getContainer(), ctx.getRow());
                count = getSampleCounts(ctx).get(current);
            }
            return (count != null && count.intValue() > 0);
        }
    }

    private class LastSpecimenDisplayColumn extends SimpleDisplayColumn
    {
        private boolean _showOneVialIndicator;
        private boolean _showZeroVialIndicator;
        private TableInfo _table;

        public LastSpecimenDisplayColumn(TableInfo table, boolean showOneVialIndicator, boolean showZeroVialIndicator)
        {
            _showOneVialIndicator = showOneVialIndicator;
            _showZeroVialIndicator = showZeroVialIndicator;
            _table = table;
        }

        @Override
        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            SpecimenSummary current = new SpecimenSummary(ctx.getContainer(), ctx.getRow());
            Integer count;
            if (ctx.getRow().get("AvailableCount") != null)
            {
                count = ((Number)ctx.getRow().get("AvailableCount")).intValue();
            }
            else
            {
                count = getSampleCounts(ctx).get(current);
            }
            
            if (_showOneVialIndicator && count == 1)
            {
                String exclaimHtml = "<center><img src=\"" + ctx.getViewContext().getContextPath() + "/_images/one.gif\"></center>";
                out.write(PageFlowUtil.helpPopup("One Vial Available",
                        "Only one vial of this primary specimen is available.  Study procedures may not permit requests for the last vial of a primary specimen.",
                        false, exclaimHtml));
            }
            else if (_showZeroVialIndicator && count == 0)
            {
                String exclaimHtml = "<center><img src=\"" + ctx.getViewContext().getContextPath() + "/_images/exclaim.gif\"></center>";
                out.write(PageFlowUtil.helpPopup("Zero Vials Available",
                        "No vials of this primary specimen are currently available for request.",
                        false, exclaimHtml));
            }
            else if (_showZeroVialIndicator || _showOneVialIndicator)
                out.write(PageFlowUtil.helpPopup(count + " Vials Available",
                        count + " Vials of this primary specimen are currently available for new requests.", false,
                        "<center style='color:gray'>" + String.valueOf(count)  + "</center>"));
            else
                out.write("&nbsp;");
        }


        @Override
        public void addQueryColumns(Set<ColumnInfo> set)
        {
            set.add(_table.getColumn("SpecimenNumber"));
            set.add(_table.getColumn("PrimaryType"));
            set.add(_table.getColumn("DerivativeType"));
            set.add(_table.getColumn("ParticipantId"));
            set.add(_table.getColumn("VisitDescription"));
            set.add(_table.getColumn("Visit"));
            set.add(_table.getColumn("VolumeUnits"));
            set.add(_table.getColumn("AdditiveType"));
            set.add(_table.getColumn("DrawTimestamp"));
            set.add(_table.getColumn("SalReceiptDate"));
            set.add(_table.getColumn("ClassId"));
            set.add(_table.getColumn("ProtocolNumber"));
            set.add(_table.getColumn("SubAdditiveDerivative"));
            // fix for https://cpas.fhcrc.org/Issues/home/issues/details.view?issueId=3116
            ColumnInfo atRepositoryColumn = _table.getColumn("AtRepository");
            if (atRepositoryColumn != null)
                set.add(atRepositoryColumn);
            ColumnInfo lockedInRequestColumn = _table.getColumn("LockedInRequest");
            if (lockedInRequestColumn != null)
                set.add(lockedInRequestColumn);
            ColumnInfo availableCountColumn = _table.getColumn("AvailableCount");
            if (availableCountColumn != null)
                set.add(availableCountColumn);
        }
    }

    private boolean _detailsView;
    private boolean _showHistoryLinks;
    private boolean _disableLowVialIndicators;
    private boolean _showRecordSelectors;
    private boolean _restrictRecordSelectors = true;
    private Map<String, String> _hiddenFormFields;
    private Map<SpecimenSummary,Integer> _availableSpecimenCounts;
    private boolean _participantVisitFiltered = false;


    protected SpecimenQueryView(ViewContext context, UserSchema schema, QuerySettings settings,
                            SimpleFilter filter, Sort sort, boolean detailsView, boolean participantVisitFiltered)
    {
        super(context, schema, settings, filter, sort);
        _detailsView = detailsView;
        _participantVisitFiltered = participantVisitFiltered;
    }

    public static SpecimenQueryView createView(ViewContext context, boolean detailsView)
    {
        SimpleFilter filter = new SimpleFilter();
        return createView(context, filter, createDefaultSort(detailsView), detailsView, false);
    }

    public static SpecimenQueryView createView(ViewContext context, Specimen[] samples, boolean detailsView)
    {
        SimpleFilter filter = new SimpleFilter();
        addFilterClause(filter, samples, detailsView);
        return createView(context, filter, createDefaultSort(detailsView), detailsView, false);
    }

    public static SpecimenQueryView createView(ViewContext context, ParticipantDataset[] participantDatasets, boolean detailsView)
    {
        SimpleFilter filter = new SimpleFilter();
        addFilterClause(filter, participantDatasets);
        return createView(context, filter, createDefaultSort(detailsView), detailsView, true);
    }

    public enum PARAMS
    {
        excludeRequestedBySite,
        showRequestedBySite,
        showRequestedByEnrollmentSite,
        showRequestedOnly
    }

    private static SpecimenQueryView createView(ViewContext context, SimpleFilter filter, Sort sort, boolean detailsView, boolean participantVisitFiltered)
    {
        Study study = StudyManager.getInstance().getStudy(context.getContainer());
        StudyQuerySchema schema = new StudyQuerySchema(study, context.getUser(), true);
        String queryName = detailsView ? "SpecimenDetail" : "SpecimenSummary";
        QuerySettings qs = new QuerySettings(context, queryName);
        qs.setSchemaName(schema.getSchemaName());
        qs.setQueryName(queryName);
        qs.setAllowCustomizeView(false);

        String excludeRequested = StringUtils.trimToNull((String) context.get(PARAMS.excludeRequestedBySite.name()));
        if (null != excludeRequested)
            addNotPreviouslyRequestedClause(filter, context.getContainer(), Integer.parseInt(excludeRequested));
        String showRequestedBySite = StringUtils.trimToNull((String) context.get(PARAMS.showRequestedBySite.name()));
        if (null != showRequestedBySite)
            addOnlyPreviouslyRequestedClause(filter, context.getContainer(), Integer.parseInt(showRequestedBySite));
        String showRequestedByEnrollmentSite = StringUtils.trimToNull((String) context.get(PARAMS.showRequestedByEnrollmentSite.name()));
        if (null != showRequestedByEnrollmentSite)
            addPreviouslyRequestedEnrollmentClause(filter, context.getContainer(), Integer.parseInt(showRequestedByEnrollmentSite));
        String showRequestedOnly = StringUtils.trimToNull((String) context.get(PARAMS.showRequestedOnly.name()));
        if (null != showRequestedOnly && showRequestedOnly.equalsIgnoreCase("true"))
            addOnlyPreviouslyRequestedClause(filter, context.getContainer());
        return new SpecimenQueryView(context, schema, qs, filter, sort, detailsView, participantVisitFiltered);
    }

    private Map<SpecimenSummary,Integer> getSampleCounts(RenderContext ctx)
    {
        if (null  == _availableSpecimenCounts)
            try
            {
                _availableSpecimenCounts = new HashMap<SpecimenSummary, Integer>();
                ResultSet rs = ctx.getResultSet();
                Set<String> specimenNumbers = new HashSet<String>();
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
                        specimenNumbers.add(rs.getString("SpecimenNumber"));
                        if (specimenNumbers.size() > 150)
                        {
                            _availableSpecimenCounts.putAll(SampleManager.getInstance().getSampleCounts(ctx.getContainer(), specimenNumbers));
                            specimenNumbers = new HashSet<String>();
                        }
                    }
                    while(rs.next());
                    if (!specimenNumbers.isEmpty())
                    {
                        _availableSpecimenCounts.putAll(SampleManager.getInstance().getSampleCounts(ctx.getContainer(), specimenNumbers));
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

    private static Sort createDefaultSort(boolean details)
    {
        if (details)
            return new Sort("SpecimenNumber,GlobalUniqueId");
        else
            return new Sort("SpecimenNumber");
    }

    protected static SimpleFilter addFilterClause(SimpleFilter filter, Specimen[] specimens, boolean detailsView)
    {
        if (specimens != null && specimens.length > 0)
        {
            StringBuilder whereClause = new StringBuilder();
            if (detailsView)
                whereClause.append("RowId IN (");
            else
                whereClause.append("SpecimenNumber IN (");
            for (int i = 0; i < specimens.length; i++)
            {
                if (detailsView)
                    whereClause.append(specimens[i].getRowId());
                else
                    whereClause.append(specimens[i].getSpecimenNumber());
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
        String sql = "SpecimenNumber NOT IN (" +
        "SELECT s.SpecimenNumber from study.SampleRequestSpecimen rs join study.SampleRequest r on rs.SamplerequestId=r.RowId "
                + "join study.Specimen s on rs.SpecimenGlobalUniqueId=s.GlobalUniqueId "
                + "join study.SamplerequestStatus status ON r.StatusId=status.RowId "
                +  "where r.DestinationSiteId=? AND s.Container=? AND status.SpecimensLocked=?)";

        filter.addWhereClause(sql, new Object[] {siteId, container.getId(), Boolean.TRUE}, "SpecimenNumber");

        return filter;
    }

    protected static SimpleFilter addOnlyPreviouslyRequestedClause(SimpleFilter filter, Container container, int siteId)
    {
        String sql = "GlobalUniqueId IN ("
                + "SELECT rs.SpecimenGlobalUniqueId FROM study.SampleRequestSpecimen rs join study.SampleRequest r on rs.SamplerequestId=r.RowId "
                + "join study.SamplerequestStatus status ON r.StatusId=status.RowId "
                +  "where r.DestinationSiteId=? AND rs.Container=? AND status.SpecimensLocked=?)";

        filter.addWhereClause(sql, new Object[] {siteId, container.getId(), Boolean.TRUE}, "GlobalUniqueId");

        return filter;
    }

    protected static SimpleFilter addPreviouslyRequestedEnrollmentClause(SimpleFilter filter, Container container, int siteId)
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
                        "     Specimen.Container = ? AND\n" +
                        "     Participant.EnrollmentSiteId ";
                        Object[] params;
                        if (siteId == -1)
                        {
                            sql += "IS NULL)";
                            params = new Object[] { Boolean.TRUE, container.getId()};
                        }
                        else
                        {
                            sql += "= ?)";
                            params = new Object[] { Boolean.TRUE, container.getId(), siteId };
                        }



        filter.addWhereClause(sql, params, "GlobalUniqueId");

        return filter;
    }

    protected static SimpleFilter addOnlyPreviouslyRequestedClause(SimpleFilter filter, Container container)
    {
        String sql = "GlobalUniqueId IN ("
                + "SELECT rs.SpecimenGlobalUniqueId FROM study.SampleRequestSpecimen rs join study.SampleRequest r on rs.SamplerequestId=r.RowId "
                + "join study.SamplerequestStatus status ON r.StatusId=status.RowId "
                +  "where rs.Container=? AND status.SpecimensLocked=?)";

        filter.addWhereClause(sql, new Object[] {container.getId(), Boolean.TRUE}, "GlobalUniqueId");

        return filter;
    }

    protected DataRegion createDataRegion()
    {
        DataRegion rgn;
        if (_detailsView)
        {
            if (_showRecordSelectors && _restrictRecordSelectors)
                rgn = new VialRestrictedDataRegion();
            else
                rgn = new DataRegion();
            rgn.setName(getDataRegionName());
            rgn.setDisplayColumns(getDisplayColumns());
            rgn.setShowRecordSelectors(_showRecordSelectors);
            if (_showHistoryLinks)
            {
                rgn.setRecordSelectorValueColumns("RowId");
                String eventsBase = ActionURL.toPathString("Study-Samples", "sampleEvents", getContainer().getPath());
                rgn.addDisplayColumn(0, new SimpleDisplayColumn("<a href=\"" + eventsBase + "?selected=" +
                        Boolean.toString(_participantVisitFiltered) + "&id=${rowid}\">[history]</a>"));
            }
            rgn.setAggregates(new Aggregate(getTable().getColumn("Volume"), Aggregate.Type.SUM),
                    new Aggregate(getTable().getColumn("SpecimenNumber"), Aggregate.Type.COUNT));
        }
        else
        {
            if (_showRecordSelectors && _restrictRecordSelectors)
                rgn = new SpecimenRestrictedDataRegion();
            else
                rgn = new DataRegion();
            rgn.setName(getDataRegionName());
            rgn.setDisplayColumns(getDisplayColumns());
            rgn.setShowRecordSelectors(_showRecordSelectors);

            rgn.setRecordSelectorValueColumns("SpecimenNumber","DerivativeType","AdditiveType");
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
        if (!_disableLowVialIndicators)
        {
            try
            {
                SampleManager.DisplaySettings settings =  SampleManager.getInstance().getDisplaySettings(getContainer());
                boolean oneVialIndicator = settings.getLastVialEnum() == SampleManager.DisplaySettings.DisplayOption.ALL_USERS ||
                    (settings.getLastVialEnum() == SampleManager.DisplaySettings.DisplayOption.ADMINS_ONLY &&
                        getUser().isAdministrator());
                boolean zeroVialIndicator = settings.getZeroVialsEnum() == SampleManager.DisplaySettings.DisplayOption.ALL_USERS ||
                        (settings.getZeroVialsEnum() == SampleManager.DisplaySettings.DisplayOption.ADMINS_ONLY &&
                                getUser().isAdministrator());
                rgn.addDisplayColumn(0, new LastSpecimenDisplayColumn(getTable(), zeroVialIndicator, oneVialIndicator));
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
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

        return rgn;
    }

    public boolean isShowingVials()
    {
        return _detailsView;
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
            builder.append("<table>\n  <tr>\n");
            for (DisplayColumn col : columns)
            {
                String header = PageFlowUtil.filter(col.getCaption());
                header = header.replaceAll(" ", "&nbsp;");
                builder.append("    <td><u>").append(header).append("</u></td>\n");
            }
            builder.append("  </tr>\n");
            Map<String, Object> rowMap = null;
            while (rs.next())
            {
                renderContext.setRow(ResultSetUtil.mapRow(rs, rowMap));
                builder.append("  <tr>\n");
                for (DisplayColumn col : columns)
                {
                    Object value = col.getDisplayValue(renderContext);
                    builder.append("    <td valign=\"top\">").append(PageFlowUtil.filter(value)).append("</td>\n");
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
}
