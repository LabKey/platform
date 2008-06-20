/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.experiment.controllers.list;

import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.query.QueryPicker;
import org.labkey.api.query.QueryView;
import org.labkey.api.view.DataView;
import org.labkey.api.view.DetailsView;

import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Nov 6, 2007
 */
public class ListDetailsView extends QueryView
{
    private Object _listItemKey;
    private String _keyName;

    public ListDetailsView(ListQueryForm form, Object listItemKey)
    {
        super(form);
        getSettings().setAllowChooseQuery(false);
        getSettings().setAllowChooseView(false);
        ListDefinition def = form.getList();
        if (def != null)
        {
            _keyName = def.getKeyName();
        }
        _listItemKey = listItemKey;
    }

    protected DataView createDataView()
    {
        DataRegion rgn = createDataRegion();
        rgn.setButtonBar(ButtonBar.BUTTON_BAR_EMPTY, DataRegion.MODE_DETAILS);
        DetailsView view = new DetailsView(rgn);

        SimpleFilter filter = new SimpleFilter();
        if (_listItemKey != null && _keyName != null)
            filter.addCondition(_keyName, _listItemKey);

        SimpleFilter baseFilter = (SimpleFilter) view.getRenderContext().getBaseFilter();
        if (baseFilter != null)
            baseFilter.addAllClauses(filter);
        else
            baseFilter = filter;
        view.getRenderContext().setBaseFilter(baseFilter);

        return view;
    }
}
