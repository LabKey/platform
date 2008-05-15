<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    response.setContentType("text/css");
%>

@page { size: 8.5in 11in; margin: 0.79in }

.no-print,
#leftmenupanel, #headerpanel, #navpanel,
.dataregion_header,
.dataregion_footer,
.button-bar
{
    display: none;
}

.grid .details,
.grid .selectors
{
    visibility: collapse;
    display: none;
}

h1, h2, h3, h4, h5, h6
{
    page-break-after: avoid;
}

p, blockquote, ul, ol, dl, pre
{
    page-break-inside: avoid;
}

div.pagebreak
{
    page-break-before: always;
}