/*
 * Copyright (c) 2018 LabKey Corporation
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
package org.labkey.api.webdav;

import javax.servlet.http.HttpServletResponse;

public enum WebdavStatus
{
    SC_CONTINUE(HttpServletResponse.SC_CONTINUE, "Continue"),
    //101=Switching Protocols
    //102=Processing
    SC_OK(HttpServletResponse.SC_OK, "OK"),
    SC_CREATED(HttpServletResponse.SC_CREATED, "Created"),
    SC_ACCEPTED(HttpServletResponse.SC_ACCEPTED, "Accepted"),
    //203=Non-Authoritative Information
    SC_NO_CONTENT(HttpServletResponse.SC_NO_CONTENT, "No Content"),
    //205=Reset Content
    SC_PARTIAL_CONTENT(HttpServletResponse.SC_PARTIAL_CONTENT, "Partial Content"),
    SC_MULTI_STATUS(207, "Multi-Status"),
    SC_FILE_MATCH(208, "File Conflict"),
    //300=Multiple Choices
    SC_MOVED_PERMANENTLY(HttpServletResponse.SC_MOVED_PERMANENTLY, "Moved Permanently"),
    SC_MOVED_TEMPORARILY(HttpServletResponse.SC_MOVED_TEMPORARILY, "Moved Temporarily"),    // Found
    SC_SEE_OTHER(HttpServletResponse.SC_SEE_OTHER, "See Other"),    // Found
    SC_NOT_MODIFIED(HttpServletResponse.SC_NOT_MODIFIED, "Not Modified"),
    //305=Use Proxy
    SC_TEMPORARY_REDIRECT(HttpServletResponse.SC_TEMPORARY_REDIRECT, "Temporary Redirect"),
    SC_BAD_REQUEST(HttpServletResponse.SC_BAD_REQUEST, "Bad Request"),
    SC_UNAUTHORIZED(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"),
    //402=Payment Required
    SC_FORBIDDEN(HttpServletResponse.SC_FORBIDDEN, "Forbidden"),
    SC_NOT_FOUND(HttpServletResponse.SC_NOT_FOUND, "Not Found"),
    SC_METHOD_NOT_ALLOWED(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Method Not Allowed"),
    //406=Not Acceptable
    //407=Proxy Authentication Required
    //408=Request Time-out
    SC_CONFLICT(HttpServletResponse.SC_CONFLICT, "Conflict"),
    //410=Gone
    //411=Length Required
    SC_PRECONDITION_FAILED(HttpServletResponse.SC_PRECONDITION_FAILED, "Precondition Failed"),
    SC_REQUEST_TOO_LONG(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Request Too Long"),
    //414=Request-URI Too Large
    SC_UNSUPPORTED_MEDIA_TYPE(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, "Unsupported Media Type"),
    SC_REQUESTED_RANGE_NOT_SATISFIABLE(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE, "Requested Range Not Satisfiable"),
    //417=Expectation Failed
    SC_UNPROCESSABLE_ENTITY(418, "Unprocessable Entity"),
    SC_INSUFFICIENT_SPACE_ON_RESOURCE(419, "Insufficient Space On Resource"),
    SC_METHOD_FAILURE(420, "Method Failure"),
    //422=Unprocessable Entity
    SC_LOCKED(423, "Locked"),
    //424=Failed Dependency
    SC_INTERNAL_SERVER_ERROR(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error"),
    SC_NOT_IMPLEMENTED(HttpServletResponse.SC_NOT_IMPLEMENTED, "Not Implemented"),
    SC_BAD_GATEWAY(HttpServletResponse.SC_BAD_GATEWAY, "Bad Gateway"),
    SC_SERVICE_UNAVAILABLE(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Service Unavailable")
    //504=Gateway Time-out
    //505=HTTP Version not supported
    //507=Insufficient Storage
    ;

    public final int code;
    public final String message;

    WebdavStatus(int code, String text)
    {
        this.code = code;
        this.message = text;
    }

    public static WebdavStatus fromCode(int code)
    {
        for (WebdavStatus status : values())
            if (status.code == code)
                return status;

        throw new IllegalArgumentException("Unknown code: " + code);
    }

    public String toString()
    {
        return "" + code + " " + message;
    }
}


