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

import org.labkey.api.miniprofiler.MiniProfiler;
import org.labkey.api.miniprofiler.Timing;
import org.labkey.api.view.BadRequestException;
import org.labkey.api.view.ViewServlet;
import org.labkey.api.view.WebPartView;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

/**
 * Base class for actions that want to display a single page in response to a GET, including
 * form parameters. Not intended for actions that may separately receive a POST.
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
        try (Timing ignored = MiniProfiler.step("bind"))
        {
            if (null == getCommandClass())
                errors = new NullSafeBindException(new Object(), "command");
            else
            {
                // GET parameters have already been validated in ViewServlet
                PropertyValues pvs = getPropertyValues();
                if (!"GET".equalsIgnoreCase(getViewContext().getRequest().getMethod()))
                    validateUnicodePropertyValues(pvs);
                errors = bindParameters(pvs);
            }
        }

        FORM form;
        try (Timing ignored = MiniProfiler.step("validate"))
        {
            form = (FORM) errors.getTarget();
            validate(form, errors);
        }

        ModelAndView v;

        try (Timing ignored = MiniProfiler.step("createView"))
        {
            if (_print)
                v = getPrintView(form, errors);
            else
                v = getView(form, errors);
        }

        return v;
    }


    private static void validateUnicodePropertyValues(PropertyValues pvs) throws ServletException
    {
        for (PropertyValue pv : pvs.getPropertyValues())
        {
            String key = pv.getName();
            Object value = pv.getValue();
            if (!ViewServlet.validChars(key))
                throw new BadRequestException(HttpServletResponse.SC_BAD_REQUEST, "Unrecognized unicode character in request", null);
            if (null == value)
            {
                continue;
            }
            else if (value instanceof CharSequence)
            {
                if (!ViewServlet.validChars((CharSequence) value))
                    throw new BadRequestException(HttpServletResponse.SC_BAD_REQUEST, "Unrecognized unicode character in request", null);
            }
            else if (value.getClass().isArray())
            {
                Object[] array = (Object[]) value;
                for (Object item : array)
                {
                    if (item instanceof CharSequence)
                        if (!ViewServlet.validChars((CharSequence)item))
                            throw new BadRequestException(HttpServletResponse.SC_BAD_REQUEST, "Unrecognized unicode character in request", null);
                }
            }
        }
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
