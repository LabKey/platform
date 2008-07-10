CREATE VIEW core.Users AS
    SELECT Principals.Name AS Email, UsersData.*
    FROM core.Principals Principals
        INNER JOIN core.UsersData UsersData ON Principals.UserId = UsersData.UserId
    WHERE Type = 'u';

CREATE VIEW core.Contacts AS
    SELECT DISTINCT Users.FirstName || ' ' || Users.LastName AS Name, Users.Email, Users.DisplayName, Users.Phone, Users.UserId, Principals.OwnerId, Principals.Container
    FROM core.Principals Principals
        INNER JOIN core.Members Members ON Principals.UserId = Members.GroupId
        INNER JOIN core.Users Users ON Members.UserId = Users.UserId;

CREATE OR REPLACE RULE Users_Update AS
	ON UPDATE TO core.Users DO INSTEAD
		UPDATE core.UsersData SET
			ModifiedBy = NEW.ModifiedBy,
			Modified = NEW.Modified,
			FirstName = NEW.FirstName,
			LastName = NEW.LastName,
			Phone = NEW.Phone,
			Mobile = NEW.Mobile,
			Pager = NEW.Pager,
			IM = NEW.IM,
			Description = NEW.Description,
			LastLogin = NEW.LastLogin,
			DisplayName = NEW.DisplayName
		WHERE UserId = NEW.UserId;


