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
<%@ page import="org.labkey.api.query.CustomView" %>
<%@ page import="org.labkey.api.query.QueryAction" %>
<%@ page import="org.labkey.api.query.QueryParam" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.query.controllers.ChooseColumnsForm" %>
<%@ page import="java.util.Collections" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<labkey:errors />
<%
    ChooseColumnsForm form = (ChooseColumnsForm) __form;
    CustomView view = form.getCustomView();
    ActionURL urlTableInfo = form.getSchema().urlFor(QueryAction.tableInfo);
    urlTableInfo.addParameter(QueryParam.queryName.toString(), form.getQueryName());

    boolean canEdit = form.canEdit();
%>
<link rel="stylesheet" href="<%=request.getContextPath()%>/_yui/build/container/assets/container.css" type="text/css"/>
<link rel="stylesheet" href="<%=request.getContextPath()%>/utils/dialogBox.css" type="text/css"/>

<script type="text/javascript">
    LABKEY.requiresYahoo("yahoo");
    LABKEY.requiresYahoo("event");
    LABKEY.requiresYahoo("dom");
    LABKEY.requiresYahoo("dragdrop");
    LABKEY.requiresYahoo("animation");
    LABKEY.requiresYahoo("container");
    LABKEY.requiresScript("utils/dialogBox.js");
</script>
<script type="text/javascript" src="<%=request.getContextPath()%>/designer/designer.js"></script>
<script type="text/javascript" src="<%=request.getContextPath()%>/query/columnPicker.js"></script>
<script type="text/javascript" src="<%=request.getContextPath()%>/query/queryDesigner.js"></script>
<% if (form.ff_designXML == null) {
    return;
}%>
<script type="text/javascript">
    var contextPath = <%=q(request.getContextPath())%>;
    var designer = new ViewDesigner(new TableInfoService(<%=q(urlTableInfo.toString())%>));
    <% if (form.getDefaultTab() != null)
    { %>
        designer.defaultTab = '<%= form.getDefaultTab() %>';
    <% } %>
    designer.setShowHiddenFields(<%= form.getQuerySettings().isShowHiddenFieldsWhenCustomizing() %>);
    designerInit();
    function updateViewNameDescription(elCheckbox)
    {
        var elShared = document.getElementById("sharedViewNameDescription");
        var elPersonal = document.getElementById("personalViewNameDescription");
        if (elCheckbox.checked)
        {
            elShared.style.display = "";
            elPersonal.style.display = "none";
        }
        else
        {
            elShared.style.display = "none";
            elPersonal.style.display = "";
        }
    }

    function onSubmit()
    {
        if (designer.validate())
        {
            window.onbeforeunload = null;
            return true;
        }
        return false;
    }
</script>

<p>
<table width="100%">
    <tr class="wpHeader"><td class="wpTitle" align="left">
    Grid View :
        <% if (view != null) { %>
            <%=h(view.getName())%>
        <% } %>
        <% if (form.getQueryDef() != null && form.getQueryDef().getName() != null) { %>
            based on <%=form.getQueryDef().getName()%> query
        <% } %>
        <%=form.isCustomViewInherited() ? "(inherited from project " + view.getContainer().getPath() + ")" : ""%>
    </td></tr>
</table>
</p>

<% if (form.isCustomViewInherited()) { %>
    <p><b>This grid view can't be edited since it is inherited from project <%=view.getContainer().getPath()%>.</b></p>
<% }
   else if (getUser().isGuest()) { %>
    <p><b>You are not currently logged in.  Changes you make here will only persist for the duration of your session.</b></p>
<% } %>
<table class="normal" cellspacing=0 cellpadding=0>
    <tr>
        <th>
            <table cellspacing=0 cellpadding=0 width="100%">
                <tr>
                    <td class="navtab" style="border-top:none;border-left:none;border-right:none;">
                        <img src="<%=request.getContextPath()%>/_.gif" height=1 width=5>
                    </td>
                    <td class="navtab-selected" style="cursor:pointer">
                        Available&nbsp;Fields
                    </td>
                    <td class="navtab" style="border-top:none;border-left:none;border-right:none;text-align:right;" width=100%>

                        <labkey:helpPopup title="Available Fields">
                            <p>Click on the available fields to select them.  Click the 'Add' button to add selected fields to the grid view.</p>

                            <p>Expand elements of the tree to add related fields from other tables.</p>
                        </labkey:helpPopup>
                    </td>
                </tr>
            </table>
        </th>
        <th></th>
        <th colspan="2" align="left">
            <table cellspacing=0 cellpadding=0 width="100%">
                <tr>
                    <td class="navtab" style="border-top:none;border-left:none;border-right:none;">
                        <img src="<%=request.getContextPath()%>/_.gif" height=1 width=5>
                    </td>
                    <td style="cursor:pointer" class="navtab" id="columns.tab"
                        onclick="designer.setActiveTab(designer.tabs.columns)">
                        Fields&nbsp;In&nbsp;Grid
                    </td>
                    <td class="navtab" style="border-top:none;border-left:none;border-right:none;padding-left:0px;padding-right:0px;">
                        <img src="<%=request.getContextPath()%>/_.gif" height=1 width=5>
                    </td>
                    <td style="cursor:pointer" id="filter.tab" class="navtab"
                        onclick="designer.setActiveTab(designer.tabs.filter)">
                        Filter
                    </td>
                    <td class="navtab" style="border-top:none;border-left:none;border-right:none;padding-left:0px;padding-right:0px;">
                        <img src="<%=request.getContextPath()%>/_.gif" height=1 width=5>
                    </td>
                    <td style="cursor:pointer" id="sort.tab" class="navtab"
                        onclick="designer.setActiveTab(designer.tabs.sort)">
                        Sort
                    </td>
                    <td class="navtab" style="border-top:none;border-left:none;border-right:none;text-align:right;" width=100%>
                        <labkey:helpPopup title="Fields In Grid / Filter / Sort">
                            <p>There are three tabs for choosing which fields are to be displayed in the grid, and setting the filter and sort.</p>
                            <p>Add fields from the Available Fields</p>
                            <p>Use the arrows to move elements up and down, or remove them.</p>
                        </labkey:helpPopup>
                    </td>
                </tr>
            </table>
        </th>
    </tr>
    <tr>
    <td valign="top" onSelectStart="return false;" onMouseDown="return false;" class="navtab" style="border-top:none;">
        <div style="height:400px;width:300px;overflow:auto;" id="columnPicker">
        </div>
    </td>
    <td>
        <br>
        <br>
        <br>

        <p style="margin:5px;">
            <labkey:button text="Add >>" href="#" onclick="designer.add();return false;"/>
        </p>
    </td>

    <td id="columns.list" valign="top" style="display:none;border-top:none;border-right:none;" class="navtab">
        <div id="columns.list.div" style="height:400px;width:500px;overflow:auto;"></div>
    </td>
    <td valign="top" id="columns.controls" style="display:none;border-top:none;border-left:none;vertical-align:top;" class="navtab">
        <br>

        <p><a href="#" onclick="designer.moveUp();return false"><img src="<%=request.getContextPath()%>/query/moveup.gif"
                                                       alt="Move Up" title="Move Up" border="0"></a></p>

        <p><a href="#" onclick="designer.moveDown();return false"><img src="<%=request.getContextPath()%>/query/movedown.gif"
                                                         alt="Move Down" title="Move Down" border="0"></a></p>

        <p><a href="#" onclick="designer.remove();return false"><img src="<%=request.getContextPath()%>/query/delete.gif" alt="Delete"
                                                       border="0" title="Delete"></a></p>
        <p><a href="#" onclick="designer.showColumnProperties();return false;"><img src="<%=request.getContextPath()%>/query/columnProperties.gif" alt="Set Field Caption" title="Set Field Caption"></a></p>
    </td>
    <td id="filter.list" valign="top" style="display:none;border-top:none;border-right:none" class="navtab">
        <div id="filter.list.div" style="height:400px;width:600px;overflow:auto;"></div>
    </td>
    <td id="filter.controls" valign="top" style="display:none;border-top:none;border-left:none;vertical-align:top;" class="navtab">
        <br>

        <p><a href="#" onclick="designer.tabs.filter.moveUp();return false"><img src="<%=request.getContextPath()%>/query/moveup.gif"
                                                                   alt="Move Up" border="0" title="Move Up"></a></p>

        <p><a href="#" onclick="designer.tabs.filter.moveDown();return false"><img
                src="<%=request.getContextPath()%>/query/movedown.gif"
                alt="Move Down" title="Move Down" border="0"></a></p>

        <p><a href="#" onclick="designer.tabs.filter.remove();return false"><img src="<%=request.getContextPath()%>/query/delete.gif"
                                                                   alt="Delete" border="0" title="Delete"></a></p>
    </td>
    <td id="sort.list" valign="top" style="display:none;border-top:none;border-right:none;" class="navtab">
        <div id="sort.list.div" style="height:400px;width:500px;overflow:auto;"></div>
    </td>
    <td id="sort.controls" valign="top" style="display:none;border-top:none;border-left:none;vertical-align:top;" class="navtab">
        <br>

        <p><a href="#" onclick="designer.tabs.sort.moveUp();return false"><img src="<%=request.getContextPath()%>/query/moveup.gif"
                                                                 alt="Move Up" title="Move Up" border="0"></a></p>

        <p><a href="#" onclick="designer.tabs.sort.moveDown();return false"><img
                src="<%=request.getContextPath()%>/query/movedown.gif"
                alt="Move Down" border="0"></a></p>

        <p><a href="#" onclick="designer.tabs.sort.remove();return false"><img src="<%=request.getContextPath()%>/query/delete.gif"
                                                                 alt="Delete" border="0" title="Delete"></a></p>
    </td>
</table>
<form method="POST" action="<%=form.urlFor(QueryAction.chooseColumns)%>" onsubmit="return onSubmit();">
    <span title="Some fields may be hidden by default from the list of available fields by default.">
        <input type="checkbox"<% if (form.getQuerySettings().isShowHiddenFieldsWhenCustomizing()) { %> checked <% } %> onchange="designer.setShowHiddenFields(this.checked)"> Show hidden fields
    </span><br>
    <input type="hidden" name="ff_designXML" id="ff_designXML" value="<%=h(form.ff_designXML)%>">
    <input type="hidden" name="ff_dirty" id="ff_dirty" value="<%=form.ff_dirty%>">
    <p>
    <% boolean isHidden = view != null && view.isHidden(); %>
    <% if (isHidden) { %>
        <input type="hidden" name="ff_columnListName" value="<%=h(view.getName())%>">
        <% if (view.getOwner() == null) { %>
            <input type="hidden" name="ff_saveForAllUsers" value="true">
        <% } %>
    <% } else if (canEdit) { %>
        <b>View Name:</b> <input type="text" name="ff_columnListName" maxlength="50" value="<%=h(form.ff_columnListName)%>">
        <span id="personalViewNameDescription" <%=!form.canSaveForAllUsers() || view == null || view.getOwner() != null ? "" : " style=\"display:none"%>>(Leave blank to save as your default grid view for '<%=h(form.getQueryName())%>'.)</span>
        <span id="sharedViewNameDescription" <%=!form.canSaveForAllUsers() || view == null || view.getOwner() != null ? " style=\"display:none\"" : ""%>>(Leave blank to save as the default grid view for '<%=h(form.getQueryName())%>' for all users.)</span>
        <br>
        <% if (form.canSaveForAllUsers()) { %>
            <input type="checkbox" name="ff_saveForAllUsers" value="true"<%=view != null && view.getOwner() == null ? " checked" : ""%> onclick="updateViewNameDescription(this)"> Make
            this grid view available to all users.<br>
            <labkey:checkbox name="ff_inheritable" value="<%=true%>" checkedSet="<%=Collections.singleton(form.ff_inheritable)%>"/> Make this grid view available in child folders.<br>
        <% } %>
    <% } %>

    <% if (canEdit && form.hasFilterOrSort()) { %>
        <input id="ff_saveFilterCbx" type="checkbox" name="ff_saveFilter" value="true"> Remember current filter.<br>
    <% } else { %>
        <input type="hidden" name="ff_saveFilter" value="true">
    <% } %>
    </p>
    <% if (canEdit)
    {
        %><labkey:button text="Save" onclick="needToPrompt = false" /> <%
        if (view != null && ! view.isHidden())
        {
            ActionURL urlDeleteView = form.urlFor(QueryAction.deleteView);
            ActionURL srcURL = form.getSourceURL();
            srcURL.deleteParameter(form.getQuerySettings().param(QueryParam.viewName));
            urlDeleteView.replaceParameter(QueryParam.srcURL.toString(), srcURL.toString());
            String strButtonText;
            if (view.getName() == null)
            {
                if (view.getOwner() == null)
                {
                    strButtonText = "Reset default grid view";
                }
                else
                {
                    strButtonText = "Reset my default grid view";
                }
            }
            else
            {
                if (view.getOwner() == null)
                {
                    strButtonText = "Delete grid view '" + view.getName() + "'";
                }
                else
                {
                    strButtonText = "Delete my grid view '" + view.getName() + "'";
                }
            }
        %><labkey:button href="<%=urlDeleteView%>" text="<%=strButtonText%>"/><%
        }
    } %>
    <br>
</form>
<div id="columnPropertiesDialog">
<div class="hd">Set Column Caption</div>
<div class="bd">
    <p><span id="columnPropertiesDialogName"></span></p>
    <p><label for="columnPropertiesDialogLabel">Column Caption:</label><input type="textbox" name="label" id="columnPropertiesDialogLabel"/></p>
    <p><labkey:button id="columnPropertiesDialogOK" text="OK" /> <labkey:button id="columnPropertiesDialogCancel" text="Cancel" /></p>

</div>
</div>
