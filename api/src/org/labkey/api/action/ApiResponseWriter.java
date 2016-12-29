/*
 * Copyright (c) 2008-2016 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.PropertyValidationError;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.ExpectedException;
import org.labkey.api.util.Pair;
import org.labkey.api.view.NotFoundException;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Stack;

/**
 * Used by API actions to write various types of objects to the response stream.
 * Note that this class is abstract--use the derived classes to write objects
 * in various formats (JSON, XML, etc.)
 *
 * User: Dave
 * Date: Feb 8, 2008
 */
public abstract class ApiResponseWriter implements AutoCloseable
{
     /*
     * (MAB) This code defaults to using setting the response to SC_BAD_REQUEST
     * when any error is encountered.  I think this is wrong.  Expected
     * errors should be encoded in a normal JSON response and SC_OK.
     *
     * Allow new code to specify that SC_OK should be used for errors
     */
    int errorResponseStatus = HttpServletResponse.SC_BAD_REQUEST;

    private boolean serializeViaJacksonAnnotations = false;

    protected static class StreamState
    {
        private String _name;
        private String _separator;
        private int _level = 0;

        public StreamState() {}
        public StreamState(String name)
        {
            this(name, 0);
        }
        public StreamState(String name, int level)
        {
            _name = name;
        }

        public String getName() { return _name;}
        public int getLevel() { return _level;}
        public String getSeparator()
        {
            //first time return ""
            //all successive times, return ","
            if(null == _separator)
            {
                _separator = ",\n";
                return "";
            }
            else
                return _separator;
        }
    }

    protected Stack<StreamState> _streamStack = new Stack<>();

    public enum Format
    {
        JSON
        {
            @Override
            public ApiResponseWriter createWriter(HttpServletResponse response, String contentTypeOverride) throws IOException
            {
                return new ApiJsonWriter(response, contentTypeOverride, true); // TODO: FOR DEBUGGING. Before final commit, decide if pretty or compact should be default.
            }
        },
        XML
        {
            @Override
            public ApiResponseWriter createWriter(HttpServletResponse response, String contentTypeOverride) throws IOException
            {
                return new ApiXmlWriter(response, contentTypeOverride);
            }
        },
        JSON_COMPACT
        {
            @Override
            public ApiResponseWriter createWriter(HttpServletResponse response, String contentTypeOverride) throws IOException
            {
                return new ApiJsonWriter(response, contentTypeOverride, false);
            }
        };

        public abstract ApiResponseWriter createWriter(HttpServletResponse response, String contentTypeOverride) throws IOException;
    }

    private final HttpServletResponse _response;
    private final Writer _writer;

    public ApiResponseWriter(HttpServletResponse response) throws IOException
    {
        _response = response;
        _writer = _response.getWriter();
    }

    public ApiResponseWriter(Writer out) throws IOException
    {
        _response = null;
        _writer = out;
    }

    public void setErrorResponseStatus(int status)
    {
        errorResponseStatus = status;
    }

    /**
     * Entry-point for writing a response back to the client in the desired format.
     * The object argument may be an response object to be serialized or an {@link ApiResponse} instance.
     *
     * @param obj
     * @throws IOException
     */
    public final void writeResponse(Object obj) throws IOException
    {
        try
        {
            if (obj instanceof ApiResponse)
                write((ApiResponse)obj);
            else
                writeObject(obj);
        }
        finally
        {
            close();
        }
    }

    protected void write(ApiResponse response) throws IOException
    {
        try
        {
            response.render(this);
        }
        catch (Exception e)
        {
            if (ExceptionUtil.isClientAbortException(e))
            {
                close();
            }
            else
            {
                if (!(e instanceof ExpectedException))
                {
                    ExceptionUtil.logExceptionToMothership(null, e);
                    Logger.getLogger(ApiResponseWriter.class).warn("ApiResponseWriter exception: ", e);
                }
                //at this point, we can't guarantee a legitimate
                //JSON response, and we need to write the exception
                //back so the client can tell something went wrong
                if (null != getResponse())
                    resetOutput();

                writeAndClose(e);
            }
        }
    }

    /**
     * Override this method if the writer subclass tracks the output context independently of the response
     * stream content.
     */
    protected void resetOutput() throws IOException
    {
        if (!getResponse().isCommitted())
            getResponse().reset();
    }

    protected abstract void writeObject(Object object) throws IOException;

    /** Completes the response, writing out closing elements/tags/etc */
    public abstract void close() throws IOException;

    public void writeAndClose(Throwable e, int status) throws IOException
    {
        if (null == getResponse())
        {
            if (e instanceof IOException)
                throw (IOException) e;
            if (e instanceof RuntimeException)
                throw (RuntimeException) e;
            throw new RuntimeException(e);
        }

        getResponse().setStatus(status);

        JSONObject jsonObj = new JSONObject();
        jsonObj.put("exception", e.getMessage() != null ? e.getMessage() : e.getClass().getName());
        jsonObj.put("exceptionClass", e.getClass().getName());
        jsonObj.put("stackTrace", e.getStackTrace());

        try
        {
            writeObject(jsonObj);
        }
        finally
        {
            close();
        }
    }


    public void writeAndClose(Throwable e) throws IOException
    {
        int status;

        if (e instanceof BatchValidationException)
        {
            write((BatchValidationException) e);
            return;
        }
        if (e instanceof ValidationException)
        {
            write((ValidationException) e);
            return;
        }
        if (e instanceof NotFoundException)
            status = HttpServletResponse.SC_NOT_FOUND;
        else
            status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

        try
        {
            writeAndClose(e, status);
        }
        finally
        {
            close();
        }
    }


    public void write(BatchValidationException e) throws IOException
    {
        if (null != getResponse())
            getResponse().setStatus(errorResponseStatus);

        try
        {
            writeObject(getJSON(e));
        }
        finally
        {
            close();
        }
    }

    public JSONObject getJSON(BatchValidationException e) throws IOException
    {
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
        obj.put("errorCount", arr.length());
        obj.put("exception", message);
        obj.put("extraContext", e.getExtraContext());

        return obj;
    }

    public void write(ValidationException e) throws IOException
    {
        if (null != getResponse())
            getResponse().setStatus(errorResponseStatus);

        JSONObject obj = toJSON(e);
        obj.put("success", Boolean.FALSE);

        try
        {
            writeObject(obj);
        }
        finally
        {
            close();
        }
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
            key = ((PropertyValidationError) error).getProperty();

        JSONObject jsonError = new JSONObject();

        // these are the Ext expected property names
        jsonError.putOpt("id", key);
        jsonError.put("msg", msg);
        // TODO deprecate these with a new API version
        jsonError.putOpt("field", key);
        jsonError.put("message", msg);

        parent.put(jsonError);
    }

    public void writeAndClose(Errors errors) throws IOException
    {
        //set the status to 400 to indicate that it was a bad request
        if (null != getResponse())
            getResponse().setStatus(errorResponseStatus);

        Pair<String, JSONArray> pair = convertToJSON(errors, 0);

        JSONObject root = new JSONObject();
        root.put("success", false);
        root.put("exception", pair.getKey());
        root.put("errors", pair.getValue());
        try
        {
            writeObject(root);
        }
        finally
        {
            close();
        }
    }

    /**
     * Allows for writing a simplified status/message to the response.
     */
    public void writeAndCloseError(int status, String message) throws IOException
    {
        if (null != getResponse())
            getResponse().setStatus(status);

        JSONObject root = new JSONObject();
        root.put("success", false);
        root.put("exception", message);
        root.put("errors", new JSONArray());

        try
        {
            writeObject(root);
        }
        finally
        {
            close();
        }
    }

    /**
     * Converts the errors to JSON-friendly client responses. Starts at the specified index, skipping any prior
     * errors in the collection
     * @return a Pair with the message from the first error, and the JSONArray of the full error information
     */
    public static Pair<String, JSONArray> convertToJSON(Errors errors, int startingErrorIndex)
    {
        String exceptionMessage = null;
        JSONArray errorsArray = new JSONArray();
        List<ObjectError> allErrors = errors.getAllErrors();
        for (int i = startingErrorIndex; i < allErrors.size(); i++)
        {
            ObjectError error = allErrors.get(i);
            String msg = error.getDefaultMessage();
            String key = error.getObjectName();

            if (error instanceof FieldError)
            {
                FieldError ferror = (FieldError) error;
                key = ferror.getField();
            }

            JSONObject jsonError = new JSONObject();
            // these are the Ext expected property names
            jsonError.putOpt("id", key);
            jsonError.put("msg", msg);
            // TODO deprecate these with a new API version
            jsonError.put("field", key);
            jsonError.put("message", msg);

            if (null == exceptionMessage)
                exceptionMessage = msg;

            errorsArray.put(jsonError);
        }
        return new Pair<>(exceptionMessage, errorsArray);
    }

    //stream oriented methods
    public abstract void startResponse() throws IOException;

    public abstract void endResponse() throws IOException;

    public abstract void startMap(String name) throws IOException;

    public abstract void endMap() throws IOException;

    public abstract void writeProperty(String name, Object value) throws IOException;

    public abstract void startList(String name) throws IOException;

    public abstract void endList() throws IOException;

    public abstract void writeListEntry(Object entry) throws IOException;

    protected HttpServletResponse getResponse()
    {
        return _response;
    }

    protected Writer getWriter()
    {
        return _writer;
    }

    public boolean isSerializeViaJacksonAnnotations()
    {
        return serializeViaJacksonAnnotations;
    }

    public void setSerializeViaJacksonAnnotations(boolean serializeViaJacksonAnnotations)
    {
        this.serializeViaJacksonAnnotations = serializeViaJacksonAnnotations;
    }
}
