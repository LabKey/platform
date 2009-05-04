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
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.study.controllers.BaseStudyController"%>
<%@ page import="org.labkey.study.controllers.StudyController"%>
<%@ page import="org.springframework.validation.BindException" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="java.util.List" %>
<%
    BaseStudyController.StudyJspView<StudyController.BulkImportTypesForm> me = (BaseStudyController.StudyJspView<StudyController.BulkImportTypesForm>) HttpView.currentView();
    StudyController.BulkImportTypesForm bean = me.getModelBean();
%>
<table>
<%
    BindException errors = (BindException)request.getAttribute("errors");
    if (errors != null)
    {
        for (ObjectError e : (List<ObjectError>) errors.getAllErrors())
        {
            %><tr><td colspan=3><font class="labkey-error"><%=h(HttpView.currentContext().getMessage(e))%></font></td></tr><%
        }
    }
%>
</table>
<p>
Use this form to import schemas for multiple datasets.
</p>
<p>
Paste in a tab delimited file with the following columns, as well as columns for type
name and type id.  Additional columns will just be ignored.
</p>
<table>
    <tr>
        <th align="left"><u>Column Header</u></th>
        <th align="left"><u>Description</u></th>
        <th align="left"><u>Sample Value</u></th>
    </tr>
    <tr>
        <th align=left valign=top nowrap>Property<span class="labkey-error">*</span></th>
        <td valign=top>The column name as it will appear when data is later imported</td>
        <td valign=top><code>PTID</code></td>
    </tr>
    <tr>
        <th align=left valign=top nowrap>Label</th>
        <td valign=top>Display Name</td>
        <td valign=top><code>Participant ID</code></td>
    </tr>
    <tr>
        <th align=left valign=top nowrap>ConceptURI</th>
        <td valign=top>The concept links to a definition</td>
        <td valign=top><code>SCHARP#Participant_ID</code></td>
    </tr>
    <tr>
        <th align=left valign=top nowrap>RangeURI<span class="labkey-error">*</span></th>
        <td valign=top>The storage type of this value</td>
        <td valign=top><code>xsd:int</code></td>
    </tr>
    <tr>
        <th align=left valign=top nowrap>Key</th>
        <td valign=top>Indicates that this column is an extra key (int, max 1 per dataset)</td>
        <td valign=top><code>0</code></td>
    </tr>
    <tr>
        <th align=left valign=top nowrap>AutoKey</th>
        <td valign=top>Indicates that this extra key column should be auto-incrementing, and managed by the server</td>
        <td valign=top><code>false</code></td>
    </tr>
    <tr>
        <th align=left valign=top nowrap>MvEnabled</th>
        <td valign=top>Indicates whether this column supports missing value indicators (e.g. "Q" or "N")</td>
        <td valign=top><code>false</code></td>
    </tr>
    <tr>
        <th align=left valign=top nowrap>Hidden</th>
        <td valign=top>Indicates whether this dataset should be hidden</td>
        <td valign=top><code>true</code></td>
    </tr>
    <tr>
        <th align=left valign=top nowrap>Category</th>
        <td valign=top>Indicates the category for this dataset</td>
        <td valign=top><code>CRF Data</code></td>
    </tr>
    <tr>
        <th align="left" colspan="3"><span class="labkey-error">*Required</span></th>
    </tr>
</table>

<form action="bulkImportDataTypes.post" method="POST" enctype="multipart/form-data">
    <table>
        <tr>
            <td class=labkey-form-label>Column containing dataset Name<span class="labkey-error">*</span></td>
        </tr>
        <tr>
            <td><input name="typeNameColumn" style="width:100%" value="<%=h(bean.getTypeNameColumn())%>"></td>
        </tr>
        <tr>
            <td class=labkey-form-label>Column containing dataset Label<span class="labkey-error">*</span></td>
        </tr>
        <tr>
            <td><input name="labelColumn" style="width:100%" value="<%=h(bean.getLabelColumn())%>"></td>
        </tr>
        <tr>
            <td class=labkey-form-label>Column containing dataset Id<span class="labkey-error">*</span> (an integer)</td>
        </tr>
        <tr>
            <td><input name="typeIdColumn" style="width:100%" value="<%=h(bean.getTypeIdColumn())%>"></td>
        </tr>
        <tr>
            <td class=labkey-form-label>Type Definition<span class="labkey-error">*</span> (tab delimited)</td>
        </tr>
        <tr>
            <td><textarea name=tsv rows=25 cols=80><%=h(bean.getTsv())%></textarea></td>
        </tr>
        <tr>
            <td><%= generateSubmitButton("Submit")%>&nbsp;<%= generateButton("Cancel", "manageTypes.view")%></td>
        </tr>
    </table>
</form>
