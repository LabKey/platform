<%
/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.query.controllers.OlapController" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
        dependencies.add("query/olap.js");
    }
%>
<%
    OlapController.OlapForm form = (OlapController.OlapForm)HttpView.currentModel();
%>
<labkey:errors/>
<labkey:form action="#" id="testJsonForm">
    <table>
        <tr>
            <td><label for="type">Type:</label></td>
            <td>
                <select id="type" name="type">
                    <option value="countdistinct">Count Distinct</option>
                    <option value="json">JSON</option>
                </select>
            </td>
        </tr>
        <tr>
            <td style="vertical-align: top;"><label for="query">Query:</label></td>
            <td>
                <textarea cols="80" rows="25" id="query" name="query" style="font-size:10pt; font-family: Andale Monaco, monospace;"><%=h(request.getParameter("query"))%></textarea>
            </td>
        </tr>
        <tr>
            <td colspan="2">
                <input type="button" id="submitbtn" onclick="executeQuery()" value="submit">
                <input type="button" id="clearbtn" onclick="resetForm()" value="clear">
            </td>
        </tr>
    </table>
</labkey:form>
<p>&nbsp;</p>
<div id=cellset></div>
<script type="text/javascript">
    var resizer = new Ext4.Resizable("query", {
        handles: 'se',
        minWidth: 200,
        minHeight: 100,
        maxWidth: 1200,
        maxHeight: 800,
        pinned: true
    });

    var connection = null;
    var cube = null;
    var mdx = null;

    Ext4.onReady(function()
    {
        cube = LABKEY.query.olap.CubeManager.getCube(
        {
            name:<%=q(form.getCubeName())%>,
            configId:<%=q(form.getConfigId())%>,
            schemaName:<%=q(form.getSchemaName())%>
        });
        cube.on('onready',function(m)
        {
            mdx = m;
        });
    });

    var startTime;

    function resetForm()
    {
        document.getElementById('testJsonForm').reset();
        Ext4.get('cellset').update();
    }

    function executeQuery()
    {
        var type = Ext4.get('type').getValue();
        var query = Ext4.get('query').getValue();

        // first verify that the JSON looks like JSON
        var jsonQuery = null;
        try
        {
            if (query)
            {
                jsonQuery = JSON.parse(query);
            }
        }
        catch (x)
        {
            alert(x);
            return;
        }

        var configDefaults =
        {
            configId: <%=q(form.getConfigId())%>,
            schemaName: <%=q(form.getSchemaName())%>,
            success: function(cs){renderCellSet(cs,'cellset');},
            failure: failed
        };
        var config;
        if ('query' in jsonQuery)
        {
            config = Ext4.apply({}, jsonQuery.query, configDefaults);
        }
        else
        {
            config = Ext4.apply({}, jsonQuery, configDefaults);
        }
        config.useJsonQuery = type == "json";

        startTime = new Date().getTime();
        mdx.query(config);
        return false;
    }


    function failed(json, response, options)
    {
        var msg = response.statusText;
        if (json && json.exception)
            msg = json.exception;
        alert(msg);
    }


    function renderCellSet(cs, el)
    {
        var duration = new Date().getTime() - startTime;
        Ext4.getBody().unmask();
        var h = Ext4.util.Format.htmlEncode;
        el = Ext4.get(el||'cellset');
        var html = [];
        html.push('<table class="labkey-data-region-legacy labkey-show-borders"><tr>');
        if (cs.axes.length>1)
                html.push('<td>&nbsp;</td>');

        for (var col=0 ; col<cs.axes[0].positions.length ; col++)
        {
            // only showing first member (handle hierarchy)
            html.push('<td class="labkey-column-header" title="' + h(cs.axes[0].positions[col][0].uniqueName) +'">' + h(cs.axes[0].positions[col][0].name) + "</td>");
        }
        html.push("</tr>");

        if (cs.axes.length == 1)
        {
            //for (var row=0 ; row<cs.axes[1].positions.length ; row++)
            {
                html.push('<tr>');
                for (var col=0 ; col<cs.axes[0].positions.length ; col++)
                {
                    var cell = cs.cells[col];
                    var value = Ext4.isObject(cell) ? cell.value : cell;
                    html.push("<td align=right>" + (null==value?"&nbsp;":value) + "</td>");
                }
                html.push('</tr>');
            }
        }
        else if (cs.axes.length == 2)
        {
            for (var row=0 ; row<cs.axes[1].positions.length ; row++)
            {
                html.push('<tr>');
                var pos = cs.axes[1].positions[row];
                for (var p=0; p < pos.length; p++)
                {
                    // only showing first member (handle hierarchy)
                    html.push('<td class="labkey-column-header" title="' + h(cs.axes[1].positions[row][p].uniqueName) +'">' +  h(cs.axes[1].positions[row][p].uniqueName) + "</td>");
                }

                for (var col=0 ; col<cs.axes[0].positions.length ; col++)
                {
                    var cell = cs.cells[row][col];
                    var value = Ext4.isObject(cell) ? cell.value : cell;
                    html.push("<td align=right>" + (null==value?"&nbsp;":value) + "</td>");
                }
                html.push('</tr>');
            }
        }

        html.push("</table>");
        html.push("" + cs.axes[1].positions.length + " rows");
        html.push("<p><b>" + (duration/1000) + "s</b></p>");

        el.update(html.join(""));
    }
    var cellset = undefined;
</script>
