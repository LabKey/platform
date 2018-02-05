/*
 * Copyright (c) 2007-2016 LabKey Corporation
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

package org.labkey.api.security;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.util.CSRFUtil;
import org.labkey.api.view.ViewContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Tags an action as implementing protection against cross-site request forgery attempts by including a hidden
 * form parameter that matches the value stored in a session attribute on the server. This should be added to the form
 * by embedding the <labkey:csrf/> taglib in the JSP that renders the page.
 *
 * User: matthewb
 * Date: May 11, 2010
 * Time: 11:35:28 AM
 */
public @Retention(java.lang.annotation.RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
@interface CSRF
{
    enum Method
    {
        NONE()
        {
            public boolean check(String method)
            {
                return false;
            }
        },

        POST()
        {
            public boolean check(String method)
            {
                return StringUtils.equalsIgnoreCase("POST",method);
            }
        },

        ALL()
        {
            public boolean check(String method)
            {
                return true;
            }
        };

        abstract public boolean check(String method);

        public void validate(ViewContext context)
        {
            String method = context.getRequest().getMethod();
            if (check(method))
                CSRFUtil.validate(context);
        }
    }

    Method value() default Method.POST;
}