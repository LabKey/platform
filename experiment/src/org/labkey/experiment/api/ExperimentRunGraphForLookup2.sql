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
      CAST(objecturi AS $LSIDTYPE$)                  AS self_lsid,
      CAST(objecturi AS $LSIDTYPE$)                  AS fromLsid,
      CAST(NULL AS $LSIDTYPE$)                       AS toLsid,
      CAST('/' || objecturi || '/' AS VARCHAR(8000)) AS path
    FROM exp.object
    WHERE objecturi IN ($LSIDS$)

    UNION ALL

    SELECT
      _Graph.depth - 1                                           AS depth,
      _Graph.self_lsid,
      _Edges.fromLsid,
      _Edges.toLsid,
      -- NOTE: will likely want to change fromLsid to fromObjectId in the path
      CAST(_Graph.path || _Edges.toLsid || '/' AS VARCHAR(8000)) AS path
    FROM exp.Edge _Edges
      INNER JOIN $SELF$ _Graph ON _Edges.toLsid = _Graph.fromLsid
    WHERE _Graph.path NOT LIKE ('%/' || _Edges.fromLsid || '/%')
    $AND_STUFF$
  ),

  /* CTE */
  $PARENTS$ AS
  (
      SELECT
        I.depth,
        I.self_lsid,
        --I.self_rowid,

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
      LEFT OUTER JOIN exp.material PM ON I.fromLsid = PM.lsid
      LEFT OUTER JOIN exp.data PD ON I.fromLsid = PD.lsid
      LEFT OUTER JOIN exp.experimentrun PR ON I.fromLsid = PR.lsid

  ),

  /* CTE */
  $CHILDREN_INNER$ AS
  (
    SELECT
      0                                              AS depth,
      CAST(objecturi AS $LSIDTYPE$)                  AS self_lsid,
      CAST(NULL AS $LSIDTYPE$)                       AS fromLsid,
      CAST(objecturi AS $LSIDTYPE$)                  AS toLsid,
      CAST('/' || objecturi || '/' AS VARCHAR(8000)) AS path
    FROM exp.object
    WHERE objecturi IN ($LSIDS$)

    UNION ALL

    SELECT
      _Graph.depth + 1                                             AS depth,
      _Graph.self_lsid,
      _Edges.fromLsid,
      _Edges.toLsid,
      -- NOTE: will likely want to change fromLsid to fromObjectId in the path
      CAST(_Graph.path || _Edges.fromLsid || '/' AS VARCHAR(8000)) AS path
    FROM exp.Edge _Edges
      INNER JOIN $SELF$ _Graph ON _Edges.fromLsid = _Graph.toLsid
    WHERE _Graph.path NOT LIKE ('%/' || _Edges.toLsid || '/%')
    $AND_STUFF$
  ),

  /* CTE */
  $CHILDREN$ AS
  (
      SELECT
        I.depth,
        I.self_lsid,

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
      LEFT OUTER JOIN exp.material CM ON I.toLsid = CM.lsid
      LEFT OUTER JOIN exp.data CD ON I.toLsid = CD.lsid
      LEFT OUTER JOIN exp.experimentrun CR ON I.toLsid = CR.lsid

  )


