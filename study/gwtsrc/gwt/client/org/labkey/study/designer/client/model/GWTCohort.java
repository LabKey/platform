/*
 * Copyright (c) 2010-2013 LabKey Corporation
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

package gwt.client.org.labkey.study.designer.client.model;

import com.google.gwt.user.client.rpc.IsSerializable;

public class GWTCohort implements IsSerializable
{
    private Integer cohortId;
    private String name;
    private String description;
    private int count;

    public GWTCohort()
    {
    }

    public GWTCohort(String name, String description, int count, Integer cohortId)
    {
        this.cohortId = cohortId;
        this.name = name;
        this.description = description;
        this.count = count;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public int getCount()
    {
        return count;
    }

    public void setCount(int count)
    {
        this.count = count;
    }

    public Integer getCohortId()
    {
        return cohortId;
    }

    public void setCohortId(Integer cohortId)
    {
        this.cohortId = cohortId;
    }
}
