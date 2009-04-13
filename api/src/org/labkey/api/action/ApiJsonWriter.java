/*
 * Copyright (c) 2008-2009 LabKey Corporation
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
import org.labkey.api.view.NotFoundException;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Stack;

/**
 * Writes various objects returned by API actions in JSON format.
 *
 * Created by IntelliJ IDEA.
 * User: Dave
 * Date: Feb 8, 2008
 * Time: 1:50:11 PM
 */
public class ApiJsonWriter extends ApiResponseWriter
{

    //per http://www.iana.org/assignments/media-types/application/
    public static final String CONTENT_TYPE_JSON = "application/json";

    public ApiJsonWriter(HttpServletResponse response)
    {
        this(response, null);
    }

    public ApiJsonWriter(HttpServletResponse response, String contentTypeOverride)
    {
        super(response);
        response.setContentType(null == contentTypeOverride ? CONTENT_TYPE_JSON : contentTypeOverride);
        response.setCharacterEncoding("utf-8");
    }

    public void write(ApiResponse response) throws IOException
    {
        if(response instanceof ApiStreamResponse)
        {
            try
            {
                ((ApiStreamResponse)response).render(this);
            }
            catch(Exception e)
            {
                //at this point, we can't guarantee a legitimate
                //JSON response, and we need to write the exception
                //back so the client can tell something went wrong
                if(!getResponse().isCommitted())
                    getResponse().reset();
                write(e);
            }
        }
        else
        {
            JSONObject json = new JSONObject(response.getProperties());
            writeJsonObj(json);
        }
    }

    public void write(Throwable e) throws IOException
    {
        if(e instanceof NotFoundException)
            getResponse().setStatus(404);
        else
            getResponse().setStatus(500);

        JSONObject jsonObj = new JSONObject();
        jsonObj.put("exception", e.getMessage());
        jsonObj.put("exceptionClass", e.getClass().getName());
        jsonObj.put("stackTrace", e.getStackTrace());

        writeJsonObj(jsonObj);
    }

    public void write(Errors errors) throws IOException
    {
        //set the status to 400 to indicate that it was a bad request
        getResponse().setStatus(400);

        String exception = null;
        JSONArray jsonErrors = new JSONArray();
        for(ObjectError error : (List<ObjectError>)errors.getAllErrors())
        {
            String msg = error.getDefaultMessage();
            String key = error.getObjectName();

            if(error instanceof FieldError)
            {
                FieldError ferror = (FieldError)error;
                key = ferror.getField();
                msg = ferror.getDefaultMessage();
            }

            JSONObject jsonError = new JSONObject();
            jsonError.put("field", key);
            jsonError.put("message", msg);

            if(null == exception)
                exception = msg;

            jsonErrors.put(jsonError);
        }

        JSONObject root = new JSONObject();
        root.put("exception", exception);
        root.put("errors", jsonErrors);
        writeJsonObj(root);
    }

    protected void writeJsonObj(JSONObject obj) throws IOException
    {
        //jsonObj.write(getResponse().getWriter()); //use this for compact output
        getResponse().getWriter().write(obj.toString(4)); //or this for pretty output
    }

    public void startResponse() throws IOException
    {
        assert _streamStack.size() == 0 : "called startResponse() after response was already started!";
        //we always return an object at the top level
        getResponse().getWriter().write("{");
        _streamStack.push(new StreamState());
    }

    public void endResponse() throws IOException
    {
        assert _streamStack.size() == 1 : "called endResponse without a corresponding startResponse()!";
        getResponse().getWriter().write("}");
        _streamStack.pop();
    }

    public void startMap(String name) throws IOException
    {
        StreamState state = _streamStack.peek();
        assert(null != state) : "startResponse will start the root-level map!";
        getResponse().getWriter().write("\n" + JSONObject.quote(name) + ":{");
        _streamStack.push(new StreamState(name, state.getLevel() + 1));
    }

    public void endMap() throws IOException
    {
        getResponse().getWriter().write("}");
        _streamStack.pop();
    }

    public void writeProperty(String name, Object value) throws IOException
    {
        StreamState state = _streamStack.peek();
        getResponse().getWriter().write(state.getSeparator() + JSONObject.quote(name) + ":" 
                + JSONObject.valueToString(value, 4, state.getLevel()));
    }

    public void startList(String name) throws IOException
    {
        StreamState state = _streamStack.peek();
        getResponse().getWriter().write(state.getSeparator() + JSONObject.quote(name) + ":[");
        _streamStack.push(new StreamState(name, state.getLevel() + 1));
    }

    public void endList() throws IOException
    {
        getResponse().getWriter().write("]");
        _streamStack.pop();
    }

    public void writeListEntry(Object entry) throws IOException
    {
        StreamState state = _streamStack.peek();
        getResponse().getWriter().write(state.getSeparator() + JSONObject.valueToString(entry, 4, state.getLevel()));
    }
}
