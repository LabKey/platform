/*
 * Copyright (c) 2009-2015 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.view.UnauthorizedException;
import org.springframework.beans.PropertyValues;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;

/**
 * User: matthewb
 * Date: Sep 28, 2009
 * Time: 4:09:42 PM
 *
 * This is a hybrid Api/Form action.
 *  GET is like SimpleViewForm
 *  POST is like ExtFormAction
 *
 *  Do we need ExtFormAction as well?
 */
public abstract class FormApiAction<FORM> extends ExtFormAction<FORM> implements NavTrailAction
{
    protected boolean _print = false;

    protected FormApiAction()
    {
    }

    protected FormApiAction(Class<? extends FORM> formClass)
    {
        super(formClass);
    }

    @Override
    public void checkPermissions() throws UnauthorizedException
    {
        HttpServletRequest req = getViewContext().getRequest();
        setUnauthorizedType("GET".equals(req.getMethod()) ? UnauthorizedException.Type.sendBasicAuth : UnauthorizedException.Type.redirectToLogin);
        super.checkPermissions();
    }

    @Override
    protected ModelAndView handleGet() throws Exception
    {
        FORM form = null;
        BindException errors = null;
        if (null == getCommandClass())
            errors = new NullSafeBindException(new Object(), "command");
        else
            errors = bindParameters(getPropertyValues());

        form = (FORM)errors.getTarget();
        validate(form, errors);

        ModelAndView v;

        if (null != StringUtils.trimToNull((String) getProperty("_print")) ||
            null != StringUtils.trimToNull((String) getProperty("_print.x")))
            v = getPrintView(form, errors);
        else
            v = getView(form, errors);
        return v;
    }

    public abstract ModelAndView getView(FORM form, BindException errors) throws Exception;

    public ModelAndView getPrintView(FORM form, BindException errors) throws Exception
    {
        _print = true;
        return getView(form, errors);
    }

    public BindException bindParameters(PropertyValues pvs) throws Exception
    {
        return SimpleViewAction.defaultBindParameters(getCommand(), getCommandName(), pvs);
    }
}
