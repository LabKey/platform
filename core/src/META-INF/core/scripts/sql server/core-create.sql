CREATE VIEW core.Users AS
    SELECT Principals.Name AS Email, UsersData.*
    FROM core.Principals Principals
        INNER JOIN core.UsersData UsersData ON Principals.UserId = UsersData.UserId
    WHERE Type = 'u'
GO

CREATE VIEW core.Contacts As
	SELECT DISTINCT Users.FirstName + ' ' + Users.LastName AS Name, Users.Email, Users.DisplayName, Users.Phone, Users.UserId, Principals.OwnerId, Principals.Container
	FROM core.Principals Principals
	    INNER JOIN core.Members Members ON Principals.UserId = Members.GroupId
	    INNER JOIN core.Users Users ON Members.UserId = Users.UserId
GO

