/*
 * Copyright (c) 2010-2015 LabKey Corporation
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
package org.labkey.study.model;

import org.labkey.api.exp.property.Domain;
import org.labkey.api.security.User;
import org.labkey.api.study.TimepointType;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * User: matthewb
 * Date: Oct 8, 2010
 * Time: 12:38:59 PM
 */
public class ContinuousDatasetDomainKind extends DatasetDomainKind
{
    @Override
    public String getKindName()
    {
        return "StudyDatasetVisit";
    }

    @Override
    public Priority getPriority(String domainURI)
    {
        DatasetDefinition def  = getDatasetDefinition(domainURI);
        if (null!=def && def.getStudy().getTimepointType() == TimepointType.CONTINUOUS)
        {
            return Priority.MEDIUM;
        }
        return null;
    }

    @Override
    public Set<String> getMandatoryPropertyNames(Domain domain)
    {
        return Collections.unmodifiableSet(DatasetDefinition.DEFAULT_ABSOLUTE_DATE_FIELDS);
    }


    @Override
    public Set<String> getReservedPropertyNames(Domain domain, User user)
    {
        HashSet<String> fields = new HashSet<>(getStudySubjectReservedName(domain));
        fields.addAll(DatasetDefinition.DEFAULT_ABSOLUTE_DATE_FIELDS);

        return Collections.unmodifiableSet(fields);
    }
}
