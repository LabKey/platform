-- Adding a PK on RowId seemed like a good idea, but it broke existing lookups to core.Containers and other assumptions.
-- We could add a PK on EntityId, but that column is currently nullable. For now, we'll just live without a PK.
ALTER TABLE core.Containers DROP CONSTRAINT PK_Containers;
ALTER TABLE core.Containers ADD CONSTRAINT UQ_Containers_RowId UNIQUE CLUSTERED (RowId);