<%
if ("GET".equals(request.getMethod()))
{
    %><html>
    <head>
    <title>Redirect Page</title>
    <meta http-equiv="Refresh" content="0; URL=./project/home/begin.view">
    <link rel="shortcut icon" href="./favicon.image" />
    <link rel="icon" href="./favicon.image" />
    <link rel="stylesheet" href="./core/stylesheet.view" type="text/css">
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