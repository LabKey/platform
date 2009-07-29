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
	Container ENTITYID,
    QcValue VARCHAR(64),
    Label VARCHAR(255) NULL,

    CONSTRAINT PK_QcValues_Container_QcValue PRIMARY KEY (Container, QcValue)
	);

/* core-9.11-9.12.sql */

/* PostgreSQL */

select core.fn_dropifexists('RoleAssignments', 'core', 'TABLE', null);

create table core.RoleAssignments
(
    ResourceId entityid not null,
    UserId userid not null,
    Role varchar(500) not null,
    Container entityid,
    ResourceClass varchar(1000),

    constraint PK_RoleAssignments primary key(ResourceId,UserId,Role),
    constraint FK_RA_UP foreign key(UserId) references core.Principals(UserId)
);

/* core-9.12-9.13.sql */

CREATE TABLE core.MvIndicators
	(
	Container ENTITYID,
    MvIndicator VARCHAR(64),
    Label VARCHAR(255) NULL,

    CONSTRAINT PK_MvIndicators_Container_MvIndicator PRIMARY KEY (Container, MvIndicator)
	);

INSERT INTO core.MvIndicators (Container, MvIndicator, Label)
(
  SELECT Container, QcValue, Label
  FROM
  core.QcValues
);

DROP TABLE core.QcValues;

/* core-9.13-9.14.sql */

/* PostgreSQL */

-- scrub ACLs where container is null
-- some old databases might have saved ACLs like these
-- but they are no longer retrievable anyway
delete from core.ACLs where Container is null;

-- create Policies and RoleAssignments tables
select core.fn_dropifexists('RoleAssignments', 'core', 'TABLE', null);

-- create Policies and RoleAssignments tables
select core.fn_dropifexists('Policies', 'core', 'TABLE', null);

create table core.Policies
(
    ResourceId entityid not null,
    ResourceClass varchar(1000),
    Container entityid not null,
    Modified timestamp not null,

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

select core.executeJavaUpgradeCode('migrateAcls');

drop table core.ACLs;