<%
/*
 * Copyright (c) 2011-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    Map<String, String> props = new HashMap<>();
    props.put("style", "float: left;");
%>
<style type="text/css">
    .labkey-row, .labkey-alternate-row {
        display: none;
    }
</style>
<h4>Links</h4>
<table border="1">
    <tr>
        <th>Example</th>
        <th>Usage</th>
        <th>Comment</th>
    </tr>
    <tr>
        <td><a>Standard Link</a></td>
        <td>Standard anchor tag.</td>
        <td>The most common link type.</td>
    </tr>
    <tr>
        <td><a class="labkey-text-link">LabKey Text Link</a></td>
        <td>Styled anchor tag.</td>
        <td></td>
    </tr>
    <tr>
        <td><div id="js-link"></div></td>
        <td>JavaScript generated text link.</td>
        <td></td>
    </tr>
    <tr>
        <td><%=textLink("Generated Text Link", "#")%></td>
        <td>Generated styled anchor tag.</td>
        <td></td>
    </tr>
    <td>
        <table>
            <tr><td><%=textLink("Text Link Top", "#", "", "", props)%></td></tr>
            <tr><td><%=textLink("Text Link Middle", "#", "", "", props)%></td></tr>
            <tr><td><%=textLink("Text Link Bottom", "#", "", "", props)%></td></tr>
        </table>
    </td>
    <tr>
        <td><%= PageFlowUtil.generateDropDownTextLink("Drop Down Link", "#", "", false, "0", null)%></td>
        <td></td>
        <td></td>
    </tr>
</table>

<h4>Buttons</h4>
<table border="1">
    <tr>
        <th>Example</th>
        <th>Usage</th>
        <th>Comment</th>
    </tr>
    <tr>
        <td><button>HTML Button</button></td>
        <td>Standard "button" tag.</td>
        <td></td>
    </tr>
    <tr>
        <td><input type="button" value="Input Button"/></td>
        <td>Standard "input" tag of type "button".</td>
        <td></td>
    </tr>
    <tr>
        <td><%=generateBackButton("Generated Back Button")%></td>
        <td></td>
        <td></td>
    </tr>
    <tr>
        <td><%=button("Generated Button").href("#")%></td>
        <td></td>
        <td></td>
    </tr>
    <tr>
        <td><%= button("Generated Submit Button").submit(true) %></td>
        <td></td>
        <td></td>
    </tr>
    <tr>
        <td><%= button("Gen Left").href("#") %><%= button("Gen Right").href("#") %></td>
        <td></td>
        <td></td>
    </tr>
    <tr>
        <td><%= PageFlowUtil.generateDropDownButton("Drop Down Button", "#", "")%></td>
        <td></td>
        <td></td>
    </tr>
    <tr>
        <td>
            <table>
                <tr><td><%= button("Button").href("#") %></td></tr>
                <tr><td><%= button("Button").href("#") %></td></tr>
                <tr><td><%= button("Button").href("#") %></td></tr>
            </table>
        </td>
        <td>Stacked generated buttons.</td>
        <td></td>
    </tr>
    <tr>
        <td><div id="ext-button-1"></div></td>
        <td></td>
        <td></td>
    </tr>
    <tr>
        <td><div id="ext-button-2"></div></td>
        <td></td>
        <td>Disabled via Ext "disabled" property.</td>
    </tr>
    <tr>
        <td><div id="ext-button-left"></div><div id="ext-button-right"></div></td>
        <td></td>
        <td></td>
    </tr>
</table>
<h4>Menus & Toolbars</h4>
<table border="1">
    <tr>
        <th>Example</th>
        <th>Usage</th>
        <th>Comment</th>
    </tr>
    <tr>
        <td>
            <div class="labkey-button-bar">
                <span><%= PageFlowUtil.generateDropDownButton("Button Bar Drop Down", "#", "")%></span>
                <span><%= PageFlowUtil.button("Button Bar Button").href("#") %></span>
            </div>
        </td>
        <td></td>
        <td></td>
    </tr>
    <tr>
        <td>
            <div class="labkey-button-bar">
                <span><%= PageFlowUtil.generateDropDownButton("labkey-disabled-button", "#", "", java.util.Collections.singletonMap("id", "dis-drop-1"))%></span>
                <span><%= PageFlowUtil.generateDropDownButton("Enabled Drop", "#", "")%></span>
                <span><%= PageFlowUtil.button("Button").href("#") %></span>
            </div>
        </td>
        <td></td>
        <td></td>
    </tr>
    <tr>
        <td><div id="ext-tb-1"></div></td>
        <td>Standard Ext Toolbar.</td>
        <td></td>
    </tr>
</table>
<h4>Webparts</h4>
<table border="1">
    <tr>
        <th>Example</th>
        <th>Usage</th>
        <th>Comment</th>
    </tr>
    <tr>
        <td><div id="wp-div-1"></div></td>
        <td>Standard Webpart.</td>
        <td></td>
    </tr>
    <tr>
        <td><div id="wp-div-2"></div></td>
        <td>Standard Query Webpart.</td>
        <td></td>
    </tr>
</table>
<script type="text/javascript">

    function init(){

        /* Links */
        var el = Ext.get('js-link');
        el.update(LABKEY.Utils.textLink({text: 'JavaScript Link', href: '#'}));        

        /* Buttons */
        el = Ext.get('dis-drop-1');
        el.addClass('labkey-disabled-button');
        
        var extButton1 = new Ext.Button({
            renderTo : 'ext-button-1',
            text     : 'Ext Button'
        });

        var extButton2 = new Ext.Button({
            renderTo : 'ext-button-2',
            text     : 'Ext Disabled Button',
            disabled : true
        });

        var extButtonLeft = new Ext.Button({
            renderTo : 'ext-button-left',
            text     : 'Ext Left',
            style    : 'float: left;'
        });

        var extButtonRight = new Ext.Button({
            renderTo : 'ext-button-right',
            text     : 'Ext Right'
        });    

        /* Menus / Toolbars*/
        var toolbar = new Ext.Toolbar({
            renderTo: 'ext-tb-1',
            items   : [{
                text : 'First Button'
            },{
                text : 'Second Button'
            },{
                text : 'Disabled Ext Button',
                disabled : true
            }]
        });
        
        /* Webparts */
        var renderer = new LABKEY.WebPart({partName: 'query',
            renderTo: 'wp-div-1',
            partConfig: {
                title: 'Normal Webpart',
                schemaName: 'core',
                queryName: 'Users'
            }});
        renderer.render();

        renderer = new LABKEY.QueryWebPart({
            renderTo  : 'wp-div-2',
            title     : 'Normal QueryWebpart',
            schemaName: 'core',
            queryName : 'users'            
        });
    }

    Ext.onReady(init);
</script>
