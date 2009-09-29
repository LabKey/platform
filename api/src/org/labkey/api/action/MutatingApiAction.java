/*
 * Copyright (c) 2009 LabKey Corporation
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

import org.springframework.web.servlet.ModelAndView;

/*
* User: Dave
* Date: May 25, 2009
* Time: 11:20:59 AM
*/

/**
 * Base class for all API actions that change the server's state in some way. This class enforces
 * that clients use the HTTP POST method.
 * @param <FORM> The form class
 */
public abstract class MutatingApiAction<FORM> extends ApiAction<FORM>
{
    public MutatingApiAction()
    {
    }

    public MutatingApiAction(Class<? extends FORM> formClass)
    {
        super(formClass);
    }

    @Override
    protected ModelAndView handleGet() throws Exception
    {
        throw new IllegalArgumentException("You must use the POST method when calling this action!");
    }
}