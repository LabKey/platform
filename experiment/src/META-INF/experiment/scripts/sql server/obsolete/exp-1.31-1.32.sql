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
ALTER TABLE exp.ObjectProperty DROP CONSTRAINT FK_ObjectProperty_PropertyDescriptor 
go
ALTER TABLE exp.PropertyDomain DROP CONSTRAINT FK_PropertyDomain_Property 
go
ALTER TABLE exp.PropertyDomain DROP CONSTRAINT FK_PropertyDomain_DomainDescriptor 
go
ALTER TABLE exp.PropertyDescriptor DROP CONSTRAINT UQ_PropertyDescriptor
go
ALTER TABLE exp.PropertyDescriptor DROP CONSTRAINT PK_PropertyDescriptor
go
ALTER TABLE exp.DomainDescriptor DROP CONSTRAINT UQ_DomainDescriptor
go
ALTER TABLE exp.DomainDescriptor DROP CONSTRAINT PK_DomainDescriptor
go
 
ALTER TABLE exp.PropertyDescriptor ADD Project ENTITYID NULL
go
ALTER TABLE exp.PropertyDescriptor
	ADD CONSTRAINT PK_PropertyDescriptor PRIMARY KEY NONCLUSTERED (PropertyId)
go


ALTER TABLE exp.DomainDescriptor 
	ADD Project ENTITYID NULL
go
ALTER TABLE exp.DomainDescriptor 
	ADD CONSTRAINT PK_DomainDescriptor PRIMARY KEY NONCLUSTERED (DomainId)
go

ALTER TABLE exp.ObjectProperty ADD
	CONSTRAINT FK_ObjectProperty_PropertyDescriptor FOREIGN KEY (PropertyId) REFERENCES exp.PropertyDescriptor (PropertyId)
go
ALTER TABLE exp.PropertyDomain ADD
	CONSTRAINT FK_PropertyDomain_Property FOREIGN KEY (PropertyId) REFERENCES exp.PropertyDescriptor (PropertyId)
go
ALTER TABLE exp.PropertyDomain ADD 
	CONSTRAINT FK_PropertyDomain_DomainDescriptor FOREIGN KEY (DomainId) REFERENCES exp.DomainDescriptor (DomainId)
go