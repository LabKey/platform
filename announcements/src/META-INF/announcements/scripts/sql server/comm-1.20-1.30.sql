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
CREATE TABLE comm.Renderers
     (
     RowId INT IDENTITY(1,1) NOT NULL,
     Label NVARCHAR(50) NOT NULL,
     Name NVARCHAR(30) NOT NULL,
     CONSTRAINT PK_Renderers PRIMARY KEY (RowId)
     )

INSERT INTO comm.Renderers (Label, Name) VALUES ('Radeox Engine', 'Radeox')

CREATE TABLE comm.PageVersions
	(
	RowId int IDENTITY (1, 1) NOT NULL ,
	PageEntityId ENTITYID NOT NULL ,
	CreatedBy USERID NULL ,
	Created datetime NULL ,
	Owner USERID NULL ,
	Version int NOT NULL,
	RendererId int NOT NULL,
	Title nvarchar (255),
	Body ntext,

	CONSTRAINT PK_PageVersions PRIMARY KEY (RowId),
	CONSTRAINT FK_PageVersions_Pages FOREIGN KEY (PageEntityId) REFERENCES comm.Pages(EntityId),
	CONSTRAINT FK_PageVersions_Renderer FOREIGN KEY (RendererId) REFERENCES comm.Renderers(RowId),
	CONSTRAINT UQ_PageVersions UNIQUE (PageEntityId, Version)
	)

INSERT INTO comm.PageVersions (PageEntityId, Title, Body, Created, CreatedBy, Owner, Version, RendererId)
     SELECT EntityId, Title, Body, Modified, ModifiedBy, Owner, 1, 1 FROM comm.Pages

ALTER TABLE comm.Pages DROP COLUMN Title, Body