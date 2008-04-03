exec core.fn_dropifexists 'materialsource', 'cabig','VIEW', NULL
GO

DROP VIEW exp.MaterialSourceWithProject
GO

ALTER TABLE exp.materialsource ADD
    IdCol1 NVARCHAR(200) NULL,
    IdCol2 NVARCHAR(200) NULL,
    IdCol3 NVARCHAR(200) NULL
GO

CREATE VIEW exp.MaterialSourceWithProject AS
    SELECT ms.RowId, ms.Name, ms.LSID, ms.MaterialLSIDPrefix, ms.Description,
        ms.Created,	ms.CreatedBy, ms.Modified, ms.ModifiedBy, ms.Container , dd.Project, ms.IdCol1, ms.IdCol2, ms.IdCol3
    FROM exp.MaterialSource ms
    LEFT OUTER JOIN exp.DomainDescriptor dd ON ms.lsid = dd.domainuri
go
