ALTER TABLE exp.list ADD COLUMN TitleColumn VARCHAR(200) NULL;

select core.fn_dropifexists('materialsource', 'cabig','VIEW', NULL);

DROP VIEW exp.MaterialSourceWithProject;

ALTER TABLE exp.materialsource DROP COLUMN urlpattern;

CREATE VIEW exp.MaterialSourceWithProject AS
    SELECT ms.RowId, ms.Name, ms.LSID, ms.MaterialLSIDPrefix, ms.Description,
        ms.Created,	ms.CreatedBy, ms.Modified, ms.ModifiedBy, ms.Container , dd.Project
    FROM exp.MaterialSource ms
    LEFT OUTER JOIN exp.DomainDescriptor dd ON ms.lsid = dd.domainuri;
