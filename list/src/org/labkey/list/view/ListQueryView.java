/*
 * Copyright (c) 2009-2019 LabKey Corporation
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

import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.lists.permissions.DesignListPermission;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.list.model.ListQuerySchema;
import org.springframework.validation.BindException;

public class ListQueryView extends QueryView
{
    private final ListDefinition _list;

    public ListQueryView(ListDefinition def, ListQuerySchema schema, QuerySettings settings, BindException errors)
    {
        super(schema, settings, errors);
        _list = def;
        init();
    }

    public ListQueryView(ListQueryForm form, BindException errors)
    {
        super(form, errors);
        _list = form.getList();
        init();
    }

    protected void init()
    {
        setShowExportButtons(_list.getAllowExport());
        setShowUpdateColumn(true);
        setAllowableContainerFilterTypes(
            ContainerFilter.Type.Current,
            ContainerFilter.Type.CurrentAndSubfoldersPlusShared,
            ContainerFilter.Type.CurrentPlusProjectAndShared,
            ContainerFilter.Type.AllFolders
        );
    }

    @Override
    protected boolean canDelete()
    {
        return super.canDelete() && _list.getAllowDelete();
    }

    @Override
    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        super.populateButtonBar(view, bar);

        if (getViewContext().hasPermission(DesignListPermission.class))
        {
            ActionURL designURL = getList().urlShowDefinition();
            URLHelper returnURL = getSettings() != null ? getSettings().getReturnURLHelper() : null;
            designURL.addReturnURL(returnURL != null ? returnURL : getViewContext().getActionURL());
            ActionButton btnUpload = new ActionButton("Design", designURL);
            bar.add(btnUpload);
        }
        if (canDelete())
            bar.add(super.createDeleteAllRowsButton("list"));
    }

    public ListDefinition getList()
    {
        return _list;
    }
}
