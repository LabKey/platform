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

import org.labkey.api.data.*;
import org.labkey.api.exp.LsidManager;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.*;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.QueryReport;
import org.labkey.api.reports.report.view.RReportBean;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.NavTree;
import org.labkey.study.controllers.DatasetController;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.controllers.reports.ReportsController;
import org.labkey.study.model.*;
import org.labkey.study.reports.StudyRReport;
import org.labkey.study.reports.ReportManager;

import java.io.IOException;
import java.io.Writer;
import java.util.*;

/**
 * User: brittp
 * Date: Aug 25, 2006
 * Time: 4:03:59 PM
 */
public class DataSetQueryView extends QueryView
{
    private final DataSetDefinition _dataset;
    private List<ActionButton> _buttons;
    private final VisitImpl _visit;
    private final CohortImpl _cohort;
    private boolean _showSourceLinks;
    private boolean _forExport;
    public static final String DATAREGION = "Dataset";
    private QCStateSet _qcStateSet;
    private boolean _showEditLinks = true;

    public DataSetQueryView(DataSetDefinition dataset, UserSchema schema, QuerySettings settings, VisitImpl visit, CohortImpl cohort, QCStateSet qcStateSet)
    {
        super(schema, settings);
        if (dataset == null)
            throw new IllegalArgumentException("dataset");
        _qcStateSet = qcStateSet;
        getSettings().setAllowChooseQuery(false);
        getSettings().setAllowChooseView(false);
        _dataset = dataset;
        _visit = visit;
        _cohort = cohort;
    }

    public DataView createDataView()
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
        view.getDataRegion().setShowBorders(true);
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
        if (null != _qcStateSet)
        {
            SimpleFilter filter = (SimpleFilter) view.getRenderContext().getBaseFilter();
            if (null == filter)
            {
                filter = new SimpleFilter();
                view.getRenderContext().setBaseFilter(filter);
            }
            FieldKey qcStateKey = FieldKey.fromParts(DataSetTable.QCSTATE_ID_COLNAME, "rowid");
            Map<FieldKey, ColumnInfo> qcStateColumnMap = QueryService.get().getColumns(view.getDataRegion().getTable(), Collections.singleton(qcStateKey));
            ColumnInfo qcStateColumn = qcStateColumnMap.get(qcStateKey);
            filter.addClause(new SimpleFilter.SQLClause(_qcStateSet.getStateInClause(qcStateColumn.getAlias()), null, qcStateColumn.getName()));
        }

        StudyManager.getInstance().applyDefaultFormats(getContainer(), view.getDataRegion().getDisplayColumns());
        ColumnInfo sourceLsidCol = view.getTable().getColumn("SourceLsid");
        DisplayColumn sourceLsidDisplayCol = view.getDataRegion().getDisplayColumn("SourceLsid");
        if (sourceLsidCol != null)
        {
            if (sourceLsidDisplayCol != null)
                sourceLsidDisplayCol.setVisible(false);
            if (_showSourceLinks && hasUsefulDetailsPage())
            {
                view.getDataRegion().addDisplayColumn(0, new DatasetDetailsColumn(sourceLsidCol, getUser()));
            }
        }
        Container c = getContainer();
        User user = getUser();
        // Only show link to edit if permission allows it
        if (_showEditLinks && !_forExport &&
                _dataset.canWrite(user) &&
                c.getPolicy().hasPermission(user, UpdatePermission.class)
            )
        {
            TableInfo tableInfo = view.getDataRegion().getTable();
            ColumnInfo lsidColumn = tableInfo.getColumn("lsid");
            view.getDataRegion().addDisplayColumn(0, new DatasetEditColumn(c, lsidColumn));
        }

        // allow posts from dataset data regions to determine which dataset was being displayed:
        view.getDataRegion().addHiddenFormField(DataSetDefinition.DATASETKEY, "" + _dataset.getDataSetId());

        return view;
    }

    private boolean hasUsefulDetailsPage()
    {
        Integer protocolId = _dataset.getProtocolId();
        if (protocolId == null)
            return true; // we don't have a protocol at all, so we don't know if we have useful details

        ExpProtocol protocol = ExperimentService.get().getExpProtocol(protocolId.intValue());
        if (protocol == null)
            return false; // We have a protocol, but it's been deleted

        AssayProvider provider = AssayService.get().getProvider(protocol);
        if (provider == null)
            return false; // Unlikely, but possible -- provider no longer available
        return provider.hasUsefulDetailsPage();
    }

    private class DatasetDetailsColumn extends SimpleDisplayColumn
    {
        private final ColumnInfo _sourceLsidColumn;
        private final User _user;

        public DatasetDetailsColumn(ColumnInfo sourceLsidCol, User user)
        {
            super();
            _sourceLsidColumn = sourceLsidCol;
            _user = user;
        }

        @Override
        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            Object lsid = ctx.get(_sourceLsidColumn.getName());
            if (lsid != null)
            {
                // If the user has the ability to read this dataset, always
                // provide a link to the source assay details page.
                ActionURL dataURL = new ActionURL(StudyController.DatasetItemDetailsAction.class, getContainer());
                dataURL.addParameter("sourceLsid", lsid.toString());
                dataURL.addParameter("datasetId", DataSetQueryView.this._dataset.getDataSetId());
                dataURL.addParameter("studyContainerId", getContainer().getId());
                out.write(PageFlowUtil.textLink("assay", dataURL));
                return;
            }
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
            actionURL.addParameter("datasetId", _dataset.getDataSetId());

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
                url = ReportUtil.getRReportDesignerURL(_viewContext, bean);
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

    public void setShowSourceLinks(boolean showSourceLinks)
    {
        _showSourceLinks = showSourceLinks;
    }

    public boolean isShowEditLinks()
    {
        return _showEditLinks;
    }

    public void setShowEditLinks(boolean showEditLinks)
    {
        _showEditLinks = showEditLinks;
    }

    public void setForExport(boolean forExport)
    {
        _forExport = forExport;
    }

    protected void populateReportButtonBar(ButtonBar bar)
    {
        Report report = getSettings().getReportView();

        // query reports will render their own button bar
        if (!(report instanceof QueryReport))
        {
            MenuButton button = createViewButton(getViewItemFilter());
            button.addMenuItem("Set Default View", getViewContext().cloneActionURL().setAction(StudyController.ViewPreferencesAction.class));

            bar.add(button);
        }
    }

    public MenuButton createPageSizeMenuButton()
    {
        return super.createPageSizeMenuButton();
    }

    @Override
    public void addManageViewItems(MenuButton button)
    {
        button.addMenuItem("Manage Views", new ActionURL(ReportsController.ManageReportsAction.class, getContainer()).
                addParameter("schemaName", getSchema().getSchemaName()).
                addParameter("queryName", getSettings().getQueryName()));
    }

    protected void addReportViews(MenuButton menu, ActionURL target)
    {
        String reportKey = ReportUtil.getReportKey(getSchema().getSchemaName(), getSettings().getQueryName());
        Map<String, List<Report>> views = new TreeMap<String, List<Report>>();
        for (Report report : ReportUtil.getReports(getViewContext(), reportKey, true))
        {
            // Filter out reports that don't match what this view is supposed to show. This can prevent
            // reports that were created on the same schema and table/query from a different view from showing up on a
            // view that's doing magic to add additional filters, for example.
            if (getViewItemFilter().accept(report.getType(), null))
            {
                if (!ReportManager.get().canReadReport(getUser(), getContainer(), report))
                    continue;

                if (!views.containsKey(report.getType()))
                    views.put(report.getType(), new ArrayList<Report>());

                views.get(report.getType()).add(report);
            }
        }

        if (views.size() > 0)
            menu.addSeparator();

        for (Map.Entry<String, List<Report>> entry : views.entrySet())
        {
            for (Report report : entry.getValue())
            {
                String reportId = report.getDescriptor().getReportId().toString();
                NavTree item = new NavTree(report.getDescriptor().getReportName(), target.clone().replaceParameter(param(QueryParam.reportId), reportId).getLocalURIString());
                item.setId("Views:" + report.getDescriptor().getReportName());
                if (report.getDescriptor().getReportId().equals(getSettings().getReportId()))
                    item.setHighlighted(true);
                item.setImageSrc(ReportService.get().getReportIcon(getViewContext(), report.getType()));
                menu.addMenuItem(item);
            }
        }
    }
}
