DROP VIEW core.Contacts;
DROP VIEW core.Users;

ALTER TABLE core.ACLs ADD Container UNIQUEIDENTIFIER;
UPDATE core.ACLs SET Container = ObjectId WHERE ObjectId in (SELECT EntityId FROM core.Containers);

ALTER TABLE core.Principals ADD Type CHAR(1) NOT NULL DEFAULT 'u';
UPDATE core.Principals SET Type='u';
UPDATE core.Principals SET Type='g' WHERE IsGroup = '1';

ALTER TABLE core.Principals ADD Container ENTITYID NULL;
ALTER TABLE core.Principals DROP CONSTRAINT UQ_Principals_ProjectId_Name;
UPDATE core.Principals SET Container = ProjectId;
ALTER TABLE core.Principals ADD CONSTRAINT UQ_Principals_Container_Name UNIQUE (Container, Name);

ALTER TABLE core.Principals DROP IsGroup;

ALTER TABLE core.Principals ADD OwnerId ENTITYID NULL;
UPDATE core.Principals SET OwnerId = Container;

ALTER TABLE core.Principals DROP CONSTRAINT UQ_Principals_Container_Name;
ALTER TABLE core.Principals ADD CONSTRAINT UQ_Principals_Container_Name_OwnerId UNIQUE (Container, Name, OwnerId);
ALTER TABLE core.Principals DROP COLUMN ProjectId;

CREATE VIEW core.Users AS
    SELECT Principals.Name AS Email, UsersData.*
    FROM core.Principals Principals
        INNER JOIN core.UsersData UsersData ON Principals.UserId = UsersData.UserId
    WHERE Type = 'u';

CREATE VIEW core.Contacts As
    SELECT Users.FirstName || ' ' || Users.LastName AS Name, Users.Email, Users.Phone, Users.UserId, Principals.OwnerId, Principals.Container, Principals.Name AS GroupName
    FROM core.Principals Principals
        INNER JOIN core.Members Members ON Principals.UserId = Members.GroupId
        INNER JOIN core.Users Users ON Members.UserId = Users.UserId;
