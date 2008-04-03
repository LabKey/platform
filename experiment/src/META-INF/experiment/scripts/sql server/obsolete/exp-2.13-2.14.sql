exec core.fn_dropifexists 'materialsource', 'cabig','VIEW', NULL
GO

DROP VIEW exp.MaterialSourceWithProject
GO

ALTER TABLE exp.materialsource DROP COLUMN urlpattern
GO

CREATE VIEW exp.MaterialSourceWithProject AS
    SELECT ms.RowId, ms.Name, ms.LSID, ms.MaterialLSIDPrefix, ms.Description,
        ms.Created,	ms.CreatedBy, ms.Modified, ms.ModifiedBy, ms.Container , dd.Project
    FROM exp.MaterialSource ms
    LEFT OUTER JOIN exp.DomainDescriptor dd ON ms.lsid = dd.domainuri
go
