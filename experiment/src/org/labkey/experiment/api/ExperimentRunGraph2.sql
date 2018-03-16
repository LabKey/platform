-- noinspection SqlNoDataSourceInspectionForFile
-- noinspection SqlDialectInspectionForFile
-- CTE comments are used as a marker to split up this file
-- we could have multiple files, or multiple multi-line string constants, but it's easier to develop this way.

  /* CTE */
    $PARENTS_INNER$ AS
  (
    SELECT
      0                                              AS depth,
      CAST(objecturi AS $LSIDTYPE$)                  AS fromLsid,
      CAST(NULL AS $LSIDTYPE$)                       AS toLsid,
      CAST('/' || objecturi || '/' AS VARCHAR(8000)) AS path
    FROM exp.object
    WHERE objecturi IN ($LSIDS$)

    UNION ALL

    SELECT
      _Graph.depth - 1                                           AS depth,
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
        COALESCE(PM.lsid, PD.lsid, PR.lsid)                   AS parent_lsid,
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
        COALESCE(CM.lsid, CD.lsid, CR.lsid)                   AS child_lsid,
        COALESCE(CM.rowId, CD.rowId, CR.rowId)                AS child_rowId

      FROM $PARENTS_INNER$ AS I
        LEFT OUTER JOIN exp.material PM ON I.fromLsid = PM.lsid
        LEFT OUTER JOIN exp.data PD ON I.fromLsid = PD.lsid
        LEFT OUTER JOIN exp.experimentrun PR ON I.fromLsid = PR.lsid

        LEFT OUTER JOIN exp.material CM ON I.toLsid = CM.lsid
        LEFT OUTER JOIN exp.data CD ON I.toLsid = CD.lsid
        LEFT OUTER JOIN exp.experimentrun CR ON I.toLsid = CR.lsid

  ),

  /* CTE */
    $CHILDREN_INNER$ AS
  (
    SELECT
      0                                              AS depth,
      CAST(NULL AS $LSIDTYPE$)                       AS fromLsid,
      CAST(objecturi AS $LSIDTYPE$)                  AS toLsid,
      CAST('/' || objecturi || '/' AS VARCHAR(8000)) AS PATH
    FROM exp.object
    WHERE objecturi IN ($LSIDS$)

    UNION ALL

    SELECT
      _Graph.depth + 1                                             AS depth,
      _Edges.fromLsid,
      _Edges.toLsid,
      -- NOTE: will likely want to change fromLsid to fromObjectId in the path
      CAST(_Graph.path || _Edges.fromLsid || '/' AS VARCHAR(8000)) AS PATH
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
        COALESCE(PM.lsid, PD.lsid, PR.lsid)                   AS parent_lsid,
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
        COALESCE(CM.lsid, CD.lsid, CR.lsid)                   AS child_lsid,
        COALESCE(CM.rowId, CD.rowId, CR.rowId)                AS child_rowId

      FROM $CHILDREN_INNER$ AS I
        LEFT OUTER JOIN exp.material PM ON I.fromLsid = PM.lsid
        LEFT OUTER JOIN exp.data PD ON I.fromLsid = PD.lsid
        LEFT OUTER JOIN exp.experimentrun PR ON I.fromLsid = PR.lsid

        LEFT OUTER JOIN exp.material CM ON I.toLsid = CM.lsid
        LEFT OUTER JOIN exp.data CD ON I.toLsid = CD.lsid
        LEFT OUTER JOIN exp.experimentrun CR ON I.toLsid = CR.lsid

  )


