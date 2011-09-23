/*
 * Copyright (c) 2008-2011 LabKey Corporation
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

    /*
     * (MAB) This code defaults to using setting the response to SC_BAD_REQUEST
     * when any error is encountered.  I think this is wrong.  Expected
     * errors should be encoded in a normal JSON reponse and SC_OK.
     *
     * Allow new code to specify that SC_OK should be used for errors
     */
    int errorResponseStatus = HttpServletResponse.SC_BAD_REQUEST;


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


    public void setErrorResponseStatus(int status)
    {
        errorResponseStatus = status;
    }


    public void write(ApiResponse response) throws IOException
    {
        if(response instanceof ApiStreamResponse)
        {
            try
            {
                ((ApiStreamResponse)response).render(this);
            }
            catch (Exception e)
            {
                //at this point, we can't guarantee a legitimate
                //JSON response, and we need to write the exception
                //back so the client can tell something went wrong
                if (null != getResponse())
                {
                    if (!getResponse().isCommitted())
                        getResponse().reset();
                }
                write(e);
            }
        }
        else
        {
            JSONObject json = new JSONObject(response.getProperties());
            writeJsonObj(json);
        }
    }

    public void write(Throwable e, int status) throws IOException
    {
        if (null == getResponse())
        {
            if (e instanceof IOException)
                throw (IOException)e;
            if (e instanceof RuntimeException)
                throw (RuntimeException)e;
            throw new RuntimeException(e);
        }

        getResponse().setStatus(status);

        JSONObject jsonObj = new JSONObject();
        jsonObj.put("exception", e.getMessage() != null ? e.getMessage() : e.getClass().getName());
        jsonObj.put("exceptionClass", e.getClass().getName());
        jsonObj.put("stackTrace", e.getStackTrace());

        writeJsonObj(jsonObj);
    }


    public void write(Throwable e) throws IOException
    {
        int status;

        if (e instanceof NotFoundException)
            status = HttpServletResponse.SC_NOT_FOUND;
        else
            status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

        write(e, status);
    }
    

    public void write(BatchValidationException e) throws IOException
    {
        if (null != getResponse())
            getResponse().setStatus(errorResponseStatus);

        JSONObject obj = new JSONObject();
        JSONArray arr = new JSONArray();
        String message = null;
        for (ValidationException vex : e.getRowErrors())
        {
            JSONObject child = toJSON(vex);
            if (message == null)
                message = child.optString("exception", "(No error message)");
            arr.put(child);
        }
        obj.put("success", Boolean.FALSE);
        obj.put("errors", arr);
        obj.put("exception", message);
        obj.put("extraContext", e.getExtraContext());

        writeJsonObj(obj);
    }

    public void write(ValidationException e) throws IOException
    {
        if (null != getResponse())
            getResponse().setStatus(errorResponseStatus);

        JSONObject obj = toJSON(e);
        obj.put("success", Boolean.FALSE);
        writeJsonObj(obj);
    }


    protected JSONObject toJSON(ValidationException e)
    {
        JSONObject obj = new JSONObject();
        String message = null;
        JSONArray jsonErrors = new JSONArray();
        for (ValidationError error : e.getErrors())
        {
            if (message == null)
                message = error.getMessage();
            toJSON(jsonErrors, error);
        }

        obj.put("errors", jsonErrors);
        obj.put("exception", message == null ? "(No error message)" : message);
        if (e.getSchemaName() != null)
            obj.put("schemaName", e.getSchemaName());
        if (e.getQueryName() != null)
            obj.put("queryName", e.getQueryName());
        if (e.getRow() != null)
            obj.put("row", e.getRow());
        if (e.getRowNumber() > -1)
            obj.put("rowNumber", e.getRowNumber());

        return obj;
    }

    public void toJSON(JSONArray parent, ValidationError error)
    {
        String msg = error.getMessage();
        String key = null;
        if (error instanceof PropertyValidationError)
            key = ((PropertyValidationError)error).getProperty();

        JSONObject jsonError = new JSONObject();

        // these are the Ext expected property names
        jsonError.putOpt("id", key);
        jsonError.put("msg", msg);
        // TODO deprecate these with a new API version
        jsonError.putOpt("field", key);
        jsonError.put("message", msg);

        parent.put(jsonError);
    }

    public void write(Errors errors) throws IOException
    {
//set the status to 400 to indicate that it was a bad request
//        if (null != getResponse())
//            getResponse().setStatus(errorResponseStatus);

        String exception = null;
        JSONArray jsonErrors = new JSONArray();
        for (ObjectError error : (List<ObjectError>)errors.getAllErrors())
        {
            String msg = error.getDefaultMessage();
            String key = error.getObjectName();

            if (error instanceof FieldError)
            {
                FieldError ferror = (FieldError)error;
                key = ferror.getField();
            }

            JSONObject jsonError = new JSONObject();
            // these are the Ext expected property names
            jsonError.putOpt("id", key);
            jsonError.put("msg", msg);
            // TODO deprecate these with a new API version
            jsonError.put("field", key);
            jsonError.put("message", msg);

            if (null == exception)
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
