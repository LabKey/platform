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
package org.labkey.study.specimen.report.participant;

import org.labkey.study.model.VisitImpl;
import org.labkey.study.specimen.report.SpecimenVisitReportParameters;
import org.labkey.api.data.SimpleFilter;

import java.util.List;

/**
 * User: brittp
 * Created: Feb 4, 2008 3:48:04 PM
 */
public class ParticipantSiteReport extends ParticipantVisitReport
{
    public ParticipantSiteReport(String title, List<VisitImpl> visits, SimpleFilter filter, SpecimenVisitReportParameters parameters)
    {
        super(title, visits, filter, parameters);
    }

    protected SimpleFilter replaceFilterParameterName(SimpleFilter filter, String oldKey, String newKey)
    {
        // this report adds a strange filter to 'ptid' that we want to just drop when it comes
        // time to generate our link URLs:
        if (!"ptid".equals(oldKey))
            return super.replaceFilterParameterName(filter, oldKey, newKey);
        else
            return filter;
    }
}
