/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

-- Discussions can be private, constrained to a certain subset of users (like a Cc: line)
CREATE TABLE comm.UserList
(
	MessageId INT NOT NULL,
	UserId USERID NOT NULL,

	CONSTRAINT PK_UserList PRIMARY KEY (MessageId, UserId)
)
GO

-- Improve performance of user list lookups for permission checking
CREATE INDEX IX_UserList_UserId ON comm.UserList(UserId)
GO

-- Add EmailList (place to store history of addresses that were notified), Status and AssignedTo
ALTER TABLE comm.Announcements
    ADD EmailList VARCHAR(1000) NULL, Status VARCHAR(50) NULL, AssignedTo USERID NULL
GO

-- Allow RenderType to be NULL; updates to properties will result in NULL body and NULL render type
ALTER TABLE comm.Announcements
    ALTER COLUMN RendererType NVARCHAR(50) NULL
GO

-- Drop unused Owner column
ALTER TABLE comm.Announcements
    DROP COLUMN Owner
GO

UPDATE comm.Announcements
    SET Title = (SELECT Title FROM comm.Announcements p WHERE p.EntityId = comm.Announcements.Parent),
    Expires = (SELECT Expires FROM comm.Announcements p WHERE p.EntityId = comm.Announcements.Parent)
    WHERE Parent IS NOT NULL
GO
