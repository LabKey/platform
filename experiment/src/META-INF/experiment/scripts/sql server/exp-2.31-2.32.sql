-- Fix up sample sets that were incorrectly created in a folder other than their domain
-- https://www.labkey.org/issues/home/Developer/issues/details.view?issueId=4976

update exp.materialsource set container = (select dd.container from exp.domaindescriptor dd where exp.materialsource.lsid = dd.domainuri)
where rowid in (select ms.rowid from exp.materialsource ms, exp.domaindescriptor dd where dd.domainuri = ms.lsid and ms.container != dd.container)
GO
