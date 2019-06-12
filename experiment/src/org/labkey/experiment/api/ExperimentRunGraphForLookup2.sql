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
      0                             AS depth,
      objecturi                     AS self_lsid,
      objectid                      AS self,
      objectid                      AS fromObjectId,
      CAST(NULL AS INT)             AS toObjectId,
      CAST('/' AS VARCHAR(8000)) AS path
    FROM exp.object
    WHERE objecturi IN ($LSIDS$)

    UNION ALL

    SELECT
      _Graph.depth - 1              AS depth,
      _Graph.self_lsid,
      _Graph.self,
      _Edges.fromObjectId,
      _Edges.toObjectId,
      CAST(SUBSTRING(_Graph.path,1+{fn LENGTH(_Graph.path)}+21-8000,8000) || CAST(_Edges.toObjectId AS VARCHAR(20)) || '/' AS VARCHAR(8000)) AS path
    FROM exp.Edge _Edges
      INNER JOIN $SELF$ _Graph ON _Edges.toObjectId = _Graph.fromObjectId
    WHERE 0 = {fn LOCATE('/' || CAST(_Edges.fromObjectId as VARCHAR(20)) || '/', _Graph.path)}
    $AND_STUFF$
  ),

  /* CTE */
  $PARENTS$ AS
  (
      SELECT
        I.depth,
        I.self_lsid,
        I.self,

        -- parent columns
        COALESCE(PM.container, PD.container, PR.container)    AS container,
        CASE
        WHEN PM.rowId IS NOT NULL
          THEN 'Material'
        WHEN PD.rowId IS NOT NULL
          THEN 'Data'
        WHEN PR.rowId IS NOT NULL
          THEN 'ExperimentRun'
        END                                                   AS expType,
        COALESCE(PM.cpasType, PD.cpasType, PR.protocolLsid)   AS cpasType,
        COALESCE(PM.name, PD.name, PR.name)                   AS name,
        COALESCE(PM.lsid, PD.lsid, PR.lsid)                   AS lsid,
        COALESCE(PM.rowId, PD.rowId, PR.rowId)                AS rowId

      FROM $PARENTS_INNER$ AS I
      LEFT OUTER JOIN exp.material PM ON I.fromObjectId = PM.ObjectId
      LEFT OUTER JOIN exp.data PD ON I.fromObjectId = PD.ObjectId
      LEFT OUTER JOIN exp.experimentrun PR ON I.fromObjectId = PR.ObjectId
  ),

  /* CTE */
  $CHILDREN_INNER$ AS
  (
    SELECT
      0                             AS depth,
      objecturi                     AS self_lsid,
      objectid                      AS self,
      CAST(NULL AS INT)             AS fromObjectId,
      objectid                      AS toObjectId,
      CAST('/' AS VARCHAR(8000)) AS path
    FROM exp.object
    WHERE objecturi IN ($LSIDS$)

    UNION ALL

    SELECT
      _Graph.depth + 1    AS depth,
      _Graph.self_lsid,
      _Graph.self,
      _Edges.fromObjectId           AS fromObjectId,
      _Edges.toObjectId             AS toObjectId,
      CAST(SUBSTRING(_Graph.path,1+{fn LENGTH(_Graph.path)}+21-8000,8000) || CAST(_Edges.fromObjectId AS VARCHAR(20)) || '/' AS VARCHAR(8000)) AS path
    FROM exp.Edge _Edges
      INNER JOIN $SELF$ _Graph ON _Edges.fromObjectId = _Graph.toObjectId
    WHERE 0 = {fn LOCATE('/' || CAST(_Edges.toObjectId as VARCHAR(20)) || '/', _Graph.path)}
    $AND_STUFF$
  ),

  /* CTE */
  $CHILDREN$ AS
  (
      SELECT
        I.depth,
        I.self_lsid,
        I.self,

        -- child columns
        COALESCE(CM.container, CD.container, CR.container)    AS container,
        CASE
        WHEN CM.rowId IS NOT NULL
          THEN 'Material'
        WHEN CD.rowId IS NOT NULL
          THEN 'Data'
        WHEN CR.rowId IS NOT NULL
          THEN 'ExperimentRun'
        END                                                   AS expType,
        COALESCE(CM.cpasType, CD.cpasType, CR.protocolLsid)   AS cpasType,
        COALESCE(CM.name, CD.name, CR.name)                   AS name,
        COALESCE(CM.lsid, CD.lsid, CR.lsid)                   AS lsid,
        COALESCE(CM.rowId, CD.rowId, CR.rowId)                AS rowId

      FROM $CHILDREN_INNER$ AS I
      LEFT OUTER JOIN exp.material CM ON I.toObjectId = CM.ObjectId
      LEFT OUTER JOIN exp.data CD ON I.toObjectId = CD.ObjectId
      LEFT OUTER JOIN exp.experimentrun CR ON I.toObjectId = CR.ObjectId
  )


