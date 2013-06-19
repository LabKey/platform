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

import org.labkey.api.gwt.client.util.StringUtils;
import com.google.gwt.user.client.rpc.IsSerializable;

/**
 * User: Mark Igra
 * Date: Dec 17, 2006
 * Time: 11:20:36 PM
 */
public abstract class VaccineComponent implements IsSerializable
{
    private String name;
    private String dose;

    public String toString()
    {
        return StringUtils.trimToEmpty(name) + "(" + StringUtils.trimToEmpty(dose) + ")";
    }

    public boolean equals(Object o)
    {
        if (null == o)
            return false;

        VaccineComponent vc = (VaccineComponent) o;
        return StringUtils.equals(name, vc.name) && StringUtils.equals(dose, vc.dose);
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getDose()
    {
        return dose;
    }

    public void setDose(String dose)
    {
        this.dose = dose;
    }
}
