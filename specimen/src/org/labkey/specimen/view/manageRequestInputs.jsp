<%
/*
 * Copyright (c) 2014-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.study.StudyUrls"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.specimen.SpecimenRequestManager.SpecimenRequestInput"%>
<%@ page import="org.labkey.specimen.actions.ManageRequestInputsBean"%>
<%@ page import="org.labkey.specimen.actions.SpecimenController.HandleUpdateRequestInputsAction" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ManageRequestInputsBean> me = (JspView<ManageRequestInputsBean>) HttpView.currentView();
    ManageRequestInputsBean bean = me.getModelBean();

    String tdTitle = "<input type=\"text\" name=\"title\" size=\"20\">";
    String tdHelpText = "<input type=\"text\" name=\"helpText\" size=\"50\">";
    String tdMultiline = "<input type=\"checkbox\" name=\"multiline\">";
    String tdRequired = "<input type=\"checkbox\" name=\"required\">";
    String tdRememberSiteValue = "<input type=\"checkbox\" name=\"rememberSiteValue\">";
%>
<script type="text/javascript" nonce="<%=getScriptNonce()%>">
    function moveRow(elem, up)
    {
        const table = document.getElementById("inputTable");
        let row = elem;
        while (row.tagName !== 'TR')
            row = row.parentNode;
        // find our row, starting with 1 (0 is the header row):
        let rowIndex = 1;
        while (rowIndex < table.rows.length && table.rows[rowIndex] !== row)
            rowIndex++;
        const otherIndex = (up === true ? rowIndex - 1 : rowIndex + 1);
        if (otherIndex < 1 || otherIndex >= table.rows.length)
            return false;
        const otherRow = table.rows[otherIndex];

        swapRowProperties(row, otherRow, "title", "value");
        swapRowProperties(row, otherRow, "helpText", "value");
        swapRowProperties(row, otherRow, "multiline", "checked");
        swapRowProperties(row, otherRow, "required", "checked");
        swapRowProperties(row, otherRow, "rememberSiteValue", "checked");
        return false;
    }

    function swapRowProperties(row1, row2, elemName, property)
    {
        const row1Elem = getNamedElemFromRow(row1, elemName);
        const row2Elem = getNamedElemFromRow(row2, elemName);
        const temp = row1Elem[property];
        row1Elem[property] = row2Elem[property];
        row2Elem[property] = temp;
    }

    function getNamedElemFromRow(elem, name)
    {
        let value;
        if (elem.name === name)
            value = elem;
        if (!value && elem.firstChild)
            value = getNamedElemFromRow(elem.firstChild, name);
        if (!value && elem.nextSibling)
            value = getNamedElemFromRow(elem.nextSibling, name);
        return value;
    }

    function deleteRow(elem)
    {
        const table = document.getElementById("inputTable");
        if (table.rows.length === 2)
        {
            alert("At least one input must be present.");
            return false;
        }
        let row = elem;
        while (row.tagName !== 'TR')
            row = row.parentNode;
        const titleElem = getNamedElemFromRow(row, "title");
        if (titleElem.value && !confirm("Delete \"" + titleElem.value + "\"?"))
            return false;

        let rowIndex = 0;
        // find our row:
        while (rowIndex < table.rows.length && table.rows[rowIndex] !== row)
            rowIndex++;
        table.deleteRow(rowIndex);

        // We may need to reindex our values, since they're indexes into the array of inputs.
        for (let fixIndex = rowIndex; fixIndex < table.rows.length; fixIndex++)
        {
            const fixRow = table.rows[fixIndex];
            // set our checkbox values to be the zero-indexed row number, excluding the header row (hence the "-1")
            getNamedElemFromRow(fixRow, "multiline").value = (fixIndex - 1);
            getNamedElemFromRow(fixRow, "required").value = (fixIndex - 1);
            getNamedElemFromRow(fixRow, "rememberSiteValue").value = (fixIndex - 1);
        }

        return false;
    }

    function addRow()
    {
        const table = document.getElementById("inputTable");
        table.insertRow(table.rows.length);
        const newRow = table.rows[table.rows.length - 1];

        // create the new cells:
        const buttonCell = newRow.insertCell(newRow.cells.length);
        const titleCell = newRow.insertCell(newRow.cells.length);
        const helpTextCell = newRow.insertCell(newRow.cells.length);
        const multilineCell = newRow.insertCell(newRow.cells.length);
        const requiredCell = newRow.insertCell(newRow.cells.length);
        const rememberSiteValueCell = newRow.insertCell(newRow.cells.length);

        // set the HTML for the new cell:
        setButtonsHtml(buttonCell);
        attachAllButtonEvents();
        titleCell.innerHTML = <%= q(tdTitle)%>;
        helpTextCell.innerHTML = <%= q(tdHelpText)%>;
        multilineCell.innerHTML = <%= q(tdMultiline)%>;
        requiredCell.innerHTML = <%= q(tdRequired)%>;
        rememberSiteValueCell.innerHTML = <%= q(tdRememberSiteValue)%>;

        // align the checkboxes to center:
        multilineCell.align = "center";
        requiredCell.align = "center";
        rememberSiteValueCell.align = "center";

        // set our checkbox values to be the zero-indexed row number, excluding the header row (hence the "-2")
        getNamedElemFromRow(newRow, "multiline").value = (table.rows.length - 2);
        getNamedElemFromRow(newRow, "required").value = (table.rows.length - 2);
        getNamedElemFromRow(newRow, "rememberSiteValue").value = (table.rows.length - 2);
        return false;
    }

    function verifyForm()
    {
        const table = document.getElementById("inputTable");
        for (let i = 0; i < table.rows.length; i++)
        {
            const row = table.rows[i];
            const elem = getNamedElemFromRow(row, "title");
            if (!elem.value)
            {
                alert("A title is required for all inputs.");
                return false;
            }
        }
        return true;
    }

    function setButtonsHtml(td)
    {
        td.innerHTML =
            '<a id="buttonUp" href="#"><i class="fa fa-arrow-up"></i></a>\n' +
            '<a id="buttonDown" href="#"><i class="fa fa-arrow-down"></i></a>\n' +
            '<a id="buttonDelete" href="#""><i class="fa fa-times"></i></a>\n';
    }

    // Attach events "by hand" because rows are dynamically added and removed after initial render
    function attachAllButtonEvents()
    {
        LABKEY.Utils.attachEventHandler('buttonUp', 'click', function (){ return moveRow(this, true); }, 1);
        LABKEY.Utils.attachEventHandler('buttonDown', 'click', function (){ return moveRow(this, false); }, 1);
        LABKEY.Utils.attachEventHandler('buttonDelete', 'click', function (){ return deleteRow(this); }, 1);
    }

    LABKEY.Utils.onReady(function() {
        const list = document.querySelectorAll('#buttonsTd');
        for (let i in list)
            setButtonsHtml(list[i]);
        attachAllButtonEvents();
    });
</script>
<labkey:form action="<%=urlFor(HandleUpdateRequestInputsAction.class)%>" method="POST" onsubmit="return verifyForm()">
    <table id="inputTable" class="lk-fields-table">
        <tr>
            <th valign="bottom">&nbsp;</th>
            <th valign="bottom">Title</th>
            <th valign="bottom">Help Text</th>
            <th valign="bottom">Multiline</th>
            <th valign="bottom">Required</th>
            <th valign="bottom">Remember by Site<%= helpPopup("Remember by Location",
                    "If checked, the input will be pre-populated with the previous value entered for the destination location.")%></th>
        </tr>
    <%
        SpecimenRequestInput[] inputs = bean.getInputs();
        for (int inputIndex = 0; inputIndex < inputs.length; inputIndex++)
        {
            SpecimenRequestInput input = inputs[inputIndex];
    %>
        <tr>
            <td id="buttonsTd" style="padding-right: 5px;"></td>
            <td><input type="text" name="title" size="20" value="<%= h(input.getTitle()) %>"></td>
            <td><input type="text" name="helpText" size="50" value="<%= h(input.getHelpText()) %>"></td>
            <td align="center"><input type="checkbox" value="<%= inputIndex %>" name="multiline"<%=checked(input.isMultiLine())%>></td>
            <td align="center"><input type="checkbox" value="<%= inputIndex %>" name="required"<%=checked(input.isRequired())%>></td>
            <td align="center"><input type="checkbox" value="<%= inputIndex %>" name="rememberSiteValue"<%=checked(input.isRememberSiteValue())%>></td>
        </tr>
    <%
        }
    %>
    </table>
    <%= button("Add New Input").submit(true).onClick("return addRow();") %>
    <%= button("Save").submit(true) %>
    <%= button("Cancel").href(urlProvider(StudyUrls.class).getManageStudyURL(bean.getContainer())) %>
</labkey:form>