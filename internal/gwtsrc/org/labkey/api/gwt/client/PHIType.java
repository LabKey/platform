/*
 * Copyright (c) 2017 LabKey Corporation
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
package org.labkey.api.gwt.client;

public enum PHIType
{
    NotPHI("Not PHI"),
    Limited("Limited PHI"),
    PHI("Full PHI"),
    Restricted("Restricted PHI");

    private String _label;

    PHIType(String label)
    {
        _label = label;
    }

    public String getLabel()
    {
        return _label;
    }

    public static PHIType fromString(String value)
    {
        for (PHIType phi : values())
            if (phi.name().equals(value))
                return phi;

        return Restricted;   // default
    }

    public static PHIType fromOrdinal(int value)
    {
        switch (value)
        {
            case 0: return NotPHI;
            case 1: return Limited;
            case 2: return PHI;
            case 3: return Restricted;
        }
        return null;
    }

    public boolean isLevelAllowed(PHIType maxLevelAllowed)
    {
        return ordinal() <= maxLevelAllowed.ordinal();
    }
}
