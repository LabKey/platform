/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

import org.labkey.api.view.AjaxCompletion;
import org.labkey.api.view.AjaxCompletionView;
import org.labkey.api.view.AjaxResponse;
import org.springframework.validation.BindException;

import java.util.List;

/**
 * User: adamr
 * Date: September 19, 2007
 * Time: 9:15:37 AM
 */
public abstract class AjaxCompletionAction<FORM> extends AjaxAction<FORM>
{
    public AjaxCompletionAction()
    {
    }

    public AjaxCompletionAction(Class<? extends FORM> commandClass)
    {
        super(commandClass);
    }

    protected String getCommandClassMethodName()
    {
        return "getCompletions";
    }

    public abstract List<AjaxCompletion> getCompletions(FORM form, BindException errors) throws Exception;

    public AjaxResponse getResponse(FORM form, BindException errors) throws Exception
    {
        return new AjaxCompletionView(getCompletions(form, errors));
    }
}
