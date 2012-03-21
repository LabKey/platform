/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.api.view;

import org.springframework.validation.BindException;

/**
 * Simple view that just shows all of the Spring errors
 * User: jeckels
 * Date: Mar 5, 2012
 */
public class SpringErrorView extends JspView<Object>
{
    public SpringErrorView(BindException errors)
    {
        super("/org/labkey/api/view/springError.jsp", null, errors);
    }
}
