/*
 * Copyright (c) 2009-2017 LabKey Corporation
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

import org.labkey.api.view.ViewContext;

/**
 * An error that has already been HTML encoded and may contain HTML formatting tags.

 * User: Matthew
 * Date: Feb 5, 2009
 */
public class FormattedError extends LabKeyError
{
    public FormattedError(String message)
    {
        super(message);
    }

    /** Don't need to HTML encode */
    @Override
    public String renderToHTML(ViewContext context)
    {
        return context.getMessage(this);
    }
}