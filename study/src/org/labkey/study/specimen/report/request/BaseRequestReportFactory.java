/*
 * Copyright (c) 2014-2019 LabKey Corporation
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

import org.apache.xpath.operations.Bool;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.element.Option;
import org.labkey.api.util.element.Select;
import org.labkey.study.specimen.report.specimentype.TypeReportFactory;
import org.labkey.api.util.Pair;

import java.util.List;

import static org.labkey.api.util.HtmlString.unsafe;

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

    @Override
    public List<Pair<String, HtmlString>> getAdditionalFormInputHtml()
    {
        List<Pair<String, HtmlString>> superInputs = super.getAdditionalFormInputHtml();

        Select.SelectBuilder sb = new Select.SelectBuilder();

        sb.addOption(new Option.OptionBuilder()
            .value(Boolean.toString(false))
            .label("Include in-process requests")
            .build()
        );

        sb.addOption(new Option.OptionBuilder()
            .value(Boolean.toString(true))
            .label("Completed requests only")
            .selected(_completedRequestsOnly)
            .build()
        );

        superInputs.add(new Pair<>("Request status", unsafe(sb.toString())));
        return superInputs;
    }
}
