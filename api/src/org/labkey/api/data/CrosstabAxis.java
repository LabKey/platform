/*
 * Copyright (c) 2008-2013 LabKey Corporation
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

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a result axis for a cross tab table info (column or row)
 *
 * User: Dave
 * Date: Jan 28, 2008
 * Time: 9:23:53 AM
 */
public class CrosstabAxis
{
    private String _caption = null;
    private ArrayList<CrosstabDimension> _dimensions = new ArrayList<>();
    private CrosstabSettings _settings = null;

    public CrosstabAxis(CrosstabSettings settings)
    {
        _settings = settings;
    }

    public String getCaption()
    {
        return _caption;
    }

    public void setCaption(String caption)
    {
        _caption = caption;
    }

    public ArrayList<CrosstabDimension> getDimensions()
    {
        return _dimensions;
    }

    public CrosstabDimension addDimension(FieldKey fieldKey)
    {
        CrosstabDimension dim = new CrosstabDimension(_settings.getSourceTable(), fieldKey);
        _dimensions.add(dim);
        return dim;
    }

    public CrosstabDimension getDimension(FieldKey fieldKey)
    {
        for (CrosstabDimension dimension : _dimensions)
        {
            if (dimension.getFieldKey().equals(fieldKey))
                return dimension;
        }
        return null;
    }

    public List<ColumnInfo> getSourceColumns()
    {
        List<CrosstabDimension> dims = getDimensions();
        List<ColumnInfo> cols = new ArrayList<>(dims.size());
        for(CrosstabDimension dim : dims)
        {
            cols.add(dim.getSourceColumn());
        }
        return cols;
    }

} //CrosstabAxis
