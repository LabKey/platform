/*
 * Copyright (c) 2007-2016 LabKey Corporation
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

package org.labkey.api.study.query;

import org.labkey.api.data.*;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.study.assay.*;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * User: brittp
 * Date: Jun 29, 2007
 * Time: 11:13:44 AM
 */
public class ResultsQueryView extends AssayBaseQueryView
{
    private final ReplacedRunFilter _replacedRunFilter;

    public ResultsQueryView(ExpProtocol protocol, ViewContext context, QuerySettings settings)
    {
        this(protocol, AssayService.get().getProvider(protocol).createProtocolSchema(context.getUser(), context.getContainer(), protocol, null), settings);
    }

    public ResultsQueryView(ExpProtocol protocol, AssayProtocolSchema schema, QuerySettings settings)
    {
        super(protocol, schema, settings);

        _replacedRunFilter = ReplacedRunFilter.getFromURL(this, getReplacedFieldKey());
    }

    private FieldKey getReplacedFieldKey()
    {
        return new FieldKey(_provider.getTableMetadata(_protocol).getRunFieldKeyFromResults(), ExpRunTable.Column.Replaced);
    }


    @Override
    protected DataRegion createDataRegion()
    {
        ResultsDataRegion rgn = new ResultsDataRegion(_provider, _protocol);
        initializeDataRegion(rgn);
        return rgn;
    }

    protected void initializeDataRegion(DataRegion rgn)
    {
        configureDataRegion(rgn);
        rgn.setShowRecordSelectors(true);
    }

    public DataView createDataView()
    {
        DataView view = super.createDataView();
        Sort sort = view.getRenderContext().getBaseSort();
        if (sort == null)
        {
            sort = new Sort();
        }
        // Add a default sort to the end of any sorts that have already been specified (by a custom view, for example)
        sort.appendSortColumn(AssayService.get().getProvider(_protocol).getTableMetadata(_protocol).getResultRowIdFieldKey(), Sort.SortDirection.ASC, false);
        view.getDataRegion().addHiddenFormField("rowId", "" + _protocol.getRowId());
        String returnURL = getViewContext().getRequest().getParameter(ActionURL.Param.returnUrl.name());

        if (returnURL == null)
        {
            // 27693: Respect returnURL from async webpart requests
            if (getSettings().getReturnUrl() != null)
                returnURL = getSettings().getReturnUrl().toString();
            else
                returnURL = getViewContext().getActionURL().toString();
        }

        view.getDataRegion().addHiddenFormField(ActionURL.Param.returnUrl, returnURL);

        String redirectUrl = getViewContext().getRequest().getParameter(ActionURL.Param.redirectUrl.name());
        if (redirectUrl != null)
            view.getDataRegion().addHiddenFormField(ActionURL.Param.redirectUrl, redirectUrl);

        view.getTable();
        SimpleFilter filter = (SimpleFilter) view.getRenderContext().getBaseFilter();
        if (filter == null)
        {
            filter = new SimpleFilter();
            view.getRenderContext().setBaseFilter(filter);
        }
        _replacedRunFilter.addFilterCondition(filter, getReplacedFieldKey());

        return view;
    }

    @Override
    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        if (showControls())
        {
            super.populateButtonBar(view, bar);

            if (!AssayPublishService.get().getValidPublishTargets(getUser(), InsertPermission.class).isEmpty())
            {
                ActionURL publishURL = PageFlowUtil.urlProvider(AssayUrls.class).getCopyToStudyURL(getContainer(), _protocol);
                for (Pair<String, String> param : publishURL.getParameters())
                {
                    if (!"rowId".equalsIgnoreCase(param.getKey()))
                        view.getDataRegion().addHiddenFormField(param.getKey(), param.getValue());
                }
                publishURL.deleteParameters();

                if (getTable().getContainerFilter() != null && getTable().getContainerFilter().getType() != null)
                    publishURL.addParameter("containerFilterName", getTable().getContainerFilter().getType().name());

                ActionButton publishButton = new ActionButton(publishURL,
                        "Copy to Study", DataRegion.MODE_GRID, ActionButton.Action.POST);
                publishButton.setDisplayPermission(InsertPermission.class);
                publishButton.setRequiresSelection(true);

                bar.add(publishButton);
            }

            bar.addAll(AssayService.get().getImportButtons(_protocol, getUser(), getContainer(), false));
            FieldKey runFK = _provider.getTableMetadata(_protocol).getRunRowIdFieldKeyFromResults();
            String runId = getViewContext().getRequest().getParameter(view.getDataRegion().getName() + "." + runFK + "~eq");

            if (runId != null && getViewContext().hasPermission(InsertPermission.class) &&
                    getViewContext().hasPermission(DeletePermission.class) &&
                    _provider.getReRunSupport() != AssayProvider.ReRunSupport.None)
            {
                try
                {
                    // Don't give the user to re-import if the run has already been replaced
                    ExpRun run = ExperimentService.get().getExpRun(Integer.parseInt(runId));
                    if (run != null && run.getReplacedByRun() == null)
                    {
                        ActionURL reRunURL = _provider.getImportURL(getContainer(), _protocol);
                        reRunURL.addParameter("reRunId", runId);
                        bar.add(new ActionButton("Re-import run", reRunURL).setTooltip("Import a revised version of this run, with updated metadata or data file."));
                    }
                }
                catch (NumberFormatException ignored) {}
            }

            if (_provider != null && _provider.getReRunSupport() == AssayProvider.ReRunSupport.ReRunAndReplace)
            {
                MenuButton button = new MenuButton("Replaced Filter");
                for (ReplacedRunFilter.Type type : ReplacedRunFilter.Type.values())
                {
                    ActionURL url = view.getViewContext().cloneActionURL();
                    type.addToURL(url, getDataRegionName(), getReplacedFieldKey());
                    button.addMenuItem(type.getTitle(), url).setSelected(type == _replacedRunFilter.getType());
                }
                bar.add(button);
            }
        }
    }

    protected ColumnHeaderType getColumnHeaderType()
    {
        return ColumnHeaderType.Caption;
    }

    public static class ResultsDataRegion extends DataRegion
    {
        private ColumnInfo _matchColumn;
        private final AssayProvider _provider;
        private final ExpProtocol _protocol;

        public ResultsDataRegion(AssayProvider provider, ExpProtocol protocol)
        {
            _provider = provider;
            _protocol = protocol;
        }

        @Override
        protected boolean isErrorRow(RenderContext ctx, int rowIndex)
        {
            // If we know that the specimen info doesn't match, flag the row as being problematic
            return _matchColumn != null && Boolean.FALSE.equals(_matchColumn.getValue(ctx));
        }

        @Override
        public void addQueryColumns(Set<ColumnInfo> columns)
        {
            super.addQueryColumns(columns);
            FieldKey fk = new FieldKey(_provider.getTableMetadata(_protocol).getSpecimenIDFieldKey(), AbstractAssayProvider.ASSAY_SPECIMEN_MATCH_COLUMN_NAME);
            Map<FieldKey, ColumnInfo> newColumns = QueryService.get().getColumns(getTable(), Collections.singleton(fk), columns);
            _matchColumn = newColumns.get(fk);
            if (_matchColumn != null)
            {
                // Add the column that decides if the specimen info has changed on the study side
                // Don't add until perf problems are resolved
//                columns.add(_matchColumn);
            }
        }
    }
}
