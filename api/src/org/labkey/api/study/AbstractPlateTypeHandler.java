/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
package org.labkey.api.study;

import org.labkey.api.data.Container;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * User: klum
 * Date: Jun 13, 2012
 */
public abstract class AbstractPlateTypeHandler implements PlateTypeHandler
{
    @Override
    public void validate(Container container, User user, PlateTemplate template) throws ValidationException
    {
    }

    @Override
    public Map<String, List<String>> getDefaultGroupsForTypes()
    {
        return Collections.emptyMap();
    }

    @Override
    public boolean showEditorWarningPanel()
    {
        return true;
    }
}
