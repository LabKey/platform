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
CREATE TABLE exp.ActiveMaterialSource (
	Container ENTITYID NOT NULL,
	MaterialSourceLSID LSIDtype NOT NULL,
	CONSTRAINT PK_ActiveMaterialSource PRIMARY KEY (Container),
	CONSTRAINT FK_ActiveMaterialSource_Container FOREIGN KEY (Container)
			REFERENCES core.Containers(EntityId),
	CONSTRAINT FK_ActiveMaterialSource_MaterialSourceLSID FOREIGN KEY (MaterialSourceLSID)
			REFERENCES exp.MaterialSource(LSID)
);

ALTER TABLE exp.DataInput
    ADD COLUMN PropertyId INT NULL;

ALTER TABLE exp.DataInput
    ADD CONSTRAINT FK_DataInput_PropertyDescriptor FOREIGN KEY(PropertyId)
        REFERENCES exp.PropertyDescriptor(PropertyId);

ALTER TABLE exp.MaterialInput
    ADD COLUMN PropertyId INT NULL;

ALTER TABLE exp.MaterialInput
    ADD CONSTRAINT FK_MaterialInput_PropertyDescriptor FOREIGN KEY(PropertyId)
        REFERENCES exp.PropertyDescriptor(PropertyId);

DROP INDEX exp.IDX_ObjectProperty_StringValue
;

ALTER TABLE exp.ObjectProperty ALTER COLUMN StringValue TYPE VARCHAR(4000)
;

UPDATE exp.ObjectProperty SET StringValue=CAST(TextValue AS VARCHAR(4000)), TypeTag='s'
WHERE StringValue IS NULL AND (TextValue IS NOT NULL OR TypeTag='t')
;

ALTER TABLE exp.ObjectProperty DROP COLUMN TextValue
;

UPDATE exp.Material SET CpasType='Material' WHERE CpasType IS NULL;

