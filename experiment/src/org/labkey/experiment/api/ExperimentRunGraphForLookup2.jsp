<%
/*
 * Copyright (c) 2018-2019 LabKey Corporation
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
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.data.CoreSchema" %>
<%@ page import="org.labkey.api.data.JdbcType" %>
<%@ page import="org.labkey.api.data.dialect.SqlDialect" %>
<%@ page import="org.labkey.api.exp.api.ExpLineageOptions" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="static org.labkey.api.util.HtmlString.unsafe" %>
<%@ page import="org.labkey.api.exp.api.ExpRun" %>
<%@ page import="org.labkey.api.exp.api.ExpMaterial" %>
<%@ page import="org.labkey.api.exp.api.ExpData" %>
<%@ page extends="org.labkey.api.jsp.JspContext" %>
-- CTE comments are used as a marker to split up this file
-- we could have multiple files, or multiple multi-line string constants, but it's easier to develop this way.

<%
    SqlDialect dialect = CoreSchema.getInstance().getSqlDialect();
    var bean = (ExpLineageOptions) HttpView.currentModel();
    String expType = StringUtils.defaultString(bean.getExpTypeValue(), "ALL");
    // See Issue 37332, better (but more complicated) fix for sql server would be to use "option (maxrecursion 1000)"
    int depth = bean.getConfiguredDepth();
    var CONCAT = unsafe(dialect.isPostgreSQL() ? "||" : "+");

    String varcharType = dialect.getSqlTypeName(JdbcType.VARCHAR);

    assert "ALL".equals(expType) || ExpData.DEFAULT_CPAS_TYPE.equals(expType) || ExpMaterial.DEFAULT_CPAS_TYPE.equals(expType) || ExpRun.DEFAULT_CPAS_TYPE.equals(expType);
%>
<%!
    //COALESCE(PM.cpasType, PD.cpasType, PR.protocolLsid)
    HtmlString COALESCE(String expType, String name)
    {
        return switch (expType)
        {
            case ExpData.DEFAULT_CPAS_TYPE -> unsafe("D." + name);
            case ExpMaterial.DEFAULT_CPAS_TYPE -> unsafe("M." + name);
            case ExpRun.DEFAULT_CPAS_TYPE -> unsafe("R." + name);
            default -> unsafe("COALESCE(M." + name + ", D." + name + ", R." + name + ")");
        };
    }
%>

  /* CTE */
  $PARENTS_INNER$ AS
  (
    SELECT
      0                             AS depth,
      objectid                      AS self,
      objectid                      AS fromObjectId,
      CAST(NULL AS INT)             AS toObjectId,
      CAST('/' <%=CONCAT%> CAST(objectid AS VARCHAR(20)) <%=CONCAT%> '/' AS VARCHAR(8000)) AS path
<% if (bean.isUseObjectIds()) { %>
    FROM ($LSIDS$) as _seed_(objectid)
<% } else { %>
    FROM exp.object
    WHERE objecturi IN ($LSIDS$)
<% } %>

    UNION ALL

<%--
    NOTE this query (for lookup) stops short of including the node that would complete a cycle.  This node is already in the set
    and won't add any information once the lookup SQL enforces uniqueness (GROUP BY self, objectid)
    This is different than ExperimentRunGraph2.jsp.
--%>
    SELECT
      _Graph.depth - 1              AS depth,
      _Graph.self,
      _Edges.fromObjectId,
      _Edges.toObjectId,
      CAST('/' <%=CONCAT%> CAST(_Edges.fromObjectId AS VARCHAR(20)) <%=CONCAT%> _Graph.path AS VARCHAR(8000)) AS path
    FROM <%=unsafe(bean.getExpEdge())%> _Edges
      INNER JOIN $SELF$ _Graph ON _Edges.toObjectId = _Graph.fromObjectId
    WHERE 0 = {fn LOCATE('/' <%=CONCAT%> CAST(_Edges.fromObjectId as VARCHAR(20)) <%=CONCAT%> '/', _Graph.path)}
      AND _Graph.depth >= <%= (-1 * Math.abs(depth)) + 1 %>
    <% if (bean.getSourceKey() != null) { %>
      AND _Edges.sourcekey = $SOURCEKEY$
    <% } %>
  ),

  /* CTE */
  $PARENTS$ AS
  (
      SELECT
        I.depth,
        I.self,

        -- parent columns
        I.fromObjectId                         AS objectid
<%
if (bean.isOnlySelectObjectId()) {
%>
    FROM $PARENTS_INNER$ AS I
<%
} else {
%> , <%
   if ("ALL".equals(expType)) { %>
        CASE
        WHEN M.rowId IS NOT NULL
          THEN 'Material'
        WHEN D.rowId IS NOT NULL
          THEN 'Data'
        WHEN R.rowId IS NOT NULL
          THEN 'ExperimentRun'
        END                                    AS expType,
<% } else { %>
        CAST('<%=unsafe(expType)%>' AS <%=unsafe(varcharType)%>(100)) AS expType,
<% } %>
        <%=COALESCE(expType,"container")%>     AS container,
        <%=COALESCE(expType,"cpasType")%>      AS cpasType,
        <%=COALESCE(expType,"name")%>          AS name,
        <%=COALESCE(expType,"lsid")%>          AS lsid,
        <%=COALESCE(expType,"rowId")%>         AS rowId

      FROM $PARENTS_INNER$ AS I
<%  switch (expType)
    {
    case "ALL":
%>
        LEFT OUTER JOIN exp.data D ON I.fromObjectId = D.ObjectId
        LEFT OUTER JOIN (select *, protocolLsid as cpasType FROM exp.experimentrun) R ON I.fromObjectId = R.ObjectId
        LEFT OUTER JOIN exp.material M ON I.fromObjectId = M.ObjectId
<%
        break;
    case ExpData.DEFAULT_CPAS_TYPE:
%>
        JOIN exp.data D ON I.fromObjectId = D.ObjectId
<%
        break;
    case ExpRun.DEFAULT_CPAS_TYPE:
%>
        JOIN (select *, protocolLsid as cpasType FROM exp.experimentrun) R ON I.fromObjectId = R.ObjectId
<%
        break;
    case ExpMaterial.DEFAULT_CPAS_TYPE:
%>
        JOIN exp.material M ON I.fromObjectId = M.ObjectId
<%
    }
}
%>),

  /* CTE */
  $CHILDREN_INNER$ AS
  (
    SELECT
      0                             AS depth,
      objectid                      AS self,
      CAST(NULL AS INT)             AS fromObjectId,
      objectid                      AS toObjectId,
      CAST('/' <%=CONCAT%> CAST(objectid AS VARCHAR(20)) <%=CONCAT%> '/' AS VARCHAR(8000)) AS path
<% if (bean.isUseObjectIds()) { %>
    FROM ($LSIDS$) as _seed_(objectid)
<% } else { %>
    FROM exp.object
    WHERE objecturi IN ($LSIDS$)
<% } %>

    UNION ALL

    SELECT
      _Graph.depth + 1    AS depth,
      _Graph.self,
      _Edges.fromObjectId           AS fromObjectId,
      _Edges.toObjectId             AS toObjectId,
      CAST('/' <%=CONCAT%> CAST(_Edges.toObjectId AS VARCHAR(20)) <%=CONCAT%> _Graph.path AS VARCHAR(8000)) AS path
    FROM <%=unsafe(bean.getExpEdge())%> _Edges
      INNER JOIN $SELF$ _Graph ON _Edges.fromObjectId = _Graph.toObjectId
    WHERE 0 = {fn LOCATE('/' <%=CONCAT%> CAST(_Edges.toObjectId as VARCHAR(20)) <%=CONCAT%> '/', _Graph.path)}
      AND _Graph.depth <= <%= depth - 1 %>
    <% if (bean.getSourceKey() != null) { %>
      AND _Edges.sourcekey = $SOURCEKEY$
    <% } %>
  ),

  /* CTE */
  $CHILDREN$ AS
  (
      SELECT
        I.depth,
        I.self,

        -- child columns
        I.toObjectId                           AS objectid
<%
if (bean.isOnlySelectObjectId()) {
%>
    FROM $CHILDREN_INNER$ AS I
<%
} else {
%> , <%
   if ("ALL".equals(expType)) { %>
        CASE
        WHEN M.rowId IS NOT NULL
          THEN 'Material'
        WHEN D.rowId IS NOT NULL
          THEN 'Data'
        WHEN R.rowId IS NOT NULL
          THEN 'ExperimentRun'
        END                                    AS expType,
<% } else { %>
        CAST('<%=unsafe(expType)%>' AS <%=unsafe(varcharType)%>(100)) AS expType,
<% } %>
        <%=COALESCE(expType,"container")%>     AS container,
        <%=COALESCE(expType,"cpasType")%>      AS cpasType,
        <%=COALESCE(expType,"name")%>          AS name,
        <%=COALESCE(expType,"lsid")%>          AS lsid,
        <%=COALESCE(expType,"rowId")%>         AS rowId

      FROM $CHILDREN_INNER$ AS I
<%  switch (expType)
    {
    case "ALL":
%>
        LEFT OUTER JOIN exp.data D ON I.toObjectId = D.ObjectId
        LEFT OUTER JOIN (select *, protocolLsid as cpasType FROM exp.experimentrun) R ON I.toObjectId = R.ObjectId
        LEFT OUTER JOIN exp.material M ON I.toObjectId = M.ObjectId
<%
        break;
    case ExpData.DEFAULT_CPAS_TYPE:
%>
        JOIN exp.data D ON I.toObjectId = D.ObjectId
<%
        break;
    case ExpRun.DEFAULT_CPAS_TYPE:
%>
        JOIN (select *, protocolLsid as cpasType FROM exp.experimentrun) R ON I.toObjectId = R.ObjectId
<%
        break;
    case ExpMaterial.DEFAULT_CPAS_TYPE:
%>
        JOIN exp.material M ON I.toObjectId = M.ObjectId
<%
    }
}
%>)


