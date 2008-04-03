/*
 * Copyright (c) 2007 LabKey Software Foundation
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
import org.labkey.api.query.QueryView;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.view.NotFoundException;

import javax.servlet.http.HttpServletResponse;
import java.sql.ResultSet;
import java.util.Map;

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
        super(response);
        response.setContentType(CONTENT_TYPE_JSON);
        response.setCharacterEncoding("utf-8");
    }

    public void write(ApiResponse response) throws Exception
    {
        if(response instanceof ApiCustomRender)
            ((ApiCustomRender)response).render(Format.JSON, getResponse());
        else
        {
            JSONObject json = new JSONObject(response.getProperties());
            //json.write(_response.getWriter()); //use this for compact output
            getResponse().getWriter().write(json.toString(4)); //or this for pretty output
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

        //jsonObj.write(getResponse().getWriter()); //use this for compact output
        getResponse().getWriter().write(jsonObj.toString(4)); //or this for pretty output
    }
}
