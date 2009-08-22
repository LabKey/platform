/*
 * Copyright (c) 2009 LabKey Corporation
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

/* core-9.10-9.11.sql */

CREATE TABLE core.QcValues
	(
	Container ENTITYID NOT NULL,
    QcValue VARCHAR(64) NOT NULL,
    Label VARCHAR(255),

    CONSTRAINT PK_QcValues_Container_QcValue PRIMARY KEY (Container, QcValue)
	)
GO

/* core-9.11-9.12.sql */

/* SQL Server */

exec core.fn_dropifexists 'RoleAssignments', 'core', 'TABLE', null;

create table core.RoleAssignments
(
    ResourceId entityid not null,
    UserId userid not null,
    Role varchar(500) not null,
    Container entityid,
    ResourceClass varchar(1000),

    constraint PK_RoleAssignments primary key clustered (ResourceId,UserId,Role),
    constraint FK_RA_UP foreign key(UserId) references core.Principals(UserId)
);

/* core-9.12-9.13.sql */

/* SQL Server */

CREATE TABLE core.MvIndicators
	(
	Container ENTITYID NOT NULL,
    MvIndicator VARCHAR(64) NOT NULL,
    Label VARCHAR(255),

    CONSTRAINT PK_MvIndicators_Container_MvIndicator PRIMARY KEY (Container, MvIndicator)
	)

GO

INSERT INTO core.MvIndicators (Container, MvIndicator, Label)
(
  SELECT Container, QcValue, Label
  FROM
  core.QcValues
)

GO

DROP TABLE core.QcValues

GO

/* core-9.13-9.14.sql */

/* SQL Server */

-- scrub ACLs where container is null
-- some old databases might have saved ACLs like these
-- but they are no longer retrievable anyway
delete from core.ACLs where Container is null;

-- create Policies and RoleAssignments tables
exec core.fn_dropifexists 'RoleAssignments', 'core', 'TABLE', null;

create table core.Policies
(
    ResourceId entityid not null,
    ResourceClass varchar(1000),
    Container entityid not null,
    Modified datetime not null,

    constraint PK_Policies primary key(ResourceId)
);

create table core.RoleAssignments
(
    ResourceId entityid not null,
    UserId userid not null,
    Role varchar(500) not null,

    constraint PK_RoleAssignments primary key(ResourceId,UserId,Role),
    constraint FK_RA_P foreign key(ResourceId) references core.Policies(ResourceId),
    constraint FK_RA_UP foreign key(UserId) references core.Principals(UserId)
);

exec core.executeJavaUpgradeCode 'migrateAcls'

drop table core.ACLs;