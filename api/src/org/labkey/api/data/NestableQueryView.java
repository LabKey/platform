/*
 * Copyright (c) 2012-2017 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryNestingOption;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.view.DataView;
import org.labkey.api.view.GridView;

import java.util.List;

/**
 * A QueryView variant that renders nested grids. Some of the columns (typically those one one side of a foreign key
 * relationship) are shown in the outer grid, while other columns are rendered in a separate grid that's associated
 * with the outer set of rows. There are many nested grids.
 *
 * Among other things, ensures that the ResultSet is sorting such that all of the nested grid rows are returned together
 * so that they are correctly affiliated with the correct outer grid row.
 *
 * User: jeckels
 * Date: May 1, 2012
 */
public abstract class NestableQueryView extends QueryView
{
    protected QueryNestingOption _selectedNestingOption;

    protected final boolean _expanded;
    protected final boolean _allowNesting;
    protected final QueryNestingOption[] _queryNestingOptions;
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

    /** Derive the best nesting option given the selected columns */
    @Nullable
    protected QueryNestingOption determineNestingOption()
    {
        for (QueryNestingOption queryNestingOption : _queryNestingOptions)
        {
            if (queryNestingOption.isNested(getDisplayColumns()))
            {
                return queryNestingOption;
            }
        }
        return null;
    }

    @Override
    protected DataRegion createDataRegion()
    {
        List<DisplayColumn> originalColumns = getDisplayColumns();

        // Figure out if we have nestable columns
        if (_allowNesting)
        {
            _selectedNestingOption = determineNestingOption();
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
        return rgn;
    }
}
