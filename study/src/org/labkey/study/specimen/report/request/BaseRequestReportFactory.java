/*
 * Copyright (c) 2009-2014 LabKey Corporation
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
package org.labkey.study.specimen.report.request;

import org.labkey.study.specimen.report.specimentype.TypeReportFactory;
import org.labkey.api.util.Pair;

import java.util.List;

/**
 * User: brittp
 * Date: Jun 25, 2009
 * Time: 10:57:36 AM
 */
public abstract class BaseRequestReportFactory extends TypeReportFactory
{
    private boolean _completedRequestsOnly = false;

    public boolean isCompletedRequestsOnly()
    {
        return _completedRequestsOnly;
    }

    public void setCompletedRequestsOnly(boolean completedRequestsOnly)
    {
        _completedRequestsOnly = completedRequestsOnly;
    }

    public List<Pair<String, String>> getAdditionalFormInputHtml()
    {
        List<Pair<String, String>> superInputs = super.getAdditionalFormInputHtml();

        StringBuilder builder = new StringBuilder();
        builder.append("<select name=\"completedRequestsOnly\">\n");
        builder.append("<option value=\"false\">Include in-process requests</option>\n");
        builder.append("<option value=\"true\"").append(_completedRequestsOnly ? " SELECTED" : "");
        builder.append(">Completed requests only</option>\n");
        builder.append("</select>");
        superInputs.add(new Pair<>("Request status", builder.toString()));
        return superInputs;
    }
}
