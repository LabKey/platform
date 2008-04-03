package org.labkey.study.samples;

import org.labkey.api.data.*;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;

import org.labkey.study.model.Specimen;
import org.labkey.study.model.ParticipantDataset;
import org.labkey.study.StudySchema;

/**
 * User: brittp
 * Date: Apr 7, 2006
 * Time: 1:41:04 PM
 */
public class SamplesGroupedGridView extends SamplesGridView
{
    private static final String SPECIMEN_GROUPED_COLUMN_ORDER =
            "Container, SpecimenNumber, Ptid, VisitDescription, VisitValue, Volume, AvailableVolume, " +
            "VolumeUnits, PrimaryTypeId, AdditiveTypeId, DerivativeTypeId, VialCount, LockedInRequest, " +
            "AtRepository, Available, DrawTimestamp, " +
            "SalReceiptDate, ClassId, ProtocolNumber, OtherSpecimenId, ExpectedTimeValue, " +
            "ExpectedTimeUnit, SubAdditiveDerivative, " +
            "SampleNumber, XSampleOrigin, ExternalLocation, RecordSource";


    public RenderContext getRenderContext()
    {
        return getModelBean();
    }


    private static class SamplesGroupedRenderContext extends RenderContext
    {
        protected SimpleFilter buildFilter(TableInfo tinfo, ActionURL url, String name)
        {
            SimpleFilter filter = super.buildFilter(tinfo, url, name);
            filter.deleteConditions("GlobalUniqueId");
            return filter;
        }

        protected Sort buildSort(TableInfo tinfo, ActionURL url, String name)
        {
            Sort sort = super.buildSort(tinfo, url, name);
            sort.deleteSortColumn("GlobalUniqueId");
            return sort;
        }
    }


    protected SamplesGroupedGridView(SimpleFilter filter)
    {
        super(createDataRegion(), new SamplesGroupedRenderContext());
        if (filter != null)
            setFilter(filter);
    }

    public static SamplesGroupedGridView createGroupedView(ViewContext context)
    {
        SimpleFilter filter = new SimpleFilter();
        addFilterClause(filter, context.getContainer());
        return new SamplesGroupedGridView(filter);
    }

    public static SamplesGroupedGridView createGroupedView(ViewContext context, Specimen[] samples)
    {
        SimpleFilter filter = new SimpleFilter();
        addFilterClause(filter, context.getContainer());
        addFilterClause(filter, samples);
        return new SamplesGroupedGridView(filter);
    }

    public static SamplesGroupedGridView createGroupedView(ViewContext context, ParticipantDataset[] participantDatasets)
    {
        SimpleFilter filter = new SimpleFilter();
        addFilterClause(filter, context.getContainer());
        addFilterClause(filter, participantDatasets);
        return new SamplesGroupedGridView(filter);
    }

    public boolean isShowingVials()
    {
        return false;
    }

    private static DataRegion createDataRegion()
    {
        DataRegion rgn = new DataRegion();
        TableInfo tableInfo = StudySchema.getInstance().getTableInfoSpecimenSummary();
        rgn.setTable(tableInfo);
        rgn.setColumns(tableInfo.getColumns(SPECIMEN_GROUPED_COLUMN_ORDER));
        rgn.getDisplayColumn("Container").setVisible(false);
        rgn.setSortable(true);
        rgn.setShowFilters(true);
        rgn.setButtonBar(ButtonBar.BUTTON_BAR_EMPTY);
        rgn.setShadeAlternatingRows(true);
        rgn.setShowColumnSeparators(true);

        rgn.setAggregates(new Aggregate(tableInfo.getColumn("Volume"), Aggregate.Type.SUM),
                new Aggregate(tableInfo.getColumn("LockedInRequest"), Aggregate.Type.SUM),
                new Aggregate(tableInfo.getColumn("AtRepository"), Aggregate.Type.SUM),
                new Aggregate(tableInfo.getColumn("VialCount"), Aggregate.Type.SUM),
                new Aggregate(tableInfo.getColumn("AvailableVolume"), Aggregate.Type.SUM),
                new Aggregate(tableInfo.getColumn("Available"), Aggregate.Type.SUM));

        return rgn;
    }
}
