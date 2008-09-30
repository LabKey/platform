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
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.CompareType;
import org.apache.commons.lang.StringUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;

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
        LookAndFeelProperties props = LookAndFeelProperties.getInstance(_view.getViewContext().getContainer());
        return null == props ? "LabKey Server" : props.getShortName();
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
        String sortParam = _view.getSettings().getSortFilterURL().getParameter(_view.getDataRegionName() +  ".sort");
        if(null == sortParam || sortParam.length() == 0)
            return "NULL";
        else
            return "\"" + sortParam + "\"";
    }

    public String getFilters()
    {
        //R package wants filters like this:
        //   makefilter(c(name, operator, value), c(name, operator, value))
        //where 'name' is the column name, 'operator' is the string version of the operator
        //as defined in the Rlabkey package, and 'value' is the filter value.

        //load the sort/filter url into a new SimpleFilter
        //and iterate the clauses
        ArrayList<String> makeFilterExprs = new ArrayList<String>();
        SimpleFilter filter = new SimpleFilter(_view.getSettings().getSortFilterURL(), _view.getDataRegionName());
        String name;
        CompareType operator;
        String value;
        for(SimpleFilter.FilterClause clause : filter.getClauses())
        {
            //all filter clauses can report col names and values,
            //each of which in this case should contain only one value
            name = clause.getColumnNames().get(0);
            value = getFilterValue(clause.getParamVals());

            //two kinds of clauses can be used on URLs: CompareClause and InClause
            if(clause instanceof CompareType.CompareClause)
                operator = ((CompareType.CompareClause)clause).getComparison();
            else if(clause instanceof SimpleFilter.InClause)
                operator = CompareType.IN;
            else
                operator = CompareType.EQUAL;

            makeFilterExprs.add(makeFilterExpr(name, operator, value));
        }

        if(makeFilterExprs.size() == 0)
            return "NULL";

        StringBuilder filtersExpr = new StringBuilder("makeFilter(");
        String sep = "";
        for(String mf : makeFilterExprs)
        {
            filtersExpr.append(sep);
            filtersExpr.append(mf);
            sep = ",";
        }
        filtersExpr.append(")");
        return filtersExpr.toString();
    }

    protected String getFilterValue(Object[] values)
    {
        if(null == values || values.length == 0)
            return "";
        
        StringBuilder sb = new StringBuilder();
        String sep = "";
        for(Object val : values)
        {
            sb.append(sep);
            sb.append(val.toString());
            sep = ";";
        }
        return sb.toString();
    }

    protected String makeFilterExpr(String name, CompareType operator, String value)
    {
        return "c(\"" + name + "\",\"" + operator.getRName() + "\",\"" + value + "\")";
    }

}