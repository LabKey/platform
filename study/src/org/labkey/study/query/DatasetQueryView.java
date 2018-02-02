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
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.action.BaseViewAction;
import org.labkey.api.action.QueryViewAction;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.MenuButton;
import org.labkey.api.data.PHI;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SimpleDisplayColumn;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.LsidManager;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.QueryReport;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.study.DataspaceContainerFilter;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;
import org.labkey.study.CohortFilter;
import org.labkey.study.CohortFilterFactory;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.BaseStudyController;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.controllers.specimen.SpecimenController;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.ParticipantGroup;
import org.labkey.study.model.ParticipantGroupManager;
import org.labkey.study.model.QCStateSet;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.reports.ReportManager;
import org.labkey.study.reports.StudyReportUIProvider;
import org.springframework.beans.PropertyValues;
import org.springframework.validation.BindException;

import java.io.IOException;
import java.io.Writer;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: brittp
 * Date: Aug 25, 2006
 * Time: 4:03:59 PM
 */
public class DatasetQueryView extends StudyQueryView
{
    private DatasetDefinition _dataset;
    private VisitImpl _visit;
    private CohortFilter _cohortFilter;
    private boolean _showSourceLinks;
    public static final String DATAREGION = "Dataset";
    private QCStateSet _qcStateSet;
    private ExpProtocol _protocol;
    private AssayProvider _provider;
    protected static Logger _systemLog = Logger.getLogger(DatasetQueryView.class);

    public DatasetQueryView(UserSchema schema, DatasetQuerySettings settings, BindException errors)
    {
        super(schema, settings, errors);

        ViewContext context = getViewContext();
        DatasetFilterForm form = getForm(context);

        _dataset = StudyManager.getInstance().getDatasetDefinitionByQueryName(_study, settings.getQueryName());     // Robust enough to consider that QueryName may actually be label
        if (_dataset == null)
            throw new IllegalArgumentException("Unable to find the dataset specified");

        if (!_dataset.getName().equalsIgnoreCase(settings.getQueryName()))
        {
            // settings has label instead of name; warn that label is being used to lookup
            _systemLog.warn("Dataset in schema'" + schema.getName() + "' was referenced by label (" + settings.getQueryName() + "), not name (" + _dataset.getName() + ").");
        }

        if (settings.isUseQCSet() && StudyManager.getInstance().showQCStates(getContainer()))
            _qcStateSet = QCStateSet.getSelectedStates(getContainer(), form.getQCState());

        _showSourceLinks = settings.isShowSourceLinks();

        // Only show link to edit if permission allows it
        setShowUpdateColumn(settings.isShowEditLinks() && !isExportView() && _dataset.canWrite(getUser()));

        getSettings().setAllowChooseView(false);

        if (form.getVisitRowId() != 0)
        {
            assert _study.getTimepointType() != TimepointType.CONTINUOUS;
            _visit = StudyManager.getInstance().getVisitForRowId(_study, form.getVisitRowId());
            if (null == _visit)
                throw new NotFoundException();
        }
        if (context.getActionURL() != null)
            _cohortFilter = CohortFilterFactory.getFromURL(getContainer(), getUser(), getViewContext().getActionURL(), DATAREGION);

        _protocol = _dataset.getAssayProtocol();
        if (_protocol != null)
            _provider = AssayService.get().getProvider(_protocol);

        setViewItemFilter(StudyReportUIProvider.getItemFilter());
        // Issue 23076: hide container filter option in dataset view menu even if dataset is shared
        disableContainerFilterSelection();

        addSessionParticipantGroup(settings);
    }

    private void addSessionParticipantGroup(DatasetQuerySettings settings)
    {
        if (getViewContext() != null && getViewContext().getRequest() != null)
        {
            ParticipantGroup sessionGroup = ParticipantGroupManager.getInstance().getSessionParticipantGroup(getContainer(), getUser(), getViewContext().getRequest());
            if (sessionGroup != null && sessionGroup.getRowId() > 0)
            {
                ActionURL sortFilterURL = settings.getSortFilterURL();
                sortFilterURL.addParameter("query.ParticipantId/" + sessionGroup.getCategoryLabel() + "~eq", sessionGroup.getLabel());
                settings.setSortFilterURL(sortFilterURL);
            }
        }
    }

    @Override
    public CohortFilter getCohortFilter()
    {
        return _cohortFilter;
    }

    private DatasetFilterForm getForm(ViewContext context)
    {
        DatasetFilterForm form = new DatasetFilterForm();

        form.bindParameters(context.getBindPropertyValues());
        return form;
    }

    public DataView createDataView()
    {
        DataView view = super.createDataView();
        TableInfo table = view.getTable();

        if (null == table)
            throw new IllegalStateException("Could not create table from dataset: " + this._dataset.getName());

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

        if (null != _cohortFilter)
        {
            SimpleFilter filter = (SimpleFilter) view.getRenderContext().getBaseFilter();
            if (null == filter)
            {
                filter = new SimpleFilter();
                view.getRenderContext().setBaseFilter(filter);
            }
            _cohortFilter.addFilterCondition(table, getContainer(), filter);
        }

        if (null != _qcStateSet)
        {
            SimpleFilter filter = (SimpleFilter) view.getRenderContext().getBaseFilter();
            if (null == filter)
            {
                filter = new SimpleFilter();
                view.getRenderContext().setBaseFilter(filter);
            }
            FieldKey qcStateKey = FieldKey.fromParts(DatasetTableImpl.QCSTATE_ID_COLNAME, "rowid");
            Map<FieldKey, ColumnInfo> qcStateColumnMap = QueryService.get().getColumns(table, Collections.singleton(qcStateKey));
            ColumnInfo qcStateColumn = qcStateColumnMap.get(qcStateKey);
            if (qcStateColumn != null)
            {
                filter.addClause(new SimpleFilter.SQLClause(_qcStateSet.getStateInClause(qcStateColumn.getAlias()), null, qcStateColumn.getFieldKey()));
            }
        }

        ColumnInfo sourceLsidCol = table.getColumn("SourceLsid");
        DisplayColumn sourceLsidDisplayCol = view.getDataRegion().getDisplayColumn("SourceLsid");

        if (sourceLsidCol != null)
        {
            if (sourceLsidDisplayCol != null)
                sourceLsidDisplayCol.setVisible(false);
            if (_showSourceLinks && hasUsefulDetailsPage() && null != _dataset.getAssayProtocol())
            {
                view.getDataRegion().addDisplayColumn(0, new DatasetDetailsColumn(sourceLsidCol, getUser()));
            }
        }

        // allow posts from dataset data regions to determine which dataset was being displayed:
        view.getDataRegion().addHiddenFormField(DatasetDefinition.DATASETKEY, "" + _dataset.getDatasetId());

        return view;
    }

    private boolean hasUsefulDetailsPage()
    {
        if (!_dataset.isAssayData())
            return true; // we don't have a protocol at all, so we don't know if we have useful details

        if (_protocol == null)
            return false; // We have a protocol, but it's been deleted

        if (_provider == null)
            return false; // Unlikely, but possible -- provider no longer available
        return _provider.hasUsefulDetailsPage();
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
                if (LsidManager.get().hasPermission(lsid.toString(), _user, ReadPermission.class))
                {
                    ActionURL dataURL = new ActionURL(StudyController.DatasetItemDetailsAction.class, getContainer());
                    dataURL.addParameter("sourceLsid", lsid.toString());
                    out.write(PageFlowUtil.textLink("assay", dataURL));
                    return;
                }
            }
            out.write("&nbsp;");
        }

        @Override
        public void addQueryColumns(Set<ColumnInfo> set)
        {
            set.add(_sourceLsidColumn);
        }
    }

    @Override
    protected ActionURL urlFor(QueryAction action)
    {
        ActionURL url = super.urlFor(action);

        // need to add back the parameters that aren't added by the base urlFor, cohort and qc state
        // don't get automatically added because they lack the proper dataregion prefix
        //
        if (url != null)
        {
            if (_cohortFilter != null)
                _cohortFilter.addURLParameters(_study, url, DATAREGION);

            if (_qcStateSet != null)
                url.replaceParameter(BaseStudyController.SharedFormParameters.QCState, _qcStateSet.getFormValue());
        }
        return url;
    }

    protected void populateReportButtonBar(ButtonBar bar)
    {
        Report report = getSettings().getReportView(getViewContext());

        // query reports will render their own button bar
        if (!(report instanceof QueryReport))
        {
            bar.add(createViewButton(getViewItemFilter()));
            populateChartsReports(bar);
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
    protected boolean canViewReport(User user, Container c, Report report)
    {
        return ReportManager.get().canReadReport(getUser(), getContainer(), report);
    }

    @Override
    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        bar.add(createFilterButton());
        bar.add(createViewButton(getItemFilter()));

        populateChartsReports(bar);

        List<String> recordSelectorColumns = view.getDataRegion().getRecordSelectorValueColumns();
        bar.add(createExportButton(recordSelectorColumns));

        User user = getUser();
        boolean canWrite = canWrite(_dataset, user);
        boolean canManage = canManage(_dataset, user);
        boolean isSnapshot = QueryService.get().isQuerySnapshot(getContainer(), StudySchema.getInstance().getSchemaName(), _dataset.getName());
        boolean isAssayDataset = _dataset.isAssayData();
        ExpProtocol protocol = null;

        if (isAssayDataset)
        {
            protocol = _dataset.getAssayProtocol();
            if (protocol == null)
                isAssayDataset = false;
        }

        if (!isSnapshot)
        {
            if (!isAssayDataset) // admins always get the import and manage buttons
            {
                if (canWrite)
                {
                    // insert menu button contain Insert New and Bulk import, or button for either option
                    ActionURL importURL = view.getTable().getImportDataURL(getContainer());
                    ActionButton insertButton = createInsertMenuButton(null, importURL);
                    if (insertButton != null)
                    {
                        insertButton.setDisplayModes(DataRegion.MODE_GRID);
                        insertButton.setDisplayPermission(InsertPermission.class);
                        bar.add(insertButton);
                    }
                }

                if (canWrite && _study instanceof StudyImpl)
                {
                    ActionURL deleteRowsURL = urlFor(QueryAction.deleteQueryRows);
                    if (deleteRowsURL != null)
                    {
                        ActionButton deleteRows = new ActionButton(deleteRowsURL, "Delete");
                        deleteRows.setIconCls("trash");
                        if (_dataset.getDataSharingEnum() == DatasetDefinition.DataSharing.NONE)
                            deleteRows.setRequiresSelection(true, "Delete selected row from this dataset?", "Delete selected rows from this dataset?");
                        else
                            deleteRows.setRequiresSelection(true, "Delete selected shared row from this dataset?  This operation may affect other studies in this project.", "Delete selected shared rows from this dataset?  This operation may affect other studies in this project.");
                        deleteRows.setActionType(ActionButton.Action.POST);
                        deleteRows.setDisplayPermission(DeletePermission.class);
                        bar.add(deleteRows);
                    }
                }

                if (canManage)
                {
                    // manage dataset
                    ActionButton manageButton = new ActionButton(new ActionURL(StudyController.DatasetDetailsAction.class, getContainer()).addParameter("id", _dataset.getDatasetId()), "Manage");
                    manageButton.setDisplayModes(DataRegion.MODE_GRID);
                    manageButton.setActionType(ActionButton.Action.LINK);
                    manageButton.setDisplayPermission(InsertPermission.class);
                    bar.add(manageButton);
                }
            }
            else
            {
                bar.addAll(AssayService.get().getImportButtons(protocol, getUser(), getContainer(), true));

                if (user.hasRootAdminPermission() || canWrite)
                {
                    ActionURL deleteRowsURL = new ActionURL(StudyController.DeletePublishedRowsAction.class, getContainer());
                    deleteRowsURL.addParameter("protocolId", protocol.getRowId());
                    ActionButton deleteRows = new ActionButton(deleteRowsURL, "Recall");
                    deleteRows.setRequiresSelection(true, "Recall selected row of this dataset?", "Recall selected rows of this dataset?");
                    deleteRows.setActionType(ActionButton.Action.POST);
                    deleteRows.setDisplayPermission(DeletePermission.class);
                    bar.add(deleteRows);
                }
            }
        }

        bar.add(ParticipantGroupManager.getInstance().createParticipantGroupButton(getViewContext(), getDataRegionName(), _cohortFilter, true));

        if (StudyManager.getInstance().showQCStates(getContainer()))
            bar.add(createQCStateButton(_qcStateSet));

        ActionURL viewSamplesURL = new ActionURL(SpecimenController.SelectedSamplesAction.class, getContainer());
        ActionButton viewSamples = new ActionButton(viewSamplesURL, "View Specimens");
        viewSamples.setRequiresSelection(true);
        viewSamples.setActionType(ActionButton.Action.POST);
        viewSamples.setDisplayPermission(ReadPermission.class);
        bar.add(viewSamples);

        if (isAssayDataset)
        {
            // provide a link to the source assay
            Container c = protocol.getContainer();
            if (c.hasPermission(getUser(), ReadPermission.class))
            {
                ActionURL url = PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(
                        c,
                        protocol,
                        new ContainerFilter.CurrentAndSubfolders(getUser()));
                ActionButton viewAssayButton = new ActionButton("View Source Assay", url);
                bar.add(viewAssayButton);
            }
        }
    }

    @Override
    public MenuButton createViewButton(ReportService.ItemFilter filter)
    {
        MenuButton button =  super.createViewButton(filter);

        ActionURL url = new ActionURL(StudyController.ViewPreferencesAction.class, getContainer());
        url.addParameter("datasetId", _dataset.getDatasetId());
        button.addMenuItem("Set Default", url);

        return button;
    }

    private ActionButton createFilterButton()
    {
        ActionButton btn = new ActionButton("Filter");
        btn.setIconCls("filter");
        btn.setActionType(ActionButton.Action.SCRIPT);
        btn.setScript(DataRegion.getJavaScriptObjectReference(getDataRegionName()) + ".showFaceting(); return false;");
        return btn;
    }

    private MenuButton createQCStateButton(QCStateSet currentSet)
    {
        List<QCStateSet> stateSets = QCStateSet.getSelectableSets(getContainer());
        MenuButton button = new MenuButton("QC State");

        for (QCStateSet set : stateSets)
        {
            NavTree setItem = new NavTree(set.getLabel(), getViewContext().cloneActionURL().replaceParameter(BaseStudyController.SharedFormParameters.QCState, set.getFormValue()).toString());
            setItem.setId("QCState:" + set.getLabel());
            if (set.equals(currentSet))
                setItem.setSelected(true);
            button.addMenuItem(setItem);
        }
        if (getContainer().hasPermission(getUser(), AdminPermission.class))
        {
            button.addSeparator();
            ActionURL updateAction = new ActionURL(StudyController.UpdateQCStateAction.class, getContainer());
            NavTree updateItem = button.addMenuItem("Update state of selected rows", "#", "if (verifySelected(" + DataRegion.getJavaScriptObjectReference(getDataRegionName()) + ".form, \"" +
                    updateAction.getLocalURIString() + "\", \"post\", \"rows\")) " + DataRegion.getJavaScriptObjectReference(getDataRegionName()) + ".form.submit()");
            updateItem.setId("QCState:updateSelected");

            button.addMenuItem("Manage states", new ActionURL(StudyController.ManageQCStatesAction.class,
                    getContainer()).addParameter(ActionURL.Param.returnUrl, getViewContext().getActionURL().getLocalURIString()));
        }
        return button;
    }

    private boolean canWrite(DatasetDefinition def, User user)
    {
        return def.canWrite(user) && def.getContainer().hasPermission(user, UpdatePermission.class);
    }

    private boolean canManage(DatasetDefinition def, User user)
    {
        return user.hasRootAdminPermission() || def.getContainer().hasPermission(user, AdminPermission.class);
    }

    private PHI getMaxContainedPhi()
    {
        TableInfo tableInfo = getTable();
        if (tableInfo instanceof DatasetTableImpl)
        {
            if (((DatasetTableImpl)tableInfo).getRealTable() instanceof DatasetDefinition.DatasetSchemaTableInfo)
                return ((DatasetDefinition.DatasetSchemaTableInfo)((DatasetTableImpl)tableInfo).getRealTable()).getMaxContainedPhi();
        }
        return PHI.NotPHI;
    }

    private boolean hasSourceLsids() throws SQLException
    {
        TableInfo datasetTable = getTable();
        SimpleFilter sourceLsidFilter = new SimpleFilter();
        sourceLsidFilter.addCondition(FieldKey.fromParts("SourceLsid"), null, CompareType.NONBLANK);

        return new TableSelector(datasetTable, sourceLsidFilter, null).exists();
    }

    protected QCStateSet getQcStateSet()
    {
        return _qcStateSet;
    }

    protected void setQcStateSet(QCStateSet qcStateSet)
    {
        _qcStateSet = qcStateSet;
    }

    public static class DatasetFilterForm extends QueryViewAction.QueryExportForm
    {
        private String _cohortFilterType;
        private Integer _cohortId;
        private String _QCState;
        private int _datasetId;
        private int _visitRowId;

        public BindException bindParameters(PropertyValues params)
        {
            return BaseViewAction.springBindParameters(this, "form", params);
        }

        public String getCohortFilterType()
        {
            return _cohortFilterType;
        }

        public void setCohortFilterType(String cohortFilterType)
        {
            _cohortFilterType = cohortFilterType;
        }

        public Integer getCohortId()
        {
            return _cohortId;
        }

        public void setCohortId(Integer cohortId)
        {
            _cohortId = cohortId;
        }

        public String getQCState()
        {
            return _QCState;
        }

        public void setQCState(String QCState)
        {
            _QCState = QCState;
        }

        public int getDatasetId()
        {
            return _datasetId;
        }

        public void setDatasetId(int datasetId)
        {
            _datasetId = datasetId;
        }

        public int getVisitRowId()
        {
            return _visitRowId;
        }

        public void setVisitRowId(int visitRowId)
        {
            _visitRowId = visitRowId;
        }
    }



    @Override
    protected DataRegion createDataRegion()
    {
        DataRegion rgn = new DatasetDataRegion();
        configureDataRegion(rgn);
        return rgn;
    }


    public static class DatasetDataRegion extends DataRegion
    {
        @Override
        protected void addHeaderMessage(StringBuilder headerMessage, RenderContext ctx) throws IOException
        {
            super.addHeaderMessage(headerMessage, ctx);

            UserSchema s = getTable().getUserSchema();
            if (!(s instanceof DataspaceQuerySchema))
                return;
            DataspaceQuerySchema dqs = (DataspaceQuerySchema)s;
            ContainerFilter cf = dqs.getDefaultContainerFilter();
            if (!(cf instanceof DataspaceContainerFilter))
                return;

            StringBuilder msg = new StringBuilder();
            DataspaceContainerFilter dcf = (DataspaceContainerFilter)cf;
            if (dcf.isSubsetOfStudies())
            {
                // DISPLAY the current subset
                Collection<GUID> ids = dcf.getIds(dqs.getContainer(), ReadPermission.class, null);
                ArrayList<String> labels = new ArrayList<>(ids.size());
                for (GUID id : ids)
                {
                    Container c = ContainerManager.getForId(id);
                    if (null == c)
                        continue;
                    labels.add(c.getName());
                }
                sortLabels(labels);

                msg.append("<div><span class=\"labkey-strong\">Selected Studies:</span>&nbsp;");
                String comma = "";
                for (String label : labels)
                {
                    msg.append(comma).append(PageFlowUtil.filter(label));
                    comma = ", ";
                }
            }

            ParticipantGroup sessionGroup = ParticipantGroupManager.getInstance().getSessionParticipantGroup(dqs.getContainer(), dqs.getUser(), ctx.getViewContext().getRequest());
            if (sessionGroup != null)
            {
                if (msg.length() > 0)
                    msg.append("&nbsp;&nbsp;");
                msg.append("<span class=\"labkey-strong\">Selected Subjects:</span>&nbsp;");
                msg.append(sessionGroup.getParticipantIds().length);
            }

            if (msg.length() == 0)
                return;

            // HACK -- link to immport/studyFinder.view
            Module immport = ModuleLoader.getInstance().getModule("immport");
            Container project = ctx.getContainer().getProject();
            if (immport != null && project != null && project.getActiveModules().contains(immport))
            {
                ActionURL finderURL = new ActionURL("immport", "dataFinder.view", project);
                msg.append("&nbsp;&nbsp;").append(PageFlowUtil.button("Edit").href(finderURL.toString()));
            }
            msg.append("</div>");

            headerMessage.append(msg);
        }
    }


    static void sortLabels(List<String> labels)
    {
        final Pattern alphaNumPattern = Pattern.compile("(\\D*)(\\d+)?(.*)");

        try
        {
            labels.sort((o1, o2) ->
            {
                Matcher m = alphaNumPattern.matcher(o1);
                boolean matches = m.matches();
                assert matches;
                String prefix1 = m.groupCount() > 0 ? m.group(1) : "";
                String number1 = m.groupCount() > 1 ? m.group(2) : "";
                String suffix1 = m.groupCount() > 2 ? m.group(3) : "";

                m = alphaNumPattern.matcher(o2);
                matches = m.matches();
                assert matches;
                String prefix2 = m.groupCount() > 0 ? m.group(1) : "";
                String number2 = m.groupCount() > 1 ? m.group(2) : "";
                String suffix2 = m.groupCount() > 2 ? m.group(3) : "";

                if (0 == prefix1.compareTo(prefix2) && StringUtils.isNotEmpty(number1) && StringUtils.isNotEmpty(number2))
                {
                    long i1 = Long.parseLong(number1);
                    long i2 = Long.parseLong(number2);
                    if (i1 != i2)
                        return i1 > i2 ? 1 : -1;
                    return suffix1.compareTo(suffix2);
                }
                return o1.compareTo(o2);
            });
        }
        catch (IllegalStateException x)
        {
            assert false : "error sorting study list: " + StringUtils.join(labels,",");
        }
    }


    static public class TestCase extends Assert
    {
        @Test
        public void testSort()
        {
            String[] tests = new String[] {"SDY2","SDY100","SDYxyzpdq","100","SDY200","SDY1","abc21","qwerty77 abc","xyz99","54", "SDY144 Study"};
            ArrayList<String> list = new ArrayList<>();
            list.addAll(Arrays.asList(tests));
            sortLabels(list);
            assertTrue(list.indexOf("SDY1") < list.indexOf("SDY2"));
            assertTrue(list.indexOf("SDY2") < list.indexOf("SDY200"));
            assertTrue(list.indexOf("SDY21") < list.indexOf("SDY100"));
            assertTrue(list.indexOf("SDY21") < list.indexOf("SDY144 Study"));
            assertTrue(list.indexOf("SDY144 Study") < list.indexOf("SDY200"));
        }

    }
}
