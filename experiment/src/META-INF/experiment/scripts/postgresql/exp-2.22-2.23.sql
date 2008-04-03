-- Used to attach discussions to lists
ALTER TABLE exp.IndexInteger
    ADD COLUMN EntityId ENTITYID;

ALTER TABLE exp.IndexVarchar
    ADD COLUMN EntityId ENTITYID;
