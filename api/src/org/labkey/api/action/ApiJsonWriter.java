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
import java.util.List;

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
        if(response instanceof ApiCustomRender)
            ((ApiCustomRender)response).render(Format.JSON, getResponse());
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
}
