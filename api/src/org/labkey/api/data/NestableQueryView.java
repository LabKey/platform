/*
 * Copyright (c) 2012-2014 LabKey Corporation
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
package org.labkey.api.data;

import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryNestingOption;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.view.DataView;
import org.labkey.api.view.GridView;

import java.util.List;

/**
* User: jeckels
* Date: May 1, 2012
*/
public abstract class NestableQueryView extends QueryView
{
    protected QueryNestingOption _selectedNestingOption;

    protected final boolean _expanded;
    protected final boolean _allowNesting;
    private final QueryNestingOption[] _queryNestingOptions;
    protected List<FieldKey> _overrideColumns;

    public NestableQueryView(UserSchema schema, QuerySettings settings, boolean expanded, boolean allowNesting, QueryNestingOption... queryNestingOptions)
    {
        super(schema, settings);
        _expanded = expanded;
        _allowNesting = allowNesting;
        _queryNestingOptions = queryNestingOptions;
        _buttonBarPosition = DataRegion.ButtonBarPosition.TOP;
        setShowExportButtons(false);
    }

    public void setOverrideColumns(List<FieldKey> fieldKeys)
    {
        _overrideColumns = fieldKeys;
    }

    public abstract TableInfo createTable();

    public QueryNestingOption getSelectedNestingOption()
    {
        return _selectedNestingOption;
    }

    /** @return the default sort for the base table (the nested grids) */
    protected abstract Sort getBaseSort();

    public DataView createDataView()
    {
        DataRegion rgn = createDataRegion();
        GridView result = new GridView(rgn, new NestedRenderContext(_selectedNestingOption, getViewContext()));
        setupDataView(result);

        Sort customViewSort = result.getRenderContext().getBaseSort();
        Sort sort = getBaseSort();
        if (customViewSort != null)
        {
            sort.insertSort(customViewSort);
        }
        result.getRenderContext().setBaseSort(sort);

        Filter customViewFilter = result.getRenderContext().getBaseFilter();
        SimpleFilter filter = new SimpleFilter(customViewFilter);
        result.getRenderContext().setBaseFilter(filter);

        return result;
    }

    @Override
    protected DataRegion createDataRegion()
    {
        List<DisplayColumn> originalColumns = getDisplayColumns();

        // Figure out if we have nestable columns
        if (_allowNesting)
        {
            for (QueryNestingOption queryNestingOption : _queryNestingOptions)
            {
                if (queryNestingOption.isNested(originalColumns))
                {
                    _selectedNestingOption = queryNestingOption;
                    break;
                }
            }
        }

        // Create the right kind of data region
        DataRegion rgn;
        if (_selectedNestingOption != null && (_allowNesting || !_expanded))
        {
            rgn = _selectedNestingOption.createDataRegion(originalColumns, getDataRegionName(), _expanded);
        }
        else
        {
            rgn = new DataRegion();
            rgn.setDisplayColumns(originalColumns);
        }
        
        // We render the expand/collapse UI as part of the record selector, so we always need to include them
        rgn.setShowRecordSelectors(true);
        rgn.setSettings(getSettings());
        rgn.setFixedWidthColumns(true);
        return rgn;
    }
}
