/*
 * Copyright (c) 2009-2017 LabKey Corporation
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

import org.labkey.api.data.AnalyticsProviderItem;
import org.labkey.api.query.CustomViewInfo.ColumnProperty;
import org.labkey.api.query.FieldKey;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

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
    private final CustomViewXmlReader _customView;
    private final long _lastModified;
    private final Path _path;

    public ModuleCustomViewDef(Resource r)
    {
        _lastModified = r.getLastModified();
        _customView = CustomViewXmlReader.loadDefinition(r);
        _path = r.getPath();

        String fileName = r.getName();
        assert fileName.length() >= CustomViewXmlReader.XML_FILE_EXTENSION.length();

        if (fileName.length() > CustomViewXmlReader.XML_FILE_EXTENSION.length())
        {
            // Module custom views always use the file name as the name
            _customView._name = fileName.substring(0, fileName.length() - CustomViewXmlReader.XML_FILE_EXTENSION.length());
        }
    }

    public Date getLastModified()
    {
        return new Date(_lastModified);
    }

    public String getName()
    {
        return _customView.getName();
    }

    public String getSchema()
    {
        return _customView.getSchema();
    }

    public String getQuery()
    {
        return _customView.getQuery();
    }

    public List<Entry<FieldKey, Map<ColumnProperty, String>>> getColList()
    {
        return _customView.getColList();
    }

    public boolean isHidden()
    {
        return _customView.isHidden();
    }

    public boolean isOverridable()
    {
        return _customView.canOverride();
    }

    public boolean isShowInDataViews()
    {
        return _customView.isShowInDataViews();
    }

    public String getCategory()
    {
        return _customView.getCategory();
    }

    public List<Pair<String, String>> getFilters()
    {
        return _customView.getFilters();
    }

    public List<String> getSorts()
    {
        return _customView.getSorts();
    }

    public String getSortParamValue()
    {
        return _customView.getSortParamValue();
    }

    public String getFilterAndSortString()
    {
        return _customView.getFilterAndSortString();
    }

    public List<AnalyticsProviderItem> getAnalyticsProviders()
    {
        return _customView.getAnalyticsProviders();
    }

    public String getCustomIconUrl()
    {
        return _customView.getCustomIconUrl();
    }

    public String getCustomIconCls()
    {
        return _customView.getCustomIconCls();
    }

    public String getContainerFilterName()
    {
        return _customView.getContainerFilter() != null ? _customView.getContainerFilter().name() : null;
    }

    public String getLabel()
    {
        return _customView.getLabel() != null ? _customView.getLabel() : _customView.getName();
    }

    public Path getPath()
    {
        return _path;
    }

    public List<String> getErrors()
    {
        return _customView.getErrors();
    }
}
