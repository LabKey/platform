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
CREATE TABLE study.Plate
    (
	RowId SERIAL,
	LSID VARCHAR(200) NOT NULL,
    Container ENTITYID NOT NULL,
    Name VARCHAR(200) NULL,
    CreatedBy USERID NOT NULL,
    Created TIMESTAMP NOT NULL,
    Template BOOLEAN NOT NULL,
    DataFileId ENTITYID,
    Rows INT NOT NULL,
    Columns INT NOT NULL,
    CONSTRAINT PK_Plate PRIMARY KEY (RowId)
    );


CREATE TABLE study.WellGroup
    (
	RowId SERIAL,
    PlateId INT NOT NULL,
	LSID VARCHAR(200) NOT NULL,
    Container ENTITYID NOT NULL,
    Name VARCHAR(200) NULL,
    Template BOOLEAN NOT NULL,
    TypeName VARCHAR(50) NOT NULL,
    CONSTRAINT PK_WellGroup PRIMARY KEY (RowId),
    CONSTRAINT FK_WellGroup_Plate FOREIGN KEY (PlateId) REFERENCES study.Plate(RowId)
    );

    
CREATE TABLE study.Well
    (
	RowId SERIAL,
	LSID VARCHAR(200) NOT NULL,
    Container ENTITYID NOT NULL,
	Value FLOAT NULL,
    Dilution FLOAT NULL,
    PlateId INT NOT NULL,
    Row INT NOT NULL,
    Col INT NOT NULL,
    CONSTRAINT PK_Well PRIMARY KEY (RowId),
    CONSTRAINT FK_Well_Plate FOREIGN KEY (PlateId) REFERENCES study.Plate(RowId)
    );