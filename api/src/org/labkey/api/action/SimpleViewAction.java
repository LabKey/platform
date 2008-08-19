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

import org.springframework.beans.PropertyValues;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;
import org.apache.commons.lang.StringUtils;

/**
 * User: matthewb
 * Date: May 16, 2007
 * Time: 1:48:01 PM
 */
public abstract class SimpleViewAction<FORM> extends BaseViewAction<FORM> implements NavTrailAction
{
    protected boolean _print = false;

    protected SimpleViewAction()
    {
    }

    protected SimpleViewAction(Class<? extends FORM> formClass)
    {
        super(formClass);
    }

    public ModelAndView handleRequest() throws Exception
    {
        FORM form = null;
        BindException errors = null;
        if (null != getCommandClass())
        {
            errors = bindParameters(getPropertyValues());
            form = (FORM)errors.getTarget();
            validate(form, errors);
        }

        if (null != StringUtils.trimToNull((String) getProperty("_print")) ||
            null != StringUtils.trimToNull((String) getProperty("_print.x")))
            return getPrintView(form, errors);
        else
            return getView(form, errors);
    }

    protected String getCommandClassMethodName()
    {
        return "getView";
    }

    public BindException bindParameters(PropertyValues pvs) throws Exception
    {
        return defaultBindParameters(getCommand(), pvs);
    }

    public void validate(FORM form, BindException errors)
    {
    }

    public abstract ModelAndView getView(FORM form, BindException errors) throws Exception;

    public ModelAndView getPrintView(FORM form, BindException errors) throws Exception
    {
        _print = true;
        return getView(form, errors);
    }

    public void validate(Object target, Errors errors)
    {
    }

    public boolean isPrint()
    {
        return _print;
    }
}
