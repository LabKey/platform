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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.data.QcUtil" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    int rowId = 0;
%>
<form name="qcValuesForm" method="POST" action=folderSettings.post?tabId=qcValues>

    <table>
        <tr>
            <td>
                <%
                    Container c = HttpView.getContextContainer();
                    Container definingContainer = QcUtil.getDefiningContainer(c);
                    boolean inherited = !c.equals(definingContainer);
                    ActionURL inheritURL = HttpView.currentContext().cloneActionURL();
                    inheritURL.setContainer(definingContainer);
                    String containerLabel;
                    if (definingContainer.isRoot())
                        containerLabel = "Site";
                    else if (definingContainer.isProject())
                        containerLabel = "Project (" + definingContainer.getName() + ")";
                    else
                        containerLabel = "Parent Folder (" + definingContainer.getPath() + ")";

                    if (!c.isRoot())
                    {
                        // Only show the link to an enclosing container if there is one.
                %>
                <input type="checkbox" id="inherit" name="inheritQcValues" <%=inherited ? "checked='true'" : ""%>
                       onclick="toggleInherited(this);">
                Inherit QC settings from
                <a href="<%=inheritURL.getLocalURIString()%>">
                    <%=containerLabel%>
                </a>
                <%
                    }
                %>

            </td>
        </tr>
        <tr>
            <td>
                <div id="qcValuesDiv" style="display: <%=inherited ? "none" : "block"%>;">
                    <table id="qcTable">
                        <tr>
                            <th>&nbsp;</th>
                            <th>QC Value</th>
                            <th>Description</th>
                        </tr>

                        <%
                            Map<String, String> qcValuesAndLabels = QcUtil.getValuesAndLabels(definingContainer);
                            for (Map.Entry<String, String> entry : qcValuesAndLabels.entrySet())
                            {
                                String qcValue = entry.getKey();
                                String description = entry.getValue();
                                if (description == null)
                                    description = "";

                        %>

                        <tr id="rowId<%=++rowId%>">
                            <td><img src="<%=getViewContext().getContextPath()%>/_images/partdelete.gif"
                                     alt="delete" onclick="removeRow(<%=rowId%>);"></td>
                            <td><input name="qcValues" type="TEXT" size=3
                                       id="qcValues<%=rowId%>" value="<%=qcValue%>">
                            </td>
                            <td><input name="qcLabels" type="TEXT" size=60
                                       value="<%=description%>">
                            </td>
                        </tr>

                        <%
                            }
                        %>
                        <tr>
                            <td>&nbsp;</td>
                            <td><%=PageFlowUtil.generateButton("Add", "#", "addRowToTable();")%>
                            </td>
                            <td>&nbsp;</td>
                        </tr>
                    </table>
                </div>
            </td>
        </tr>
    </table>
    <%=PageFlowUtil.generateSubmitButton("Save", "return validate();")%>
</form>
<script type="text/javascript">

    var maxRowId = <%=rowId%>;

    function removeRow(rowIdNumber)
    {
        var row = document.getElementById("rowId" + rowIdNumber);
        row.parentNode.removeChild(row);
    }

    function addRowToTable()
    {
        var tbl = document.getElementById("qcTable");
        var lastRow = tbl.rows.length;
        var row = tbl.insertRow(lastRow - 1);
        row.id = "rowId" + ++maxRowId;

        var cellLeft = row.insertCell(0);
        var imgNode = document.createElement('img');
        imgNode.src = '<%=getViewContext().getContextPath()%>/_images/partdelete.gif';
        imgNode.setAttribute("onclick", 'removeRow(' + maxRowId + ');');
        imgNode.setAttribute("alt", "delete");
        cellLeft.appendChild(imgNode);

        var cellMiddle = row.insertCell(1);
        var middle = document.createElement('input');
        middle.type = 'text';
        middle.name = "qcValues";
        middle.id = "qcValues" + maxRowId;
        middle.size = 3;

        cellMiddle.appendChild(middle);

        var cellRightSel = row.insertCell(2);
        var right = document.createElement('input');
        right.type = 'text';
        right.name = 'qcLabels';
        right.size = 60;
        cellRightSel.appendChild(right);

        middle.focus();

        return false; // To prevent navigation
    }

    function toggleInherited(checkbox)
    {
        var div = document.getElementById("qcValuesDiv");
        if (checkbox.checked)
            div.style.display = "none";
        else
            div.style.display = "block";
    }

    function validate()
    {
        // Check that we have at least one value
        if (document.getElementById("qcValuesDiv").checked)
            return true;
        var tbl = document.getElementById("qcTable");
        var lastRow = tbl.rows.length;
        if (lastRow <= 2) // Labels and add button are the two rows that remain if all others are deleted
        {
            alert("You must have at least one QC value.");
            return false;
        }

        // Check that there are no blank values, and no repeats
        var qcValues = document.getElementsByName("qcValues");
        var blankValue = qcValues.length == 0;
        var repeatedValue = null;
        var valuesFound = new Object();
        for (var i = 0; i < qcValues.length; i++)
        {
            var qcValue = qcValues[i].value;
            if (qcValue == "")
            {
                blankValue = true;
            }
            if (valuesFound[qcValue])
                repeatedValue = qcValue;
            valuesFound[qcValue] = true;
        }
        if (blankValue)
        {
            alert("QC values cannot be blank.");
            return false;
        }
        if (repeatedValue)
        {
            alert("Found the QC value '" + repeatedValue + "' more than once.");
            return false;
        }

        return true;
    }

</script>