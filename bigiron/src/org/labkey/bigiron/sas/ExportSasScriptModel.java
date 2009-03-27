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
package org.labkey.bigiron.sas;

import org.labkey.api.data.CompareType;
import org.labkey.api.query.ExportScriptModel;
import org.labkey.api.query.QueryView;

import java.util.List;

/**
 * User: adam
 * Date: Jan 27, 2009
 * Time: 3:13:06 PM
 */
public class ExportSasScriptModel extends ExportScriptModel
{
    public ExportSasScriptModel(QueryView view)
    {
        super(view);
    }

    public String getFilters()
    {
        List<String> filterExprs = getFilterExpressions();

        if (filterExprs.isEmpty())
            return null;

        StringBuilder filtersExpr = new StringBuilder("%makeFilter(");
        String sep = "";

        for(String mf : filterExprs)
        {
            filtersExpr.append(sep);
            filtersExpr.append(mf);
            sep = ",";
        }
        filtersExpr.append(")");

        return filtersExpr.toString();
    }

    protected String makeFilterExpression(String name, CompareType operator, String value)
    {
        if (operator.isDataValueRequired())
            return "\"" + name + "\",\"" + operator.getScriptName() + "\",\"" + value + "\"";
        else
            return "\"" + name + "\",\"" + operator.getScriptName() + "\"";
    }
}
