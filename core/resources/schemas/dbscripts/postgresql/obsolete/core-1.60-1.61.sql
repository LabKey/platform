/*
 * Copyright (c) 2006-2008 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

DROP VIEW core.Contacts;
DROP VIEW core.Users;

ALTER TABLE core.UsersData ADD DisplayName VARCHAR(64) NULL;

 DELETE FROM core.UsersData
 WHERE UserID NOT IN
 	(SELECT P1.UserId
  	FROM core.Principals P1
  	WHERE P1.Type = 'u');
  	
UPDATE core.UsersData
SET DisplayName =
	(SELECT Name
		FROM core.Principals P1
		WHERE P1.Type = 'u'
		AND P1.UserId = core.UsersData.UserId
	);

ALTER TABLE core.UsersData ALTER COLUMN DisplayName SET NOT NULL;
ALTER TABLE core.UsersData ADD CONSTRAINT UQ_DisplayName UNIQUE (DisplayName);

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
