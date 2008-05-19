/*
 * Copyright (c) 2005-2008 Fred Hutchinson Cancer Research Center
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
/*
Need to manually drop some constraints since they were automatically named by old scripts.
*/

CREATE TABLE Location (
	_ts TIMESTAMP,
	CreatedBy USERID,
	Created DATETIME,
	ModifiedBy USERID,
	Modified DATETIME,

    SourceId ENTITYID,
    SampleId nvarchar(100) NOT NULL,
    Freezer nvarchar(50) NULL,
    Rack nvarchar(50) NULL,
    Shelf nvarchar(50) NULL,
    Drawer nvarchar(50) NULL,
    Box nvarchar(50) NOT NULL,
    Cell int NOT NULL,
    CONSTRAINT Location_PK PRIMARY KEY (SourceId, SampleId),
    CONSTRAINT Location_SampleSource FOREIGN KEY (SourceId) REFERENCES SampleSource(SourceId),
    CONSTRAINT Location_One_Per_Slot UNIQUE (Freezer,Rack,Shelf,Drawer,Box,Cell)
)
GO


ALTER TABLE Mouse ADD litterId INT
Go

UPDATE Mouse SET litterId = (SELECT litterId FROM Cage WHERE Mouse.CageId=Cage.CageId)
GO

ALTER TABLE MOUSE ADD
  CONSTRAINT Mouse_LitterId FOREIGN KEY (litterId) REFERENCES Litter(litterId)
GO

ALTER TABLE Cage ALTER COLUMN litterId INT NULL
GO

ALTER TABLE Cage DROP CONSTRAINT FK__Cage__litterId__08EA5793
GO

/*
ALTER TABLE Cage DROP COLUMN litterId
*/

UPDATE SampleSource Set SourceName='BDI Mouse Models' WHERE SourceName='EDI Mouse Models'
go

DROP VIEW MouseSample
Go

-- Create Views
CREATE VIEW MouseSample AS
SELECT
    Sample.SampleId SampleId, SampleType.SampleType SampleType, Sample.SourceId SourceId, Sample.EntityId sampleEntityId,
    Sample.CollectionDate CollectionDate, Sample.Description Description, Sample.Fixed Fixed, Sample.Frozen Frozen, Sample.FrozenUsed FrozenUsed,
    Mouse.MouseNo MouseNo, Mouse.Control Control, Mouse.BirthDate BirthDate, Mouse.DeathDate DeathDate, Mouse.Sex Sex, Mouse.ModelId ModelId,
    Mouse.EntityId mouseEntityId, Mouse.Container Container, Location.Freezer Freezer, Location.Box Box, Location.Rack Rack, Location.Cell Cell, DATEDIFF(week, Mouse.BirthDate, Sample.CollectionDate) Weeks
FROM Sample
	JOIN SampleType ON Sample.SampleTypeId = SampleType.SampleTypeId
	JOIN Mouse ON Sample.OrganismId = Mouse.EntityId
    LEFT OUTER JOIN Location ON Sample.SampleId = Location.SampleId AND Location.SourceId = Location.SourceId
GO



