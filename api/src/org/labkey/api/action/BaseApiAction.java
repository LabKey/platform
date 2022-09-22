/*
 * Copyright (c) 2014-2019 LabKey Corporation
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
import org.jetbrains.annotations.NotNull;
import org.json.old.JSONArray;
import org.json.old.JSONException;
import org.json.old.JSONObject;
import org.labkey.api.miniprofiler.MiniProfiler;
import org.labkey.api.miniprofiler.Timing;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.RuntimeValidationException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.HttpUtil;
import org.labkey.api.util.JsonUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.ResponseHelper;
import org.labkey.api.view.BadRequestException;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Map;

/**
 * Common base class for all API actions
 *
 * User: Dave
 * Date: Feb 8, 2008
 */
public abstract class BaseApiAction<FORM> extends BaseViewAction<FORM>
{
    private final Marshaller _marshaller;

    private ApiResponseWriter.Format _reqFormat = null;
    private String _contentTypeOverride = null;
    private double _requestedApiVersion = -1;
    private ObjectMapper _requestObjectMapper;
    private ObjectMapper _responseObjectMapper;

    protected enum CommonParameters
    {
        apiVersion
    }

    public BaseApiAction()
    {
        _marshaller = findMarshaller();
    }

    protected abstract ModelAndView handleGet() throws Exception;

    public abstract Object execute(FORM form, BindException errors) throws Exception;

    private Marshaller findMarshaller()
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

    @Override
    public ApiResponseWriter.Format getDefaultResponseFormat()
    {
        return ApiResponseWriter.Format.JSON;
    }

    @Override
    protected String getCommandClassMethodName()
    {
        return "execute";
    }

    @Override
    public ModelAndView handleRequest() throws Exception
    {
        switch (getViewContext().getMethod())
        {
            case POST:
            case PUT:
            case DELETE:
            case PATCH:
                return handlePost();
            case GET:
                return handleGet();
        }
        throw new BadRequestException("Method Not Allowed: " + getViewContext().getRequest().getMethod(), null, HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    @Override
    public void setViewContext(ViewContext context)
    {
        // Issue 34825 - don't prompt for basic auth for browser requests
        setUnauthorizedType(HttpUtil.isBrowser(context.getRequest()) ? UnauthorizedException.Type.sendUnauthorized : UnauthorizedException.Type.sendBasicAuth);
        super.setViewContext(context);
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

            if (form != null)
            {
                // validate the form, if a binding error didn't prevent it from being created. See issue 40888
                validate(form, errors);
            }

            //if we had binding or validation errors,
            //return them without calling execute.
            if (isFailure(errors))
            {
                createResponseWriter().writeAndClose((Errors) errors);
            }
            else
            {
                boolean cachable = false;

                // ETag header
                String eTag = getETag(form);
                if (eTag != null)
                {
                    getViewContext().getResponse().setHeader("ETag", eTag);
                    cachable = true;
                }

                // Last-Modified header
                long lastModified = getLastModified(form);
                if (lastModified != Long.MIN_VALUE)
                {
                    getViewContext().getResponse().addDateHeader("Last-Modified", lastModified);
                    cachable = true;
                }

                if (cachable)
                {
                    // Include max-age to tell the browser to cache for a short duration before making another request to check "If-Modified-Since"
                    ResponseHelper.setPrivate(getViewContext().getResponse(), Duration.ofSeconds(10));
                }

                // Check if the conditions specified in the optional If headers are satisfied.
                if (!ResponseHelper.checkIfHeaders(getViewContext(), eTag, lastModified))
                {
                    assert getViewContext().getResponse().getStatus() != HttpServletResponse.SC_OK;
                    return null;
                }

                Object response;
                try (Timing ignored = MiniProfiler.step("execute"))
                {
                    response = execute(form, errors);
                }

                try (Timing ignored = MiniProfiler.step("render"))
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


    @NotNull
    private Pair<FORM, BindException> populateForm() throws Exception
    {
        try (Timing ignored = MiniProfiler.step("bind"))
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
    private Pair<FORM, BindException> populateJsonForm() throws Exception
    {
        if (_marshaller == Marshaller.Jackson)
            return populateJacksonForm();
        else
            return populateJSONObjectForm();
    }


    @NotNull
    private Pair<FORM, BindException> defaultPopulateForm() throws Exception
    {
        saveRequestedApiVersion(getViewContext().getRequest(), null);

        BindException errors = defaultBindParameters(getCommand(), getPropertyValues());
        FORM form = (FORM)errors.getTarget();

        return Pair.of(form, errors);
    }

    /**
     * Use Jackson to parse POST body as JSON and instantiate the FORM class directly.
     */
    @NotNull
    // Leave this protected; client-developed action classes override it. See #38307
    protected Pair<FORM, BindException> populateJacksonForm() throws Exception
    {
        FORM form = null;
        BindException errors;

        try
        {
            Class c = getCommandClass();
            // Ideally, ObjectReader would handle the Object case as well, but currently readValue() throws with "end-of-input" exception
            if (Object.class != c)
            {
                ObjectReader reader = getObjectReader(c);
                form = reader.readValue(getViewContext().getRequest().getInputStream());
            }
            else
            {
                form = (FORM)new Object();
            }
            errors = new NullSafeBindException(form, "form");
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

    private ObjectMapper getRequestObjectMapper()
    {
        return _requestObjectMapper == null ? _requestObjectMapper = createRequestObjectMapper() : _requestObjectMapper;
    }

    private ObjectMapper getResponseObjectMapper()
    {
        return _responseObjectMapper == null ? _responseObjectMapper = createResponseObjectMapper() : _responseObjectMapper;
    }

    /**
     * Clone and configure the Jackson ObjectMapper for use in serialization/deserialization.
     * If you need to perform custom configuration, override this method and create
     * a copy of the <code>JsonUtil.DEFAULT_MAPPER</code>.
     *
     * Example:
     * <pre>
     *     ObjectMapper om = JsonUtil.DEFAULT_MAPPER.copy();
     *     om.addMixin(GWTDomain.class, GWTDomainMixin.class);
     *     return om;
     * </pre>
     */
    protected ObjectMapper createRequestObjectMapper()
    {
        return JsonUtil.DEFAULT_MAPPER;
    }

    /**
     * {@link #createRequestObjectMapper()}
    */
    protected ObjectMapper createResponseObjectMapper()
    {
        return JsonUtil.DEFAULT_MAPPER;
    }

    protected ObjectReader getObjectReader(Class c)
    {
        return getRequestObjectMapper().readerFor(c);
    }

    /**
     * Parse POST body as JSONObject then use either CustomApiForm or spring form binding to populate the FORM instance.
     */
    @NotNull
    private Pair<FORM, BindException> populateJSONObjectForm() throws Exception
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


    private boolean _empty(Object o)
    {
        return null == o || (o instanceof String && ((String)o).isEmpty());
    }

    // Leave this protected; client-developed action classes call it. See #38307
    protected void saveRequestedApiVersion(HttpServletRequest request, Object obj)
    {
        Object o = null;

        if (obj instanceof Map && ((Map) obj).containsKey(CommonParameters.apiVersion.name()))
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
    }


    public double getRequestedApiVersion()
    {
        assert _requestedApiVersion >= 0;
        return _requestedApiVersion < 0 ? 0 : _requestedApiVersion;
    }


    private JSONObject getJsonObject() throws IOException
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

    private BindException populateForm(JSONObject jsonObj, FORM form)
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

    @Override
    public final void validate(Object form, Errors errors)
    {
        try (Timing ignored = MiniProfiler.step("validate"))
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

    protected ApiResponseWriter createResponseWriter() throws IOException
    {
        // Let the response format dictate how we write the response. Typically JSON, but not always.
        ApiResponseWriter writer = ApiResponseWriter.getResponseFormat(getViewContext().getRequest(), ApiResponseWriter.Format.JSON).createWriter(getViewContext().getResponse(), getContentTypeOverride(), getResponseObjectMapper());
        if (_marshaller == Marshaller.Jackson)
            writer.setSerializeViaJacksonAnnotations(true);
        return writer;
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
    protected boolean isServerSideRequest()
    {
        return getViewContext().getRequest() instanceof MockHttpServletRequest;
    }

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

    void notFound() throws NotFoundException
    {
        throw new NotFoundException();
    }

    void notFound(String message) throws NotFoundException
    {
        throw new NotFoundException(message);
    }

}

