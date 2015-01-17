/*
 * Copyright (c) 2007-2015 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.DisplayElement;
import org.labkey.study.CohortFilter;

import java.util.List;

/**
 * User: brittp
 * Date: Jan 26, 2007
 * Time: 10:03:34 AM
 */
public abstract class BaseStudyQueryView extends QueryView
{
    protected final SimpleFilter _filter;
    protected final Sort _sort;
    protected @Nullable CohortFilter _cohortFilter;
    private List<DisplayElement> _buttons;

    public BaseStudyQueryView(UserSchema schema, QuerySettings settings, SimpleFilter filter, Sort sort)
    {
        super(schema, settings);
        _filter = filter;
        _sort = sort;
    }

    public DataView createDataView()
    {
        DataView view = super.createDataView();
        SimpleFilter filter = (SimpleFilter) view.getRenderContext().getBaseFilter();
        if (filter != null)
            filter.addAllClauses(_filter);
        else
            filter = _filter;

        if (_cohortFilter != null)
            _cohortFilter.addFilterCondition(view.getTable(), getContainer(), filter);

        view.getRenderContext().setBaseFilter(filter);
        Sort sort = view.getRenderContext().getBaseSort();
        if (sort == null)
        {
            view.getRenderContext().setBaseSort(_sort);
        }
        else
        {
            sort.insertSort(_sort);
        }
        if (_buttons != null)
        {
            ButtonBar bbar = new ButtonBar();
            bbar.addAll(view.getDataRegion().getButtonBar(DataRegion.MODE_GRID).getList());
            bbar.addAll(_buttons);
            view.getDataRegion().setShowRecordSelectors(true);
            view.getDataRegion().setButtonBar(bbar);
        }
        view.getDataRegion().setButtonBarPosition(getButtonBarPosition());
        return view;
    }

    protected DataRegion.ButtonBarPosition getButtonBarPosition()
    {
        return DataRegion.ButtonBarPosition.TOP;
    }

    public void setButtons(List<DisplayElement> buttons)
    {
        _buttons = buttons;
    }

    public ActionURL getBaseViewURL()
    {
        return urlBaseView();
    }
}
