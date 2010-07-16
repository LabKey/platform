/*
 * Copyright (c) 2008-2010 LabKey Corporation
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
/* core-2.30-2.31.sql */

ALTER TABLE core.Report ADD Flags INT NOT NULL DEFAULT 0
GO

/* core-2.31-2.32.sql */

-- clean up user history prefs for deleted users (issue#5465)
CREATE VIEW core._Users AS
    SELECT Principals.Name AS Email, UsersData.*
    FROM core.Principals Principals
        INNER JOIN core.UsersData UsersData ON Principals.UserId = UsersData.UserId
    WHERE Type = 'u'
GO

DELETE FROM core.UserHistory WHERE UserID NOT IN
    (
    SELECT U1.UserId
    FROM core._Users U1
    )
GO

DROP VIEW core._Users
GO
