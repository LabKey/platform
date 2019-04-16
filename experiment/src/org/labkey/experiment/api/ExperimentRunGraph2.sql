/*
 * Copyright (c) 2018 LabKey Corporation
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
-- noinspection SqlNoDataSourceInspectionForFile
-- noinspection SqlDialectInspectionForFile
-- CTE comments are used as a marker to split up this file
-- we could have multiple files, or multiple multi-line string constants, but it's easier to develop this way.

  /* CTE */
    $PARENTS_INNER$ AS
  (
    SELECT
      0                                              AS depth,
      objectid                                       AS fromObjectId,
      CAST(NULL AS INT)                              AS toObjectId,
      CAST('/' || CAST(objectid as VARCHAR(20)) || '/' AS VARCHAR(8000)) AS path
    FROM exp.object
    WHERE objecturi IN ($LSIDS$)

    UNION ALL

    SELECT
      _Graph.depth - 1                                           AS depth,
      _Edges.fromObjectId,
      _Edges.toObjectId,
      CAST(SUBSTRING(_Graph.path,1+{fn LENGTH(_Graph.path)}+21-8000,8000) || CAST(_Edges.fromObjectId AS VARCHAR(20)) || '/' AS VARCHAR(8000)) AS path
    FROM exp.Edge _Edges
      INNER JOIN $SELF$ _Graph ON _Edges.toObjectId = _Graph.fromObjectId
    WHERE 0 = {fn LOCATE('/' || CAST(_Edges.toObjectId AS VARCHAR(20)) || '/' || CAST(_Edges.fromObjectId as VARCHAR(20)) || '/', _Graph.path)}
    $AND_STUFF$
  ),

  /* CTE */
    $PARENTS$ AS
  (
      SELECT
        I.depth,
        -- CONSIDER: If we want to include role, we could add a protocolApplication for both 'from' and 'to' to the exp.edge table
        --'no role' AS role,

        -- parent columns
        COALESCE(PM.container, PD.container, PR.container)    AS parent_container,
        CASE
        WHEN PM.rowId IS NOT NULL
          THEN 'Material'
        WHEN PD.rowId IS NOT NULL
          THEN 'Data'
        WHEN PR.rowId IS NOT NULL
          THEN 'ExperimentRun'
        END                                                   AS parent_expType,
        COALESCE(PM.cpasType, PD.cpasType, PR.protocolLsid)   AS parent_cpasType,
        COALESCE(PM.name, PD.name, PR.name)                   AS parent_name,
        PO.objectURI                                          AS parent_lsid,
        COALESCE(PM.rowId, PD.rowId, PR.rowId)                AS parent_rowId,

        -- child columns
        COALESCE(CM.container, CD.container, CR.container)    AS child_container,
        CASE
        WHEN CM.rowId IS NOT NULL
          THEN 'Material'
        WHEN CD.rowId IS NOT NULL
          THEN 'Data'
        WHEN CR.rowId IS NOT NULL
          THEN 'ExperimentRun'
        END                                                   AS child_expType,
        COALESCE(CM.cpasType, CD.cpasType, CR.protocolLsid)   AS child_cpasType,
        COALESCE(CM.name, CD.name, CR.name)                   AS child_name,
        CO.objectURI                                          AS child_lsid,
        COALESCE(CM.rowId, CD.rowId, CR.rowId)                AS child_rowId

      FROM $PARENTS_INNER$ AS I
            INNER JOIN exp.Object PO ON I.fromObjectId = PO.objectId
            INNER JOIN exp.Object CO ON I.toObjectId   = CO.objectId

        LEFT OUTER JOIN exp.material PM      ON PO.objectUri = PM.lsid
        LEFT OUTER JOIN exp.data PD          ON PO.objectUri = PD.lsid
        LEFT OUTER JOIN exp.experimentrun PR ON PO.objectUri = PR.lsid

        LEFT OUTER JOIN exp.material CM      ON CO.objectUri = CM.lsid
        LEFT OUTER JOIN exp.data CD          ON CO.objectUri = CD.lsid
        LEFT OUTER JOIN exp.experimentrun CR ON CO.objectUri = CR.lsid

  ),

  /* CTE */
    $CHILDREN_INNER$ AS
  (
    SELECT
      0                                              AS depth,
      CAST(NULL AS INT)                              AS fromObjectId,
      objectid                                       AS toObjectId,
      CAST('/' || CAST(objectid as VARCHAR(20)) || '/' AS VARCHAR(8000)) AS PATH
    FROM exp.object
    WHERE objecturi IN ($LSIDS$)

    UNION ALL

    SELECT
      _Graph.depth + 1                               AS depth,
      _Edges.fromObjectId,
      _Edges.toObjectId,
      CAST(SUBSTRING(_Graph.path,1+{fn LENGTH(_Graph.path)}+21-8000,8000) || CAST(_Edges.toObjectId AS VARCHAR(20)) || '/' AS VARCHAR(8000)) AS path
    FROM exp.Edge _Edges
      INNER JOIN $SELF$ _Graph ON _Edges.fromObjectId = _Graph.toObjectId
    WHERE 0 = {fn LOCATE('/' || CAST(_Edges.fromObjectId AS VARCHAR(20)) || '/' || CAST(_Edges.toObjectId AS VARCHAR(20)) || '/', _Graph.path)}
    $AND_STUFF$
  ),

  /* CTE */
    $CHILDREN$ AS
  (
      SELECT
        I.depth,
        -- CONSIDER: If we want to include role, we could add a protocolApplication for both 'from' and 'to' to the exp.edge table
        --'no role' AS role,

        -- parent columns
        COALESCE(PM.container, PD.container, PR.container)    AS parent_container,
        CASE
        WHEN PM.rowId IS NOT NULL
          THEN 'Material'
        WHEN PD.rowId IS NOT NULL
          THEN 'Data'
        WHEN PR.rowId IS NOT NULL
          THEN 'ExperimentRun'
        END                                                   AS parent_expType,
        COALESCE(PM.cpasType, PD.cpasType, PR.protocolLsid)   AS parent_cpasType,
        COALESCE(PM.name, PD.name, PR.name)                   AS parent_name,
        PO.objectUri                                          AS parent_lsid,
        COALESCE(PM.rowId, PD.rowId, PR.rowId)                AS parent_rowId,

        -- child columns
        COALESCE(CM.container, CD.container, CR.container)    AS child_container,
        CASE
        WHEN CM.rowId IS NOT NULL
          THEN 'Material'
        WHEN CD.rowId IS NOT NULL
          THEN 'Data'
        WHEN CR.rowId IS NOT NULL
          THEN 'ExperimentRun'
        END                                                   AS child_expType,
        COALESCE(CM.cpasType, CD.cpasType, CR.protocolLsid)   AS child_cpasType,
        COALESCE(CM.name, CD.name, CR.name)                   AS child_name,
        CO.objectUri                                          AS child_lsid,
        COALESCE(CM.rowId, CD.rowId, CR.rowId)                AS child_rowId

      FROM $CHILDREN_INNER$ AS I
            INNER JOIN exp.Object PO ON I.fromObjectId = PO.objectId
            INNER JOIN exp.Object CO ON I.toObjectId   = CO.objectId

        LEFT OUTER JOIN exp.material PM      ON PO.objectUri = PM.lsid
        LEFT OUTER JOIN exp.data PD          ON PO.objectUri = PD.lsid
        LEFT OUTER JOIN exp.experimentrun PR ON PO.objectUri = PR.lsid

        LEFT OUTER JOIN exp.material CM      ON CO.objectUri = CM.lsid
        LEFT OUTER JOIN exp.data CD          ON CO.objectUri = CD.lsid
        LEFT OUTER JOIN exp.experimentrun CR ON CO.objectUri = CR.lsid

  )


