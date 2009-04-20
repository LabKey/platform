/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

package org.labkey.api.query;

import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;


public class DomainForeignKey extends PropertyForeignKey
{
    public DomainForeignKey(Domain domain, QuerySchema schema)
    {
        super(convertProperties(domain), schema);
    }

    private static PropertyDescriptor[] convertProperties(Domain domain)
    {
        DomainProperty[] properties = domain.getProperties();
        PropertyDescriptor[] result = new PropertyDescriptor[properties.length];
        for (int i = 0; i < properties.length; i++)
        {
            result[i] = properties[i].getPropertyDescriptor();
        }
        return result;
    }
}
