EXEC core.fn_dropifexists 'PrincipalRelations','core','TABLE';

CREATE TABLE core.PrincipalRelations
(
  userid USERID NOT NULL,
  otherid USERID NOT NULL,
  relationship NVARCHAR(100) NOT NULL,
  created DATETIME,

  CONSTRAINT PK_PrincipalRelations PRIMARY KEY (userid, otherid, relationship)
);