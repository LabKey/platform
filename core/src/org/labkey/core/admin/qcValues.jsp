<%
    /*
    * Copyright (c) 2005-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.data.QcUtil" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

QC Values:

<p>
    <%
        Map<String, String> qcValuesAndLabels = QcUtil.getValuesAndLabels(ContainerManager.getRoot());
        for (Map.Entry entry : qcValuesAndLabels.entrySet())
        {
            out.print(entry.getKey() + ": " + entry.getValue());
            out.print("<br>\n");
        }
    %>

</p>