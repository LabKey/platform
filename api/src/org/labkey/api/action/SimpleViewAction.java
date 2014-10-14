/*
 * Copyright (c) 2007-2014 LabKey Corporation
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

import org.labkey.api.miniprofiler.MiniProfiler;
import org.labkey.api.miniprofiler.Timing;
import org.labkey.api.view.WebPartView;
import org.springframework.beans.PropertyValues;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

/**
 * User: matthewb
 * Date: May 16, 2007
 * Time: 1:48:01 PM
 */
public abstract class SimpleViewAction<FORM> extends BaseViewAction<FORM> implements NavTrailAction
{
    protected SimpleViewAction()
    {
    }

    protected SimpleViewAction(Class<? extends FORM> formClass)
    {
        super(formClass);
    }

    public ModelAndView handleRequest() throws Exception
    {
        BindException errors;
        try (Timing t = MiniProfiler.step("bind"))
        {
            if (null == getCommandClass())
                errors = new NullSafeBindException(new Object(), "command");
            else
                errors = bindParameters(getPropertyValues());
        }

        FORM form;
        try (Timing t = MiniProfiler.step("validate"))
        {
            form = (FORM) errors.getTarget();
            validate(form, errors);
        }

        ModelAndView v;

        try (Timing t = MiniProfiler.step("createView"))
        {
            if (_print)
                v = getPrintView(form, errors);
            else
                v = getView(form, errors);
        }

        return v;
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
        ModelAndView print = getView(form, errors);
        if (print instanceof WebPartView && ((WebPartView)print).getFrame() == WebPartView.FrameType.PORTAL)
            ((WebPartView)print).setFrame(WebPartView.FrameType.TITLE);
        return print;
    }

    public void validate(Object target, Errors errors)
    {
    }
}
