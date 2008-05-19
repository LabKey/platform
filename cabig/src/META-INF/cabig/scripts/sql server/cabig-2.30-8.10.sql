/*
 * Copyright (c) 2008 LabKey Corporation
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
/* cabig-2.30-2.31.sql */

EXEC core.fn_dropifexists 'experimentrun', 'cabig','VIEW', NULL
GO

CREATE VIEW cabig.experimentrun
AS
SELECT er.rowid, er.lsid, er.name, er.protocollsid, er.comments, er.filepathroot
	, c.rowid as containerid
	, o.objectid
	, p.rowid as protocolid
	, pa.rowid as actionid
FROM (exp.experimentrun er
	INNER JOIN cabig.containers c ON (er.container = c.entityid)
	LEFT OUTER JOIN exp.object o ON (er.lsid = o.objecturi))
	INNER JOIN exp.protocol p ON (er.protocollsid = p.lsid)
	INNER JOIN exp.protocolaction pa ON (pa.parentprotocolid = p.rowid) AND (pa.parentprotocolid = pa.childprotocolid)
GO