<%
/*
 * Copyright (c) 2005-2014 LabKey Corporation
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
<%@ page import="org.labkey.experiment.types.TypesController" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    TypesController.ImportVocabularyForm form = (TypesController.ImportVocabularyForm )HttpView.currentModel();
%>
<h3>Upload Vocabulary</h3>

<p>
Use this form to upload a vocabulary or dictionary of concepts.  The file format is compatible with
the flat file format used by NCICB, except column headers are required. see <a href="http://ncicb.nci.nih.gov/NCICB/infrastructure/cacore_overview/vocabulary">http://ncicb.nci.nih.gov/NCICB/infrastructure/cacore_overview/vocabulary</a>.
</p>
<p>
Specifically it should be a tab-delimited text file with no column headers, and these columns.
</p>

<table>
    <tr><th align=left valign=top nowrap>Code</th><td valign=top>not used internally</td><td valign=top><code>C25298</code></td></tr>
    <tr><th align=left valign=top nowrap>UMLS</th><td valign=top>8 char UMLS code, not used internally</td><td valign=top><code>C0428880</code></td></tr>
    <tr><th align=left valign=top nowrap>Name<span class="labkey-error">*</span></th><td valign=top>Alphanumeric with - and _, required</td><td valign=top><code>Systolic_Pressure</code></td></tr>
    <tr><th align=left valign=top nowrap>Label</th><td valign=top>Display name</td><td valign=top><code>Systolic Pressure</code></td></tr>
    <tr><th align=left valign=top nowrap>Parent</th><td valign=top>More general concept for this term</td><td valign=top><code>Personal_Attribute</code></td></tr>
    <tr><th align=left valign=top nowrap>SemanticType</th><td valign=top>Type of concept, | separated if more than one</td><td valign=top><code>Quantitative Concept</code></td></tr>
    <tr><th align=left valign=top nowrap>Synonyms</th><td valign=top>Synonyms separated by |.  These words are used for searching.  The first synonym is used as the label if no label is specified.</td><td valign=top><code>Systolic Pressure|SBP</code></td></tr>
    <tr><th align=left valign=top nowrap>Description</th><td valign=top>Free form description</td><td valign=top><code>The blood pressure during the contraction of the left ventricle of the heart.</code></td></tr>
    <tr><th align=left valign=top nowrap>RangeURI</th><td valign=top>Datatype of this property</td><td valign=top><code>xsd:int</code></td></tr>
</table>

<p>&nbsp;</p>

<labkey:form action="<%=h(buildURL(TypesController.ImportVocabularyAction.class))%>" method="POST" enctype="multipart/form-data">
<table>
<tr><td class=labkey-form-label>Thesaurus URI<br><small>e.g http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl</small></td><td><input name="name" value="<%=h(form.getName())%>"></td></tr>
<!--<tr><td class=labkey-form-label>Short prefix<br><small>e.g NCI_Thesaurus</small></td><td><input name="prefix" value=""></td></tr> -->
<tr><td class=labkey-form-label>Upload File</td><td><input type=file name=thesaurus></td></tr>
</table>
    <%= button("Submit").submit(true) %>
</labkey:form>