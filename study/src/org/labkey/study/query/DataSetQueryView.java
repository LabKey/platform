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

import org.labkey.api.data.*;
import org.labkey.api.query.*;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.view.ChartUtil;
import org.labkey.api.reports.report.view.RReportBean;
import org.labkey.api.security.ACL;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.study.controllers.DatasetController;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.Cohort;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.Visit;
import org.labkey.study.reports.StudyRReport;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: brittp
 * Date: Aug 25, 2006
 * Time: 4:03:59 PM
 */
public class DataSetQueryView extends QueryView
{
    private final int _datasetId;
    private List<ActionButton> _buttons;
    private final Visit _visit;
    private final Cohort _cohort;
    private boolean _showSourceLinks;
    private boolean _forExport;
    public static final String DATAREGION = "Dataset";

    public DataSetQueryView(int datasetId, UserSchema schema, QuerySettings settings, Visit visit, Cohort cohort)
    {
        super(schema, settings);
        _datasetId = datasetId;
        _visit = visit;
        _cohort = cohort;
    }

    protected DataView createDataView()
    {
        DataView view = super.createDataView();
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
        view.getDataRegion().setRecordSelectorValueColumns("lsid");
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
                view.getDataRegion().addDisplayColumn(0, new DatasetDetailsColumn(view.getRenderContext().getContainer(),
                        sourceLsidCol));
            }
        }
        // Only show link to edit for editors and if permission allows it
        if (!_forExport &&
                getViewContext().hasPermission(ACL.PERM_UPDATE) &&
                StudyManager.getInstance().getStudy(getContainer()).isDatasetRowsEditable())
        {
            TableInfo tableInfo = view.getDataRegion().getTable();
            ColumnInfo lsidColumn = tableInfo.getColumn("lsid");
            view.getDataRegion().addDisplayColumn(0, new DatasetEditColumn(view.getRenderContext().getContainer(), lsidColumn));
        }
        return view;
    }

    private class DatasetDetailsColumn extends DetailsColumn
    {
        private ColumnInfo _sourceLsidColumn;
        public DatasetDetailsColumn(Container container, ColumnInfo sourceLsidCol)
        {
            super(new LookupURLExpression(new ActionURL(StudyController.DatasetItemDetailsAction.class, container),
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

    private class DatasetEditColumn extends SimpleDisplayColumn
    {
        private final Container container;
        private final ColumnInfo lsidColumn;

        public DatasetEditColumn(Container container, ColumnInfo lsidColumn)
        {
            super();
            setWidth(null);
            this.container = container;
            this.lsidColumn = lsidColumn;
        }

        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            out.write("[<a href=\"");

            ActionURL actionURL = new ActionURL(DatasetController.UpdateAction.class, container);

            String lsid = lsidColumn.getValue(ctx).toString();
            actionURL.addParameter("lsid", lsid);

            actionURL.addParameter("datasetId", _datasetId);
            

            out.write(PageFlowUtil.filter(actionURL.getLocalURIString()));
            out.write("\">");
            out.write("edit");
            out.write("</a>]");
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

    public void setForExport(boolean forExport)
    {
        _forExport = forExport;
    }

    protected void populateReportButtonBar(ButtonBar bar)
    {
        MenuButton button = createViewButton(ReportService.EMPTY_ITEM_LIST);
        button.addMenuItem("Set Default View", getViewContext().cloneActionURL().setAction(StudyController.ViewPreferencesAction.class));

        bar.add(button);
        //bar.add(createPrintButton());
    }
}
