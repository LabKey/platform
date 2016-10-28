/*
 * Copyright (c) 2016 LabKey Corporation
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

	SELECT container, CAST('ExperimentRun' AS $VARCHAR$(50)) AS exptype, CAST(NULL AS $VARCHAR$(200)) AS cpastype, name, lsid, rowid, 'r'||CAST(rowid AS VARCHAR) as pathpart
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
		M.container as parent_container, CAST('Material' AS $VARCHAR$(50)) AS parent_exptype, M.cpastype AS parent_cpastype, M.name AS parent_name, M.lsid AS parent_lsid, M.rowid AS parent_rowid, 'm'||CAST(M.rowid AS VARCHAR) AS parent_pathpart,
		MI.role AS role,
		ER.container as child_container, CAST('ExperimentRun' AS $VARCHAR$(50)) AS child_exptype, CAST(NULL AS $VARCHAR$(200)) AS child_cpastype, ER.name AS child_name, ER.lsid AS child_lsid, ER.rowid AS child_rowid, 'r'||CAST(ER.rowid AS VARCHAR) AS child_pathpart
	FROM exp.experimentrun ER INNER JOIN exp.protocolapplication PA ON ER.rowid=PA.runid AND PA.cpastype='ExperimentRun'
		INNER JOIN exp.MaterialInput MI ON PA.rowid = MI.targetapplicationid INNER JOIN exp.Material M on MI.materialid = M.rowid

	UNION ALL

	-- EXPERIMENTRUN -> MATERIAL
	SELECT
		ER.container as parent_container, CAST('ExperimentRun' AS $VARCHAR$(50)) AS parent_exptype, CAST(NULL AS $VARCHAR$(200)) AS parent_cpastype, ER.name AS parent_name, ER.lsid AS parent_lsid, ER.rowid AS parent_rowid, 'r'||CAST(ER.rowid AS VARCHAR) AS parent_pathpart,
		MI.role AS role,
		M.container as child_container, CAST('Material' AS $VARCHAR$(50)) AS child_exptype, M.cpastype AS child_cpastype, M.name AS child_name, M.lsid AS child_lsid, M.rowid AS child_rowid, 'm'||CAST(M.rowid AS VARCHAR) AS child_pathpart
	FROM exp.experimentrun ER INNER JOIN exp.protocolapplication PA ON ER.rowid=PA.runid AND PA.cpastype='ExperimentRunOutput'
		INNER JOIN exp.MaterialInput MI ON PA.rowid = MI.targetapplicationid INNER JOIN exp.Material M on MI.materialid = M.rowid

	UNION ALL

	-- DATA --> EXPERIMENTRUN
	SELECT
		D.container as parent_container, CAST('Data' AS $VARCHAR$(50)) AS parent_exptype, CAST(D.cpastype AS $VARCHAR$(200)) AS parent_cpastype, D.name AS parent_name, D.lsid AS parent_lsid, D.rowid AS parent_rowid, 'd'||CAST(D.rowid AS VARCHAR) AS parent_pathpart,
		DI.role AS role,
		ER.container as child_container, CAST('ExperimentRun' AS $VARCHAR$(50)) AS child_exptype, CAST(NULL AS $VARCHAR$(200)) AS child_cpastype, ER.name AS child_name, ER.lsid AS child_lsid, ER.rowid AS child_rowid, 'r'||CAST(ER.rowid AS VARCHAR) AS parent_pathpart
	FROM exp.experimentrun ER INNER JOIN exp.protocolapplication PA ON ER.rowid=PA.runid AND PA.cpastype='ExperimentRun'
		INNER JOIN exp.DataInput DI ON PA.rowid = DI.targetapplicationid INNER JOIN exp.Data D on DI.dataid = D.rowid

	UNION ALL

	-- EXPERIMENTRUN -> DATA
	SELECT
		ER.container as parent_container, CAST('ExperimentRun' AS $VARCHAR$(50)) AS parent_exptype, CAST(NULL AS $VARCHAR$(200)) AS parent_cpastype, ER.name AS parent_name, ER.lsid AS parent_lsid, ER.rowid AS parent_rowid, 'r'||CAST(ER.rowid AS VARCHAR) AS parent_pathpart,
		DI.role AS role,
		D.container as child_container, CAST('Data' AS $VARCHAR$(50)) AS child_exptype, CAST(D.cpastype AS $VARCHAR$(200)) AS child_cpastype, D.name AS child_name, D.lsid AS child_lsid, D.rowid AS child_rowid, 'd'||CAST(D.rowid AS VARCHAR) AS parent_pathpart
	FROM exp.experimentrun ER INNER JOIN exp.protocolapplication PA ON ER.rowid=PA.runid AND PA.cpastype='ExperimentRunOutput'
		INNER JOIN exp.DataInput DI ON PA.rowid = DI.targetapplicationid INNER JOIN exp.Data D on DI.dataid = D.rowid
),


/* CTE */
$PARENTS$ AS
(
	SELECT
		0 AS depth,
		lsid AS self_lsid,
		rowid AS self_rowid,
		container AS parent_container,
		exptype AS parent_exptype,
		cpastype AS parent_cpastype,
		name AS parent_name,
		lsid AS parent_lsid,
		rowid AS parent_rowid,
		CAST('SELF' AS $VARCHAR$(50)) AS role,
		container AS child_container,
		exptype AS child_exptype,
		cpastype AS child_cpastype,
		name AS child_name,
		lsid AS child_lsid,
		rowid AS child_rowid,
		CAST('/' || pathpart || '/' AS VARCHAR(8000)) AS path
	FROM $SEED$

	UNION ALL

	SELECT
		_Graph.depth - 1 AS depth,
		_Graph.self_lsid,
		_Graph.self_rowid,
		_Edges.parent_container,
		_Edges.parent_exptype,
		_Edges.parent_cpastype,
		_Edges.parent_name,
		_Edges.parent_lsid,
		_Edges.parent_rowid,
		CAST(_Edges.role AS $VARCHAR$(50)) AS role,
		_Edges.child_container,
		_Edges.child_exptype,
		_Edges.child_cpastype,
		_Edges.child_name,
		_Edges.child_lsid,
		_Edges.child_rowid,
		CAST(_Graph.path || _Edges.parent_pathpart || '/' AS VARCHAR(8000)) AS path
	FROM $EDGES$ _Edges INNER JOIN $SELF$ _Graph ON _Edges.child_lsid = _Graph.parent_lsid
	WHERE _Graph.path NOT LIKE ('%/' || _Edges.parent_pathpart || '/%')
),

/* CTE */
$CHILDREN$ AS
(
	SELECT
		0 AS depth,
		lsid AS self_lsid,
		rowid AS self_rowid,
		container AS parent_container,
		exptype AS parent_exptype,
		cpastype AS parent_cpastype,
		name AS parent_name,
		lsid AS parent_lsid,
		rowid AS parent_rowid,
		CAST('SELF' AS $VARCHAR$(50)) AS role,
		container AS child_container,
		exptype AS child_exptype,
		cpastype AS child_cpastype,
		name AS child_name,
		lsid AS child_lsid,
		rowid AS child_rowid,
    CAST('/' || pathpart || '/' AS VARCHAR(8000)) AS path
	FROM $SEED$

	UNION ALL

	SELECT
		_Graph.depth + 1 AS depth,
		_Graph.self_lsid,
		_Graph.self_rowid,
		_Edges.parent_container,
		_Edges.parent_exptype,
		_Edges.parent_cpastype,
		_Edges.parent_name,
		_Edges.parent_lsid,
		_Edges.parent_rowid,
		CAST(_Edges.role AS $VARCHAR$(50)) AS role,
		_Edges.child_container,
		_Edges.child_exptype,
		_Edges.child_cpastype,
		_Edges.child_name,
		_Edges.child_lsid,
		_Edges.child_rowid,
		CAST(_Graph.path || _Edges.child_pathpart || '/' AS VARCHAR(8000)) AS path
	FROM $EDGES$ _Edges INNER JOIN $SELF$ _Graph ON _Edges.parent_lsid = _Graph.child_lsid
	WHERE _Graph.path NOT LIKE ('%/' || _Edges.child_pathpart || '/%')
)

--SELECT * FROM _GraphParents_$UNIQ$ UNION SELECT * FROM _GraphChildren_$UNIQ$
