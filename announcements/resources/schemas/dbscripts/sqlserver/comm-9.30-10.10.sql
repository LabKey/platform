/*
 * Copyright (c) 2010-2011 LabKey Corporation
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

/* comm-9.30-9.31.sql */

ALTER TABLE comm.Pages ADD LastIndexed DATETIME NULL
GO

/* comm-9.31-9.32.sql */

ALTER TABLE comm.Announcements ADD LastIndexed DATETIME NULL
GO

/* comm-9.32-9.33.sql */

insert into comm.EmailOptions (EmailOptionID, EmailOption)
values (3, 'Broadcast only');