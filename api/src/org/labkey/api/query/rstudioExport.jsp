<%
/*
 * Copyright (c) 2016-2017 LabKey Corporation
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
<tr><td colspan=2><button class="labkey-button primary" id="open_in_rstudio_button">Export to RStudio</button>&nbsp;<span id="rstudioStatus"></span></td></tr>
</table>
<script type="text/javascript" >
(function($, urlGenerateScript)
{
    function startsWith(s,start)
    {
        return s.lastIndexOf(start,0) !== -1;
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
        if (!msg && json.status === 500)
            msg = "Unexpected server error";
        if (!msg)
            msg = "status code " + json.status;
        alert(msg);
        console.log(JSON.stringify(json));
    }

    var rscript = null;
    var rstudioWindow = null;

    function start_exporting()
    {
        // some browsers require window.open() only in response to user action (e.g. not in a callback). So do that first
        immediate_open_window();
        return false;
    }

    function immediate_open_window()
    {
        $('#rstudioStatus').html("Opening window...");
        rstudioWindow = window.open("", "labkey_rstudio");
        start_rsession();
        return false;
    }

    function start_rsession()
    {
        $('#rstudioStatus').html("Starting RStudio...");
        LABKEY.Ajax.request(
        {
            method: "POST",
            url: LABKEY.ActionURL.buildURL("rstudio","startContainer.api","/"),
            success: function(response)
            {
                var json = json_from_response(response);
                if (json.success)
                {
                    wait_for_rstudio();
                }
                else
                {
                    fail(json);
                }
            },
            failure : function(response)
            {
                fail(json_from_response(response));
            }
        });
        return false;
    }

    /* this may not work depending on the browser, so don't make timeout too long */
    function wait_for_rstudio()
    {
        // unfortunately onload seems problematic, so poll
        var remainingWait = 5000;
        function checkReady()
        {
            var ready =
                    rstudioWindow.location.pathname === LABKEY.contextPath + "/_rstudio/" &&
                    rstudioWindow.document.readyState === "complete" &&
                    typeof rstudioWindow.sendRemoteServerRequest === "function";
            if (ready || remainingWait < 0)
            {
                generate_rscript();
            }
            else
            {
                remainingWait -= 100;
                window.setTimeout(checkReady, 100);
            }
        }

        if (rstudioWindow)
        {
            rstudioWindow.location = LABKEY.contextPath + "/_rstudio/";
            window.setTimeout(checkReady, 100);
        }
        else
        {
            // user will have to open window manually (or change pop-up blocker setting)
            generate_rscript();
        }
    }

    function generate_rscript()
    {
        var url = urlGenerateScript + "&r~clean=1&r~view=rstudio";
        var variableName = $("#rstudio_variable_name")[0].value;
        if (variableName)
            url += "&r~variable=" + encodeURIComponent(variableName);
        $('#rstudioStatus').html("Generating script...");
        LABKEY.Ajax.request(
        {
            method: "POST",
            url: url,
            success: function(response)
            {
                rscript = response.responseText;
                runscript_console();
            },
            failure : function(response)
            {
                fail(json_from_response(response));
            }
        });
        return false;
    }

    function runscript_console()
    {
        $('#rstudioStatus').html("Running script...");

        if (!rstudioWindow || typeof rstudioWindow.sendRemoteServerRequest !== "function")
            return runscript();

        var commands = rscript.split("\n\n");

        function send_console_input()
        {
            if (commands.length > 0)
            {
                var line = commands.shift().trim();
                rstudioWindow.sendRemoteServerRequest(rstudioWindow, "rpc", "console_input", [line,""], false, null);
                setTimeout(send_console_input,50);
            }
            else
            {
                finish();
            }
        }
        send_console_input();
    }

    function runscript()
    {
        LABKEY.Ajax.request(
        {
            method: "POST",
            url: LABKEY.ActionURL.buildURL("rstudio","startContainer.api","/"),
            params : {'runScript': rscript},
            success: function(response)
            {
                var json = json_from_response(response);
                if (json.success)
                {
                    finish();
                }
                else
                {
                    fail(json);
                }
            },
            failure : function(response)
            {
                fail(json_from_response(response));
            }
        });
    }

    function finish()
    {
        var status = $('#rstudioStatus');

        if (rstudioWindow)
        {
            rstudioWindow.focus();
            status.html('Done. <a id=openinnewwindow href="' + LABKEY.contextPath + "/_rstudio/" + '" target="labkey_rstudio">Go to RStudio window</a>.');
        }
        else
        {
            status.html('<b><a id=openinnewwindow href="' + LABKEY.contextPath + "/_rstudio/" + '" target="labkey_rstudio">Click here to open RStudio in new window/tab</a></b>');
        }
        return false;
    }

    $().ready( function(){$('#open_in_rstudio_button').click(start_exporting);} );

})(jQuery, <%= q(bean.getTsvURL().getLocalURIString()) %>);
</script>
