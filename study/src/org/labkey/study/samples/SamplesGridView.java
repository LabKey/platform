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

package org.labkey.study.samples;

import org.labkey.api.view.GridView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.data.*;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.study.StudySchema;
import org.labkey.study.model.ParticipantDataset;
import org.labkey.study.model.Specimen;

import java.io.Writer;
import java.io.IOException;

/**
 * User: brittp
 * Date: Feb 17, 2006
 * Time: 10:41:16 AM
 */
public class SamplesGridView extends GridView
{
    private static final String SPECIMEN_COLUMN_ORDER =
            "RowId, SpecimenNumber, GlobalUniqueId, Ptid, VisitDescription, VisitValue, Volume, " +
                    "VolumeUnits, PrimaryTypeId, AdditiveTypeId, DerivativeTypeId, SiteName, " +
                    "SiteLdmsCode, DrawTimestamp, LockedInRequest, AtRepository, Available," +
                    "SalReceiptDate, ClassId, ProtocolNumber, OtherSpecimenId, ExpectedTimeValue, " +
                    "ExpectedTimeUnit, SubAdditiveDerivative, SpecimenCondition, " +
                    "SampleNumber, XSampleOrigin, ExternalLocation, UpdateTimestamp, RecordSource";

    // the list of columns whose sort/filter options should not be carried over when switching between
    // grouped and vial views of specimens:
    public static final String[] UNTRANSLATABLE_COLUMNS = {
            "LockedInRequest", "AtRepository", "Available",
            "GlobalUniqueId", "VialCount", "AvailableVolume"};

    private static final Sort DEFAULT_SORT = new Sort("SpecimenNumber,GlobalUniqueId");

    private static class RestrictedDataRegion extends DataRegion
    {
        private String _historyLinkBase = null;

        protected boolean isRecordSelectorEnabled(RenderContext ctx)
        {
            return isAvailable(ctx);
        }

        protected void renderExtraRecordSelectorContent(RenderContext ctx, Writer out) throws IOException
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


    protected SamplesGridView(DataRegion region, SimpleFilter filter, Sort sort)
    {
        super(region);
        if (sort != null)
            setSort(sort);
        if (filter != null)
            setFilter(filter);
    }


    protected SamplesGridView(DataRegion region, RenderContext ctx)
    {
        super(region, ctx);
    }

    public static SamplesGridView createView(Container container, boolean allowRegionLinks, boolean restrictSelection)
    {
        DataRegion region = createDataRegion(container, allowRegionLinks, restrictSelection);
        SimpleFilter filter = new SimpleFilter();
        addFilterClause(filter, container);
        return new SamplesGridView(region, filter, DEFAULT_SORT);
    }

    public static SamplesGridView createView(Container container, Specimen[] samples, boolean allowRegionLinks, boolean restrictSelection)
    {
        DataRegion region = createDataRegion(container, allowRegionLinks, restrictSelection);
        SimpleFilter filter = new SimpleFilter();
        addFilterClause(filter, container);
        addFilterClause(filter, samples);
        return new SamplesGridView(region, filter, DEFAULT_SORT);
    }

    public static SamplesGridView createView(Container container, ParticipantDataset[] participantDatasets, boolean allowRegionLinks, boolean restrictSelection)
    {
        DataRegion region = createDataRegion(container, allowRegionLinks, restrictSelection);
        SimpleFilter filter = new SimpleFilter();
        addFilterClause(filter, container);
        addFilterClause(filter, participantDatasets);
        return new SamplesGridView(region, filter, DEFAULT_SORT);
    }

    protected static SimpleFilter addFilterClause(SimpleFilter filter, Container container)
    {
        return filter.addCondition("Container", container.getId());
    }

    protected static SimpleFilter addFilterClause(SimpleFilter filter, Specimen[] specimens)
    {
        if (specimens != null && specimens.length > 0)
        {
            StringBuilder whereClause = new StringBuilder();
            whereClause.append("RowId IN (");
            for (int i = 0; i < specimens.length; i++)
            {
                whereClause.append(specimens[i].getRowId());
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
                whereClause.append("(VisitValue = ? AND Ptid= ?)");
                params[param++] = pd.getSequenceNum();
                params[param++] = pd.getParticipantId();
            }
            filter = filter.addWhereClause(whereClause.toString(), params);
        }
        return filter;
    }

    public boolean isShowingVials()
    {
        return true;
    }

    private static DataRegion createDataRegion(Container container, boolean allowRegionLinks, boolean restrictSelection)
    {
        DataRegion rgn;

        if (restrictSelection)
            rgn = new RestrictedDataRegion();
        else
            rgn = new DataRegion();
        TableInfo tableInfo = StudySchema.getInstance().getTableInfoSpecimenDetail();
        rgn.setTable(tableInfo);
        rgn.setColumns(tableInfo.getColumns(SPECIMEN_COLUMN_ORDER));
        rgn.getDisplayColumn("SiteName").setNoWrap(true);
        rgn.getDisplayColumn("RowId").setVisible(false);
        rgn.getDisplayColumn("AtRepository").setVisible(false);
        rgn.setRecordSelectorValueColumns("RowId");
        rgn.setButtonBar(ButtonBar.BUTTON_BAR_EMPTY);
        rgn.setShadeAlternatingRows(true);
        rgn.setShowColumnSeparators(true);

        rgn.setAggregates(new Aggregate(tableInfo.getColumn("Volume"), Aggregate.Type.SUM));

        if (allowRegionLinks)
        {
            String eventsBase = ActionURL.toPathString("Study-Samples", "sampleEvents", container.getPath());
            rgn.addDisplayColumn(0, new SimpleDisplayColumn(
                    "<a href=\"" + eventsBase + "?id=${rowid}\">[history]</a>"));
        }
        rgn.setSortable(allowRegionLinks);
        rgn.setShowFilters(allowRegionLinks);
        return rgn;
    }

    public void setButtons(ActionButton... buttons)
    {
        ButtonBar bbar = new ButtonBar();
        for (ActionButton button : buttons)
        {
            bbar.add(button);
            if (button == ActionButton.BUTTON_SELECT_ALL || button == ActionButton.BUTTON_CLEAR_ALL)
                getDataRegion().setShowRecordSelectors(true);
        }
        getDataRegion().setButtonBar(bbar);
    }
}
