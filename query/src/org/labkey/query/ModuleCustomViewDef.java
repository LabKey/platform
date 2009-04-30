/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.query;

import org.apache.log4j.Logger;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.Pair;
import org.labkey.data.xml.queryCustomView.*;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
* User: Dave
* Date: Jan 14, 2009
* Time: 12:43:18 PM
*/

/**
 * A bean that represents a custom view definition stored
 * in a module resource file. This is separate from ModuleCustomView
 * because that class cannot be cached, as it must hold a reference
 * to the source QueryDef, which holds a reference to the QueryView,
 * etc., etc.
 */
public class ModuleCustomViewDef
{
    public static final String FILE_EXTENSION = ".qview.xml";

    private File _sourceFile;
    private String _name;
    private List<Map.Entry<FieldKey,Map<CustomView.ColumnProperty,String>>> _colList = new ArrayList<Map.Entry<FieldKey,Map<CustomView.ColumnProperty,String>>>();
    private boolean _hidden = false;
    private List<Pair<String,String>> _filters;
    private List<String> _sorts;
    private long _lastModified;
    private String _customIconUrl;

    public ModuleCustomViewDef(File sourceFile)
    {
        _sourceFile = sourceFile;
        _lastModified = sourceFile.lastModified();

        String fileName = _sourceFile.getName();
        assert fileName.length() > FILE_EXTENSION.length();
        _name = fileName.substring(0, fileName.length() - FILE_EXTENSION.length());

        loadDefinition();
    }

    public boolean isStale()
    {
        return _sourceFile.lastModified() != _lastModified;
    }

    public File getSourceFile()
    {
        return _sourceFile;
    }

    public String getName()
    {
        return _name;
    }

    public List<Map.Entry<FieldKey, Map<CustomView.ColumnProperty, String>>> getColList()
    {
        return _colList;
    }

    public boolean isHidden()
    {
        return _hidden;
    }

    public List<Pair<String, String>> getFilters()
    {
        return _filters;
    }

    public List<String> getSorts()
    {
        return _sorts;
    }

    public long getLastModified()
    {
        return _lastModified;
    }

    public String getCustomIconUrl()
    {
        return _customIconUrl;
    }

    protected void loadDefinition()
    {
        try
        {
            CustomViewDocument doc = CustomViewDocument.Factory.parse(_sourceFile);
            CustomViewType viewElement = doc.getCustomView();

            _hidden = viewElement.isSetHidden() && viewElement.getHidden();
            _customIconUrl = viewElement.getCustomIconUrl();


            //load the columns
            _colList = loadColumns(viewElement.getColumns());

            //load the filters
            _filters = loadFilters(viewElement.getFilters());

            //load the sorts
            _sorts = loadSorts(viewElement.getSorts());

        }
        catch(Exception e)
        {
            Logger.getLogger(ModuleCustomView.class).warn("Unable to load custom view definition from file "
                    + _sourceFile.getPath(), e);
        }
    }

    protected List<Map.Entry<FieldKey,Map<CustomView.ColumnProperty,String>>> loadColumns(ColumnsType columns)
    {
        List<Map.Entry<FieldKey,Map<CustomView.ColumnProperty,String>>> ret = new ArrayList<Map.Entry<FieldKey,Map<CustomView.ColumnProperty,String>>>();

        if(null == columns)
            return ret;

        for(ColumnType column : columns.getColumnArray())
        {
            FieldKey fieldKey = getFieldKey(column.getName());
            if(null == fieldKey)
                continue;

            //load any column properties that might be there
            Map<CustomView.ColumnProperty,String> props = new HashMap<CustomView.ColumnProperty,String>();

            PropertiesType propsList = column.getProperties();
            if(null != propsList)
            {
                for(PropertyType propDef : propsList.getPropertyArray())
                {
                    CustomView.ColumnProperty colProp = CustomView.ColumnProperty.valueOf(propDef.getName());
                    if(null == colProp)
                        continue;

                    props.put(colProp, propDef.getValue());
                }
            }

            ret.add(Pair.of(fieldKey, props));
        }

        return ret;
    }

    protected List<Pair<String,String>> loadFilters(FiltersType filters)
    {
        if(null == filters)
            return null;

        List<Pair<String,String>> ret = new ArrayList<Pair<String,String>>();
        for(FilterType filter : filters.getFilterArray())
        {
            if(null == filter.getColumn() || null == filter.getOperator())
                continue;

            ret.add(new Pair<String,String>(filter.getColumn() + "~" + filter.getOperator().toString(), filter.getValue()));
        }

        return ret;
    }

    protected FieldKey getFieldKey(String name)
    {
        return null == name ? null : FieldKey.fromString(name);
    }

    protected List<String> loadSorts(SortsType sorts)
    {
        if(null == sorts)
            return null;

        List<String> ret = new ArrayList<String>();
        for(SortType sort : sorts.getSortArray())
        {
            if(null == sort.getColumn())
                continue;

            ret.add(sort.isSetDescending() && sort.getDescending() ? "-" + sort.getColumn() : sort.getColumn());
        }
        return ret;
    }
}