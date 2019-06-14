/*
 * Copyright (c) 2007-2018 LabKey Corporation
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

import org.labkey.api.util.EnumHasHtmlString;

/**
 * User: markigra
 * Date: Oct 31, 2007
 * Time: 12:24:31 PM
 */
public enum TimepointType implements EnumHasHtmlString<TimepointType>
{
    VISIT(true),
    DATE(false),
    CONTINUOUS(false);

    private final boolean _visitBased;

    TimepointType(boolean visitBased)
    {
        _visitBased = visitBased;
    }

    public boolean isVisitBased()
    {
        return _visitBased;
    }
}
