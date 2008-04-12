/* cabig-2.30-2.31.sql */

select core.fn_dropifexists('experimentrun', 'cabig','VIEW', NULL);

create or replace view cabig.experimentrun
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
	INNER JOIN exp.protocolaction pa ON (pa.parentprotocolid = p.rowid) AND (pa.parentprotocolid = pa.childprotocolid);