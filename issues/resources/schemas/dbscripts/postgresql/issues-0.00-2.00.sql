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

/* issues-0.00-1.00.sql */

CREATE SCHEMA issues;
SET search_path TO issues, public;  -- include public to get ENTITYID, USERID

CREATE TABLE Issues
	(
	_ts TIMESTAMP DEFAULT now(),
	Container ENTITYID NOT NULL,
	IssueId SERIAL,
	EntityId ENTITYID NOT NULL,

	Title VARCHAR(255) NOT NULL,
	Status VARCHAR(8) NOT NULL,
	AssignedTo USERID NOT NULL,
	Type VARCHAR(32),

	--Product VARCHAR(32), -- implied by parent?
	Area VARCHAR(32),
	--SubArea VARCHAR(32) -- nah

	Priority INT NOT NULL DEFAULT 2,
	--Severity INT NOT NULL DEFAULT 2, -- let's just use Priority for now
	Milestone VARCHAR(32),
	BuildFound VARCHAR(32),

	ModifiedBy USERID NOT NULL,
	Modified TIMESTAMP DEFAULT now(),

	CreatedBy USERID NOT NULL,
	Created TIMESTAMP DEFAULT now(),
	Tag VARCHAR(32),

	ResolvedBy USERID,
	Resolved TIMESTAMP,
	Resolution VARCHAR(32),
	Duplicate INT,

	ClosedBy USERID,
	Closed TIMESTAMP,

	CONSTRAINT PK_Issues PRIMARY KEY (IssueId)
	);
CREATE INDEX IX_Issues_AssignedTo ON Issues (AssignedTo);
CREATE INDEX IX_Issues_Status ON Issues (Status);


CREATE TABLE Comments
	(
	-- EntityId ENTITYID DEFAULT CAST(NEXTVAL('guids') AS ENTITYID),	-- used for attachments
	CommentId SERIAL,
	IssueId INT,
	CreatedBy USERID,
	Created TIMESTAMP DEFAULT now(),
	Comment TEXT,
	
	CONSTRAINT PK_Comments PRIMARY KEY (IssueId, CommentId),
	CONSTRAINT FK_Comments_Issues FOREIGN KEY (IssueId) REFERENCES Issues(IssueId)
	);


CREATE TABLE IssueKeywords
	(
	Container ENTITYID NOT NULL,
	Type INT NOT NULL,	-- area or milestone (or whatever)
	Keyword VARCHAR(255) NOT NULL,

	CONSTRAINT PK_IssueKeywords PRIMARY KEY (Container, Type, Keyword)
	);

/* issues-1.10-1.30.sql */

INSERT INTO issues.IssueKeywords (Container, Type, Keyword)
(SELECT DISTINCT Container, 3, Milestone
FROM issues.Issues
WHERE Milestone IS NOT NULL);

/* issues-1.50-1.60.sql */

ALTER TABLE issues.Issues
    ADD COLUMN Int1 INT NULL,
    ADD COLUMN Int2 INT NULL,
    ADD COLUMN String1 VARCHAR(200) NULL,
    ADD COLUMN String2 VARCHAR(200) NULL;

/* issues-1.60-1.70.sql */

ALTER TABLE issues.Issues
    ADD COLUMN NotifyList TEXT;

CREATE TABLE issues.EmailPrefs
	(
	Container ENTITYID,
	UserId USERID,
	EmailOption INT4 NOT NULL,
	CONSTRAINT PK_EmailPrefs PRIMARY KEY (Container, UserId),
	CONSTRAINT FK_EmailPrefs_Containers FOREIGN KEY (Container) REFERENCES core.Containers (EntityId),
	CONSTRAINT FK_EmailPrefs_Principals FOREIGN KEY (UserId) REFERENCES core.Principals (UserId)
	);

/* issues-1.70-2.00.sql */

ALTER TABLE issues.IssueKeywords
    ADD COLUMN "default" BOOLEAN NOT NULL DEFAULT '0';

/*
    Old, hard-coded defaults were Priority="3", ResolutionType="Fixed"
    Set these in the keywords table so existing issues lists continue to work as before
*/
UPDATE issues.IssueKeywords SET "default"='1' WHERE Type=6 AND Keyword='3';
UPDATE issues.IssueKeywords SET "default"='1' WHERE Type=7 AND Keyword='Fixed';