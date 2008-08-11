<%
/*
 * Copyright (c) 2003-2008 Fred Hutchinson Cancer Research Center
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
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    response.setContentType("text/css");
%>

@page { size: 8.5in 11in; margin: 0.79in }

.no-print,
#leftmenupanel, #headerpanel, #navpanel,
.labkey-button-bar
{
    display: none;
}

.labkey-data-region .labkey-details,
.labkey-data-region .labkey-selectors
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