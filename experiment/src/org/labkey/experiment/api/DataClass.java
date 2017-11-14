/*
 * Copyright (c) 2015-2017 LabKey Corporation
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
package org.labkey.experiment.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Bean class for rows in the exp.dataclass table.
 * User: kevink
 * Date: 9/21/15
 */
public class DataClass extends IdentifiableEntity implements Comparable<DataClass>
{
    private String _description;
    private String _nameExpression;
    private Integer _materialSourceId;

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public String getNameExpression()
    {
        return _nameExpression;
    }

    public void setNameExpression(String nameExpression)
    {
        _nameExpression = nameExpression;
    }

    @Nullable
    public Integer getMaterialSourceId()
    {
        return _materialSourceId;
    }

    public void setMaterialSourceId(@Nullable Integer materialSourceId)
    {
        _materialSourceId = materialSourceId;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof DataClass)) return false;
        DataClass dataClass = (DataClass) o;
        return Objects.equals(this.getLSID(), dataClass.getLSID());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getLSID());
    }

    @Override
    public int compareTo(@NotNull DataClass o)
    {
        return getName().compareToIgnoreCase(o.getName());
    }

}
