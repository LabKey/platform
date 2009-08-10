<%
/*
 * Copyright (c) 2005-2009 LabKey Corporation
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
%>
<%
if ("GET".equals(request.getMethod()))
{
    %><html>
    <head>
    <title>Redirect Page</title>
    <meta http-equiv="Refresh" content="0; URL=./project/home/begin.view">
    <link rel="shortcut icon" href="./favicon.image" />
    <link rel="icon" href="./favicon.image" />
    <link rel="stylesheet" href="./stylesheet.css" type="text/css">
    <link rel="stylesheet" href="./core/themeStylesheet.view" type="text/css">
    </head>
    <body bgcolor="#FFFFFF" text="#000000">
    <script type="text/javascript"><!--
    window.location.replace("./project/home/begin.view");
    --></script>
    <p><a href="./project/home/begin.view">Redirect to Home Page</a></p>
    </body>
    </html><%
    return;
}

if ("OPTIONS".equals(request.getMethod()))
{
    response.addHeader("DAV", "1,2");
    response.addHeader("MS-Author-Via", "DAV");
    response.setHeader("Allow", "OPTIONS, GET, HEAD, POST, DELETE, COPY, MOVE, PROPFIND");
    return;
}

if ("PROPFIND".equals(request.getMethod()))
{
    String resourcePath = request.getContextPath();
    if (!resourcePath.endsWith("/"))
        resourcePath += "/";
    response.setStatus(207);
    response.setContentType("text/xml; charset=UTF-8");
    %><?xml version="1.0" encoding="utf-8" ?>
    <multistatus xmlns="DAV:">
    <response><href><%=resourcePath%></href><propstat>
        <prop><getcontentlength>0</getcontentlength>
        <resourcetype><collection/></resourcetype>
        </prop>
        <status>HTTP/1.1 200 OK</status>
     </propstat></response>
        <response><href><%=resourcePath%>webdav/</href><propstat>
            <prop><getcontentlength>0</getcontentlength>
            <resourcetype><collection/></resourcetype>
            </prop>
            <status>HTTP/1.1 200 OK</status>
         </propstat></response>
    </multistatus><%
    return;
}%>