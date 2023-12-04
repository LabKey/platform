SELECT core.executeJavaUpgradeCode('uniquifyPrincipalsName');

ALTER TABLE core.Principals DROP CONSTRAINT UQ_Principals_Container_Name_OwnerId;
CREATE UNIQUE INDEX UQ_Principals_Container_Name_OwnerId ON core.Principals (Container, LOWER(Name), OwnerId) NULLS NOT DISTINCT;
