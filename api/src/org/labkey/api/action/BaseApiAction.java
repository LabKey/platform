/*
 * Copyright (c) 2014 LabKey Corporation
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
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Map;

/**
 * Base class for API actions.
 *
 * User: Dave
 * Date: Feb 8, 2008
 */
public abstract class BaseApiAction<FORM, RESP> extends BaseViewAction<FORM>
{
    private ApiResponseWriter.Format _reqFormat = null;
    private ApiResponseWriter.Format _respFormat = ApiResponseWriter.Format.JSON;
    private String _contentTypeOverride = null;
    private double _requestedApiVersion = -1;

    protected enum CommonParameters
    {
        apiVersion
    }

    public BaseApiAction()
    {
        setUseBasicAuthentication(true);
    }

    public BaseApiAction(Class<? extends FORM> formClass)
    {
        super(formClass);
        setUseBasicAuthentication(true);
    }

    protected String getCommandClassMethodName()
    {
        return "execute";
    }


    protected boolean isPost()
    {
        return "POST".equals(getViewContext().getRequest().getMethod());
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
            Pair<FORM, BindException> pair = populateForm();
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
                createResponseWriter().write((Errors)errors);
            else
            {
                RESP response = execute(form, errors);
                if (isFailure(errors))
                    createResponseWriter().write((Errors)errors);
                else if (null != response)
                    createResponseWriter().writeResponse(response);
            }
        }
        catch (BindException e)
        {
            createResponseWriter().write((Errors)e);
        }
        //don't log exceptions that result from bad inputs
        catch (BatchValidationException e)
        {
            // Catch separately to be sure that we call the subclass-specific write() method
            createResponseWriter().write(e);
        }
        catch (ValidationException e)
        {
            // Catch separately to be sure that we call the subclass-specific write() method
            createResponseWriter().write(e);
        }
        catch (QueryException | IllegalArgumentException |
                NotFoundException | InvalidKeyException | ApiUsageException e)
        {
            createResponseWriter().write(e);
        }
        catch (UnauthorizedException e)
        {
            e.setUseBasicAuthentication(_useBasicAuthentication);
            throw e;
        }
        catch (Exception e)
        {
            ExceptionUtil.logExceptionToMothership(getViewContext().getRequest(), e);
            Logger.getLogger(ApiAction.class).warn("ApiAction exception: ", e);

            createResponseWriter().write(e);
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

    // CONSIDER: Extract ApiRequestReader similar to the ApiResponseWriter
    // CONSIDER: Something like Jersey's MessageBodyReader? https://jax-rs-spec.java.net/nonav/2.0/apidocs/javax/ws/rs/ext/MessageBodyReader.html
    protected Pair<FORM, BindException> populateForm() throws Exception
    {
        String contentType = getViewContext().getRequest().getContentType();
        if (null != contentType)
        {
            if (contentType.contains(ApiJsonWriter.CONTENT_TYPE_JSON))
            {
                _reqFormat = ApiResponseWriter.Format.JSON;
                return populateJsonForm();
            }
//            else if (contentType.contains(ApiXmlWriter.CONTENT_TYPE))
//            {
//                _reqFormat = ApiResponseWriter.Format.XML;
//                return populateXmlForm();
//            }
        }

        return defaultPopulateForm();
    }

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
     * @return
     * @throws Exception
     */
    protected Pair<FORM, BindException> populateJsonForm() throws Exception
    {
        FORM form = null;
        BindException errors = null;

        try
        {
            ObjectMapper mapper = new ObjectMapper();
            Class c = getCommandClass();
            if (c != null)
            {
                ObjectReader reader = mapper.reader(getCommandClass());
                form = reader.readValue(getViewContext().getRequest().getInputStream());
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
            if (errors == null)
                errors = new NullSafeBindException(new Object(), "form");
            errors.reject(SpringActionController.ERROR_MSG, "Error binding property: " + x.getMessage());
        }
        catch (JsonProcessingException x)
        {
            // Bad JSON
            getViewContext().getResponse().sendError(HttpServletResponse.SC_BAD_REQUEST, x.getMessage());
            return null;
        }

        saveRequestedApiVersion(getViewContext().getRequest(), form);
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

    public final void validate(Object form, Errors errors)
    {
        validateForm((FORM)form, errors);
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

        writer.setSerializeViaJacksonAnnotations(false);

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

    public abstract RESP execute(FORM form, BindException errors) throws Exception;

}
