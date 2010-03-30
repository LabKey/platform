/*
 * Copyright (c) 2007-2010 LabKey Corporation
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

package org.labkey.list.view;

import org.labkey.api.data.*;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.security.ACL;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.springframework.validation.BindException;

public class ListQueryView extends QueryView
{
    private final ListDefinition _list;
    private final boolean _exportAsWebPage;

    public ListQueryView(ListQueryForm form, BindException errors)
    {
        super(form, errors);
        _list = form.getList();
        _exportAsWebPage = form.isExportAsWebPage();
        setShowExportButtons(_list.getAllowExport());
        setShowUpdateColumn(true);
        QuerySettings settings = getSettings();
        settings.setAllowChooseQuery(false);
        disableContainerFilterSelection();
    }

    public DataView createDataView()
    {
        DataView view = super.createDataView();
        DataRegion rgn = view.getDataRegion();
        rgn.setShadeAlternatingRows(true);
        rgn.setShowBorders(true);
        return view;
    }

    protected boolean canDelete()
    {
        return super.canDelete() && _list.getAllowDelete();
    }

    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        super.populateButtonBar(view, bar, _exportAsWebPage);
        if (getViewContext().hasPermission(ACL.PERM_UPDATE) && _list.getAllowUpload())
        {
            ActionButton btnUpload = new ActionButton("Import Data", getList().urlFor(ListController.Action.uploadListItems));
            bar.add(btnUpload);
        }
        if (getViewContext().hasPermission(ACL.PERM_UPDATE))
        {
            ActionURL designURL = getList().urlFor(ListController.Action.showListDefinition);
            designURL.addReturnURL(getViewContext().getActionURL());
            ActionButton btnUpload = new ActionButton("View Design", designURL);
            bar.add(btnUpload);
        }
    }

    public ListDefinition getList()
    {
        return _list;
    }
}
