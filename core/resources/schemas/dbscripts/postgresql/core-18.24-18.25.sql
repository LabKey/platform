CREATE TABLE core.PrincipalRelations
(
  userid USERID NOT NULL,
  otherid USERID NOT NULL,
  relationship VARCHAR(100) NULL,
  created TIMESTAMP,

  CONSTRAINT PK_PrincipalRelations PRIMARY KEY (userid, otherid, relationship)
);