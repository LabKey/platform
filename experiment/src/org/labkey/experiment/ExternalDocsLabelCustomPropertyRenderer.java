/*
 * Copyright (c) 2006-2016 LabKey Corporation
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

package org.labkey.experiment;

import org.labkey.api.data.Container;
import org.labkey.api.exp.ObjectProperty;

import java.util.List;

/**
 * User: jeckels
 * Date: Jan 23, 2006
 */
public class ExternalDocsLabelCustomPropertyRenderer implements CustomPropertyRenderer
{
    public static final String URI = "terms.fhcrc.org#ExternalDocumentation.label";

    public String getDescription(ObjectProperty prop, List<ObjectProperty> siblingProperties)
    {
        throw new UnsupportedOperationException();
    }

    public String getValue(ObjectProperty prop, List<ObjectProperty> siblingProperties, Container c)
    {
        throw new UnsupportedOperationException();
    }

    public boolean shouldRender(ObjectProperty prop, List<ObjectProperty> siblingProperties)
    {
        return false;
    }
}
