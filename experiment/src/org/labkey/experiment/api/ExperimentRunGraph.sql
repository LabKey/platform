/*
 * Copyright (c) 2016-2017 LabKey Corporation
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
$NODES$ AS
(
	SELECT container, CAST('Data' AS $VARCHAR$(50)) AS exptype, CAST(cpastype AS $VARCHAR$(200)) AS cpastype, name, lsid, rowid, 'd'||CAST(rowid AS VARCHAR) AS pathpart
	FROM exp.Data

	UNION ALL

	SELECT container, CAST('Material' AS $VARCHAR$(50)) AS exptype, CAST(cpastype AS $VARCHAR$(200)) AS cpastype, name, lsid, rowid, 'm'||CAST(rowid AS VARCHAR) as pathpart
	FROM exp.Material

 	UNION ALL

	SELECT container, CAST('ExperimentRun' AS $VARCHAR$(50)) AS exptype, CAST(protocollsid AS $VARCHAR$(200)) AS cpastype, name, lsid, rowid, 'r'||CAST(rowid AS VARCHAR) as pathpart
	FROM exp.ExperimentRun
),


/* CTE */
$SEED$ AS
(
	SELECT *
	FROM $NODES$
	WHERE lsid IN ($LSIDS$)
),


/* CTE */
$EDGES$ AS
(
	-- MATERIAL --> EXPERIMENTRUN
	SELECT
	    M.lsid AS parent_lsid, 'm'||CAST(M.rowid AS VARCHAR) AS parent_pathpart,
	    MI.role AS role,
	    ER.lsid AS child_lsid, 'r'||CAST(ER.rowid AS VARCHAR) AS child_pathpart
	FROM exp.experimentrun ER INNER JOIN exp.protocolapplication PA ON ER.rowid=PA.runid AND PA.cpastype='ExperimentRun'
		INNER JOIN exp.MaterialInput MI ON PA.rowid = MI.targetapplicationid INNER JOIN exp.Material M on MI.materialid = M.rowid

	UNION ALL

	-- EXPERIMENTRUN -> MATERIAL
	SELECT
	    ER.lsid AS parent_lsid, 'r'||CAST(ER.rowid AS VARCHAR) AS parent_pathpart,
	    MI.role AS role,
	    M.lsid AS child_lsid, 'm'||CAST(M.rowid AS VARCHAR) AS child_pathpart
	FROM exp.experimentrun ER INNER JOIN exp.protocolapplication PA ON ER.rowid=PA.runid AND PA.cpastype='ExperimentRunOutput'
		INNER JOIN exp.MaterialInput MI ON PA.rowid = MI.targetapplicationid INNER JOIN exp.Material M on MI.materialid = M.rowid

	UNION ALL

	-- DATA --> EXPERIMENTRUN
	SELECT
	    D.lsid AS parent_lsid, 'd'||CAST(D.rowid AS VARCHAR) AS parent_pathpart,
	    DI.role AS role,
	    ER.lsid AS child_lsid, 'r'||CAST(ER.rowid AS VARCHAR) AS child_pathpart
	FROM exp.experimentrun ER INNER JOIN exp.protocolapplication PA ON ER.rowid=PA.runid AND PA.cpastype='ExperimentRun'
		INNER JOIN exp.DataInput DI ON PA.rowid = DI.targetapplicationid INNER JOIN exp.Data D on DI.dataid = D.rowid

	UNION ALL

	-- EXPERIMENTRUN -> DATA
	SELECT
	    ER.lsid AS parent_lsid, 'r'||CAST(ER.rowid AS VARCHAR) AS parent_pathpart,
	    DI.role AS role,
	    D.lsid AS child_lsid, 'd'||CAST(D.rowid AS VARCHAR) AS child_pathpart
	FROM exp.experimentrun ER INNER JOIN exp.protocolapplication PA ON ER.rowid=PA.runid AND PA.cpastype='ExperimentRunOutput'
		INNER JOIN exp.DataInput DI ON PA.rowid = DI.targetapplicationid INNER JOIN exp.Data D on DI.dataid = D.rowid
),


/* CTE */
$PARENTS_INNER$ AS
(
	SELECT
		0 AS depth,
		lsid AS parent_lsid,
		CAST('SELF' AS $VARCHAR$(50)) AS role,
		lsid AS child_lsid,
		CAST('/' || pathpart || '/' AS VARCHAR(8000)) AS path
	FROM $SEED$

	UNION ALL

	SELECT
		_Graph.depth - 1 AS depth,
		_Edges.parent_lsid,
		CAST(_Edges.role AS $VARCHAR$(50)) AS role,
		_Edges.child_lsid,
		CAST(_Graph.path || _Edges.parent_pathpart || '/' AS VARCHAR(8000)) AS path
	FROM $EDGES$ _Edges INNER JOIN $SELF$ _Graph ON _Edges.child_lsid = _Graph.parent_lsid
	WHERE _Graph.path NOT LIKE ('%/' || _Edges.parent_pathpart || '/%')
	$AND_STUFF$
),

/* CTE */
$PARENTS$ AS
(
  SELECT
    I.depth as depth,
    I.role,

    PN.container AS parent_container,
    PN.exptype AS parent_exptype,
    PN.cpastype AS parent_cpastype,
    PN.name AS parent_name,
    PN.lsid AS parent_lsid,
    PN.rowid AS parent_rowid,

    CN.container AS child_container,
    CN.exptype AS child_exptype,
    CN.cpastype AS child_cpastype,
    CN.name AS child_name,
    CN.lsid AS child_lsid,
    CN.rowid AS child_rowid

    FROM $PARENTS_INNER$ AS I
    INNER JOIN $NODES$ PN ON I.parent_lsid = PN.lsid
    INNER JOIN $NODES$ CN ON I.child_lsid = CN.lsid
),

/* CTE */
$CHILDREN_INNER$ AS
(
	SELECT
		0 AS depth,
		lsid AS parent_lsid,
		CAST('SELF' AS $VARCHAR$(50)) AS role,
		lsid AS child_lsid,
    CAST('/' || pathpart || '/' AS VARCHAR(8000)) AS path
	FROM $SEED$

	UNION ALL

	SELECT
		_Graph.depth + 1 AS depth,
		_Edges.parent_lsid,
		CAST(_Edges.role AS $VARCHAR$(50)) AS role,
		_Edges.child_lsid,
		CAST(_Graph.path || _Edges.child_pathpart || '/' AS VARCHAR(8000)) AS path
	FROM $EDGES$ _Edges INNER JOIN $SELF$ _Graph ON _Edges.parent_lsid = _Graph.child_lsid
	WHERE _Graph.path NOT LIKE ('%/' || _Edges.child_pathpart || '/%')
	$AND_STUFF$
),

/* CTE */
$CHILDREN$ AS
(
  SELECT
    I.depth as depth,
    I.role,

    PN.container AS parent_container,
    PN.exptype AS parent_exptype,
    PN.cpastype AS parent_cpastype,
    PN.name AS parent_name,
    PN.lsid AS parent_lsid,
    PN.rowid AS parent_rowid,

    CN.container AS child_container,
    CN.exptype AS child_exptype,
    CN.cpastype AS child_cpastype,
    CN.name AS child_name,
    CN.lsid AS child_lsid,
    CN.rowid AS child_rowid

    FROM $CHILDREN_INNER$ AS I
    INNER JOIN $NODES$ PN ON I.parent_lsid = PN.lsid
    INNER JOIN $NODES$ CN ON I.child_lsid = CN.lsid
)

--SELECT * FROM _GraphParents_$UNIQ$ UNION SELECT * FROM _GraphChildren_$UNIQ$
