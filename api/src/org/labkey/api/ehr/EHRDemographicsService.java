/*
 * Copyright (c) 2012-2014 LabKey Corporation
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
package org.labkey.api.ehr;

import org.labkey.api.data.Container;
import org.labkey.api.ehr.demographics.AnimalRecord;

import java.util.Collection;
import java.util.List;

/**
 * User: bimber
 * Date: 9/14/12
 * Time: 4:44 PM
 */
abstract public class EHRDemographicsService
{
    static EHRDemographicsService _instance;

    public static EHRDemographicsService get()
    {
        return _instance;
    }

    static public void setInstance(EHRDemographicsService instance)
    {
        _instance = instance;
    }

    abstract public void reportDataChange(final Container c, final String schema, final String query, final List<String> ids);

    abstract public AnimalRecord getAnimal(Container c, String id);

    abstract public List<AnimalRecord> getAnimals(Container c, Collection<String> ids);
}
