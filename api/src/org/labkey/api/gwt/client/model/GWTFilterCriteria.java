/*
 * Copyright (c) 2024-2026 LabKey Corporation
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
package org.labkey.api.gwt.client.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Setter
@Getter
@EqualsAndHashCode
public class GWTFilterCriteria implements Serializable
{
    private String name;
    private String op;
    private Integer propertyId;
    private Integer referencePropertyId;
    private Object value;

    public GWTFilterCriteria()
    {
    }

    public GWTFilterCriteria(GWTFilterCriteria fc)
    {
        setName(fc.getName());
        setOp(fc.getOp());
        setPropertyId(fc.getPropertyId());
        setReferencePropertyId(fc.getReferencePropertyId());
        setValue(fc.getValue());
    }
}

