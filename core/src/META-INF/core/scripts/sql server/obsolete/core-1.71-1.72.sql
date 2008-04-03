exec core.fn_dropifexists 'Containers', 'core', 'Index', 'IX_Containers_Parent_Entity'
go
exec core.fn_dropifexists 'Documents', 'core', 'Index', 'IX_Documents_Container'
go
exec core.fn_dropifexists 'Documents', 'core', 'Index', 'IX_Documents_Parent'
go
--ALTER TABLE core.Containers DROP CONSTRAINT UQ_Containers_RowId
--go



CREATE INDEX IX_Containers_Parent_Entity ON core.Containers(Parent, EntityId)
go

ALTER TABLE core.Containers ADD CONSTRAINT UQ_Containers_RowId UNIQUE CLUSTERED (RowId)
go

CREATE INDEX IX_Documents_Container ON core.Documents(Container)
go

CREATE INDEX IX_Documents_Parent ON core.Documents(Parent)
go


