/*
 * Copyright (c) 2015-2016 LabKey Corporation
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
package org.labkey.api.data;

import org.apache.commons.lang3.EnumUtils;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

public class Activity implements Serializable
{
    private final Container _container;
    private final String _name;
    private final String _irb;
    private final String _phiStr;
    private final PHI _phi;
    private final String[] _terms;

    public Activity(Container container, String name, String irb, String phiStr, String... terms)
    {
        _container = container;
        _name = name;
        _irb = irb;
        _phiStr = phiStr;
        _terms = terms;

        if (phiStr != null && EnumUtils.isValidEnum(PHI.class, phiStr))
            _phi = PHI.valueOf(phiStr);
        else
            _phi = null;
    }

    public Activity(Container container, String name, String irb, @NotNull PHI phi, String... terms)
    {
        _container = container;
        _name = name;
        _irb = irb;
        _phiStr = phi.name();
        _phi = phi;
        _terms = terms;
    }

    public Container getContainer()
    {
        return _container;
    }

    public String getName()
    {
        return _name;
    }

    public String getIRB()
    {
        return _irb;
    }

    public String getPhiStr()
    {
        return _phiStr;
    }

    public PHI getPHI()
    {
        return _phi;
    }

    public String[] getTerms()
    {
        return _terms;
    }
}
