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
CREATE VIEW exp.ExperimentRunMaterialOutputs AS
	SELECT exp.Material.LSID AS MaterialLSID, exp.ExperimentRun.LSID AS RunLSID, exp.ExperimentRun.Container AS Container
	FROM exp.Material
	JOIN exp.ExperimentRun ON exp.Material.RunId=exp.ExperimentRun.RowId
	WHERE SourceApplicationId IS NOT NULL
go
CREATE TABLE exp.ActiveMaterialSource (
	Container ENTITYID NOT NULL,
	MaterialSourceLSID LSIDtype NOT NULL,
	CONSTRAINT PK_ActiveMaterialSource PRIMARY KEY (Container),
	CONSTRAINT FK_ActiveMaterialSource_Container FOREIGN KEY (Container)
			REFERENCES core.Containers(EntityId),
	CONSTRAINT FK_ActiveMaterialSource_MaterialSourceLSID FOREIGN KEY (MaterialSourceLSID)
			REFERENCES exp.MaterialSource(LSID)
)
go

CREATE VIEW exp.MaterialSourceWithProject AS
    SELECT ms.RowId, ms.Name, ms.LSID, ms.MaterialLSIDPrefix, ms.URLPattern, ms.Description,
        ms.Created,	ms.CreatedBy, ms.Modified, ms.ModifiedBy, ms.Container , dd.Project
    FROM exp.MaterialSource ms
    LEFT OUTER JOIN exp.DomainDescriptor dd ON ms.lsid = dd.domainuri
go

ALTER TABLE exp.DataInput
    ADD PropertyId INT NULL
go

ALTER TABLE exp.DataInput
    ADD CONSTRAINT FK_DataInput_PropertyDescriptor FOREIGN KEY(PropertyId)
        REFERENCES exp.PropertyDescriptor(PropertyId)
go

ALTER TABLE exp.MaterialInput
    ADD PropertyId INT NULL
go

ALTER TABLE exp.MaterialInput
    ADD CONSTRAINT FK_MaterialInput_PropertyDescriptor FOREIGN KEY(PropertyId)
        REFERENCES exp.PropertyDescriptor(PropertyId)
go

DROP INDEX exp.ObjectProperty.IDX_ObjectProperty_StringValue
go

DROP VIEW exp.ObjectPropertiesView
go

ALTER TABLE exp.ObjectProperty ALTER COLUMN StringValue NVARCHAR(4000)NULL
go

UPDATE exp.ObjectProperty SET StringValue=CAST(TextValue AS NVARCHAR(4000)), TypeTag='s'
WHERE StringValue IS NULL AND (TextValue IS NOT NULL OR TypeTag='t')
go

ALTER TABLE exp.ObjectProperty DROP COLUMN TextValue
go

CREATE VIEW exp.ObjectPropertiesView AS
	SELECT
		O.ObjectId, O.Container, O.ObjectURI, O.OwnerObjectId,
		PD.name, PD.PropertyURI, PD.RangeURI,
		P.TypeTag, P.FloatValue, P.StringValue, P.DatetimeValue
	FROM exp.ObjectProperty P JOIN exp.Object O ON P.ObjectId = O.ObjectId 
	    JOIN exp.PropertyDescriptor PD ON P.PropertyId = PD.PropertyId
go
        
UPDATE exp.Material SET CpasType='Material' WHERE CpasType IS NULL
go