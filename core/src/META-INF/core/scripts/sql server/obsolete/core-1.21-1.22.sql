ALTER TABLE core.Principals ADD OwnerId ENTITYID NULL;
go
UPDATE core.Principals SET OwnerId = Container;
go

ALTER TABLE core.Principals DROP CONSTRAINT UQ_Principals_Container_Name;
go
ALTER TABLE core.Principals ADD CONSTRAINT UQ_Principals_Container_Name_OwnerId UNIQUE (Container, Name, OwnerId);
go

DROP VIEW core.Contacts;
go

CREATE VIEW core.Contacts As
	SELECT Users.FirstName + ' ' + Users.LastName AS Name, Users.Email, Users.Phone, Users.UserId, Principals.OwnerId, Principals.Name AS GroupName
	FROM core.Principals Principals
	    INNER JOIN core.Members Members ON Principals.UserId = Members.GroupId
	    INNER JOIN core.Users Users ON Members.UserId = Users.UserId
go

ALTER TABLE core.Principals DROP COLUMN ProjectId;
go
