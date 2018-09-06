CREATE TABLE core.PrincipalRelations
(
  userid ENTITYID NOT NULL,
  otherid ENTITYID NOT NULL,
  relationship VARCHAR(100) NOT NULL,
  created DATETIME,

  CONSTRAINT PK_PrincipalRelations PRIMARY KEY (userid, otherid, relationship)
);

