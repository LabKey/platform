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

import org.json.JSONObject;
import org.labkey.api.query.PropertyValidationError;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;

/*
* User: Dave
* Date: Sep 3, 2008
* Time: 11:03:32 AM
*/

/**
 * This writer extends ApiJsonWriter by writing validation errors in the format
 * that Ext forms require.
 *
 * Ext has a particular format they use for consuming validation errors expressed in JSON:
 * <pre>
 * {
 *      success: false,
 *      errors: {
 *          clientCode: "Client not found",
 *          portOfLoading: "This field must not be null"
 *      }
 * }
 * </pre>
 *
 * This is a bit strange, since you can't provide more than one error
 * for a given field, and there's really no place to put object-level
 * errors. We use the virtual field "_form" for global errors.
 *
 * Also, the response code must be SC_OK(200).
 *
 * See: http://extjs.com/deploy/dev/docs/?class=Ext.form.Action.Submit
 *
 * NOTE: When returning JSON for ExtJS BasicForm with a file upload field, the
 * response Content-Type must be "text/html" with a html encoded json body.
 *
 * See: http://docs.sencha.com/extjs/4.1.3/#!/api/Ext.form.Basic-method-hasUpload
 *
 * When not submitting a file upload or when using XMLHttpRequest directly (e.g,
 * with FormData as in LABKEY.Query.importData) the response Content-Type should be application/json.
 */
public class ExtFormResponseWriter extends ApiJsonWriter
{
    boolean sendHtmlJsonResponse = false;
    boolean startResponse = true;
    
    public ExtFormResponseWriter(HttpServletResponse response) throws IOException
    {
        super(response);
        setErrorResponseStatus(HttpServletResponse.SC_OK);
    }

    public ExtFormResponseWriter(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        this(response);
        if (!"XMLHttpRequest".equals(request.getHeader("X-Requested-With")) && (request instanceof MultipartHttpServletRequest))
            sendHtmlJsonResponse = true;
        response.setContentType(sendHtmlJsonResponse ? "text/html" : CONTENT_TYPE_JSON);
    }

    public  ExtFormResponseWriter(HttpServletRequest request, HttpServletResponse response, String contentTypeOverride) throws IOException
    {
        this(request, response);
        if (!sendHtmlJsonResponse && null != contentTypeOverride)
            response.setContentType(contentTypeOverride);
    }


    public void write(ValidationException e) throws IOException
    {
        try
        {
            writeObject(toJSON(e));
        }
        finally
        {
            close();
        }
    }

    public JSONObject toJSON(ValidationException e)
    {
        String message = null;
        JSONObject jsonErrors = new JSONObject();

        for (ValidationError error : e.getErrors())
        {
            if (message == null)
                message = error.getMessage();
            toJSON(jsonErrors, error);
        }

        JSONObject obj = new JSONObject();
        obj.put("exception", message == null ? "(No error message)" : message);
        obj.put("errors", jsonErrors);
        if (e.getSchemaName() != null)
            obj.put("schemaName", e.getSchemaName());
        if (e.getQueryName() != null)
            obj.put("queryName", e.getQueryName());
        if (e.getRowNumber() > -1)
            obj.put("rowNumber", e.getRowNumber());
        return obj;
    }

    public void toJSON(JSONObject jsonErrors, ValidationError error)
    {
        String msg = error.getMessage();
        String key = "_form";
        if (error instanceof PropertyValidationError)
            key = ((PropertyValidationError)error).getProperty();
        if (jsonErrors.has(key))
            msg = jsonErrors.get(key) + "; " + msg;
        jsonErrors.put(key, msg);
    }

    @Override
    public void writeAndClose(Errors errors) throws IOException
    {
        JSONObject jsonErrors = new JSONObject();
        for(ObjectError error : errors.getAllErrors())
        {
            String msg = error.getDefaultMessage();
            String key = "_form";
            if (error instanceof FieldError)
                key = ((FieldError)error).getField();
            if (jsonErrors.has(key))
                msg = jsonErrors.get(key) + "; " + msg;
            jsonErrors.put(key, msg);
        }

        JSONObject root = new JSONObject();
        root.put("success", false); //used by Ext forms
        root.put("errors", jsonErrors);
        try
        {
            writeObject(root);
        }
        finally
        {
            close();
        }
    }

    @Override
    protected Writer getWriter()
    {
        Writer w = super.getWriter();
        if (null == w)
            return null;
        if (sendHtmlJsonResponse && startResponse)
        {
            startResponse = false;
            try
            {
            w.write("<html><body><textarea>");
            }
            catch (IOException x)
            {
                
            }
        }
        return w;
    }
}
