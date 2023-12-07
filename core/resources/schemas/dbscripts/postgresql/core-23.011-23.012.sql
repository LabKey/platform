-- Previous script attempted to create a unique index specifying NULLS NOT DISTINCT, but that syntax was just introduced
-- in PostgreSQL 15. We want to fix servers that failed to create the new index; we also want to migrate servers that
-- created it successfully, for consistency. Once PostgreSQL 15 is a minimum we could consider switching back to NULLS
-- NOT DISTINCT.

ALTER TABLE core.Principals DROP CONSTRAINT IF EXISTS UQ_Principals_Container_Name_OwnerId;
DROP INDEX IF EXISTS core.UQ_Principals_Container_Name_OwnerId;
-- COALESCE() works around PostgreSQL behavior that NULL values are not unique
CREATE UNIQUE INDEX UQ_Principals_Container_Name_OwnerId ON core.Principals
    (COALESCE(Container, '00000000-0000-0000-0000-000000000000'), LOWER(Name), COALESCE(OwnerId, '00000000-0000-0000-0000-000000000000'));
