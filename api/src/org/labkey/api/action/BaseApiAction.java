/*
 * Copyright (c) 2014-2026 LabKey Corporation
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
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.miniprofiler.MiniProfiler;
import org.labkey.api.miniprofiler.Timing;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.RuntimeValidationException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.StrictBoundedReader;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.HttpUtil;
import org.labkey.api.util.JsonUtil;
import org.labkey.api.util.MimeMap;
import org.labkey.api.util.ResponseHelper;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.view.BadRequestException;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValues;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Map;

/**
 * Common base class for all API actions
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
            Class<?> superClass = getClass().getSuperclass();
            if (null != superClass)
                marshal = superClass.getAnnotation(Marshal.class);
        }
        if (marshal == null)
        {
            Class<?> declaringClass = getClass().getDeclaringClass();
            if (declaringClass != null)
                marshal = declaringClass.getAnnotation(Marshal.class);
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
        return switch (getViewContext().getMethod())
        {
            case POST, PUT, DELETE, PATCH -> handlePost();
            case GET -> handleGet();
            default ->
                    throw new BadRequestException("Method Not Allowed: " + getViewContext().getRequest().getMethod(), null, HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        };
    }

    @Override
    public void setViewContext(ViewContext context)
    {
        // Issue 34825 - don't prompt for basic auth for browser requests
        setUnauthorizedType(HttpUtil.isBrowser(context.getRequest()) ? UnauthorizedException.Type.sendUnauthorized : UnauthorizedException.Type.sendBasicAuth);
        super.setViewContext(context);
    }

    private void writeResponse(Object o) throws IOException
    {
        try (var writer = createResponseWriter())
        {
            writer.writeResponse(o);
        }
    }

    private void writeResponse(Exception ex) throws IOException
    {
        try (var writer = createResponseWriter())
        {
            writer.writeResponse(ex);
        }
    }

    private void writeResponse(Errors errors) throws IOException
    {
        try (var writer = createResponseWriter())
        {
            writer.writeResponse(errors);
        }
    }


    @SuppressWarnings("TryWithIdenticalCatches")
    public ModelAndView handlePost() throws Exception
    {
        getViewContext().getResponse().setHeader("X-Robots-Tag", "noindex");

        try
        {
            FormAndErrors<FORM> pair;

            try
            {
                pair = populateForm();
            }
            catch (BadRequestException bad)
            {
                getViewContext().getResponse().sendError(bad.getStatus(), bad.getMessage());
                return null;
            }

            FORM form = pair.form;
            BindException errors = pair.errors;

            if (form != null)
            {
                // validate the form, if a binding error didn't prevent it from being created. See issue 40888
                validate(form, errors);
            }

            //if we had binding or validation errors,
            //return them without calling execute.
            if (isFailure(errors))
            {
                writeResponse((Errors) errors);
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
                        writeResponse((Errors) errors);
                    else if (null != response)
                        writeResponse(response);
                }
            }
        }
        catch (BindException e)
        {
            writeResponse((Errors) e);
        }
        //don't log exceptions that result from bad inputs
        catch (BatchValidationException e)
        {
            // Catch separately to be sure that we call the subclass-specific write() method
            writeResponse(e);
        }
        catch (ValidationException e)
        {
            // Catch separately to be sure that we call the subclass-specific write() method
            writeResponse(e);
        }
        catch (RuntimeValidationException e)
        {
            // Catch separately to be sure that we call the subclass-specific write() method
            writeResponse(e.getValidationException());
        }
        catch (QueryException | IllegalArgumentException |
                NotFoundException | InvalidKeyException | ApiUsageException e)
        {
            writeResponse(e);
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

            writeResponse(e);
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
    private FormAndErrors<FORM> populateForm() throws Exception
    {
        try (Timing ignored = MiniProfiler.step("bind"))
        {
            String contentType = getViewContext().getRequest().getContentType();
            if (null != contentType)
            {
                if (MimeMap.DEFAULT.isJsonContentTypeHeader(contentType))
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
    private FormAndErrors<FORM> populateJsonForm() throws Exception
    {
        if (_marshaller == Marshaller.Jackson)
            return populateJacksonForm();
        else
            return populateJSONObjectForm();
    }


    @NotNull
    private FormAndErrors<FORM> defaultPopulateForm() throws Exception
    {
        saveRequestedApiVersion(getViewContext().getRequest(), null);

        BindException errors = defaultBindParameters(getPropertyValues());
        FORM form = (FORM)errors.getTarget();

        return new FormAndErrors<>(form, errors);
    }

    public record FormAndErrors<FORM>(FORM form, BindException errors)
    {
    }

    /**
     * Use Jackson to parse POST body as JSON and instantiate the FORM class directly.
     */
    @NotNull
    // Leave this protected; client-developed action classes override it. See #38307
    protected FormAndErrors<FORM> populateJacksonForm() throws Exception
    {
        FORM form = null;
        BindException errors;

        try
        {
            Class<?> c = getCommandClass();
            // Ideally, ObjectReader would handle the Object case as well, but currently readValue() throws with "end-of-input" exception
            if (Object.class != c)
            {
                ObjectReader objectReader = getObjectReader(c);
                try (Reader requestReader = openRequestReader())
                {
                    form = objectReader.readValue(requestReader);
                }
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
        catch (JsonProcessingException | StrictBoundedReader.LimitExceededException x)
        {
            // Bad JSON
            throw new BadRequestException(x.getMessage(), x);
        }

        saveRequestedApiVersion(getViewContext().getRequest(), form);
        return new FormAndErrors<>(form, errors);
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

    protected ObjectReader getObjectReader(Class<?> c)
    {
        return getRequestObjectMapper().readerFor(c);
    }

    /**
     * Parse POST body as JSONObject then use either ApiJsonForm or spring form binding to populate the FORM instance.
     */
    @NotNull
    private FormAndErrors<FORM> populateJSONObjectForm() throws Exception
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

        // Records are immutable, so we can't instantiate the form up front and populate it; instead collect the JSON
        // properties and construct the record via defaultBindParameters(). Note: record forms can't implement
        // ApiJsonForm since that relies on mutating an existing instance.
        if (getCommandClass().isRecord())
        {
            PropertyValues values = null == jsonObj ? new MutablePropertyValues() : new JsonPropertyValues(jsonObj);
            BindException errors = defaultBindParameters(values);
            return new FormAndErrors<>((FORM)errors.getTarget(), errors);
        }

        FORM form = getCommand();
        BindException errors = populateForm(jsonObj, form);
        return new FormAndErrors<>(form, errors);
    }


    private boolean _empty(Object o)
    {
        return null == o || (o instanceof String && ((String)o).isEmpty());
    }

    // Leave this protected; client-developed action classes call it. See #38307
    protected void saveRequestedApiVersion(HttpServletRequest request, @Nullable Object obj)
    {
        Object o = null;

        if (obj instanceof JSONObject jo)
            o = jo.opt(CommonParameters.apiVersion.name());
        else if (obj instanceof Map<?, ?> map && map.containsKey(CommonParameters.apiVersion.name()))
            o = map.get(CommonParameters.apiVersion.name());
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

    protected long getMaximumJsonInputLength()
    {
        JsonInputLimit limitAnnotation = getClass().getAnnotation(JsonInputLimit.class);
        return limitAnnotation == null ? JsonInputLimit.DEFAULT : limitAnnotation.value();
    }

    private @Nullable JSONObject getJsonObject() throws IOException
    {
        HttpServletRequest request = getViewContext().getRequest();
        if (request == null)
            return null;

        try (Reader reader = openRequestReader())
        {
            JSONTokener tokener = new JSONTokener(reader);
            return tokener.more() ? new JSONObject(tokener) : null;
        }
    }

    private Reader openRequestReader() throws IOException
    {
        HttpServletRequest request = getViewContext().getRequest();
        String characterEncoding = request.getCharacterEncoding();
        if (characterEncoding == null)
            characterEncoding = StringUtilsLabKey.DEFAULT_CHARSET.name();
        long maxLength = getMaximumJsonInputLength();
        // Issue 53699: Use request.getInputStream() instead of request.getReader() to
        // avoid BufferUnderflowException when processing multibyte character JSON payloads.
        Reader streamReader = new InputStreamReader(request.getInputStream(), characterEncoding);
        return maxLength > 0 ? new StrictBoundedReader(streamReader, maxLength) : streamReader;
    }

    private BindException populateForm(@Nullable JSONObject jsonObj, FORM form)
    {
        if (null == jsonObj)
            return new NullSafeBindException(form, "form");

        if (form instanceof ApiJsonForm ajf)
        {
            ajf.bindJson(jsonObj);
            return new NullSafeBindException(ajf, "form");
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
            for (String key : jsonObj.keySet())
            {
                Object value = jsonObj.get(key);

                if (value == JSONObject.NULL)
                {
                    value = null;
                }
                else if (value instanceof JSONArray array)
                {
                    value = array.toList().toArray();
                }
                else if (value instanceof JSONObject)
                {
                    throw new IllegalArgumentException("Nested objects and arrays are not supported at this time.");
                }

                addPropertyValue(key, value);
            }
        }
    }

    @Override
    public final void validate(@NotNull Object form, @NotNull Errors errors)
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

    public static SimpleResponse<Void> success()
    {
        return new SimpleResponse<>(true);
    }

    public static SimpleResponse<String> success(String message)
    {
        return new SimpleResponse<>(true, message);
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

