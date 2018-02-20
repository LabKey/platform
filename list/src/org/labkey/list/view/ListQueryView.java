/*
 * Copyright (c) 2007-2017 LabKey Corporation
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
import org.labkey.api.data.DetailsColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.lists.permissions.DesignListPermission;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.list.model.ListQuerySchema;
import org.springframework.validation.BindException;

import java.util.HashSet;
import java.util.Set;

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
        disableContainerFilterSelection();
    }

    protected boolean canDelete()
    {
        return super.canDelete() && _list.getAllowDelete();
    }

    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        super.populateButtonBar(view, bar);

        if (getViewContext().hasPermission(DesignListPermission.class))
        {
            ActionURL designURL = getList().urlShowDefinition();
            URLHelper returnURL = getSettings() != null ? getSettings().getReturnUrl() : null;
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

    @Override
    protected DisplayColumn createDetailsColumn(StringExpression urlDetails, TableInfo table)
    {
        return new DetailsColumn(urlDetails, table)
        {
            @Override
            public boolean areURLExpressionValuesPresent(RenderContext ctx)
            {
                Set<FieldKey> fieldKeys = new HashSet<>();
                table.getPkColumns().forEach(column -> fieldKeys.add(new FieldKey(null, column.getAlias())));
                return getURLExpression().canRender(fieldKeys);
            }
        };
    }
}
