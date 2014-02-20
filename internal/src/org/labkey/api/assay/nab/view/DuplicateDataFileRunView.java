/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
package org.labkey.api.assay.nab.view;

import org.labkey.api.assay.nab.Luc5Assay;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.nab.NabUrls;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.study.assay.AssayProtocolSchema;
import org.labkey.api.study.query.RunListQueryView;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.DataView;

/**
 * User: klum
 * Date: 5/17/13
 */
public class DuplicateDataFileRunView extends RunListQueryView
{
    private Luc5Assay _assay;
    private ExpRun _run;

    public DuplicateDataFileRunView(AssayProtocolSchema schema, QuerySettings settings, Luc5Assay assay, ExpRun run)
    {
        super(schema, settings);
        setShowExportButtons(false);
        _assay = assay;
        _run = run;
    }

    public DataView createDataView()
    {
        DataView view = super.createDataView();
        DataRegion rgn = view.getDataRegion();
        ButtonBar bar = rgn.getButtonBar(DataRegion.MODE_GRID);

        ActionButton deleteButton = new ActionButton(PageFlowUtil.urlProvider(NabUrls.class).urlDeleteRun(getContainer()),
                "Delete", DataRegion.MODE_GRID, ActionButton.Action.POST);
        deleteButton.setRequiresSelection(true);
        deleteButton.setDisplayPermission(DeletePermission.class);
        bar.add(deleteButton);

        SimpleFilter filter;
        if (view.getRenderContext().getBaseFilter() instanceof SimpleFilter)
        {
            filter = (SimpleFilter) view.getRenderContext().getBaseFilter();
        }
        else
        {
            filter = new SimpleFilter(view.getRenderContext().getBaseFilter());
        }
        filter.addCondition(FieldKey.fromParts("Name"), _assay.getDataFile().getName());
        filter.addCondition(FieldKey.fromParts("RowId"), _run.getRowId(), CompareType.NEQ);
        view.getRenderContext().setBaseFilter(filter);
        return view;
    }

    public void setRun(ExpRun run)
    {
        _run = run;
    }
}
