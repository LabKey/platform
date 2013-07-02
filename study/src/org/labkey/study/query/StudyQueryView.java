/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
package org.labkey.study.query;

import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.study.CohortFilter;
import org.springframework.validation.BindException;

/**
 * User: matthewb
 * Date: 2012-12-10
 * Time: 2:03 PM
 *
 * Base class for DatasetQueryView and SpecimenQueryView
 */
public class StudyQueryView extends QueryView
{
    protected Study _study;

    public StudyQueryView(UserSchema schema, QuerySettings settings, BindException errors)
    {
        super(schema, settings, errors);
        _study = StudyService.get().getStudy(getContainer());
    }

    public CohortFilter getCohortFilter()
    {
        return null;
    }
}
