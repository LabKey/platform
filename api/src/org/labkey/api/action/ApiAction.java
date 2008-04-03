/*
 * Copyright (c) 2007 LabKey Software Foundation
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

import org.apache.commons.beanutils.BeanUtils;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.io.BufferedReader;
import java.util.Iterator;

/**
 * Base class for API actions.
 *
 * Created by IntelliJ IDEA.
 * User: Dave
 * Date: Feb 8, 2008
 * Time: 1:14:43 PM
 */
public abstract class ApiAction<FORM> extends BaseViewAction<FORM>
{
    private ApiResponseWriter.Format _reqFormat = null;
    private ApiResponseWriter.Format _respFormat = ApiResponseWriter.Format.JSON;

    public ApiAction()
    {
    }

    public ApiAction(Class<? extends FORM> formClass)
    {
        super(formClass);
    }

    protected String getCommandClassMethodName()
    {
        return "execute";
    }

    public ModelAndView handleRequest() throws Exception
    {
        FORM form = null;
        BindException errors = null;

        try
        {
            String contentType = getViewContext().getRequest().getContentType();
            if(null != contentType && contentType.contains(ApiJsonWriter.CONTENT_TYPE_JSON))
            {
                _reqFormat = ApiResponseWriter.Format.JSON;
                JSONObject jsonObj = getJsonObject();
                form = getCommand();
                errors = populateForm(jsonObj, form);
            }
            else
            {
                if (null != getCommandClass())
                {
                    errors = defaultBindParameters(getCommand(), getPropertyValues());
                    form = (FORM)errors.getTarget();
                    validate(form, errors);
                }
            }

            ApiResponse response = execute(form, errors);
            if(null != response)
                createResponseWriter().write(response);
        }
        catch (Exception e)
        {
            Logger.getLogger(ApiAction.class).warn("ApiAction exception: ", e);
            createResponseWriter().write(e);
        }

        return null;
    } //handleRequest()

    protected JSONObject getJsonObject() throws Exception
    {
        //read the JSON into a buffer
        //unfortunately the json.org classes can't read directly from a stream!
        char[] buf = new char[2048];
        int chars;
        StringBuffer json = new StringBuffer();
        BufferedReader reader = getViewContext().getRequest().getReader();

        while((chars = reader.read(buf)) > 0)
            json.append(buf, 0, chars);

        //deserialize the JSON
        return new JSONObject(json.toString());
    }

    protected BindException populateForm(JSONObject jsonObj, FORM form) throws Exception
    {
        if(form instanceof ApiJsonForm)
        {
            ((ApiJsonForm)form).setJsonObject(jsonObj);
            return null;
        }
        else
            return populateForm(jsonObj, form, null);
    }

    protected BindException populateForm(JSONObject jsonObj, FORM form, String parentProperty) throws Exception
    {
        Iterator keys = jsonObj.keys();
        while(keys.hasNext())
        {
            String key = (String)keys.next();
            String beanKey = null == parentProperty ? key : parentProperty + "." + key;
            Object value = jsonObj.get(key);

            if(value instanceof JSONArray)
            {
                JSONArray array = (JSONArray)value;
                for(int idx = 0; idx < array.length(); ++idx)
                {
                    String entryKey = beanKey + "[" + String.valueOf(idx) + "]";
                    Object entry = array.get(idx);
                    if(entry instanceof JSONObject)
                        populateForm((JSONObject)entry, form, entryKey);
                    else
                        BeanUtils.setProperty(form, entryKey, entry);
                }
            }
            else if(value instanceof JSONObject)
            {
                if(!value.equals(JSONObject.NULL))
                    populateForm((JSONObject)value, form, beanKey);
            }
            else
                BeanUtils.setProperty(form, beanKey, value);

        }
        return null;
    }

    public void validate(Object o, Errors errors)
    {
    }

    public ApiResponseWriter createResponseWriter()
    {
        //for now, always return a JSON writer.
        //in the future, look at the posted content-type, or a query string param
        //to determine which format to create
        return new ApiJsonWriter(getViewContext().getResponse());
    }

    public ApiResponseWriter.Format getResponseFormat()
    {
        return _respFormat;
    }

    public ApiResponseWriter.Format getRequestFormat()
    {
        return _reqFormat;
    }

    public abstract ApiResponse execute(FORM form, BindException errors) throws Exception;
}
