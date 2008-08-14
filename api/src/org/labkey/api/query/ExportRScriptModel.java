/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.api.query;

import org.labkey.api.query.QueryView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelAppProps;
import org.apache.commons.lang.StringUtils;

import java.text.SimpleDateFormat;
import java.util.Date;

/*
* User: Dave
* Date: Aug 13, 2008
* Time: 1:21:48 PM
*/
public class ExportRScriptModel
{
    private QueryView _view;

    public ExportRScriptModel(QueryView view)
    {
        assert view != null;
        _view = view;
    }

    public String getInstallationName()
    {
        LookAndFeelAppProps props = LookAndFeelAppProps.getInstance(_view.getViewContext().getContainer());
        return null == props ? "LabKey Server" : props.getSystemShortName();
    }

    public String getCreatedOn()
    {
        SimpleDateFormat fmt = new SimpleDateFormat("d MMM yyyy HH:mm:ss Z");
        return fmt.format(new Date());
    }

    public String getBaseUrl()
    {
        AppProps props = AppProps.getInstance();
        return props.getBaseServerUrl() + props.getContextPath();
    }

    public String getSchemaName()
    {
        return _view.getSchema().getSchemaName();
    }

    public String getQueryName()
    {
        return _view.getSettings().getQueryName();
    }

    public String getFolderPath()
    {
        return _view.getContainer().getPath();
    }

    public String getViewName()
    {
        return StringUtils.trimToEmpty(_view.getSettings().getViewName());
    }

    public String getSort()
    {
        return StringUtils.trimToEmpty(_view.getSettings().getSortFilterURL().getParameter(_view.getDataRegionName() +  ".sort"));
    }

    public String getFilters()
    {
        //R package wants filters like this:
        //   c("colnameX~operator=value", "colnameY~operator=value", "colnameZ~operator=value")
        //get all parameters on the sort/filter url that are prefixed with the data region name
        ActionURL url = _view.getSettings().getSortFilterURL();
        String[] keys = url.getKeysByPrefix(_view.getDataRegionName() + ".");
        if(null == keys)
            return "";

        StringBuilder filters = new StringBuilder("c(");
        String sep = "";
        for(String key : keys)
        {
            //ignore non-filter parameters
            if(key.equalsIgnoreCase(_view.getDataRegionName() + ".sort")
                    || key.equalsIgnoreCase(_view.getDataRegionName() + ".queryName")
                    || key.equalsIgnoreCase(_view.getDataRegionName() + ".viewName")
                    )
                continue;

            filters.append(sep);
            filters.append("\"");
            filters.append(key.substring(key.indexOf(".") + 1));
            filters.append("=");
            filters.append(url.getParameter(key));
            filters.append("\"");
            sep = ",";
        }
        filters.append(")");
        
        return filters.toString();
    }
}