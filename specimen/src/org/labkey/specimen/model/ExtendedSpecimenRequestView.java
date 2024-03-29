/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.specimen.model;

import org.labkey.api.data.Entity;

/**
 * User: Nick Arnold
 * Date: 3/13/13
 */
public class ExtendedSpecimenRequestView extends Entity
{
    private boolean _active;
    private String _body;

    public static ExtendedSpecimenRequestView createView(String body)
    {
        ExtendedSpecimenRequestView view = new ExtendedSpecimenRequestView();
        view.setBody(body);
        view.setActive(true);
        return view;
    }

    public String getBody()
    {
        return _body;
    }

    public void setBody(String body)
    {
        _body = body;
    }

    public boolean isActive()
    {
        return _active;
    }

    public void setActive(boolean active)
    {
        _active = active;
    }
}
