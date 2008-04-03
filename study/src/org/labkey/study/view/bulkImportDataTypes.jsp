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
<table border=0 cellspacing=2 cellpadding=0>
<%
    BindException errors = (BindException)request.getAttribute("errors");
    if (errors != null)
    {
        for (ObjectError e : (List<ObjectError>) errors.getAllErrors())
        {
            %><tr><td colspan=3><font color="red" class="error"><%=h(HttpView.currentContext().getMessage(e))%></font></td></tr><%
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
<table cellpadding="2" class="normal">
    <tr>
        <th align="left"><u>Column Header</u></th>
        <th align="left"><u>Description</u></th>
        <th align="left"><u>Sample Value</u></th>
    </tr>
    <tr>
        <th align=left valign=top nowrap>Property<span style="color:red;">*</span></th>
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
        <th align=left valign=top nowrap>RangeURI<span style="color:red;">*</span></th>
        <td valign=top>The storage type of this value</td>
        <td valign=top><code>xsd:int</code></td>
    </tr>
    <tr>
        <th align="left" colspan="3"><span style="color:red;">*Required</span></th>
    </tr>
</table>

<form action="bulkImportDataTypes.post" method="POST" enctype="multipart/form-data">
    <table cellspacing="0" cellpadding="2">
        <tr>
            <td class=ms-searchform>Column containing dataset Name</td>
        </tr>
        <tr>
            <td><input name="typeNameColumn" style="width:100%" value="<%=h(bean.getTypeNameColumn())%>"></td>
        </tr>
        <tr>
            <td class=ms-searchform>Column containing dataset Label</td>
        </tr>
        <tr>
            <td><input name="labelColumn" style="width:100%" value="<%=h(bean.getLabelColumn())%>"></td>
        </tr>
        <tr>
            <td class=ms-searchform>Column containing dataset Id</td>
        </tr>
        <tr>
            <td><input name="typeIdColumn" style="width:100%" value="<%=h(bean.getTypeIdColumn())%>"></td>
        </tr>
        <tr>
            <td class=ms-searchform>Type Definition (tab delimited)</td>
        </tr>
        <tr>
            <td><textarea name=tsv rows=25 cols=80><%=h(bean.getTsv())%></textarea></td>
        </tr>
        <tr>
            <td><%= buttonImg("Submit")%>&nbsp;<%= buttonLink("Cancel", "manageTypes.view")%></td>
        </tr>
    </table>
</form>