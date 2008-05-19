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

ALTER TABLE exp.PropertyDescriptor ADD LookupContainer ENTITYID;
ALTER TABLE exp.PropertyDescriptor ADD LookupSchema VARCHAR(50);
ALTER TABLE exp.PropertyDescriptor ADD LookupQuery VARCHAR(50);

CREATE TABLE exp.list (
    RowId INT IDENTITY (1, 1) NOT NULL,
    EntityId uniqueidentifier NOT NULL,
    Created datetime NULL,
    CreatedBy int NULL,
    Modified datetime NULL,
    ModifiedBy int NULL,

    Container uniqueidentifier NOT NULL,
    Name NVARCHAR(64) NOT NULL,
    DomainId INT NOT NULL,
    KeyName NVARCHAR(64) NOT NULL,
    KeyType VARCHAR(64) NOT NULL,
    Description NTEXT,
    CONSTRAINT PK_List PRIMARY KEY(RowId),
    CONSTRAINT UQ_LIST UNIQUE(Container, Name),
    CONSTRAINT FK_List_DomainId FOREIGN KEY(DomainId) REFERENCES exp.DomainDescriptor(DomainId)
);


CREATE INDEX IDX_List_DomainId ON exp.List(DomainId);

CREATE TABLE exp.IndexInteger (
    ListId INT NOT NULL,
    "Key" INT NOT NULL,
    ObjectId INT NULL,
    CONSTRAINT PK_IndexInteger PRIMARY KEY(ListId, "Key"),
    CONSTRAINT FK_IndexInteger_List FOREIGN KEY(ListId) REFERENCES exp.List(RowId),
    CONSTRAINT FK_IndexInteger_Object FOREIGN KEY(ObjectId) REFERENCES exp.Object(ObjectId)
);
CREATE INDEX IDX_IndexInteger_ObjectId ON exp.IndexInteger(ObjectId);

CREATE TABLE exp.IndexVarchar (
    ListId INT NOT NULL,
    "Key" VARCHAR(300) NOT NULL,
    ObjectId INT NULL,
    CONSTRAINT PK_IndexVarchar PRIMARY KEY(ListId, "Key"),
    CONSTRAINT FK_IndexVarchar_List FOREIGN KEY(ListId) REFERENCES exp.List(RowId),
    CONSTRAINT FK_IndexVarchar_Object FOREIGN KEY(ObjectId) REFERENCES exp.Object(ObjectId)
);
CREATE INDEX IDX_IndexVarchar_ObjectId ON exp.IndexVarchar(ObjectId);

exec core.fn_dropifexists 'ObjectProperty', 'exp', 'INDEX', 'IDX_ObjectProperty_FloatValue'
go
exec core.fn_dropifexists 'ObjectProperty', 'exp', 'INDEX', 'IX_ObjectProperty_PropertyObject'
go

CREATE  INDEX IDX_ObjectProperty_PropertyId ON exp.ObjectProperty(PropertyId)
go