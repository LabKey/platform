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
 
-- This view isn't required to login, check permissions, load modules, or run sql scripts

SET search_path TO core, public;

-- Used in the Contacts webpart to list contact info for everyone in a particular group + container
CREATE VIEW Contacts As
	SELECT Users.FirstName || '&nbsp;' || Users.LastName AS Name, Users.Email, Users.Phone, Users.UserId, Principals.ProjectId, Principals.Name AS GroupName
	FROM Principals INNER JOIN
	Members ON Principals.UserId = Members.GroupId INNER JOIN
        Users ON Members.UserId = Users.UserId;