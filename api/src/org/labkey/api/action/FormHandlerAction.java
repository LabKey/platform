/*
 * Copyright (c) 2007-2017 LabKey Corporation
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
import org.springframework.validation.BindException;
import org.labkey.api.view.NavTree;

/**
 * User: matthewb
 * Date: Jun 21, 2007
 * Time: 11:14:21 AM
 *
 * Like FormViewAction but does not show a form.  Only handles post and redirects.
 */
public abstract class FormHandlerAction<FORM> extends FormViewAction<FORM>
{
    public final ModelAndView getView(FORM form, boolean reshow, BindException errors) throws Exception
    {
        if (null == errors)
            errors = new NullSafeBindException(new Object(), "FakeObject");

        // Complain except for showing errors
        if (!errors.hasErrors())
            errors.addError(new LabKeyError("This action does not support HTTP GET"));

        // TODO: use dialog template in this case?

        return new SimpleErrorView(errors);
    }

    public final NavTree appendNavTrail(NavTree root)
    {
        return root;
    }
}
