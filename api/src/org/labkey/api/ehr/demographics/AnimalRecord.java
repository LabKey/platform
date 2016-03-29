/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
package org.labkey.api.ehr.demographics;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * User: bimber
 * Date: 7/14/13
 * Time: 11:46 AM
 */
public interface AnimalRecord
{
    public AnimalRecord createCopy();

    public String getId();

    public Container getContainer();

    public Date getCreated();

    @NotNull
    public Map<String, Object> getProps();

    public String getGender();

    public String getGenderMeaning();

    public String getOrigGender();

    public String getAgeInYearsAndDays();

    public String getSpecies();

    public String getCalculatedStatus();

    public Date getBirth();

    public boolean hasBirthRecord();

    public Date getDeath();

    public String getGeographicOrigin();

    public String getDemographicsObjectId();

    public List<Map<String, Object>> getActiveAssignments();

    public List<Map<String, Object>> getActiveTreatments();

    public List<Map<String, Object>> getActiveHousing();

    public String getCurrentRoom();

    public String getCurrentCage();

    public List<Map<String, Object>> getActiveFlags();

    public List<Map<String, Object>> getActiveProblem();

    public List<Map<String, Object>> getActiveCases();

    public List<Map<String, Object>> getParents();

    public List<Map<String, Object>> getWeights();

    public Double getMostRecentWeight();

    public Date getMostRecentWeightDate();

    public Date getMostRecentDeparture();

    public Date getMostRecentArrival();

    public Integer getDaysSinceWeight();
}