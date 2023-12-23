/*
 * Copyright (c) 2008-2018 LabKey Corporation
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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.PropertyValidationError;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.ExpectedException;
import org.labkey.api.util.Pair;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.BadRequestException;
import org.labkey.api.view.HttpStatusException;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Stack;

/**
 * Used by API actions to write various types of objects to the response stream.
 * Note that this class is abstract--use the derived classes to write objects
 * in various formats (JSON, XML, etc.)
 */
public abstract class ApiResponseWriter implements AutoCloseable
{
    private static final String RESPONSE_FORMAT_ATTRIBUTE = ApiResponseWriter.class.getName() + "$responseFormat";

    /*
     * (MAB) This code defaults to using setting the response to SC_BAD_REQUEST
     * when any error is encountered.  I think this is wrong.  Expected
     * errors should be encoded in a normal JSON response and SC_OK.
     *
     * Also, this using SC_BAD_REQUEST means failed APIs get taggd by BlockListFilter
     *
     * Allow new code to specify that SC_OK should be used for errors
     */
    static final int defaultErrorStatus = HttpServletResponse.SC_BAD_REQUEST;
    Integer errorResponseStatus = null;

    private boolean serializeViaJacksonAnnotations = false;

    /**
     * @return either the response format that has already been associated with the request, or the default if it's unset
     */
    public static Format getResponseFormat(@Nullable HttpServletRequest request, ApiResponseWriter.Format defaultFormat)
    {
        ApiResponseWriter.Format result = request == null ? null : (ApiResponseWriter.Format) request.getAttribute(RESPONSE_FORMAT_ATTRIBUTE);
        return result == null ? defaultFormat : result;
    }

    /**
     * Store the preferred response format (JSON, XML, etc) as an attribute on the request so that we can send
     * the appropriate kind of content, regardless of success or error
     */
    public static void setResponseFormat(HttpServletRequest request, ApiResponseWriter.Format format)
    {
        request.setAttribute(RESPONSE_FORMAT_ATTRIBUTE, format);
    }

    protected static class StreamState
    {
        private final String _name;
        private String _separator;
        private final int _level;

        public StreamState()
        {
            this(null);
        }

        public StreamState(String name)
        {
            this(name, 0);
        }

        public StreamState(String name, int level)
        {
            _name = name;
            _level = level;
        }

        public String getName()
        {
            return _name;
        }

        public int getLevel()
        {
            return _level;
        }

        public String getSeparator()
        {
            //first time return ""
            //all successive times, return ","
            if (null == _separator)
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
                    public ApiResponseWriter createWriter(HttpServletResponse response, String contentTypeOverride, ObjectMapper objectMapper) throws IOException
                    {
                        return new ApiJsonWriter(response, contentTypeOverride, objectMapper, true); // TODO: FOR DEBUGGING. Before final commit, decide if pretty or compact should be default.
                    }

                    @Override
                    public boolean isJson()
                    {
                        return true;
                    }
                },
        XML
                {
                    @Override
                    public ApiResponseWriter createWriter(HttpServletResponse response, String contentTypeOverride, ObjectMapper objectMapper) throws IOException
                    {
                        // TODO: Use Jackson for object -> XML serialization
                        return new ApiXmlWriter(response, contentTypeOverride);
                    }

                    @Override
                    public boolean isJson()
                    {
                        return false;
                    }
                },
        JSON_COMPACT
                {
                    @Override
                    public ApiResponseWriter createWriter(HttpServletResponse response, String contentTypeOverride, ObjectMapper objectMapper) throws IOException
                    {
                        return new ApiJsonWriter(response, contentTypeOverride, objectMapper, false);
                    }

                    @Override
                    public boolean isJson()
                    {
                        return true;
                    }
                };

        public abstract ApiResponseWriter createWriter(HttpServletResponse response, String contentTypeOverride, ObjectMapper objectMapper) throws IOException;

        public abstract boolean isJson();

        public static Format getFormatByName(@Nullable String name, Format defaultFormat)
        {
            if (name == null)
            {
                return defaultFormat;
            }
            for (Format f : values())
            {
                if (f.toString().equalsIgnoreCase(name))
                {
                    return f;
                }
            }
            throw new BadRequestException("Unknown response format requested: " + name);
        }
    }

    private final HttpServletResponse _response;
    private final Writer _writer;

    public ApiResponseWriter(HttpServletResponse response) throws IOException
    {
        _response = response;
        _writer = _response.getWriter();
    }

    public ApiResponseWriter(Writer out)
    {
        _response = null;
        _writer = out;
    }

    public void setErrorResponseStatus(int status)
    {
        errorResponseStatus = status;
    }

    public int getErrorResponseStatus()
    {
        return null != errorResponseStatus ? errorResponseStatus : defaultErrorStatus;
    }

    public int getErrorResponseStatus(Throwable ex)
    {
        if (null != errorResponseStatus)
            return errorResponseStatus;

        if (ex instanceof HttpStatusException statusEx)
            return statusEx.getStatus();

        if (ex instanceof BatchValidationException || ex instanceof ValidationException)
            return defaultErrorStatus;

        return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
    }

    /**
     * Entry-point for writing a response back to the client in the desired format.
     * The object argument may be an response object to be serialized or an {@link ApiResponse} instance.
     */
    public final void writeResponse(Object obj) throws IOException
    {
        assert !(obj instanceof Throwable);
        try
        {
            if (obj instanceof ApiResponse apiResponse)
                write(apiResponse);
            else
                writeObject(obj);
        }
        finally
        {
            close();
        }
    }


    public void writeResponse(Throwable e) throws IOException
    {
        int status = getErrorResponseStatus(e);
        e = ExceptionUtil.unwrapException(e);

        try
        {
            setResponseStatus(status);
            var json = toJSON(e);
            json.put("success", false);
            writeObject(json);
        }
        finally
        {
            close();
        }
    }


    public void writeResponse(Errors errors) throws IOException
    {
        int status = getErrorResponseStatus();
        setResponseStatus(status);

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
    public void writeResponse(int status, String message) throws IOException
    {
        setResponseStatus(status);

        JSONObject root = new JSONObject();
        root.put("success", status == HttpServletResponse.SC_OK);
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


    protected void write(ApiResponse response) throws IOException
    {
        try
        {
            response.render(this);
        }
        catch (IOException io)
        {
            throw io;
        }
        catch (Exception ex)
        {
            throw UnexpectedException.wrap(ex);
        }
    }


    /**
     * Even though the default exception handling is implemented here, we require that response.render()
     * invokes this method in its own catch() block before endResponse().  This seems cleaner than coordinating
     * to have render() not output matching { open and close } braces.
     *
     * @see ApiResponse#render(ApiResponseWriter) 
     */
    public void handleRenderException(Exception e) throws IOException
    {
        try
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
                    LogManager.getLogger(ApiResponseWriter.class).warn("ApiResponseWriter exception: ", e);
                }
                //at this point, we can't guarantee a legitimate
                //JSON response, and we need to write the exception
                //back so the client can tell something went wrong
                if (null != getResponse())
                    resetOutput();

                int status =  getErrorResponseStatus();
                setResponseStatus(status);
                writeThrowableProperties(e);
                writeProperty("success", false);
            }
        }
        catch (IOException io)
        {
            /* */
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

    protected abstract void writeProperties(JSONObject json) throws IOException;

    /** Completes the response, writing out closing elements/tags/etc */
    @Override
    public abstract void close() throws IOException;


    private void setResponseStatus(int status)
    {
        var response = getResponse();
        if (null != response && !response.isCommitted())
            getResponse().setStatus(status);
    }


    private void writeThrowableProperties(Throwable e) throws IOException
    {
        if (null == getResponse())
        {
            if (e instanceof IOException)
                throw (IOException) e;
            if (e instanceof RuntimeException)
                throw (RuntimeException) e;
            throw new RuntimeException(e);
        }

        writeProperties(toJSON(e));
    }


    public JSONObject toJSON(Throwable e)
    {
        //noinspection ConstantConditions
        if (e instanceof ValidationException ve)
        {
            return toJSON(ve);
        }
        else if (e instanceof BatchValidationException bve)
        {
            JSONObject obj = new JSONObject();
            JSONArray arr = new JSONArray();
            String message = null;
            for (ValidationException vex : bve.getRowErrors())
            {
                JSONObject child = toJSON(vex);
                if (message == null)
                    message = child.optString("exception", "(No error message)");
                arr.put(child);
            }
            obj.put("errors", arr);
            obj.put("errorCount", arr.length());
            obj.put("exception", message);
            obj.put("extraContext", bve.getExtraContext());

            return obj;
        }
        else
        {
            JSONObject jsonObj = new JSONObject();
            jsonObj.put("exception", e.getMessage() != null ? e.getMessage() : e.getClass().getName());
            jsonObj.put("exceptionClass", e.getClass().getName());
            jsonObj.put("stackTrace", e.getStackTrace());
            return jsonObj;
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


    private void toJSON(JSONArray parent, ValidationError error)
    {
        String msg = error.getMessage();
        String key = null;
        if (error instanceof PropertyValidationError)
            key = ((PropertyValidationError) error).getProperty();

        String help = null;
        if (error.getHelp() != null)
            help = error.getHelp().getHelpTopicHref();

        JSONObject jsonError = new JSONObject();

        // these are the Ext expected property names
        jsonError.putOpt("id", key);
        jsonError.put("msg", msg);
        // TODO deprecate these with a new API version
        jsonError.putOpt("field", key);
        jsonError.put("message", msg);
        jsonError.putOpt("help", help);

        parent.put(jsonError);
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
            String propertyId = (null != error.getCodes() && error.getCodes().length > 0 ? error.getCodes()[0] : key);
            String severity = ValidationException.SEVERITY.ERROR.toString();
            String help = null;

            if (error instanceof FieldError fe)
            {
                key = fe.getField();

                // Strip off nested exception details from field error messages in JSON responses, Issue 45567
                int idx = msg.indexOf("; nested exception");
                if (idx != -1)
                    msg = msg.substring(0, idx);
            }

            if (error instanceof SimpleValidationError.FieldWarning)
            {
                SimpleValidationError.FieldWarning fieldWarning = (SimpleValidationError.FieldWarning) error;
                severity = fieldWarning.getSeverity();
                key = fieldWarning.getField();
                propertyId = fieldWarning.getObjectName();
                if (fieldWarning.getHelp() != null)
                    help = fieldWarning.getHelp().getHelpTopicHref();
            }

            JSONObject jsonError = new JSONObject();
            // these are the Ext expected property names
            jsonError.putOpt("id", propertyId);
            jsonError.put("msg", msg);
            // TODO deprecate these with a new API version
            jsonError.put("field", key);
            jsonError.put("message", msg);
            jsonError.put("severity", severity);
            jsonError.putOpt("help", help);

            if (error instanceof LabKeyErrorWithLink errorWithLink)
            {
                jsonError.put("adviceText", errorWithLink.getAdviceText());
                jsonError.put("adviceHref", errorWithLink.getAdviceHref());
            }

            if (error instanceof LabKeyErrorWithHtml errorWithHtml)
            {
                jsonError.put("html", errorWithHtml.getHtml());
            }

            if (null == exceptionMessage)
                exceptionMessage = msg;

            errorsArray.put(jsonError);
        }
        return new Pair<>(exceptionMessage, errorsArray);
    }

    //stream oriented methods
    public abstract void startResponse() throws IOException;

    public abstract void endResponse() throws IOException;

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
