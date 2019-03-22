/*
 * Copyright (c) 2008-2009 LabKey Corporation
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
package org.labkey.api.reports.report;

import org.labkey.api.data.DataRegion;
import org.labkey.api.query.QueryView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;

/*
* User: Dave
* Date: Sep 5, 2008
* Time: 1:31:51 PM
*/
public abstract class CustomRReport extends RReport
{
    private String[] _forwardParams = null;
    private String _type = null;

    protected CustomRReport(String[] forwardParams, String type)
    {
        _forwardParams = forwardParams;
        _type = type;
    }

    protected String[] getForwardParams()
    {
        return _forwardParams;
    }

    protected abstract QueryView getQueryView(ViewContext context) throws Exception;
    protected abstract boolean hasRequiredParams(ViewContext context);

    public String getType()
    {
        return _type;
    }

    public ActionURL getEditReportURL(ViewContext context)
    {
        return null; //no editing from manage page
    }

    protected QueryView createQueryView(ViewContext context, ReportDescriptor descriptor) throws Exception
    {
        QueryView view = getQueryView(context);

        //need to apply view name if defined in the descriptor
        //the URL to run the report does not contain the viewName parameter in the query string
        //so QuerySettings.init() won't automatically apply it
        String customViewName = descriptor.getProperty(ReportDescriptor.Prop.viewName);
        if(null != customViewName)
            view.setCustomView(customViewName);
        
        return view;
    }

    public ActionURL getDownloadDataURL(ViewContext context)
    {
        ActionURL url = super.getDownloadDataURL(context);
        return hasRequiredParams(context) ? addForwardParams(url, context) : null;
    }

    public HttpView renderDataView(ViewContext context) throws Exception
    {
        QueryView view = createQueryView(context, getDescriptor());
        view.getSettings().setReportId(null);
        view.setButtonBarPosition(DataRegion.ButtonBarPosition.NONE);
        return view;
    }

    public ActionURL getRunReportURL(ViewContext context)
    {
        ActionURL url = super.getRunReportURL(context);
        return hasRequiredParams(context) ? addForwardParams(url, context) : null;
    }


    protected ActionURL addForwardParams(ActionURL url, ViewContext context)
    {
        for(String name : getForwardParams())
        {
            String value = context.getActionURL().getParameter(name);
            if(null != value)
                url.replaceParameter(name, value);
        }
        return url;
    }
}
