/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.labkey.api.view.JspView;
import org.springframework.validation.BindException;

import static org.labkey.api.action.SpringActionController.ERROR_MSG;

/**
 * View that renders an error collection.
 * User: adam
 * Date: Sep 26, 2007
 */
public class SimpleErrorView extends JspView<Boolean>
{
    /** @param includeButtons whether to include Back and Home buttons in the rendered view */
    public SimpleErrorView(BindException errors, boolean includeButtons)
    {
        super("/org/labkey/api/action/simpleErrorView.jsp", includeButtons, errors);
    }

    public SimpleErrorView(BindException errors)
    {
        this(errors, true);
    }

    static SimpleErrorView fromMessage(String message)
    {
        BindException errors = new BindException(new Object(), "form");
        errors.reject(ERROR_MSG, message);
        return new SimpleErrorView(errors, true);
    }
}
