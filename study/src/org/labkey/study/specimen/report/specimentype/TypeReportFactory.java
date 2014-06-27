/*
 * Copyright (c) 2008-2014 LabKey Corporation
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
package org.labkey.study.specimen.report.specimentype;

import org.labkey.study.specimen.report.SpecimenVisitReportParameters;
import org.labkey.study.SpecimenManager;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;

import java.util.List;
import java.util.ArrayList;

/**
 * User: brittp
 * Created: Feb 1, 2008 4:53:25 PM
 */
public abstract class TypeReportFactory extends SpecimenVisitReportParameters
{
    public List<Pair<String, String>> getAdditionalFormInputHtml()
    {
        List<Pair<String, String>> inputs = new ArrayList<>();
        inputs.addAll(super.getAdditionalFormInputHtml());

        StringBuilder builder = new StringBuilder();
        builder.append("<select name=\"").append(PARAMS.typeLevel.name()).append("\">");
        for (SpecimenManager.SpecimenTypeLevel level : SpecimenManager.SpecimenTypeLevel.values())
        {
            builder.append("<option value=\"").append(PageFlowUtil.filter(level.name())).append("\"");
            if (getTypeLevelEnum() == level)
                builder.append(" SELECTED");
            builder.append(">");
            builder.append("Show results by: ");
            builder.append(PageFlowUtil.filter(level.getLabel())).append("</option>");
        }
        builder.append("</select>");
        inputs.add(new Pair<>("Type breakdown", builder.toString()));
        return inputs;
    }
}
