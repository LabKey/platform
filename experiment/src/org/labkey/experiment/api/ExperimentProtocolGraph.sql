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
-- This version is not aware of cpastype=='ExperimentRun' and cpastype=='ExperimentRunOutput', and is therefore probably wrong

WITH RECURSIVE

_Seed AS
(
  SELECT
		0 AS depth,
		container AS child_container,
		exptype AS child_exptype,
		cpastype AS child_cpastype,
		name AS child_name,
		lsid AS child_lsid,
		rowid AS child_rowid,
		CAST('SELF' AS VARCHAR(50)) AS role,
		container AS parent_container,
		exptype AS parent_exptype,
		cpastype AS parent_cpastype,
		name AS parent_name,
		lsid AS parent_lsid,
		rowid AS parent_rowid
	FROM _Nodes
	WHERE lsid IN (${LSIDS})
),

_Nodes AS
(
	SELECT container, CAST('Data' AS VARCHAR(50)) AS exptype, CAST(cpastype AS VARCHAR(200)) as cpastype, name, lsid, rowid
	FROM exp.Data

	UNION ALL

	SELECT container, CAST('Material' AS VARCHAR(50)) AS exptype, CAST(cpastype AS VARCHAR(200)) AS cpastype, name, lsid, rowid
	FROM exp.Material

	UNION ALL

	SELECT CAST(NULL as ENTITYID) AS container, CAST('ProtocolApplication' AS VARCHAR(50)) AS exptype, CAST(cpastype AS VARCHAR(200)) AS cpastype, name, lsid, rowid
	FROM exp.ProtocolApplication
),

_Edges AS
(
	SELECT PA.lsid AS parent_lsid, M.lsid AS child_lsid, CAST(NULL AS VARCHAR(50)) AS role
	FROM exp.protocolapplication PA INNER JOIN exp.Material M on PA.rowid = M.sourceapplicationid

	UNION ALL

	SELECT PA.lsid AS parent_lsid, D.lsid AS child_lsid, CAST(NULL AS VARCHAR(50)) AS role
	FROM exp.protocolapplication PA INNER JOIN exp.Data D on PA.rowid = D.sourceapplicationid

	UNION ALL

	SELECT M.lsid AS parent_lsid, PA.lsid AS child_lsid, MI.role
	FROM exp.Material M INNER JOIN exp.materialinput MI ON M.rowid=MI.materialid INNER JOIN exp.protocolapplication PA ON MI.targetapplicationid=PA.rowid

	UNION ALL

	SELECT D.lsid AS parent_lsid, PA.lsid AS child_lsid, DI.role
	FROM exp.Data D INNER JOIN exp.datainput DI ON D.rowid=DI.dataid INNER JOIN exp.protocolapplication PA ON DI.targetapplicationid=PA.rowid
),

_GraphParents AS
(
	SELECT *
	FROM _Seed

	UNION ALL

	SELECT
		_Graph.depth - 1 AS depth,
		_Graph.parent_container AS child_container,
		_Graph.parent_exptype AS child_exptype,
		_Graph.parent_cpastype AS child_cpastype,
		_Graph.parent_name AS child_name,
		_Graph.parent_lsid AS child_lsid,
		_Graph.parent_rowid AS child_rowid,
		_Edges.role AS role,
		_Nodes.container AS parent_container,
		_Nodes.exptype AS parent_exptype,
		_Nodes.cpastype AS parent_cpastype,
		_Nodes.name AS parent_name,
		_Nodes.lsid AS parent_lsid,
		_Nodes.rowid AS parent_rowid
	FROM _Nodes INNER JOIN _Edges on _Nodes.lsid = _Edges.parent_lsid INNER JOIN _GraphParents _Graph ON _Edges.child_lsid = _Graph.parent_lsid
),

_GraphChildren AS
(
	SELECT *
	FROM _Seed

	UNION ALL

	SELECT
		_Graph.depth + 1 AS depth,
		_Nodes.container AS child_container,
		_Nodes.exptype AS child_exptype,
		_Nodes.cpastype AS child_cpastype,
		_Nodes.name AS child_name,
		_Nodes.lsid AS child_lsid,
		_Nodes.rowid AS child_rowid,
		_Edges.role AS role,
		_Graph.child_container AS parent_container,
		_Graph.child_exptype AS parent_exptype,
		_Graph.child_cpastype AS parent_cpastype,
		_Graph.child_name AS parent_name,
		_Graph.child_lsid AS parent_lsid,
		_Graph.child_rowid AS parent_rowid
	FROM _Nodes INNER JOIN _Edges on _Nodes.lsid = _Edges.child_lsid INNER JOIN _GraphChildren _Graph ON _Edges.parent_lsid = _Graph.child_lsid
)

--SELECT * FROM _GraphParents UNION SELECT * FROM _GraphChildren
