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
ALTER TABLE core.ACLs ADD Container UNIQUEIDENTIFIER;
UPDATE core.ACLs SET Container = ObjectId WHERE ObjectId in (SELECT EntityId FROM core.Containers);


ALTER TABLE core.Principals ADD Type CHAR(1) NOT NULL DEFAULT 'u';
UPDATE core.Principals SET Type='u';
UPDATE core.Principals SET Type='g' WHERE IsGroup = '1';

ALTER TABLE core.Principals ADD Container ENTITYID NULL;
ALTER TABLE core.Principals DROP CONSTRAINT UQ_Principals_ProjectId_Name;
UPDATE core.Principals SET Container = ProjectId;
ALTER TABLE core.Principals ADD CONSTRAINT UQ_Principals_Container_Name UNIQUE (Container, Name);


DROP VIEW core.Contacts;
DROP VIEW core.Users;

ALTER TABLE core.Principals DROP IsGroup;

CREATE VIEW core.Users AS
    SELECT Principals.Name AS Email, UsersData.*
    FROM core.Principals Principals
        INNER JOIN core.UsersData UsersData ON Principals.UserId = UsersData.UserId
    WHERE Type = 'u';


CREATE VIEW core.Contacts As
	SELECT Users.FirstName || ' ' || Users.LastName AS Name, Users.Email, Users.Phone, Users.UserId, Principals.ProjectId, Principals.Name AS GroupName
	FROM core.Principals Principals
	    INNER JOIN core.Members Members ON Principals.UserId = Members.GroupId
	    INNER JOIN core.Users Users ON Members.UserId = Users.UserId;