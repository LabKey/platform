/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.util.UnexpectedException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.validation.BindException;
import org.labkey.api.action.BaseViewAction;

import java.lang.reflect.Constructor;

/**
 * Serves up a static .html file that's part of a module's ./resources/views as a webpart (by virtue of having
 * a .webpart.xml metadata file.
 *
 * User: matthewb
 * Date: Oct 16, 2008
 *
 * The tricky part is choosing the constructor, we try the following in order
 *
 * if null != _formClass
 *      view(ViewContext, FORM, BindException)
 *      view(ViewContext, FORM)
 * then
 *      view(ViewContext partentContext)
 *
 *  if _formClass != null, the bind and set
 */
public class SimpleWebPartFactory extends BaseWebPartFactory
{
    final Class<? extends ModelAndView> _viewClass;
    Class _formClass = null;
    Constructor<? extends ModelAndView> _cons = null;


    public SimpleWebPartFactory(String name, Class<? extends ModelAndView> viewClass)
    {
        this(name, WebPartFactory.LOCATION_BODY, viewClass, null);
    }


    public SimpleWebPartFactory(String name, Class<? extends ModelAndView> viewClass, Class formClass)
    {
        this(name, WebPartFactory.LOCATION_BODY, viewClass, formClass);
    }

    public SimpleWebPartFactory(String name, String defaultLocation, Class<? extends ModelAndView> viewClass, Class formClass)
    {
        super(name, defaultLocation);
        _viewClass = viewClass;
        _formClass = formClass;

        // pick the best constructor (could also do this with setters,
        // but most of the existing views already follow one of these patterns
        Constructor<? extends ModelAndView> c = null;

        if (null != _formClass)
        {
            c = getConstructor( ViewContext.class, _formClass, BindException.class);
            if (c == null)
                c = getConstructor(ViewContext.class, _formClass);
        }
        if (c == null)
            c = getConstructor(ViewContext.class, Portal.WebPart.class);
        if (c == null)
            c = getConstructor(ViewContext.class);
        if (c == null)
            c = getConstructor();
        _cons = c;

        if (_cons == null)
            throw new IllegalStateException("Could not find constructor with known signature");
    }

    
    Constructor<? extends ModelAndView> getConstructor(Class ... parameterTypes)
    {
        try
        {
            return _viewClass.getConstructor(parameterTypes);
        }
        catch (Exception x)
        {
            return null;
        }
    }


    ModelAndView newInstance(ViewContext portalCtx, Portal.WebPart wp, Object form, BindException errors)
    {
        try
        {
            Class[] parameterTypes = _cons.getParameterTypes();
            switch (parameterTypes.length)
            {
                case 3:
                    return _cons.newInstance(portalCtx, form, errors);
                case 2:
                    if (Portal.WebPart.class.isAssignableFrom(parameterTypes[1]))
                        return _cons.newInstance(portalCtx, wp);
                    else
                        return _cons.newInstance(portalCtx, form);
                case 1:
                    return _cons.newInstance(portalCtx);
                case 0:
                    return _cons.newInstance();
                default:
                    throw new IllegalStateException("unexpected server exception");
            }
        }
        catch (Exception x)
        {
            if (x instanceof RuntimeException)
                throw (RuntimeException)x;
            else
                 throw new RuntimeException(x);
        }
    }

    
    public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
    {
        Object form = null;
        BindException errors = null;

        if (null != _formClass)
        {
            try
            {
                form = _formClass.newInstance();
            }
            catch (InstantiationException | IllegalAccessException e)
            {
                throw new UnexpectedException(e);
            }
            errors = BaseViewAction.simpleBindParameters(form, "command", webPart.getPropertyValues());
        }

        ModelAndView view = newInstance(portalCtx, webPart, form, errors);

        if (view instanceof HttpView && _formClass != null)
            ((HttpView)view).setModelBean(form);
        if (view instanceof WebPartView)
            return (WebPartView)view;
        VBox v = new VBox(view);
        v.setTitle(getName());
        return v;
    }
}
