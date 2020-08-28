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
package org.labkey.study.specimen.report.specimentype;

import org.labkey.api.study.SpecimenService;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.Pair;
import org.labkey.api.util.element.Option;
import org.labkey.api.util.element.Select;
import org.labkey.study.SpecimenManager;
import org.labkey.study.specimen.report.SpecimenVisitReportParameters;

import java.util.ArrayList;
import java.util.List;

import static org.labkey.api.util.HtmlString.unsafe;

/**
 * User: brittp
 * Created: Feb 1, 2008
 */
public abstract class TypeReportFactory extends SpecimenVisitReportParameters
{
    @Override
    public List<Pair<String, HtmlString>> getAdditionalFormInputHtml()
    {
        List<Pair<String, HtmlString>> inputs = new ArrayList<>(super.getAdditionalFormInputHtml());

        if (!SpecimenService.get().getRequestCustomizer().omitTypeGroupingsWhenReporting())
        {
            Select.SelectBuilder builder = new Select.SelectBuilder();
            builder.name(PARAMS.typeLevel.name());

            for (SpecimenManager.SpecimenTypeLevel level : SpecimenManager.SpecimenTypeLevel.values())
            {
                builder.addOption(new Option.OptionBuilder()
                        .value(level.name())
                        .label("Show results by: " + level.getLabel())
                        .selected(getTypeLevelEnum() == level)
                        .build()
                );
            }
            inputs.add(new Pair<>("Type breakdown", unsafe(builder.toString())));
        }

        return inputs;
    }
}
