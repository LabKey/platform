/*
 * Copyright (c) 2008-2013 LabKey Corporation
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

import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.PropertyValidationError;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.view.NotFoundException;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;
import java.util.List;

/**
 * Writes various objects returned by API actions in JSON format.
 *
 * User: Dave
 * Date: Feb 8, 2008
 * Time: 1:50:11 PM
 */
public class ApiJsonWriter extends ApiResponseWriter
{

    //per http://www.iana.org/assignments/media-types/application/
    public static final String CONTENT_TYPE_JSON = "application/json";

    public ApiJsonWriter(Writer out) throws IOException
    {
        super(out);
    }


    public ApiJsonWriter(HttpServletResponse response) throws IOException
    {
        this(response, null);
    }


    public ApiJsonWriter(HttpServletResponse response, String contentTypeOverride) throws IOException
    {
        super(response);
        response.setContentType(null == contentTypeOverride ? CONTENT_TYPE_JSON : contentTypeOverride);
        response.setCharacterEncoding("utf-8");
    }


    protected void writeJsonObj(JSONObject obj) throws IOException
    {
        //jsonObj.write(getResponse().getWriter()); //use this for compact output
        getWriter().write(obj.toString(4)); //or this for pretty output
    }

    public void startResponse() throws IOException
    {
        assert _streamStack.size() == 0 : "called startResponse() after response was already started!";
        //we always return an object at the top level
        getWriter().write("{");
        _streamStack.push(new StreamState());
    }

    public void endResponse() throws IOException
    {
        assert _streamStack.size() == 1 : "called endResponse without a corresponding startResponse()!";
        getWriter().write("}");
        _streamStack.pop();
    }

    public void startMap(String name) throws IOException
    {
        StreamState state = _streamStack.peek();
        assert(null != state) : "startResponse will start the root-level map!";
        getWriter().write("\n" + JSONObject.quote(name) + ":{");
        _streamStack.push(new StreamState(name, state.getLevel() + 1));
    }

    public void endMap() throws IOException
    {
        getWriter().write("}");
        _streamStack.pop();
    }

    public void writeProperty(String name, Object value) throws IOException
    {
        StreamState state = _streamStack.peek();
        getWriter().write(state.getSeparator() + JSONObject.quote(name) + ":" 
                + JSONObject.valueToString(value, 4, state.getLevel()));
    }

    public void startList(String name) throws IOException
    {
        StreamState state = _streamStack.peek();
        getWriter().write(state.getSeparator() + JSONObject.quote(name) + ":[");
        _streamStack.push(new StreamState(name, state.getLevel() + 1));
    }

    public void endList() throws IOException
    {
        getWriter().write("]");
        _streamStack.pop();
    }

    public void writeListEntry(Object entry) throws IOException
    {
        StreamState state = _streamStack.peek();
        getWriter().write(state.getSeparator() + JSONObject.valueToString(entry, 4, state.getLevel()));
    }
}
