<%
/*
 * Copyright (c) 2006-2009 LabKey Corporation
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
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.query.QueryParam" %>
<%@ page import="org.labkey.api.query.QueryAction" %>
<%@ page import="org.labkey.query.controllers.DesignForm" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page extends="org.labkey.query.controllers.Page" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<labkey:errors />
<%!
    String contextPath;
%>
<%
    DesignForm form = (DesignForm) HttpView.currentModel();
    contextPath = request.getContextPath();
    ActionURL urlCheckSyntax = new ActionURL("query", "checkSyntax", getContainer());
    ActionURL urlTableInfo = new ActionURL("query", "tableInfo", getContainer());
    urlTableInfo.addParameter(QueryParam.queryName.toString(), form.getQueryName());
    urlTableInfo.addParameter(QueryParam.schemaName.toString(), form.getSchemaName());
    urlTableInfo.addParameter("design", "1");
%>
<script type="text/javascript">
    LABKEY.requiresYahoo("yahoo");
    LABKEY.requiresYahoo("event");
    LABKEY.requiresYahoo("dom");
    LABKEY.requiresYahoo("dragdrop");
    LABKEY.requiresYahoo("animation");
    LABKEY.requiresYahoo("container");
    LABKEY.requiresScript("utils/dialogBox.js");
</script>
<script type="text/javascript" src="<%=contextPath%>/designer/designer.js"></script>
<script type="text/javascript" src="<%=contextPath%>/query/columnPicker.js"></script>
<script type="text/javascript" src="<%=contextPath%>/query/queryDesigner.js"></script>
<script type="text/javascript">
    var contextPath = <%=q(contextPath)%>;
    var designer = new QueryDesigner(<%=q(urlCheckSyntax.toString())%>, new TableInfoService(<%=q(urlTableInfo.toString())%>));
    <% if (form.getDefaultTab() != null)
    { %>
        designer.defaultTab = <%=PageFlowUtil.jsString(form.getDefaultTab())%>;
    <% } %>
    designerInit();
</script>
<table class="labkey-no-spacing">
    <tr>
        <th>
            <table class="labkey-no-spacing">
                <tr>
                    <td class="labkey-tab-space">
                        <img src="<%=contextPath%>/_.gif" height=1 width=5>
                    </td>
                    <td class="labkey-tab-selected" style="cursor:pointer">
                        Available&nbsp;Fields
                    </td>
                    <td class="labkey-tab-space" width=100%>
                        <img src="<%=contextPath%>/_.gif" height=1 width=5>
                    </td>
                </tr>
            </table>
        </th>
        <th></th>
        <th colspan="3" align="left">
            <table class="labkey-no-spacing">
                <tr>
                    <td class="labkey-tab-space">
                        <img src="<%=contextPath%>/_.gif" height=1 width=5>
                    </td>
                    <td style="cursor:pointer" class="labkey-tab" id="columns.tab"
                        onclick="designer.setActiveTab(designer.tabs.columns)">
                        Select
                    </td>
                    <td class="labkey-tab-space">
                        <img src="<%=contextPath%>/_.gif" height=1 width=5>
                    </td>
                    <td style="cursor:pointer" id="filter.tab" class="labkey-tab"
                        onclick="designer.setActiveTab(designer.tabs.filter)">
                        Where
                    </td>
                    <td class="labkey-tab-space">
                        <img src="<%=contextPath%>/_.gif" height=1 width=5>
                    </td>
                    <td style="cursor:pointer" id="sort.tab" class="labkey-tab"
                        onclick="designer.setActiveTab(designer.tabs.sort)">
                        Order&nbsp;By
                    </td>
                    <td class="labkey-tab-space" width=100%>
                        <img src="<%=contextPath%>/_.gif" height=1 width=5>
                    </td>
                </tr>
            </table>
        </th>
    </tr>
    <tr>
        <td valign="top" onSelectStart="return false;" onMouseDown="return false;" class="labkey-tab" style="border-top:none;">
        <div style="height:400px;width:300px;overflow:auto">
        <table>
            <tbody id="columnPicker">
            </tbody>
        </table>
        </div>
    </td>
    <td>
        <br>
        <br>
        <br>

        <p>
            <labkey:button text="Add >>" href="#" onclick="designer.add();return false;"/>
        </p>
    </td>

    <td id="columns.list" valign="top" style="display:none;border-top:none;border-right:none;" class="labkey-tab">
        <div id="columns.list.div" style="height:400px;width:200px;overflow:auto;border:solid 1px black;"></div>
    </td>
    <td valign="top" id="columns.controls" style="display:none;vertical-align:top;" class="labkey-tab-space">
        <br>

        <p><a href="#" onclick="designer.moveUp();return false"><img src="<%=contextPath%>/query/moveup.gif"
                                                       alt="Move Up"></a></p>

        <p><a href="#" onclick="designer.moveDown();return false"><img src="<%=contextPath%>/query/movedown.gif"
                                                         alt="Move Down"></a></p>

        <p><a href="#" onclick="designer.remove();return false"><img src="<%=contextPath%>/query/delete.gif" alt="Delete"
                                                      ></a></p>
        <p><a href="#" onclick="designer.insertSQL();return false"><img src="<%=contextPath%>/query/sql.gif" alt="Add SQL Expression Column" title="Add SQL Expression Column"></a></p>
    </td>

    <td id="columns.properties" valign="top" style="display:none;border-top:none;border-left:none" class="labkey-tab">
        <div id="columns.properties.div" style="height:400px;width:300px;overflow:auto;border:solid 1px black;"></div>
    </td>
    <td id="filter.list" valign="top" colspan="2" style="display:none;border-top:none;border-right:none" class="labkey-tab">
        <div id="filter.list.div" style="height:400px;width:500px;overflow:auto;border:solid 1px black;"></div>
    </td>
    <td id="filter.controls" valign="top" style="display:none;border-top:none;border-left:none;vertical-align:top;" class="labkey-tab">
        <br>

        <p><a href="#" onclick="designer.tabs.filter.moveUp();return false"><img src="<%=contextPath%>/query/moveup.gif"
                                                                   alt="Move Up"></a></p>

        <p><a href="#" onclick="designer.tabs.filter.moveDown();return false"><img
                src="<%=contextPath%>/query/movedown.gif"
                alt="Move Down"></a></p>

        <p><a href="#" onclick="designer.tabs.filter.remove();return false"><img src="<%=contextPath%>/query/delete.gif"
                                                                   alt="Delete"></a></p>
        <p><a href="#" onclick="designer.insertSQL();return false"><img src="<%=contextPath%>/query/sql.gif" alt="Add SQL Expression Clause"
                                                          title="Add SQL Expression Clause"></a></p>

    </td>
    <td id="sort.list" valign="top" colspan="2" style="display:none;border-top:none;border-right:none;" class="labkey-tab">
        <div id="sort.list.div" style="height:400px;width:500px;overflow:auto;border:solid 1px black;"></div>
    </td>
    <td id="sort.controls" valign="top" style="display:none;border-top:none;border-left:none;vertical-align:top;" class="labkey-tab">
        <br>

        <p><a href="#" onclick="designer.tabs.sort.moveUp();return false"><img src="<%=contextPath%>/query/moveup.gif"
                                                                 alt="Move Up"></a></p>

        <p><a href="#" onclick="designer.tabs.sort.moveDown();return false"><img
                src="<%=contextPath%>/query/movedown.gif"
                alt="Move Down"></a></p>

        <p><a href="#" onclick="designer.tabs.sort.remove();return false"><img src="<%=contextPath%>/query/delete.gif"
                                                                 alt="Delete"></a></p>
        <p><a href="#" onclick="designer.insertSQL();return false"><img src="<%=contextPath%>/query/sql.gif" alt="Add SQL Expression Clause"
                                                          title="Add SQL Expression Clause"></a></p>
    </td>
    <td id="sql.editor" valign="top" colspan="3" style="display:none">
        <table>
            <tr>
                <th id="sql.editor.title"></th>
            </tr>
            <tr>
                <td id="sql.editor.errors"></td>
            </tr>
            <tr>
                <td><textarea id="sql.editor.value" style="height:300px;width:500px" onblur="rememberSelection(this)"></textarea>
            </tr>
            <tr>
                <td>
                    <labkey:button text="OK" href="#" onclick="designer.tabs.sql.save();return false"/>
                    <labkey:button text="Cancel" href="#" onclick="designer.tabs.sql.cancel();return false"/>
                </td>
            </tr>
        </table>
    </td>
</table>
<form method="POST" action="<%=form.getQueryDef().urlFor(QueryAction.designQuery)%>" onsubmit="window.onbeforeunload = null">
    <input type="hidden" name="ff_designXML" id="ff_designXML" value="<%=h(form.ff_designXML)%>">
    <input type="hidden" name="ff_dirty" id="ff_dirty" value="<%=form.ff_dirty%>">
    <input type="hidden" name="ff_redirect" id="ff_redirect" value="<%=form.ff_redirect%>">
    <labkey:button text="Save" onclick="needToPrompt = false; document.getElementById('ff_redirect').value = 'designQuery';" />

    <labkey:button text="Edit Source" onclick="needToPrompt = false; document.getElementById('ff_redirect').value = 'sourceQuery'" />
    <% if(form.getQueryDef().isMetadataEditable()) { %>
        <labkey:button text="Edit Metadata" onclick="needToPrompt = false; document.getElementById('ff_redirect').value = 'metadataQuery'" />
    <% } %>
    <labkey:button text="View Data" onclick="needToPrompt = false; document.getElementById('ff_redirect').value = 'executeQuery'" />
</form>
