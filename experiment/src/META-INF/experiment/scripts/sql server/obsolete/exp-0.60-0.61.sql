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
CREATE TABLE exp.PropertyDescriptor (
	RowId int IDENTITY (1, 1) NOT NULL,
	PropertyURI nvarchar(200) NOT NULL,
	OntologyURI nvarchar (200)  NULL,
	TypeURI nvarchar(200) NULL,
	Name nvarchar(50) NULL ,
	Description ntext NULL
	)
GO

ALTER TABLE exp.PropertyDescriptor
   ADD CONSTRAINT PK_PropertyDescriptor PRIMARY KEY (RowId),
   CONSTRAINT UQ_PropertyDescriptor UNIQUE (PropertyURI)
GO

CREATE TABLE exp.MaterialSource (
	RowId int IDENTITY (1, 1) NOT NULL,
	Name nvarchar(50) NOT NULL ,
    LSID LSIDtype NOT NULL,
	MaterialLSIDPrefix nvarchar(200) NULL,
	URLPattern nvarchar(200) NULL,
	Description ntext NULL ,
	Created datetime NULL ,
	CreatedBy int NULL ,
	Modified datetime NULL ,
	ModifiedBy int NULL ,
	Container uniqueidentifier NULL
    )
GO

ALTER TABLE exp.MaterialSource
   ADD CONSTRAINT PK_MaterialSource PRIMARY KEY (RowId),
   CONSTRAINT UQ_MaterialSource_Name UNIQUE (Name),
   CONSTRAINT UQ_MaterialSource_LSID UNIQUE (LSID)
GO

