/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
package org.labkey.specimen.actions;

import org.labkey.api.specimen.location.LocationImpl;
import org.labkey.api.view.ActionURL;

public class SelectSpecimenProviderBean
{
    private final HiddenFormInputGenerator _sourceForm;
    private final LocationImpl[] _possibleLocations;
    private final ActionURL _formTarget;

    public SelectSpecimenProviderBean(HiddenFormInputGenerator sourceForm, LocationImpl[] possibleLocations, ActionURL formTarget)
    {
        _sourceForm = sourceForm;
        _possibleLocations = possibleLocations;
        _formTarget = formTarget;
    }

    public LocationImpl[] getPossibleLocations()
    {
        return _possibleLocations;
    }

    public ActionURL getFormTarget()
    {
        return _formTarget;
    }

    public HiddenFormInputGenerator getSourceForm()
    {
        return _sourceForm;
    }
}
