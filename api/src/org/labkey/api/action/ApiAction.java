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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.labkey.api.miniprofiler.MiniProfiler;
import org.labkey.api.miniprofiler.Timing;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.RuntimeValidationException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.BadRequestException;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Map;

/**
 * Base class for API actions, which typically consume/produce JSON instead of HTML.
 *
 * User: Dave
 * Date: Feb 8, 2008
 */
public abstract class ApiAction<FORM> extends BaseViewAction<FORM>
{
    private ApiResponseWriter.Format _reqFormat = null;
    private ApiResponseWriter.Format _respFormat = ApiResponseWriter.Format.JSON;
    private String _contentTypeOverride = null;
    private double _requestedApiVersion = -1;
    private Marshaller _marshaller = null;

    protected enum CommonParameters
    {
        apiVersion
    }

    public ApiAction()
    {
        setUnauthorizedType(UnauthorizedException.Type.sendBasicAuth);
        _marshaller = findMarshaller();
    }

    public ApiAction(Class<? extends FORM> formClass)
    {
        super(formClass);
        setUnauthorizedType(UnauthorizedException.Type.sendBasicAuth);
        _marshaller = findMarshaller();
    }

    protected final Marshaller findMarshaller()
    {
        Marshal marshal = getClass().getAnnotation(Marshal.class);
        if (marshal == null)
        {
            Class superClass = getClass().getSuperclass();
            if (null != superClass)
                marshal = (Marshal) superClass.getAnnotation(Marshal.class);
        }
        if (marshal == null)
        {
            Class declaringClass = getClass().getDeclaringClass();
            if (declaringClass != null)
                marshal = (Marshal)declaringClass.getAnnotation(Marshal.class);
        }

        if (marshal != null)
            return marshal.value();

        return null;
    }

    protected String getCommandClassMethodName()
    {
        return "execute";
    }


    protected boolean isPost()
    {
        return "POST".equals(getViewContext().getRequest().getMethod());
    }

    protected boolean isDelete()
    {
        return "DELETE".equals(getViewContext().getRequest().getMethod());
    }


    public ModelAndView handleRequest() throws Exception
    {
        if (isPost())
            return handlePost();
        else
            return handleGet();
    }


    protected ModelAndView handleGet() throws Exception
    {
        return handlePost();
    }


    @SuppressWarnings("TryWithIdenticalCatches")
    public ModelAndView handlePost() throws Exception
    {
        getViewContext().getResponse().setHeader("X-Robots-Tag", "noindex");

        try
        {
            Pair<FORM, BindException> pair;

            try
            {
                pair = populateForm();
            }
            catch (BadRequestException bad)
            {
                getViewContext().getResponse().sendError(bad.getStatus(), bad.getMessage());
                return null;
            }

            FORM form = pair.first;
            BindException errors = pair.second;

            if ("xml".equalsIgnoreCase(getViewContext().getRequest().getParameter("respFormat")))
            {
                _respFormat = ApiResponseWriter.Format.XML;
            }
            else if ("json_compact".equalsIgnoreCase(getViewContext().getRequest().getParameter("respFormat")))
            {
                _respFormat = ApiResponseWriter.Format.JSON_COMPACT;
            }

            //validate the form
            validate(form, errors);

            //if we had binding or validation errors,
            //return them without calling execute.
            if (isFailure(errors))
                createResponseWriter().writeAndClose((Errors) errors);
            else
            {
                Object response;
                try (Timing t = MiniProfiler.step("execute"))
                {
                    response = execute(form, errors);
                }

                try (Timing t = MiniProfiler.step("render"))
                {
                    if (isFailure(errors))
                        createResponseWriter().writeAndClose((Errors) errors);
                    else if (null != response)
                        createResponseWriter().writeResponse(response);
                }
            }
        }
        catch (BindException e)
        {
            createResponseWriter().writeAndClose((Errors) e);
        }
        //don't log exceptions that result from bad inputs
        catch (BatchValidationException e)
        {
            // Catch separately to be sure that we call the subclass-specific write() method
            createResponseWriter().writeAndClose(e);
        }
        catch (ValidationException e)
        {
            // Catch separately to be sure that we call the subclass-specific write() method
            createResponseWriter().writeAndClose(e);
        }
        catch (RuntimeValidationException e)
        {
            // Catch separately to be sure that we call the subclass-specific write() method
            createResponseWriter().writeAndClose(e.getValidationException());
        }
        catch (QueryException | IllegalArgumentException |
                NotFoundException | InvalidKeyException | ApiUsageException e)
        {
            createResponseWriter().writeAndClose(e);
        }
        catch (UnauthorizedException e)
        {
            e.setType(_unauthorizedType);
            throw e;
        }
        catch (Exception e)
        {
            if (e instanceof IOException && e.getClass().getSimpleName().equals("ClientAbortException"))
                return null;

            ExceptionUtil.logExceptionToMothership(getViewContext().getRequest(), e);
            Logger.getLogger(ApiAction.class).error("ApiAction exception: ", e);

            createResponseWriter().writeAndClose(e);
        }

        return null;
    } //handleRequest()

    protected boolean isFailure(BindException errors)
    {
        return null != errors && errors.hasErrors();
    }

    protected double getApiVersion()
    {
        ApiVersion version = this.getClass().getAnnotation(ApiVersion.class);
        //default version is 8.3, since we made several changes in core code
        //to properly support API clients
        return null != version ? version.value() : 8.3;
    }


    boolean _empty(Object o)
    {
        return null == o || (o instanceof String && ((String)o).isEmpty());
    }


    @NotNull
    protected Pair<FORM, BindException> populateForm() throws Exception
    {
        try (Timing t = MiniProfiler.step("bind"))
        {
            String contentType = getViewContext().getRequest().getContentType();
            if (null != contentType)
            {
                if (contentType.contains(ApiJsonWriter.CONTENT_TYPE_JSON))
                {
                    _reqFormat = ApiResponseWriter.Format.JSON;
                    return populateJsonForm();
                }
            }

            return defaultPopulateForm();
        }
    }

    // CONSIDER: Extract ApiRequestReader similar to the ApiResponseWriter
    // CONSIDER: Something like Jersey's MessageBodyReader? https://jax-rs-spec.java.net/nonav/2.0/apidocs/javax/ws/rs/ext/MessageBodyReader.html
    @NotNull
    protected Pair<FORM, BindException> populateJsonForm() throws Exception
    {
        if (_marshaller == Marshaller.Jackson)
            return populateJacksonForm();
        else
            return populateJSONObjectForm();
    }


    @NotNull
    protected Pair<FORM, BindException> defaultPopulateForm() throws Exception
    {
        BindException errors = null;
        FORM form = null;

        saveRequestedApiVersion(getViewContext().getRequest(), null);

        if (null != getCommandClass())
        {
            errors = defaultBindParameters(getCommand(), getPropertyValues());
            form = (FORM)errors.getTarget();
        }

        return Pair.of(form, errors);
    }

    /**
     * Use Jackson to parse POST body as JSON and instantiate the FORM class directly.
     */
    @NotNull
    protected Pair<FORM, BindException> populateJacksonForm() throws Exception
    {
        FORM form = null;
        BindException errors;

        try
        {
            Class c = getCommandClass();
            if (c != null)
            {
                ObjectReader reader = getObjectReader(c);
                form = reader.readValue(getViewContext().getRequest().getInputStream());
            }
            errors = new NullSafeBindException(form != null ? form : new Object(), "form");
        }
        catch (SocketTimeoutException x)
        {
            ExceptionUtil.decorateException(x, ExceptionUtil.ExceptionInfo.SkipMothershipLogging, "true", true);
            throw x;
        }
        catch (JsonMappingException x)
        {
            // JSON mapping
            errors = new NullSafeBindException(new Object(), "form");
            errors.reject(SpringActionController.ERROR_MSG, "Error binding property: " + x.getMessage());
        }
        catch (JsonProcessingException x)
        {
            // Bad JSON
            throw new BadRequestException(x.getMessage(), x);
        }

        saveRequestedApiVersion(getViewContext().getRequest(), form);
        return Pair.of(form, errors);
    }


    protected ObjectReader getObjectReader(Class c)
    {
        return new ObjectMapper().reader(c);
    }


    /**
     * Parse POST body as JSONObject then use either CustomApiForm or spring form binding to populate the FORM instance.
     */
    @NotNull
    protected Pair<FORM, BindException> populateJSONObjectForm() throws Exception
    {
        JSONObject jsonObj;
        try
        {
            jsonObj = getJsonObject();
        }
        catch (SocketTimeoutException x)
        {
            ExceptionUtil.decorateException(x, ExceptionUtil.ExceptionInfo.SkipMothershipLogging, "true", true);
            throw x;
        }
        catch (JSONException x)
        {
            throw new BadRequestException(x.getMessage(), x);
        }
        saveRequestedApiVersion(getViewContext().getRequest(), jsonObj);

        FORM form = getCommand();
        BindException errors = populateForm(jsonObj, form);
        return Pair.of(form, errors);
    }


    protected double saveRequestedApiVersion(HttpServletRequest request, Object obj)
    {
        Object o = null;

        if (null != obj && obj instanceof Map && ((Map)obj).containsKey(CommonParameters.apiVersion.name()))
            o = ((Map)obj).get(CommonParameters.apiVersion.name());
        if (_empty(o))
            o = getProperty(CommonParameters.apiVersion.name());
        if (_empty(o))
            o = request.getHeader("LABKEY-" + CommonParameters.apiVersion.name());

        try
        {
            if (null == o)
                _requestedApiVersion = 0;
            else if (o instanceof Number)
                _requestedApiVersion = ((Number)o).doubleValue();
            else
                _requestedApiVersion = Double.parseDouble(o.toString());
        }
        catch (NumberFormatException x)
        {
            _requestedApiVersion = 0;
        }

        return _requestedApiVersion;
    }


    public double getRequestedApiVersion()
    {
        assert _requestedApiVersion >= 0;
        return _requestedApiVersion < 0 ? 0 : _requestedApiVersion;
    }


    protected JSONObject getJsonObject() throws IOException
    {
        //read the JSON into a buffer
        //unfortunately the json.org classes can't read directly from a stream!
        char[] buf = new char[2048];
        int chars;
        StringBuilder json = new StringBuilder();
        BufferedReader reader = getViewContext().getRequest().getReader();

        while((chars = reader.read(buf)) > 0)
            json.append(buf, 0, chars);

        String jsonString = json.toString();
        if(jsonString.isEmpty())
            return null;

        //deserialize the JSON
        return new JSONObject(jsonString);
    }

    protected BindException populateForm(JSONObject jsonObj, FORM form)
    {
        if (null == jsonObj)
            return new NullSafeBindException(form, "form");

        if (form instanceof CustomApiForm)
        {
            ((CustomApiForm)form).bindProperties(jsonObj);
            return new NullSafeBindException(form, "form");
        }
        else
        {
            JsonPropertyValues values = new JsonPropertyValues(jsonObj);
            return defaultBindParameters(form, values);
        }
    }

    public static class JsonPropertyValues extends MutablePropertyValues
    {
        public JsonPropertyValues(JSONObject jsonObj) throws JSONException
        {
            addPropertyValues(jsonObj);
        }

        private void addPropertyValues(JSONObject jsonObj) throws JSONException
        {
            for (String key : jsonObj.keySet())
            {
                Object value = jsonObj.get(key);

                if (value instanceof JSONArray)
                {
                    value = ((JSONArray) value).toArray();
                }
                else if (value instanceof JSONObject)
                    throw new IllegalArgumentException("Nested objects and arrays are not supported at this time.");
                addPropertyValue(key, value);
            }
        }
    }

    public final void validate(Object form, Errors errors)
    {
        try (Timing t = MiniProfiler.step("validate"))
        {
            validateForm((FORM) form, errors);
        }
    }

    /**
     * Override to validate the form bean and populate the Errors collection as necessary.
     * The default implementation does nothing, so override this method to perform validation.
     *
     * @param form The form bean
     * @param errors The errors collection
     */
    public void validateForm(FORM form, Errors errors)
    {
    }

    public ApiResponseWriter createResponseWriter() throws IOException
    {
        // Let the response format dictate how we write the response. Typically JSON, but not always.
        ApiResponseWriter writer = _respFormat.createWriter(getViewContext().getResponse(), getContentTypeOverride());
        if (_marshaller == Marshaller.Jackson)
            writer.setSerializeViaJacksonAnnotations(true);
        return writer;
    }

    public ApiResponseWriter.Format getResponseFormat()
    {
        return _respFormat;
    }

    public ApiResponseWriter.Format getRequestFormat()
    {
        return _reqFormat;
    }

    public String getContentTypeOverride()
    {
        return _contentTypeOverride;
    }

    public void setContentTypeOverride(String contentTypeOverride)
    {
        _contentTypeOverride = contentTypeOverride;
    }

    /**
     * Used to determine if the request originated from the client or server. Server-side scripts
     * use a mock request to invoke the action...
     */
    public boolean isServerSideRequest()
    {
        return getViewContext().getRequest() instanceof MockHttpServletRequest;
    }

    public abstract Object execute(FORM form, BindException errors) throws Exception;

    //
    // Static helpers to create a simple response object for Jackson serialization
    //

    public static SimpleResponse success()
    {
        return new SimpleResponse(true);
    }

    public static SimpleResponse success(String message)
    {
        return new SimpleResponse(true, message);
    }

    public static <T> SimpleResponse<T> success(T data)
    {
        return new SimpleResponse<>(true, null, data);
    }

    public static <T> SimpleResponse<T> success(String message, T data)
    {
        return new SimpleResponse<>(true, message, data);
    }

}

