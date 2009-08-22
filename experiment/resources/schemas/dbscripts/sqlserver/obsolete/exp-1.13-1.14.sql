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
if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[FK_BioSource_Material]') and OBJECTPROPERTY(id, N'IsForeignKey') = 1)
ALTER TABLE [exp].[BioSource] DROP CONSTRAINT FK_BioSource_Material
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[FK_Fraction_Material]') and OBJECTPROPERTY(id, N'IsForeignKey') = 1)
ALTER TABLE [exp].[Fraction] DROP CONSTRAINT FK_Fraction_Material
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[BioSource]') and OBJECTPROPERTY(id, N'IsUserTable') = 1)
drop table [exp].[BioSource]
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[Fraction]') and OBJECTPROPERTY(id, N'IsUserTable') = 1)
drop table [exp].[Fraction]
GO

ALTER TABLE exp.PropertyDescriptor ADD Container ENTITYID NULL
GO

BEGIN TRAN
-- First delete orphaned PD's
DELETE FROM exp.PropertyDescriptor WHERE PropertyID IN
	(SELECT PD.PropertyID 
	FROM exp.PropertyDescriptor PD LEFT JOIN exp.ObjectProperty OP 	ON (PD.PropertyId = OP.PropertyID)
	WHERE OP.PropertyID IS NULL)

-- Move PDs to the container where they are referenced.  
-- if multiple, pick one via MAX
UPDATE exp.PropertyDescriptor 
	SET Container = 
			(SELECT  MAX(CAST (O.Container AS VARCHAR(100)))
				FROM exp.PropertyDescriptor PD 
					INNER JOIN exp.ObjectProperty OP ON (PD.PropertyId = OP.PropertyID) 
					INNER JOIN exp.Object O ON (O.ObjectId = OP.ObjectID)	
				WHERE PDU.PropertyID = PD.PropertyID
				GROUP BY PD.PropertyID
				)
	FROM exp.PropertyDescriptor PDU
	WHERE PDU.Container IS NULL AND
	      PDU.PropertyID IN
			(SELECT PD.PropertyID
			FROM exp.PropertyDescriptor PD 
				INNER JOIN exp.ObjectProperty OP ON (PD.PropertyId = OP.PropertyID) 
				INNER JOIN exp.Object O ON (O.ObjectId = OP.ObjectID)	
			GROUP BY PD.PropertyID
			)

COMMIT TRAN
go

ALTER TABLE exp.PropertyDescriptor ALTER COLUMN Container ENTITYID NOT NULL
GO

ALTER TABLE exp.Object DROP CONSTRAINT UQ_Object 
GO

ALTER TABLE exp.Object ADD CONSTRAINT UQ_Object UNIQUE (ObjectURI)
GO

DROP INDEX exp.Object.IDX_Object_OwnerObjectId
go

CREATE CLUSTERED INDEX IDX_Object_ContainerOwnerObjectId ON exp.Object (Container, OwnerObjectId, ObjectId);
GO

if exists (select * from dbo.sysobjects where id = object_id(N'[exp].[setProperty]') and OBJECTPROPERTY(id, N'IsProcedure') = 1)
DROP PROCEDURE exp.setProperty
GO

