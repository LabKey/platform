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
<%@ page import="org.labkey.api.data.dialect.SqlDialect" %>
<%@ page import="org.labkey.api.exp.api.ExpLineageOptions" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page extends="org.labkey.api.jsp.JspContext" %>
-- CTE comments are used as a marker to split up this file
-- we could have multiple files, or multiple multi-line string constants, but it's easier to develop this way.

<%
    SqlDialect dialect = CoreSchema.getInstance().getSqlDialect();
    var bean = (ExpLineageOptions) HttpView.currentModel();
    String expType = StringUtils.defaultString(bean.getExpTypeValue(), "ALL");
  // see bug 37332, better (but more complicated) fix for sql server would be to use "option (maxrecursion 1000)"
    int depth = bean.getDepth();
    if (depth == 0)
      depth = dialect.isSqlServer() ? 100 : 1000;
    var CONCAT = HtmlString.unsafe(dialect.isPostgreSQL() ? "||" : "+");

    assert ExpLineageOptions.LineageExpType.fromValue(expType) != null;

%>
  /* CTE */
    $PARENTS_INNER$ AS
  (
    SELECT
      0                                              AS depth,
      objectid                                       AS fromObjectId,
      CAST(NULL AS INT)                              AS toObjectId,
      CAST('/' AS VARCHAR(8000)) AS path
<% if (bean.isUseObjectIds()) { %>
    FROM ($LSIDS$) as _seed_(objectid)
<% } else { %>
    FROM exp.object
    WHERE objecturi IN ($LSIDS$)
<% } %>

    UNION ALL

    SELECT
      _Graph.depth - 1                                           AS depth,
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
        -- CONSIDER: If we want to include role, we could add a protocolApplication for both 'from' and 'to' to the exp.edge table
        --'no role' AS role,

        -- parent columns
        COALESCE(PM.container, PD.container, PR.container, PO.container)
                                                              AS parent_container,
        CASE
        WHEN PM.rowId IS NOT NULL
          THEN 'Material'
        WHEN PD.rowId IS NOT NULL
          THEN 'Data'
        WHEN PR.rowId IS NOT NULL
          THEN 'ExperimentRun'
        WHEN PO.objectId IS NOT NULL
          THEN 'Object'
        END                                                   AS parent_expType,
        CASE
        WHEN PR.protocolLsid IS NOT NULL
          THEN PR.protocolLsid
        ELSE 'NONE'
        END                                                   AS parent_protocolLsid,
        COALESCE(PM.cpasType, PD.cpasType, PR.protocolLsid)   AS parent_cpasType,
        COALESCE(PM.name, PD.name, PR.name)                   AS parent_name,
        COALESCE(PM.lsid, PD.lsid, PR.lsid, PO.objectUri)     AS parent_lsid,
        COALESCE(PM.rowId, PD.rowId, PR.rowId, PO.objectId)   AS parent_rowId,

        -- child columns
        COALESCE(CM.container, CD.container, CR.container, CO.container)
                                                              AS child_container,
        CASE
        WHEN CM.rowId IS NOT NULL
          THEN 'Material'
        WHEN CD.rowId IS NOT NULL
          THEN 'Data'
        WHEN CR.rowId IS NOT NULL
          THEN 'ExperimentRun'
        WHEN CO.objectId IS NOT NULL
          THEN 'Object'
        END                                                   AS child_expType,
        CASE
          WHEN CR.protocolLsid IS NOT NULL
        THEN CR.protocolLsid
          ELSE 'NONE'
        END                                                   AS child_protocolLsid,
        COALESCE(CM.cpasType, CD.cpasType, CR.protocolLsid)   AS child_cpasType,
        COALESCE(CM.name, CD.name, CR.name)                   AS child_name,
        COALESCE(CM.lsid, CD.lsid, CR.lsid, CO.objectUri)     AS child_lsid,
        COALESCE(CM.rowId, CD.rowId, CR.rowId, CO.objectId)   AS child_rowId

      FROM $PARENTS_INNER$ AS I

        LEFT OUTER JOIN exp.material PM      ON I.fromObjectId = PM.ObjectId
        LEFT OUTER JOIN exp.data PD          ON I.fromObjectId = PD.ObjectId
        LEFT OUTER JOIN exp.experimentrun PR ON I.fromObjectId = PR.ObjectId
        LEFT OUTER JOIN exp.object PO        ON I.fromObjectId = PO.ObjectId

        LEFT OUTER JOIN exp.material CM      ON I.toObjectId  = CM.ObjectId
        LEFT OUTER JOIN exp.data CD          ON I.toObjectId  = CD.ObjectId
        LEFT OUTER JOIN exp.experimentrun CR ON I.toObjectId  = CR.ObjectId
        LEFT OUTER JOIN exp.object CO        ON I.toObjectId  = CO.ObjectId

  ),

  /* CTE */
    $CHILDREN_INNER$ AS
  (
    SELECT
      0                                              AS depth,
      CAST(NULL AS INT)                              AS fromObjectId,
      objectid                                       AS toObjectId,
      CAST('/' AS VARCHAR(8000)) AS PATH
<% if (bean.isUseObjectIds()) { %>
    FROM ($LSIDS$) as _seed_(objectid)
<% } else { %>
    FROM exp.object
    WHERE objecturi IN ($LSIDS$)
<% } %>

    UNION ALL

    SELECT
      _Graph.depth + 1                               AS depth,
      _Edges.fromObjectId,
      _Edges.toObjectId,
      CAST(SUBSTRING(_Graph.path,1+{fn LENGTH(_Graph.path)}+21-8000,8000) <%=CONCAT%> CAST(_Edges.fromObjectId AS VARCHAR(20)) <%=CONCAT%> '/' AS VARCHAR(8000)) AS path
    FROM exp.Edge _Edges
      INNER JOIN $SELF$ _Graph ON _Edges.fromObjectId = _Graph.toObjectId
    WHERE 0 = {fn LOCATE('/' <%=CONCAT%> CAST(_Edges.toObjectId AS VARCHAR(20)) <%=CONCAT%> '/', _Graph.path)}
      AND _Graph.depth <= <%= depth - 1 %>
  ),

  /* CTE */
    $CHILDREN$ AS
  (
      SELECT
        I.depth,
        -- CONSIDER: If we want to include role, we could add a protocolApplication for both 'from' and 'to' to the exp.edge table
        --'no role' AS role,

        -- parent columns
        --I.fromObjectId                                        AS parent_objectId,
        COALESCE(PM.container, PD.container, PR.container)    AS parent_container,
        CASE
        WHEN PM.rowId IS NOT NULL
          THEN 'Material'
        WHEN PD.rowId IS NOT NULL
          THEN 'Data'
        WHEN PR.rowId IS NOT NULL
          THEN 'ExperimentRun'
        WHEN PO.objectId IS NOT NULL
          THEN 'Object'
        END                                                   AS parent_expType,
        CASE
        WHEN PR.protocolLsid IS NOT NULL
          THEN PR.protocolLsid
        ELSE 'NONE'
        END                                                   AS parent_protocolLsid,
        COALESCE(PM.cpasType, PD.cpasType, PR.protocolLsid)   AS parent_cpasType,
        COALESCE(PM.name, PD.name, PR.name)                   AS parent_name,
        COALESCE(PM.lsid, PD.lsid, PR.lsid, PO.objectUri)     AS parent_lsid,
        COALESCE(PM.rowId, PD.rowId, PR.rowId, PO.objectId)   AS parent_rowId,

        -- child columns
        --I.toObjectId                                          AS child_objectId,
        COALESCE(CM.container, CD.container, CR.container, CO.container)
                                                              AS child_container,
        CASE
        WHEN CM.rowId IS NOT NULL
          THEN 'Material'
        WHEN CD.rowId IS NOT NULL
          THEN 'Data'
        WHEN CR.rowId IS NOT NULL
          THEN 'ExperimentRun'
        WHEN CO.objectId IS NOT NULL
          THEN 'Object'
        END                                                   AS child_expType,
        CASE
          WHEN CR.protocolLsid IS NOT NULL
        THEN CR.protocolLsid
          ELSE 'NONE'
        END                                                   AS child_protocolLsid,
        COALESCE(CM.cpasType, CD.cpasType, CR.protocolLsid)   AS child_cpasType,
        COALESCE(CM.name, CD.name, CR.name)                   AS child_name,
        COALESCE(CM.lsid, CD.lsid, CR.lsid, CO.objectUri)     AS child_lsid,
        COALESCE(CM.rowId, CD.rowId, CR.rowId, CO.objectId)   AS child_rowId

      FROM $CHILDREN_INNER$ AS I

        LEFT OUTER JOIN exp.material PM      ON I.fromObjectId = PM.ObjectId
        LEFT OUTER JOIN exp.data PD          ON I.fromObjectId = PD.ObjectId
        LEFT OUTER JOIN exp.experimentrun PR ON I.fromObjectId = PR.ObjectId
        LEFT OUTER JOIN exp.object PO        ON I.fromObjectId = PO.ObjectId

        LEFT OUTER JOIN exp.material CM      ON I.toObjectId = CM.ObjectId
        LEFT OUTER JOIN exp.data CD          ON I.toObjectId = CD.ObjectId
        LEFT OUTER JOIN exp.experimentrun CR ON I.toObjectId = CR.ObjectId
        LEFT OUTER JOIN exp.object CO        ON I.toObjectId = CO.ObjectId

  )