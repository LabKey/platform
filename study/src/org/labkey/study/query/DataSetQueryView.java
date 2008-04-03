package org.labkey.study.query;

import org.labkey.api.data.*;
import org.labkey.api.query.*;
import org.labkey.api.reports.report.view.ChartUtil;
import org.labkey.api.reports.report.view.RReportBean;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.Visit;
import org.labkey.study.model.Cohort;
import org.labkey.study.reports.StudyRReport;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Map;

/**
 * User: brittp
 * Date: Aug 25, 2006
 * Time: 4:03:59 PM
 */
public class DataSetQueryView extends QueryView
{
    private List<ActionButton> _buttons;
    private Visit _visit;
    private Cohort _cohort;
    private boolean _showSourceLinks;
    public static final String DATAREGION = "Dataset";

    public DataSetQueryView(QueryForm form)
    {
        super(form);
    }

    public DataSetQueryView(ViewContext context, UserSchema schema, QuerySettings settings, Visit visit, Cohort cohort)
    {
        super(schema, settings);
        this._visit = visit;
        _cohort = cohort;
    }

    protected DataView createDataView()
    {
        DataView view = super.createDataView();
        setShowRReportButton(true);
        if (_buttons != null)
        {
            ButtonBar bbar = new ButtonBar();
            for (ActionButton button : _buttons)
            {
                bbar.add(button);
            }
            view.getDataRegion().setShowRecordSelectors(true);
            view.getDataRegion().setButtonBar(bbar);
            view.getDataRegion().setButtonBarPosition(DataRegion.ButtonBarPosition.BOTH);
        }
        else
            view.getDataRegion().setButtonBar(ButtonBar.BUTTON_BAR_EMPTY);
        view.getDataRegion().setShadeAlternatingRows(true);
        view.getDataRegion().setShowColumnSeparators(true);
        view.getDataRegion().setRecordSelectorValueColumns(new String[] {"lsid"} );
        if (null != _visit)
        {
            SimpleFilter filter = (SimpleFilter) view.getRenderContext().getBaseFilter();
            if (null == filter)
            {
                filter = new SimpleFilter();
                view.getRenderContext().setBaseFilter(filter);
            }
            _visit.addVisitFilter(filter);
        }
        if (null != _cohort)
        {
            SimpleFilter filter = (SimpleFilter) view.getRenderContext().getBaseFilter();
            if (null == filter)
            {
                filter = new SimpleFilter();
                view.getRenderContext().setBaseFilter(filter);
            }
            FieldKey cohortKey = FieldKey.fromParts("ParticipantId", "Cohort", "rowid");
            Map<FieldKey, ColumnInfo> cohortColumnMap = QueryService.get().getColumns(view.getDataRegion().getTable(), Collections.singleton(cohortKey));
            ColumnInfo cohortColumn = cohortColumnMap.get(cohortKey);
            filter.addCondition(cohortColumn.getName(), _cohort.getRowId());
        }

        StudyManager.getInstance().applyDefaultFormats(getContainer(), view.getDataRegion().getDisplayColumns());
        ColumnInfo sourceLsidCol = view.getTable().getColumn("SourceLsid");
        DisplayColumn sourceLsidDisplayCol = view.getDataRegion().getDisplayColumn("SourceLsid");
        if (sourceLsidCol != null)
        {
            if (sourceLsidDisplayCol != null)
                sourceLsidDisplayCol.setVisible(false);
            if (_showSourceLinks)
            {
                view.getDataRegion().addColumn(0, new DatasetDetailsColumn(view.getRenderContext().getContainer(),
                        sourceLsidCol));
            }
        }
        return view;
    }

    private class DatasetDetailsColumn extends DetailsColumn
    {
        private ColumnInfo _sourceLsidColumn;
        public DatasetDetailsColumn(Container container, ColumnInfo sourceLsidCol)
        {
            super(new LookupURLExpression(new ActionURL("Study", "datasetItemDetails", container),
                    Collections.singletonMap("sourceLsid", sourceLsidCol)));
            _sourceLsidColumn = sourceLsidCol;
        }

        @Override
        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            if (ctx.get(_sourceLsidColumn.getName()) != null)
                super.renderGridCellContents(ctx, out);
            else
                out.write("&nbsp;");
        }

        @Override
        public void addQueryColumns(Set<ColumnInfo> set)
        {
            set.add(_sourceLsidColumn);
        }
    }

    public ActionURL urlFor(QueryAction action)
    {
        ActionURL url = super.urlFor(action);
        switch (action)
        {
            case createRReport:
                RReportBean bean = new RReportBean();
                bean.setReportType(StudyRReport.TYPE);
                bean.setSchemaName(getSchema().getSchemaName());
                bean.setQueryName(getSettings().getQueryName());
                bean.setViewName(getSettings().getViewName());
                bean.setDataRegionName(getDataRegionName());

                bean.setRedirectUrl(getViewContext().getActionURL().toString());
                url = ChartUtil.getRReportDesignerURL(_viewContext, bean);
                break;
        }
        return url;
    }

    public void setButtons(List<ActionButton> buttons)
    {
        _buttons = buttons;
    }

    public ActionURL getCustomizeURL()
    {
        return urlFor(QueryAction.chooseColumns);
    }

    protected void renderQueryPicker(PrintWriter out)
    {
        // do nothing: we don't want a query picker for dataset views
    }

    public void renderCustomizeLinks(PrintWriter out) throws Exception
    {
        // do nothing: we don't want a query picker for dataset views
    }

    @Override
    protected void renderChangeViewPickers(PrintWriter out)
    {
        //do nothing: we render our own picker here...
    }

    public void setShowSourceLinks(boolean showSourceLinks)
    {
        _showSourceLinks = showSourceLinks;
    }
}
