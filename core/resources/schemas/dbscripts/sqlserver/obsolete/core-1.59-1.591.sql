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
DROP VIEW core.Contacts
DROP VIEW core.Users
go

ALTER TABLE core.UsersData ALTER COLUMN Phone NVARCHAR(64) NULL
go
ALTER TABLE core.UsersData ALTER COLUMN Mobile NVARCHAR(64) NULL
go
ALTER TABLE core.UsersData ALTER COLUMN Pager NVARCHAR(64) NULL
go

CREATE VIEW core.Users AS
    SELECT Principals.Name AS Email, UsersData.*
    FROM core.Principals Principals
        INNER JOIN core.UsersData UsersData ON Principals.UserId = UsersData.UserId
    WHERE Type = 'u'
go

CREATE VIEW core.Contacts As
	SELECT Users.FirstName + ' ' + Users.LastName AS Name, Users.Email, Users.Phone, Users.UserId, Principals.OwnerId, Principals.Container, Principals.Name AS GroupName
	FROM core.Principals Principals
	    INNER JOIN core.Members Members ON Principals.UserId = Members.GroupId
	    INNER JOIN core.Users Users ON Members.UserId = Users.UserId
go
