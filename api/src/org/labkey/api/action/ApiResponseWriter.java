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

import org.labkey.api.query.QueryView;
import org.springframework.validation.Errors;

import javax.servlet.http.HttpServletResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.io.IOException;

/**
 * Used by API actions to writer various types of objects to the response stream.
 * Note that this class is abstract--use the dervied classes to writer objects
 * in various formats (JSON, XML, etc.)
 *
 * Created by IntelliJ IDEA.
 * User: Dave
 * Date: Feb 8, 2008
 * Time: 1:43:38 PM
 */
public abstract class ApiResponseWriter
{
    public enum Format
    {
        JSON,
        XML
    }

    private HttpServletResponse _response = null;

    public ApiResponseWriter(HttpServletResponse response)
    {
        _response = response;
    }

    public abstract void write(ApiResponse response) throws Exception;

    public abstract void write(Throwable e) throws Exception;

    public abstract void write(Errors errors) throws Exception;

    protected HttpServletResponse getResponse()
    {
        return _response;
    }

}
