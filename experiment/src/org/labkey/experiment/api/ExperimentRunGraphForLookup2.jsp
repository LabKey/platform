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
<%@ page extends="org.labkey.api.jsp.JspContext" %>
-- CTE comments are used as a marker to split up this file
-- we could have multiple files, or multiple multi-line string constants, but it's easier to develop this way.

<%
    SqlDialect dialect = CoreSchema.getInstance().getSqlDialect();
    var bean = (ExpLineageOptions) HttpView.currentModel();
    String expType = StringUtils.defaultString(bean.getExpType(), "ALL");
    // see bug 37332, better (but more complicated) fix for sql server would be to use "option (maxrecursion 1000)"
    int depth = bean.getDepth();
    if (depth == 0)
        depth = dialect.isSqlServer() ? 100 : 1000;
    var CONCAT = unsafe(dialect.isPostgreSQL() ? "||" : "+");

    String varcharType = dialect.getSqlTypeName(JdbcType.VARCHAR);

    assert "ALL".equals(expType) || "Data".equals(expType) || "Material".equals(expType) || "ExperimentRun".equals(expType);
%>
<%!
    //COALESCE(PM.cpasType, PD.cpasType, PR.protocolLsid)
    HtmlString COALESCE(String expType, String name)
    {
        switch (expType)
        {
            default:
                return unsafe("COALESCE(M." + name + ", D." + name + ", R." + name + ")");
            case "Data":
                return unsafe("D." + name);
            case "Material":
                return unsafe("M." + name);
            case "ExperimentRun":
                return unsafe("R." + name);
        }
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
      CAST('/' AS VARCHAR(8000)) AS path
<% if (bean.isUseObjectIds()) { %>
    FROM ($LSIDS$) as _seed_(objectid)
<% } else { %>
    FROM exp.object
    WHERE objecturi IN ($LSIDS$)
<% } %>

    UNION ALL

    SELECT
      _Graph.depth - 1              AS depth,
      _Graph.self,
      _Edges.fromObjectId,
      _Edges.toObjectId,
      CAST(SUBSTRING(_Graph.path,1+{fn LENGTH(_Graph.path)}+21-8000,8000) <%=CONCAT%> CAST(_Edges.toObjectId AS VARCHAR(20)) <%=CONCAT%> '/' AS VARCHAR(8000)) AS path
    FROM exp.Edge _Edges
      INNER JOIN $SELF$ _Graph ON _Edges.toObjectId = _Graph.fromObjectId
    WHERE 0 = {fn LOCATE('/' <%=CONCAT%> CAST(_Edges.fromObjectId as VARCHAR(20)) <%=CONCAT%> '/', _Graph.path)}
      AND _Graph.depth >= <%= (-1 * Math.abs(depth)) + 1 %>
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
    case "Data":
%>
        JOIN exp.data D ON I.fromObjectId = D.ObjectId
<%
        break;
    case "ExperimentRun":
%>
        JOIN (select *, protocolLsid as cpasType FROM exp.experimentrun) R ON I.fromObjectId = R.ObjectId
<%
        break;
    case "Material":
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
      CAST('/' AS VARCHAR(8000)) AS path
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
      CAST(SUBSTRING(_Graph.path,1+{fn LENGTH(_Graph.path)}+21-8000,8000) <%=CONCAT%> CAST(_Edges.fromObjectId AS VARCHAR(20)) <%=CONCAT%> '/' AS VARCHAR(8000)) AS path
    FROM exp.Edge _Edges
      INNER JOIN $SELF$ _Graph ON _Edges.fromObjectId = _Graph.toObjectId
    WHERE 0 = {fn LOCATE('/' <%=CONCAT%> CAST(_Edges.toObjectId as VARCHAR(20)) <%=CONCAT%> '/', _Graph.path)}
      AND _Graph.depth <= <%= depth - 1 %>
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
    case "Data":
%>
        JOIN exp.data D ON I.toObjectId = D.ObjectId
<%
        break;
    case "ExperimentRun":
%>
        JOIN (select *, protocolLsid as cpasType FROM exp.experimentrun) R ON I.toObjectId = R.ObjectId
<%
        break;
    case "Material":
%>
        JOIN exp.material M ON I.toObjectId = M.ObjectId
<%
    }
}
%>)


