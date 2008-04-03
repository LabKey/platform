/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
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

ALTER TABLE study.SampleRequest
    ADD DestinationSiteId INT NULL,
    ADD Hidden Boolean NOT NULL DEFAULT '0';

ALTER TABLE study.SampleRequest
    ADD CONSTRAINT FK_SampleRequest_Site FOREIGN KEY (Container,DestinationSiteId) REFERENCES study.Site(Container,SiteId);

ALTER TABLE study.SampleRequestActor
    ADD PerSite Boolean NOT NULL DEFAULT '0',
    ADD GroupName VARCHAR(64) NULL;

CREATE TABLE study.SampleRequestEvent
(
    RowId SERIAL,
    EntityId ENTITYID NOT NULL,
    CreatedBy USERID,
    Created TIMESTAMP,
    Container ENTITYID NOT NULL,
    RequestId INT NOT NULL,
    Comments TEXT,
    EntryType VARCHAR(64),
    CONSTRAINT FK_SampleRequestEvent_SampleRequest FOREIGN KEY (RequestId) REFERENCES study.SampleRequest(RowId)
);