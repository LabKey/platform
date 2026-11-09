/*
 * Copyright (c) 2025-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
DELETE FROM comm.UserList WHERE MessageId NOT IN (SELECT RowId FROM comm.Announcements);
ALTER TABLE comm.UserList ADD CONSTRAINT FK_UserList_Announcements FOREIGN KEY (MessageId) REFERENCES comm.Announcements (RowId);
