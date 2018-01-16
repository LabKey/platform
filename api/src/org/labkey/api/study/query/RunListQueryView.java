/*
 * Copyright (c) 2007-2014 LabKey Corporation
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

import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.MenuButton;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.exp.ExperimentRunListView;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.study.actions.ReimportRedirectAction;
import org.labkey.api.study.assay.AssayProtocolSchema;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayRunType;
import org.labkey.api.study.assay.ReplacedRunFilter;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * User: brittp
 * Date: Jun 29, 2007
 * Time: 11:13:44 AM
 */
public class RunListQueryView extends ExperimentRunListView
{
    public static final FieldKey REPLACED_FIELD_KEY = FieldKey.fromParts(ExpRunTable.Column.Replaced);

    protected final AssayProtocolSchema _schema;
    private final ReplacedRunFilter _replacedRunFilter;

    public RunListQueryView(AssayProtocolSchema schema, QuerySettings settings)
    {
        this(schema, settings, getDefaultAssayRunFilter(schema));
    }

    public RunListQueryView(AssayProtocolSchema schema, QuerySettings settings, AssayRunType assayRunFilter)
    {
        super(schema, settings, assayRunFilter);
        _schema = schema;
        setShowDeleteButton(true);
        setShowExportButtons(true);
        setShowAddToRunGroupButton(false);

        _replacedRunFilter = ReplacedRunFilter.getFromURL(this, REPLACED_FIELD_KEY);
    }

    protected void renderHeaderView(HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        // No-op to avoid double-rendering header links
    }

    public static AssayRunType getDefaultAssayRunFilter(AssayProtocolSchema schema)
    {
        return new AssayRunType(schema.getProtocol(), schema.getContainer());
    }

    @Override
    public DataView createDataView()
    {
        DataView result = super.createDataView();
        if (_schema.getProvider().getImportURL(getContainer(), _schema.getProtocol()) != null && getContainer().hasPermission(getUser(), InsertPermission.class))
        {
            result.getDataRegion().setNoRowsMessage("No runs to show. To add new runs, use the Import Data button.");
        }
        SimpleFilter filter = (SimpleFilter) result.getRenderContext().getBaseFilter();
        if (filter == null)
        {
            filter = new SimpleFilter();
            result.getRenderContext().setBaseFilter(filter);
        }
        _replacedRunFilter.addFilterCondition(filter, REPLACED_FIELD_KEY);
        return result;
    }

    @Override
    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        super.populateButtonBar(view, bar);
        if (_schema.getProvider().getReRunSupport() != AssayProvider.ReRunSupport.None && getViewContext().hasPermission(InsertPermission.class) && getViewContext().hasPermission(DeletePermission.class))
        {
            ActionURL reRunURL = new ActionURL(ReimportRedirectAction.class, getContainer());
            reRunURL.addParameter("rowId", _schema.getProtocol().getRowId());
            ActionButton button = new ActionButton("Re-import run", reRunURL).setTooltip("Import a revised version of this run, with updated metadata or data file.");
            button.setActionType(ActionButton.Action.POST);
            button.setRequiresSelection(true, 1, 1);
            bar.add(button);
        }

        if (_schema.getProvider().getReRunSupport() == AssayProvider.ReRunSupport.ReRunAndReplace)
        {
            MenuButton button = new MenuButton("Replaced Filter");
            for (ReplacedRunFilter.Type type : ReplacedRunFilter.Type.values())
            {
                ActionURL url = view.getViewContext().cloneActionURL();
                type.addToURL(url, getDataRegionName(), REPLACED_FIELD_KEY);
                button.addMenuItem(type.getTitle(), url).setSelected(type == _replacedRunFilter.getType());

            }
            bar.add(button);
        }
    }
}
