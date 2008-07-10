/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
ALTER TABLE comm.Announcements
    ADD COLUMN DiscussionSrcIdentifier VARCHAR(100) NULL,
    ADD COLUMN DiscussionSrcURL VARCHAR(1000) NULL;

CREATE INDEX IX_DiscussionSrcIdentifier ON comm.announcements(Container, DiscussionSrcIdentifier);

-- Better descriptions for existing email options
UPDATE comm.EmailOptions SET EmailOption = 'No email' WHERE EmailOptionId = 0;
UPDATE comm.EmailOptions SET EmailOption = 'All conversations' WHERE EmailOptionId = 1;
UPDATE comm.EmailOptions SET EmailOption = 'My conversations' WHERE EmailOptionId = 2;

-- Add new daily digest options
INSERT INTO comm.EmailOptions (EmailOptionId, EmailOption) VALUES (257, 'Daily digest of all conversations');
INSERT INTO comm.EmailOptions (EmailOptionId, EmailOption) VALUES (258, 'Daily digest of my conversations');

-- Change folder defaults from 'None' to 'My conversations' (email is sent if you're on the member list or if you've posted to a conversation)
UPDATE prop.Properties SET Value = '2' WHERE
    Set IN (SELECT Set FROM prop.PropertySets WHERE Category = 'defaultEmailSettings' AND UserId = 0) AND
    Name = 'defaultEmailOption' AND
    Value = '0';

-- Fix up mismatched containers for documents and announcements. 1.7 code inserted bad containers in some cases.
update core.documents set container =
    (select a.container from comm.announcements a where
        a.entityid = core.documents.parent and
        a.container != core.documents.container)
    where
        core.documents.parent IN
            (select a.entityid from comm.announcements a where
                a.entityid = core.documents.parent and
                a.container != core.documents.container);

-- Fix up mismatched containers for documents and wikis. 1.7 code inserted bad containers in some cases.
update core.documents set container = (select p.container from comm.pages p where p.entityid = core.documents.parent and p.container != core.documents.container) where core.documents.parent IN (select p.entityid from comm.pages p where p.entityid = core.documents.parent and p.container != core.documents.container);
