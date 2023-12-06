SELECT core.executeJavaUpgradeCode('uniquifyPrincipalsName');

-- NULLS NOT DISTINCT syntax was introduced in PostgreSQL 15, so we can't use it. Next script adds the correct index.
--ALTER TABLE core.Principals DROP CONSTRAINT UQ_Principals_Container_Name_OwnerId;
--CREATE UNIQUE INDEX UQ_Principals_Container_Name_OwnerId ON core.Principals (Container, LOWER(Name), OwnerId) NULLS NOT DISTINCT;
