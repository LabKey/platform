select core.fn_dropifexists('materialsource', 'cabig','VIEW', NULL);

DROP VIEW exp.MaterialSourceWithProject;

ALTER TABLE exp.materialsource ADD COLUMN IdCol1 VARCHAR(200) NULL;
ALTER TABLE exp.materialsource ADD COLUMN IdCol2 VARCHAR(200) NULL;
ALTER TABLE exp.materialsource ADD COLUMN IdCol3 VARCHAR(200) NULL;

CREATE VIEW exp.MaterialSourceWithProject AS
    SELECT ms.RowId, ms.Name, ms.LSID, ms.MaterialLSIDPrefix, ms.Description,
        ms.Created,	ms.CreatedBy, ms.Modified, ms.ModifiedBy, ms.Container , dd.Project, ms.IdCol1, ms.IdCol2, ms.IdCol3
    FROM exp.MaterialSource ms
    LEFT OUTER JOIN exp.DomainDescriptor dd ON ms.lsid = dd.domainuri;
