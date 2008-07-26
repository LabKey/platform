/*
 * Copyright (c) 2008 LabKey Corporation
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
CREATE VIEW core.Users AS
    SELECT Principals.Name AS Email, UsersData.*, Principals.Active
    FROM core.Principals Principals
        INNER JOIN core.UsersData UsersData ON Principals.UserId = UsersData.UserId
    WHERE Type = 'u'
GO

CREATE VIEW core.Contacts AS
	SELECT DISTINCT Users.FirstName + ' ' + Users.LastName AS Name, Users.Email, Users.DisplayName, Users.Phone, Users.UserId, Principals.OwnerId, Principals.Container
	FROM core.Principals Principals
	    INNER JOIN core.Members Members ON Principals.UserId = Members.GroupId
	    INNER JOIN core.Users Users ON Members.UserId = Users.UserId
GO

CREATE VIEW core.ActiveUsers AS
    SELECT Principals.Name AS Email, UsersData.*
    FROM core.Principals Principals
        INNER JOIN core.UsersData UsersData ON Principals.UserId = UsersData.UserId
    WHERE Type = 'u' and Principals.Active=1
GO
