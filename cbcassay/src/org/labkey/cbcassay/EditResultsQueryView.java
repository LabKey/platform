/*
 * Copyright (c) 2009-2013 LabKey Corporation
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

package org.labkey.cbcassay;

import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.study.assay.AssayProtocolSchema;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.query.AssayBaseQueryView;
import org.labkey.api.util.ReturnURLString;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;

import java.util.List;

/**
 * User: kevink
 * Date: Mar 20, 2009
 */
public class EditResultsQueryView extends AssayBaseQueryView
{
    int _runId;
    private CBCAssayProvider _provider;
    private String _returnURL;

    public EditResultsQueryView(ExpProtocol protocol, AssayProtocolSchema schema, QuerySettings settings, int runId, String returnURL)
    {
        super(protocol, schema, settings);
        _runId = runId;
        _provider = (CBCAssayProvider)AssayService.get().getProvider(_protocol);
        _returnURL = returnURL;
    }

    public EditResultsQueryView(ExpProtocol protocol, AssayProtocolSchema schema, QuerySettings settings, int runId, ReturnURLString returnURL)
    {
        super(protocol, schema, settings);
        _runId = runId;
        _provider = (CBCAssayProvider)AssayService.get().getProvider(_protocol);
        _returnURL = (null == returnURL || returnURL.isEmpty()) ? null : returnURL.getSource();
    }

    public DataView createDataView()
    {
        DataView view = super.createDataView();
        if (_runId > 0)
        {
            SimpleFilter filter = (SimpleFilter)view.getRenderContext().getBaseFilter();
            if (filter == null)
                filter = new SimpleFilter();

            filter.addCondition(_provider.getTableMetadata(_protocol).getResultRowIdFieldKey(), _runId);
            view.getRenderContext().setBaseFilter(filter);
            view.getDataRegion().addHiddenFormField("runId", String.valueOf(_runId));
        }

        view.getDataRegion().setShowSelectMessage(false);
        if (_returnURL != null)
            view.getDataRegion().addHiddenFormField(ActionURL.Param.returnUrl, new ActionURL(_returnURL).getLocalURIString());

        return view;
    }

    @Override
    public List<DisplayColumn> getDisplayColumns()
    {
        List<DisplayColumn> columns = super.getDisplayColumns();
        CBCResultsQueryView.wrapDisplayColumns(_protocol, getViewContext(), columns, true);
        return columns;
    }

    @Override
    protected boolean canDelete()
    {
        return getViewContext().getUser().isSiteAdmin() &&
               getViewContext().hasPermission(DeletePermission.class);
    }

    @Override
    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        super.populateButtonBar(view, bar);

        ActionURL url = getViewContext().cloneActionURL();
        url.setAction(CBCAssayController.EditResultsAction.class);
        url = url.deleteParameter(ActionURL.Param.returnUrl); // use 'returnURL' hidden form field
        ActionButton doneButton = new ActionButton("Save", url);
        doneButton.setActionName(url.getAction() + ".view?" + url.getQueryString()); // preserve url filters
        doneButton.setActionType(ActionButton.Action.POST);
        bar.add(doneButton);
    }
}
