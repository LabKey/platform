/*
 * Copyright (c) 2010-2016 LabKey Corporation
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

import org.labkey.api.util.StringExpression;

/** marker interface to indicate that a DisplayColumn is for the edit link/indicator, not a data column */
public interface UpdateColumn
{
    /** Renders the edit link for a row in a grid */
    class Impl extends UrlColumn implements UpdateColumn
    {
        public Impl(StringExpression urlExpression)
        {
            super(urlExpression, "edit");
            setName("Update");
            setGridHeaderClass("");
            setWidth("0");
            addDisplayClass("labkey-update");
        }
    }
}

