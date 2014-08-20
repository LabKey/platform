<%
/*
 * Copyright (c) 2006-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.study.controllers.BaseStudyController.StudyJspView"%>
<%@ page import="org.labkey.study.controllers.StudyController.BulkImportTypesForm"%>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    StudyJspView<BulkImportTypesForm> me = (StudyJspView<BulkImportTypesForm>) HttpView.currentView();
    BulkImportTypesForm bean = me.getModelBean();
%>
<labkey:errors/>
<p>
Use this page to import the schema for multiple datasets.  Paste in a tab-delimited file that includes the five required
columns and any of the optional columns.
</p>
<p>
The first five columns mentioned below are properties of the dataset; the value must be identical for all field
definitions within each dataset.  The remaining properties are field properties; these will change within each dataset.
</p>
<p>
For more information about the schema definition format, see <%=helpLink("DatasetBulkDefinition", "the dataset schema definition documentation page")%>
</p>
<table>
    <tr>
        <th align="left"><u>Column Header</u></th>
        <th align="left"><u>Description</u></th>
        <th align="left"><u>Sample Value</u></th>
    </tr>
    <tr>
        <th align=left valign=top nowrap>&lt;DatasetNameHeader&gt;<span class="labkey-error">*</span></th>
        <td valign=top>Dataset name (specify the actual column header below)</td>
        <td valign=top><code>DEM-1</code></td>
    </tr>
    <tr>
        <th align=left valign=top nowrap>&lt;DatasetLabelHeader&gt;<span class="labkey-error">*</span></th>
        <td valign=top>Dataset label (specify the actual column header below)</td>
        <td valign=top><code>Demographics</code></td>
    </tr>
    <tr>
        <th align=left valign=top nowrap>&lt;DatasetIdHeader&gt;<span class="labkey-error">*</span></th>
        <td valign=top>Dataset id (specify the actual column header below)</td>
        <td valign=top><code>1</code></td>
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
        <td colspan="3">&nbsp;</td>
    </tr>
    <tr>
        <th align=left valign=top nowrap>Property<span class="labkey-error">*</span></th>
        <td valign=top>The field name as it appears in the data to be imported</td>
        <td valign=top><code>PTID</code></td>
    </tr>
    <tr>
        <th align=left valign=top nowrap>RangeURI<span class="labkey-error">*</span></th>
        <td valign=top>The storage type of this field</td>
        <td valign=top><code>xsd:int</code></td>
    </tr>
    <tr>
        <th align=left valign=top nowrap>Label</th>
        <td valign=top>Display name for this field</td>
        <td valign=top><code>Participant ID</code></td>
    </tr>
    <tr>
        <th align=left valign=top nowrap>ConceptURI</th>
        <td valign=top>Concept associated with this field</td>
        <td valign=top><code>SCHARP#Participant_ID</code></td>
    </tr>
    <tr>
        <th align=left valign=top nowrap>Key</th>
        <td valign=top>Indicates that this field is an extra key (int, max 1 per dataset)</td>
        <td valign=top><code>0</code></td>
    </tr>
    <tr>
        <th align=left valign=top nowrap>AutoKey</th>
        <td valign=top>Indicates that this extra key field should be auto-incrementing, and managed by the server</td>
        <td valign=top><code>false</code></td>
    </tr>
    <tr>
        <th align=left valign=top nowrap>MvEnabled</th>
        <td valign=top>Indicates whether this field supports missing value indicators (e.g. "Q" or "N")</td>
        <td valign=top><code>false</code></td>
    </tr>
    <tr>
        <th align="left" colspan="3"><span class="labkey-error">*Required</span></th>
    </tr>
</table>

<labkey:form action="<%=h(buildURL(StudyController.BulkImportDataTypesAction.class))%>" method="POST" enctype="multipart/form-data">
    <table>
        <tr>
            <td class=labkey-form-label>Header of column containing dataset Name (e.g., platename)<span class="labkey-error">*</span></td>
        </tr>
        <tr>
            <td><input name="typeNameColumn" style="width:100%" value="<%=h(bean.getTypeNameColumn())%>"></td>
        </tr>
        <tr>
            <td class=labkey-form-label>Header of column containing dataset Label (e.g., platelabel)<span class="labkey-error">*</span></td>
        </tr>
        <tr>
            <td><input name="labelColumn" style="width:100%" value="<%=h(bean.getLabelColumn())%>"></td>
        </tr>
        <tr>
            <td class=labkey-form-label>Header of column containing integer dataset Id (e.g., plateid)<span class="labkey-error">*</span></td>
        </tr>
        <tr>
            <td><input name="typeIdColumn" style="width:100%" value="<%=h(bean.getTypeIdColumn())%>"></td>
        </tr>
        <tr>
            <td class=labkey-form-label>Dataset schema definition<span class="labkey-error">*</span> (tab delimited)</td>
        </tr>
        <tr>
            <td><textarea name=tsv rows=25 cols=80><%=h(bean.getTsv())%></textarea></td>
        </tr>
        <tr>
            <td><%= button("Submit").submit(true) %>&nbsp;<%= button("Cancel").href(StudyController.ManageTypesAction.class, getContainer()) %></td>
        </tr>
    </table>
</labkey:form>
