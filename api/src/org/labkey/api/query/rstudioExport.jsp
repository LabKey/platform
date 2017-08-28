<%
/*
 * Copyright (c) 2016 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.query.QueryView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
QueryView.TextExportOptionsBean bean = (QueryView.TextExportOptionsBean)HttpView.currentView().getModelBean();
%>

<table class="lk-fields-table">
    <tr><td>Variable&nbsp;Name:</td><td align="left"><input id="rstudio_variable_name" name=var value=""/></td></tr>
</table>
<table class="lk-fields-table">
<tr><td colspan=2><button id="open_in_rstudio_button">Export to RStudio</button>&nbsp;<span id="rstudioStatus"></span></td></tr>
</table>

<script>

(function($)
{
    function startsWith(s,start)
    {
        return s.lastIndexOf(start,0) != -1;
    }

    function json_from_response(response)
    {
        try
        {
            if (startsWith(response.getResponseHeader("Content-Type"),"application/json"))
                return JSON.parse(response.responseText);
        }
        catch (x)
        {
        }
        return (
        {
            success:false,
            responseText:response.responseText,
            contentType:response.getResponseHeader("Content-Type"),
            status : response.status
        });
    }

    function fail(json)
    {
        var msg = json.exception || json.msg;
        if (!msg && json.status == 500)
            msg = "Unexpected server error";
        if (!msg)
            msg = "status code " + json.status;
        alert(msg);
        console.log(JSON.stringify(json));
    }

    // URL to get r script
    var urlGenerateScript = <%= q(bean.getTsvURL().getLocalURIString()) %>;
    var rstudioWindow = null;

    $('#open_in_rstudio_button').click(script);

    function script()
    {
        var url = urlGenerateScript + "&r~clean=1&r~view=rstudio";
        var variableName = $("#rstudio_variable_name")[0].value;
        if (variableName)
            url += "&r~variable=" + encodeURIComponent(variableName);
        $('#rstudioStatus').html("Generate script...");
        LABKEY.Ajax.request(
                {
                    method: "POST",
                    url: url,
                    success: function(response)
                    {
                        var rscript = response.responseText;
                        start(rscript);
                    },
                    failure : function(response)
                    {
                        fail(json_from_response(response));
                    }
                });
        return false;
    }

    function start(withScript)
    {
        $('#rstudioStatus').html("Starting...");
        LABKEY.Ajax.request(
                {
                    method: "POST",
                    url: LABKEY.ActionURL.buildURL("rstudio","startContainer.api","/"),
                    params : {'runScript':withScript},
                    success: function(response)
                    {
                        var json = json_from_response(response);
                        if (json.success)
                        {
                            openwindow();
                        }
                        else
                        {
                            fail(json);
                        }
                    },
                    failure : function(result)
                    {
                        fail(json_from_response(response));
                    }
                });
        return false;
    }

    function openwindow()
    {
        var status = $('#rstudioStatus');
        status.html("Opening window...");

        if (rstudioWindow && rstudioWindow.window)
        {
            if (rstudioWindow.location.pathname !== LABKEY.contextPath + "/_rstudio/")
                rstudioWindow.location = LABKEY.contextPath + "/_rstudio/";
        }
        else
        {
            rstudioWindow = window.open(LABKEY.contextPath + "/_rstudio/", "labkey_rstudio");
        }
        if (rstudioWindow)
        {
            rstudioWindow.focus();
            status.html("Done. Rstudio may be in hidden window or tab.");
        }
        else
        {
            // sometimes the open must be directly in response to a user click, so give the user something to click on
            status.html(' <b><a id=openinnewwindow href="#" target="labkey_rstudio">Click here to open RStudio in new window/tab</a></b>');
            $('#openinnewwindow').click(openwindow);
        }
        return false;
    }
})(jQuery);

</script>