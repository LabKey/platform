<%
/*
 * Copyright (c) 2005-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.MvUtil" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="java.util.Map" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    int rowId = 0;
%>
<labkey:form name="mvIndicatorsForm" method="POST">
    <table>
        <tr>
            <td>
                Data columns that are configured to allow missing value indicators can have special values<br>
                indicating that the original data is missing or suspect. This page allows you to configure<br>
                what indicators are allowed.
            </td>
        </tr>
        <tr>
            <td>
                <%
                    Container c = HttpView.getContextContainer();
                    Container definingContainer = MvUtil.getDefiningContainer(c);
                    boolean inherited = !c.equals(definingContainer);

                    // Destination for inherited link
                    Container linkContainer = definingContainer;

                    if (!inherited && !c.isRoot())
                    {
                        // Need to point to the first parent that has a definition
                        linkContainer = linkContainer.getParent();
                        while (!linkContainer.isRoot() && !linkContainer.equals(MvUtil.getDefiningContainer(linkContainer)))
                            linkContainer = linkContainer.getParent();
                    }

                    boolean hasLinkPermission = linkContainer.hasPermission(getUser(), AdminPermission.class);

                    ActionURL inheritURL = getViewContext().cloneActionURL();
                    inheritURL.setContainer(linkContainer);
                    String containerLabel;
                    if (linkContainer.isRoot())
                        containerLabel = "Server default";
                    else if (linkContainer.isProject())
                        containerLabel = "Project " + linkContainer.getName();
                    else
                        containerLabel = "Parent Folder " + linkContainer.getPath();

                    if (!c.isRoot())
                    {
                        // Only show the link to an enclosing container if there is one.
                %>
                <input type="checkbox" id="inherit" name="inheritMvIndicators"<%=checked(inherited)%>
                       onclick="toggleInherited(this);">
                Inherit settings (from
                <%
                        if (hasLinkPermission)
                        {
                            out.write("<a href=\"" + inheritURL.getLocalURIString() + "\">");
                        }

                        // Always write out what we inherit from
                        out.write(containerLabel);

                        if (hasLinkPermission)
                        {
                            out.write("</a>)");
                        }

                        // Now write out what those settings are
                        out.write(": ");
                        boolean needComma = false;
                        for (Map.Entry<String, String> mvEntry : MvUtil.getIndicatorsAndLabels(linkContainer).entrySet())
                        {
                            String indicator = mvEntry.getKey();
                            String label = mvEntry.getValue();
                            if (needComma)
                            {
                                out.write(", ");
                            }
                            else
                            {
                                needComma = true;
                            }

                            String popupText = h(label);

                            out.write(PageFlowUtil.helpPopup(indicator, popupText, true, indicator, 0));
                        }
                    }
                %>

            </td>
        </tr>
        <tr>
            <td>
                <div id="mvIndicatorsDiv" style="display: <%=text(inherited ? "none" : "block")%>;">
                    <table id="mvTable">
                        <tr>
                            <th>&nbsp;</th>
                            <th>Indicator</th>
                            <th>Description</th>
                        </tr>

                        <%
                            Map<String, String> mvIndicatorsAndLabels = MvUtil.getIndicatorsAndLabels(definingContainer);
                            for (Map.Entry<String, String> entry : mvIndicatorsAndLabels.entrySet())
                            {
                                String indicator = entry.getKey();
                                String label = entry.getValue();
                                if (label == null)
                                    label = "";

                        %>

                        <tr id="rowId<%=++rowId%>">
                            <td><img src="<%=getContextPath()%>/_images/partdelete.gif"
                                     alt="delete" onclick="removeRow(<%=rowId%>);"></td>
                            <td><input name="mvIndicators" type="TEXT" size=3
                                       id="mvIndicators<%=rowId%>" value="<%=h(indicator)%>">
                            </td>
                            <td><input name="mvLabels" type="TEXT" size=60
                                       value="<%=h(label)%>">
                            </td>
                        </tr>

                        <%
                            }
                        %>
                        <tr>
                            <td>&nbsp;</td>
                            <td><%= PageFlowUtil.button("Add").href("#").onClick("addRowToTable();") %>
                            </td>
                            <td>&nbsp;</td>
                        </tr>
                    </table>
                </div>
            </td>
        </tr>
    </table>
    <%= button("Save").submit(true).onClick("return validate();") %>
</labkey:form>
<script type="text/javascript">

    var maxRowId = <%=rowId%>;

    function removeRow(rowIdNumber)
    {
        var row = document.getElementById("rowId" + rowIdNumber);
        row.parentNode.removeChild(row);
    }

    function addRowToTable()
    {
        var tbl = document.getElementById("mvTable");
        var lastRow = tbl.rows.length;
        var row = tbl.insertRow(lastRow - 1);
        row.id = "rowId" + ++maxRowId;

        var cellLeft = row.insertCell(0);
        var imgNode = document.createElement('img');
        imgNode.src = '<%=getContextPath()%>/_images/partdelete.gif';
        imgNode.setAttribute("onclick", 'removeRow(' + maxRowId + ');');
        imgNode.setAttribute("alt", "delete");
        cellLeft.appendChild(imgNode);

        var cellMiddle = row.insertCell(1);
        var middle = document.createElement('input');
        middle.type = 'text';
        middle.name = "mvIndicators";
        middle.id = "mvIndicators" + maxRowId;
        middle.size = 3;

        cellMiddle.appendChild(middle);

        var cellRightSel = row.insertCell(2);
        var right = document.createElement('input');
        right.type = 'text';
        right.name = 'mvLabels';
        right.size = 60;
        cellRightSel.appendChild(right);

        middle.focus();

        return false; // To prevent navigation
    }

    function toggleInherited(checkbox)
    {
        var div = document.getElementById("mvIndicatorsDiv");
        if (checkbox.checked)
            div.style.display = "none";
        else
            div.style.display = "block";
    }

    function validate()
    {
        // Check that we have at least one value
        if (document.getElementById("mvIndicatorsDiv").checked)
            return true;
        var tbl = document.getElementById("mvTable");
        var lastRow = tbl.rows.length;
        if (lastRow <= 2) // Labels and add button are the two rows that remain if all others are deleted
        {
            alert("You must have at least one indicator.");
            return false;
        }

        // Check that there are no blank values, and no repeats
        var indicators = document.getElementsByName("mvIndicators");
        var blankValue = indicators.length == 0;
        var repeatedValue = null;
        var valuesFound = {};
        for (var i = 0; i < indicators.length; i++)
        {
            var indicator = indicators[i].value;
            if (indicator == "")
            {
                blankValue = true;
            }
            // check for duplicates - case insensitive (issue 14513)
            if (valuesFound[indicator.toLowerCase()])
                repeatedValue = indicator;
            valuesFound[indicator.toLowerCase()] = true;
        }
        if (blankValue)
        {
            alert("Indicators cannot be blank.");
            return false;
        }
        if (repeatedValue)
        {
            alert("Found the indicator '" + repeatedValue + "' more than once.");
            return false;
        }

        return true;
    }

</script>