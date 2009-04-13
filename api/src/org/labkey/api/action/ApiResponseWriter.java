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

import org.springframework.validation.Errors;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Stack;

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
    protected static class StreamState
    {
        private String _name;
        private String _separator;
        private int _level = 0;

        public StreamState() {}
        public StreamState(String name)
        {
            this(name, 0);
        }
        public StreamState(String name, int level)
        {
            _name = name;
        }

        public String getName() { return _name;}
        public int getLevel() { return _level;}
        public String getSeparator()
        {
            //first time return ""
            //all successive times, return ","
            if(null == _separator)
            {
                _separator = ",\n";
                return "";
            }
            else
                return _separator;
        }
    }

    protected Stack<StreamState> _streamStack = new Stack<StreamState>();

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

    public abstract void write(ApiResponse response) throws IOException;

    public abstract void write(Throwable e) throws IOException;

    public abstract void write(Errors errors) throws IOException;

    //stream oriented methods
    public abstract void startResponse() throws IOException;

    public abstract void endResponse() throws IOException;

    public abstract void startMap(String name) throws IOException;

    public abstract void endMap() throws IOException;

    public abstract void writeProperty(String name, Object value) throws IOException;

    public abstract void startList(String name) throws IOException;

    public abstract void endList() throws IOException;

    public abstract void writeListEntry(Object entry) throws IOException;

    protected HttpServletResponse getResponse()
    {
        return _response;
    }

}
