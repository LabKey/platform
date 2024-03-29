/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.labkey.api.reports.report.view.ReportQueryView;
import org.labkey.api.view.ViewContext;

/**
 * User: Karl Lum
 * Date: Oct 6, 2006
 */
public class QueryReportDescriptor extends ReportDescriptor
{
    public static final String TYPE = "queryDescriptor";

    private QueryViewGenerator _qvGenerator;

    public QueryReportDescriptor()
    {
        super(TYPE);
    }

    public QueryReportDescriptor(String type)
    {
        super(type);
    }

    public void setQueryViewGenerator(QueryViewGenerator generator){_qvGenerator = generator;}
    public QueryViewGenerator getQueryViewGenerator(){return _qvGenerator;}

    public interface QueryViewGenerator
    {
        public ReportQueryView generateQueryView(ViewContext context, ReportDescriptor descriptor) throws Exception;
    }
}
