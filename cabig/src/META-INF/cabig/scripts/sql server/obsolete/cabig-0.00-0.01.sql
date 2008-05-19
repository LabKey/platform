/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

create schema cabig
go


EXEC sp_addapprole 'cabig', 'password'
go


CREATE TABLE cabig.containers
(
	rowid int NOT NULL PRIMARY KEY,
	entityid ENTITYID NOT NULL UNIQUE,
	parentid int,
	name nvarchar(255)
)
go


create view cabig.propertyvalue
as
SELECT ((4294967296 * op.propertyid) + op.objectid) AS valueid, op.objectid, op.propertyid
	, CASE WHEN op.floatvalue IS NOT NULL THEN CAST(op.floatvalue AS CHAR(255))
		WHEN op.datetimevalue IS NOT NULL THEN CAST(op.datetimevalue AS CHAR(255))
		ELSE op.stringvalue END AS strvalue
	, pd.propertyuri, pd.name, pd.rangeuri
--	, pd.propertyid, pd.ontologyuri, pd.description, pd.concepturi
--	, op.typetag, op.floatvalue, op.datetimevalue, op.stringvalue
FROM exp.objectproperty op
	INNER JOIN exp.propertydescriptor pd ON (op.propertyid = pd.propertyid)
	INNER JOIN exp.object o ON (op.objectid = o.objectid)
	INNER JOIN cabig.containers c ON (o.container = c.entityid)
go


create view cabig.propertydescriptor
as
SELECT pd.propertyid, pd.propertyuri, pd.name, pd.ontologyuri, pd.description, pd.rangeuri, pd.concepturi
FROM exp.propertydescriptor pd
	INNER JOIN cabig.containers c ON c.entityid = pd.container
go


create view cabig.domaindescriptor
as
SELECT dd.domainid , dd.name, dd.domainuri, dd.description
FROM exp.domaindescriptor dd
	INNER JOIN cabig.containers c ON c.entityid = dd.container
go


create view cabig.propertydomain
as
SELECT pd.domainid , pd.propertyid
FROM exp.propertydomain pd
go


create view cabig.customproperties
as
SELECT o.objectid, o.objecturi, o.ownerobjectid
FROM exp.object o
	INNER JOIN cabig.containers c ON c.entityid = o.container
go


create view cabig.protocoldefinition as
SELECT p.rowid as protocolid, p.lsid
	, p.name, p.protocoldescription, p.applicationtype, p.maxinputmaterialperinstance
	, p.maxinputdataperinstance, p.outputmaterialperinstance, p.outputdataperinstance, p.outputmaterialtype
	, p.outputdatatype, p.instrument, p.software, p.contactid
 	, c.rowid as containerid, o.objectid
FROM exp.protocol p
	INNER JOIN cabig.containers c on (p.container = c.entityid)
	LEFT OUTER JOIN exp.object o on (p.lsid = o.objecturi)
go


create view cabig.protocolaction as
SELECT pa.rowid as actionid, pa.childprotocolid as protocolid
FROM exp.protocolaction pa
	INNER JOIN exp.protocol p ON pa.childprotocolid=p.rowid
	INNER JOIN cabig.containers c ON c.entityid = p.container
go


create view cabig.runprotocol as
SELECT pa.rowid as runactionid
FROM exp.protocolaction pa
	INNER JOIN cabig.protocoldefinition pd ON pa.childprotocolid=pd.protocolid
WHERE pa.parentprotocolid = pa.childprotocolid
go


create view cabig.protocolstep as
SELECT pa.rowid as stepactionid, pa.sequence, pap.rowid as parentactionid
FROM exp.protocolaction pa
	INNER JOIN exp.protocolaction pap ON (pap.childprotocolid = pa.parentprotocolid)
WHERE pa.parentprotocolid <> pa.childprotocolid
go


create view cabig.actionsuccessor as
SELECT pa.rowid as successoractionid, pa.sequence
FROM exp.protocolaction pa
	INNER JOIN exp.protocol p ON pa.childprotocolid=p.rowid
	INNER JOIN cabig.containers c ON c.entityid = p.container
WHERE EXISTS (select * from exp.protocolactionpredecessor pap WHERE pap.actionid = pa.rowid)
go


create view cabig.successorpredecessor as
SELECT pap.actionid as successoractionid, pap.predecessorid as predecessoractionid
FROM exp.protocolactionpredecessor pap
WHERE pap.actionid <> pap.predecessorid
go


create view cabig.data
as
SELECT d.rowid, d.lsid, d.name, d.cpastype, d.sourceapplicationid, d.datafileurl, d.runid
	,c.rowid as containerid, o.objectid
FROM exp.data d
	INNER JOIN cabig.containers c ON d.container = c.entityid
	LEFT OUTER JOIN exp.object o on d.lsid = o.objecturi
go


create view cabig.datainput
as
SELECT dataid, targetapplicationid, propertyid
FROM exp.datainput
go


create view cabig.experiment
as
SELECT e.rowid, e.lsid, e.name, e.hypothesis, e.contactid, e.experimentdescriptionurl, e.comments
	, c.rowid as containerid
	, o.objectid
FROM exp.experiment e
	INNER JOIN cabig.containers c on e.container = c.entityid
	LEFT OUTER JOIN exp.object o on e.lsid = o.objecturi
go


create view cabig.experimentrun
as
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
go


create view cabig.material
as
SELECT m.rowid, m.lsid, m.name, m.cpastype, m.sourceapplicationid, m.runid
	,c.rowid as containerid, o.objectid, ms.rowid as materialsourceid
FROM exp.material m
	INNER JOIN cabig.containers c ON m.container = c.entityid
	LEFT OUTER JOIN exp.object o on m.lsid = o.objecturi
	LEFT OUTER JOIN exp.materialsource ms on m.cpastype = ms.lsid
go


create view cabig.materialinput
as
SELECT materialid, targetapplicationid, propertyid
FROM exp.materialinput
go


create view cabig.materialsource
as
SELECT ms.rowid, ms.name, ms.lsid, ms.materiallsidprefix, ms.urlpattern, ms.description
	,c.rowid as containerid, dd.domainid
FROM exp.materialsource ms
	INNER JOIN cabig.containers c ON ms.container = c.entityid
	INNER JOIN exp.domaindescriptor dd on (dd.domainuri = ms.lsid)
go


create view cabig.protocolapplication
as
SELECT pa.rowid, pa.lsid, pa.name, pa.cpastype, pa.protocollsid, pa.activitydate, pa.comments, pa.runid, pa.actionsequence
	,p.rowid as protocolid
FROM exp.protocolapplication pa
	INNER JOIN exp.protocol p ON pa.protocollsid = p.lsid
	INNER JOIN exp.ExperimentRun er ON er.rowid =pa.runid
	INNER JOIN cabig.containers c ON er.container = c.entityid
go


create view cabig.protocolapplicationparameter
as
SELECT pap.rowid, pap.protocolapplicationid, pap.name, pap.ontologyentryuri, pap.valuetype
	, CASE WHEN pap.integervalue  IS NOT NULL THEN CAST(pap.integervalue  AS CHAR)
		WHEN pap.doublevalue IS NOT NULL THEN CAST(pap.doublevalue AS CHAR)
		WHEN pap.datetimevalue IS NOT NULL THEN CAST(pap.datetimevalue AS CHAR)
		ELSE pap.stringvalue END AS paramvalue
--	, pap.stringvalue, pap.integervalue, pap.doublevalue, pap.datetimevalue
FROM exp.protocolapplicationparameter pap
	INNER JOIN exp.protocolapplication pa ON (pap.protocolapplicationid = pa.rowid)
	INNER JOIN exp.ExperimentRun er ON er.rowid =pa.runid
	INNER JOIN cabig.containers c ON er.container = c.entityid
go


create view cabig.protocolparameter
as
SELECT pp.rowid, pp.protocolid, pp.name, pp.ontologyentryuri, pp.valuetype
	, CASE WHEN pp.integervalue  IS NOT NULL THEN CAST(pp.integervalue  AS CHAR)
		WHEN pp.doublevalue IS NOT NULL THEN CAST(pp.doublevalue AS CHAR)
		WHEN pp.datetimevalue IS NOT NULL THEN CAST(pp.datetimevalue AS CHAR)
		ELSE pp.stringvalue END AS defaultvalue
--	, pp.stringvalue, pp.integervalue, pp.doublevalue, pp.datetimevalue
FROM exp.protocolparameter pp
	INNER JOIN exp.protocol p ON pp.protocolid = p.rowid
	INNER JOIN cabig.containers c ON c.entityid = p.container
go


create view cabig.runlist
as
SELECT experimentid, experimentrunid
FROM exp.runlist
go


create view cabig.dataruninputs as
SELECT di.dataid, di.propertyid, pa.runid
FROM exp.data d
	INNER JOIN exp.datainput di ON (d.rowid = di.dataid)
	INNER JOIN exp.protocolapplication pa ON (di.targetapplicationid = pa.rowid)
	INNER JOIN exp.experimentrun er ON (pa.runid = er.rowid)
WHERE pa.cpastype ='ExperimentRun'
go


create view cabig.datarunoutputs as
SELECT di.dataid, di.propertyid, pa.runid
FROM exp.data d
	INNER JOIN exp.datainput di ON (d.rowid = di.dataid)
	INNER JOIN exp.protocolapplication pa ON (di.targetapplicationid = pa.rowid)
	INNER JOIN exp.experimentrun er ON (pa.runid = er.rowid)
WHERE pa.cpastype ='ExperimentRunOutput'
go


create view cabig.materialruninputs as
SELECT mi.materialid, mi.propertyid, pa.runid
FROM exp.material m
	INNER JOIN exp.materialinput mi ON (m.rowid = mi.materialid)
	INNER JOIN exp.protocolapplication pa ON (mi.targetapplicationid = pa.rowid)
	INNER JOIN exp.experimentrun er ON (pa.runid = er.rowid)
WHERE pa.cpastype ='ExperimentRun'
go


create view cabig.materialrunoutputs as
SELECT mi.materialid, mi.propertyid, pa.runid
FROM exp.material m
	INNER JOIN exp.materialinput mi ON (m.rowid = mi.materialid)
	INNER JOIN exp.protocolapplication pa ON (mi.targetapplicationid = pa.rowid)
	INNER JOIN exp.experimentrun er ON (pa.runid = er.rowid)
WHERE pa.cpastype ='ExperimentRunOutput'
go
