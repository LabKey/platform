exec core.fn_dropifexists 'materialsource', 'cabig','VIEW', NULL
GO

create view cabig.materialsource
as
SELECT ms.rowid, ms.name, ms.lsid, ms.materiallsidprefix, ms.description
	,c.rowid as containerid, dd.domainid
FROM exp.materialsource ms
	INNER JOIN cabig.containers c ON ms.container = c.entityid
	INNER JOIN exp.domaindescriptor dd on (dd.domainuri = ms.lsid)
GO
