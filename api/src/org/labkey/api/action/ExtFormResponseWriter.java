/*
 * Copyright (c) 2008-2026 LabKey Corporation
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
import org.labkey.api.util.HttpUtil;
import org.labkey.api.util.MimeMap;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.FilterWriter;
import java.io.IOException;
import java.io.Writer;

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
    private Writer _encodingWriter;
    private boolean _closed;

    public ExtFormResponseWriter(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        boolean sendHtml = !"XMLHttpRequest".equals(request.getHeader("X-Requested-With")) &&
                request instanceof MultipartHttpServletRequest &&
                HttpUtil.isBrowser(request);
        super(response, sendHtml ? MimeMap.MimeType.HTML.getContentType() : CONTENT_TYPE_JSON);
        setErrorResponseStatus(HttpServletResponse.SC_OK);
    }

    private boolean isHtml()
    {
        return getResponse().getContentType().startsWith(MimeMap.MimeType.HTML.getContentType());
    }

    public ExtFormResponseWriter(HttpServletRequest request, HttpServletResponse response, String contentTypeOverride) throws IOException
    {
        this(request, response);
        if (!isHtml() && null != contentTypeOverride)
            response.setContentType(contentTypeOverride);
    }

    @Override
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
        if (error instanceof PropertyValidationError pve)
            key = pve.getProperty();
        if (jsonErrors.has(key))
            msg = jsonErrors.get(key) + "; " + msg;
        jsonErrors.put(key, msg);
    }

    @Override
    public void writeResponse(Errors errors) throws IOException
    {
        String message = null;
        JSONObject jsonErrors = new JSONObject();
        for (ObjectError error : errors.getAllErrors())
        {
            String msg = error.getDefaultMessage();
            if (message == null)
                message = msg;
            String key = "_form";
            if (error instanceof FieldError fieldError)
                key = fieldError.getField();
            if (jsonErrors.has(key))
                msg = jsonErrors.get(key) + "; " + msg;
            jsonErrors.put(key, msg);
        }

        JSONObject root = new JSONObject();
        root.put("success", false); //used by Ext forms
        // used by client api for FormApiActions (e.g. query-import.api and ImportDataCommand in java clientapi)
        root.put("exception", message == null ? "(No error message)" : message);
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
    public void close() throws IOException
    {
        super.close();
        if (isHtml() && !_closed)
        {
            _encodingWriter.flush();
            Writer w = super.getWriter();
            if (w != null)
            {
                w.flush();
            }
        }
        _closed = true;
    }

    @Override
    protected Writer getWriter()
    {
        Writer w = super.getWriter();
        if (null == w)
            return null;
        if (isHtml() && _encodingWriter == null)
        {
            _encodingWriter = new HtmlEncodingWriter(w);
        }
        return _encodingWriter != null ? _encodingWriter : w;
    }

    /** Wraps a Writer to HTML-encode all output, so JSON can be safely embedded inside a &lt;textarea&gt; element. */
    private static class HtmlEncodingWriter extends FilterWriter
    {
        HtmlEncodingWriter(Writer out)
        {
            super(out);
        }

        @Override
        public void write(int c) throws IOException
        {
            switch (c)
            {
                case '&' -> out.write("&amp;");
                case '<' -> out.write("&lt;");
                case '>' -> out.write("&gt;");
                case '"' -> out.write("&quot;");
                case '\'' -> out.write("&#39;");
                default -> out.write(c);
            }
        }

        @Override
        public void write(char[] cbuf, int off, int len) throws IOException
        {
            for (int i = off; i < off + len; i++)
                write(cbuf[i]);
        }

        @Override
        public void write(String str, int off, int len) throws IOException
        {
            for (int i = off; i < off + len; i++)
                write(str.charAt(i));
        }
    }
}
