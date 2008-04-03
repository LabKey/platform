<%@ page import="org.labkey.api.view.*" %>
<%@ page import="org.labkey.study.controllers.OldStudyController" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.model.Study" %>
<%@ page import="org.apache.commons.beanutils.ConvertUtils" %>
<%@ page import="org.labkey.api.data.DbSchema" %>
<%@ page import="org.labkey.api.data.TableInfo" %>
<%@ page import="java.util.Set" %>
<%@ page import="org.apache.struts.action.ActionErrors" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>

<%
    JspView<OldStudyController.SnapshotForm> me = (JspView<OldStudyController.SnapshotForm>) HttpView.currentView();
    OldStudyController.SnapshotForm form = me.getModelBean();

    ViewContext context = HttpView.currentContext();

    Study study = StudyManager.getInstance().getStudy(context.getContainer());
    assert null != study;

    StudyManager.SnapshotBean lastSnapshot = form.getBean();
    String schemaName = form.getSchemaName();
    if (schemaName == null)
        schemaName = lastSnapshot.getSchemaName();


if (form.isComplete())
{   %>
    <b>Snapshot completed successfully</b><br>
    Schema Name: <%=h(schemaName)%><br>
    Snapshot Date: <%=h(ConvertUtils.convert(lastSnapshot.getLastSnapshotDate()))%>

<%
    }
    else
    {
%>
<%=PageFlowUtil.getStrutsError(request, "main")%>
This page allows an administrator to create a new database schema containing a snapshot of all datasets in a study.<br>
Standard database query tools can be used to operate on the snapshot.<br>

NOTE: The existing schema will be completely replaced by this operation.<br><br>

<form action="snapshot.post" method="post">
    Schema Name <input name="schemaName" type="text" value="<%=h(schemaName)%>"> <br>
<%  } %>

<br>
<%
    int index = 0;
    for (String category : lastSnapshot.getCategories())
    {
        Set<String> sourceNames = lastSnapshot.getSourceNames(category);
        if (null == sourceNames || sourceNames.size() == 0)
            continue;
%>
        <b><%=h(category)%></b>
        <table class="dataregion"><tr>
            <th>Snapshot</th>
            <th>Source Name</th>
            <th>Snapshot Table Name</th>
            </tr>
       <%
       for (String sourceName: sourceNames)
       {
           String tableName = lastSnapshot.getDestTableName(category, sourceName);
           boolean snapshot = lastSnapshot.isSaveTable(category, sourceName);
       %>
            <tr><%
             if (form.isComplete())
             {%>
                <td><%=snapshot ? "yes" : "no"%></td>
                <td><%=h(sourceName)%></td>
                <td><%=snapshot ? h(tableName) : "&nbsp;"%><%
             } else { %>
                <td><input type="hidden" name="category[<%=index%>]" value="<%=h(category)%>" ><input type=checkbox name="snapshot[<%=index%>]" value="true" <%=snapshot ? "CHECKED" : ""%> ></td>
                <td><%=h(sourceName)%><input type=hidden name="sourceName[<%=index%>]" value="<%=h(sourceName)%>" ></td>
                <td><input type=text name="destName[<%=index%>]" value="<%=h(tableName)%>" ><%
             }
                %>
            </tr><%
            index++;
        }%>
            </table><br><br> <%
    }
    if (form.isComplete()) {
        out.write(buttonLink("Manage Study", "manageStudy.view"));
    }
    else
    {%>
        <%=buttonImg("Create Snapshot")%>
        </form> <%
    } %>


