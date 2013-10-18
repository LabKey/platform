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
package org.labkey.api.admin;

import org.labkey.api.data.Container;

/**
 * User: klum
 * Date: 10/10/13
 */
public class ImportOptions
{
    private boolean _skipQueryValidation;
    private String _containerId;

    public ImportOptions(String containerId)
    {
        _containerId = containerId;
    }

    public boolean isSkipQueryValidation()
    {
        return _skipQueryValidation;
    }

    public void setSkipQueryValidation(boolean skipQueryValidation)
    {
        _skipQueryValidation = skipQueryValidation;
    }

    public String getContainerId()
    {
        return _containerId;
    }

    public void setContainerId(String containerId)
    {
        _containerId = containerId;
    }
}
