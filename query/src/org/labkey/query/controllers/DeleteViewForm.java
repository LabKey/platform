/*
 * Copyright (c) 2006-2011 LabKey Corporation
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

package org.labkey.query.controllers;

import org.labkey.api.query.QueryForm;

public class DeleteViewForm extends QueryForm
{
    private boolean _complete;

    public DeleteViewForm()
    {
    }

    /**
     * Delete both the saved view and session view, if present.
     * Otherwise just the first view found (session or saved) will be deleted.
     * @return
     */
    public boolean isComplete()
    {
        return _complete;
    }

    public void setComplete(boolean b)
    {
        _complete = b;
    }
}
