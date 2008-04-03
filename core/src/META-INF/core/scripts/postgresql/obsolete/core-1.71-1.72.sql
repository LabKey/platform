SELECT core.fn_dropifexists ('Containers', 'core', 'Index', 'IX_Containers_Parent_Entity')
;
SELECT core.fn_dropifexists ('Documents', 'core', 'Index', 'IX_Documents_Container')
;
SELECT core.fn_dropifexists ('Documents', 'core', 'Index', 'IX_Documents_Parent')
;
--ALTER TABLE core.Containers DROP CONSTRAINT UQ_Containers_RowId


CREATE INDEX IX_Containers_Parent_Entity ON core.Containers(Parent, EntityId)
;

ALTER TABLE core.Containers ADD CONSTRAINT UQ_Containers_RowId UNIQUE (RowId)
;

CREATE INDEX IX_Documents_Container ON core.Documents(Container)
;

CREATE INDEX IX_Documents_Parent ON core.Documents(Parent)
;


