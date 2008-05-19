<%
/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.data.SqlScriptRunner"%>
<%@ page extends="org.labkey.core.admin.sql.ShowRunningScriptsPage" %>

<script type="text/javascript">
var req;

makeRequest();

function makeRequest()
{
   var url = "<%= getWaitForScriptsUrl() %>";
   if (window.XMLHttpRequest)
   {
       req = new XMLHttpRequest();
   }
   else if (window.ActiveXObject)
   {
       req = new ActiveXObject("Microsoft.XMLHTTP");
   }
   else
   {
       setTimeout("window.location.reload();", 3000);
       return;
   }
   req.open("GET", url, true);
   req.onreadystatechange = callback;
   req.send(null);
}

function callback()
{
    if (req.readyState == 4)
    {
        if (req.status == 200)
        {
            var status = req.responseXML.getElementsByTagName("status")[0];
            if ("complete" == status.childNodes[0].nodeValue)
            {
                window.location.reload();
            }
            else
            {
                var a_p = "";
                var d = new Date();
                var curr_hour = d.getHours();
                if (curr_hour < 12)
                {
                   a_p = "AM";
                }
                else
                {
                   a_p = "PM";
                }
                if (curr_hour == 0)
                {
                   curr_hour = 12;
                }
                if (curr_hour > 12)
                {
                   curr_hour = curr_hour - 12;
                }

                var curr_min = d.getMinutes();
                curr_min = curr_min + "";

                if (curr_min.length == 1)
                {
                   curr_min = "0" + curr_min;
                }

                var curr_sec = d.getSeconds();
                curr_sec = curr_sec + "";

                if (curr_sec.length == 1)
                {
                   curr_sec = "0" + curr_sec;
                }

                 var curr_time = curr_hour + ":" + curr_min + ":" + curr_sec + " " + a_p;

                document.getElementById('statusDiv').innerHTML = 'Status checked at ' + curr_time + ', SQL script(s) still running';  
                makeRequest();
            }
        }
        else
        {
            window.location.reload();
        }
    }
}

</script>

<h2>Running SQL Scripts for <%= getProvider().getProviderName()%>:</h2>
<%
    for (SqlScriptRunner.SqlScript script : getScripts())
    {
%>
    <p><%= script.getDescription() %></p>
    <%
}
%>
<p>This page should refresh automatically when the scripts have finished running.
If the page does not refresh <a href="<%= getCurrentUrl() %>">Click Here</a>.</p>
<div id="statusDiv" />
