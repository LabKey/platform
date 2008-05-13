/*
 * Copyright (c) 2008 LabKey Corporation
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
import org.json.JSONArray;
import org.labkey.api.query.QueryView;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.view.NotFoundException;
import org.springframework.validation.Errors;
import org.springframework.validation.ObjectError;
import org.springframework.validation.FieldError;

import javax.servlet.http.HttpServletResponse;
import java.sql.ResultSet;
import java.util.Map;
import java.util.HashMap;
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

    public void write(ApiResponse response) throws Exception
    {
        if(response instanceof ApiCustomRender)
            ((ApiCustomRender)response).render(Format.JSON, getResponse());
        else
        {
            JSONObject json = new JSONObject(response.getProperties());
            writeJsonObj(json);
        }
    }

    public void write(Throwable e) throws Exception
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

    public void write(Errors errors) throws Exception
    {
        /*
            Ext has a particular format they use for consuming validation errors expressed in JSON:
            {
                success: false,
                errors: {
                    clientCode: "Client not found",
                    portOfLoading: "This field must not be null"
                    }
            }

            This is a bit strange, since you can't provide more than one error
            for a given field, and there's really no place to put object-level
            errors.

            See http://extjs.com/deploy/dev/docs/?class=Ext.form.Action.Submit
        */

        //getResponse().setStatus(400); //ext seems to require that the response code is 200 even for validation errors

        JSONObject jsonErrors = new JSONObject();
        for(ObjectError error : (List<ObjectError>)errors.getAllErrors())
        {
            String msg = error.getDefaultMessage();
            String key = error.getObjectName();

            if(error instanceof FieldError)
            {
                FieldError ferror = (FieldError)error;
                key = ferror.getField();
                msg = ferror.getDefaultMessage();

                if(jsonErrors.has(ferror.getField()))
                    msg = jsonErrors.get(ferror.getField()) + " " + ferror.getDefaultMessage();
            }

            jsonErrors.put(key, msg);
        }

        JSONObject root = new JSONObject();
        root.put("success", false); //used by Ext forms
        root.put("errors", jsonErrors);
        writeJsonObj(root);
    }

    protected void writeJsonObj(JSONObject obj) throws Exception
    {
        //jsonObj.write(getResponse().getWriter()); //use this for compact output
        getResponse().getWriter().write(obj.toString(4)); //or this for pretty output
    }
}
