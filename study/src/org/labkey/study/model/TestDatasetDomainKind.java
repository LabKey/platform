/*
 * Copyright (c) 2010-2017 LabKey Corporation
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

import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * User: newton
 * Date: Oct 11, 2010
 * Time: 3:45:55 PM
 */
public class TestDatasetDomainKind extends DatasetDomainKind
{

    public static String KIND_NAME = "TestDatasetDomainKind";

    @Override
    public String getKindName()
    {
        return KIND_NAME;
    }

    @Override
    public Priority getPriority(String domainURI)
    {
        return domainURI.contains(KIND_NAME) ? Priority.MEDIUM : null;
    }

    @Override
    public Set<String> getReservedPropertyNames(Domain domain)
    {
        return Collections.emptySet();
    }

    @Override
    public Set<PropertyStorageSpec.Index> getPropertyIndices(Domain domain)
    {
        Set<PropertyStorageSpec.Index> ret = new HashSet<>(super.getPropertyIndices(domain));
        Study study = StudyManager.getInstance().getStudy(domain.getContainer());
        StudyService studyService = StudyService.get();

        if(null != study)
        {
            // Older datasets may not have participantsequencenum
            if (null == domain.getStorageTableName() || (null != studyService
                    && null != studyService.getDatasetSchema().getTable(domain.getStorageTableName()).getColumn("participantsequencenum")))
            {
                if (!study.isDataspaceStudy())
                {
                    ret.add(new PropertyStorageSpec.Index(false, PARTICIPANTSEQUENCENUM));
                }
                else
                {
                    ret.add(new PropertyStorageSpec.Index(false, CONTAINER, PARTICIPANTSEQUENCENUM));
                }
            }
        }

        return ret;
    }
}
