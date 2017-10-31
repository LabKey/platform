/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

package org.labkey.api.action;

import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.ObjectError;

/**
 * Simple Spring-style error to show a specific error message originated from Java code, and not going to a resources
 * file to look up a localized version.
 * User: jeckels
 * Date: May 2, 2008
 */
public class LabKeyError extends ObjectError
{
    public LabKeyError(Throwable t)
    {
        this(t.getMessage() == null ? t.getClass().getName() : t.getMessage());
    }

    public LabKeyError(String message)
    {
        super("main", new String[] { "Error" }, new Object[] { message }, message);
    }

    public String renderToHTML(ViewContext context)
    {
        return PageFlowUtil.filter(context.getMessage(this), true);
    }
}
