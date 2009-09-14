/*
 * Copyright (c) 2005-2009 LabKey Corporation
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


EXEC sp_addapprole 'issues', 'password'
GO

CREATE TABLE issues.Issues
	(
	_ts TIMESTAMP,
	Container ENTITYID NOT NULL,
	IssueId INT IDENTITY(1,1) NOT NULL,
	EntityId ENTITYID DEFAULT NEWID(),	-- used for attachments

	Title NVARCHAR(255) NOT NULL,
	Status NVARCHAR(8) NOT NULL,
	AssignedTo USERID NOT NULL,
	Type NVARCHAR(32),

	--Product NVARCHAR(32), -- implied by parent?
	Area NVARCHAR(32),
	--SubArea NVARCHAR(32) -- nah

	Priority INT NOT NULL DEFAULT 2,
	--Severity INT NOT NULL DEFAULT 2, -- let's just use Priority for now
	Milestone NVARCHAR(32),
	BuildFound NVARCHAR(32),

	ModifiedBy USERID NOT NULL,
	Modified DATETIME DEFAULT GETDATE(),

	CreatedBy USERID NOT NULL,
	Created DATETIME DEFAULT GETDATE(),
	Tag NVARCHAR(32),

	ResolvedBy USERID,
	Resolved DATETIME,
	Resolution NVARCHAR(32),
	Duplicate INT,

	ClosedBy USERID,
	Closed DATETIME,

	CONSTRAINT PK_Issues PRIMARY KEY (IssueId)
	)
CREATE INDEX IX_Issues_AssignedTo ON issues.Issues (AssignedTo)
CREATE INDEX IX_Issues_Status ON issues.Issues (Status)
GO


CREATE TABLE issues.Comments
	(
	--EntityId ENTITYID DEFAULT NEWID(),
	CommentId INT IDENTITY(1,1),
	IssueId INT,
	CreatedBy USERID,
	Created DATETIME DEFAULT GETDATE(),
	Comment NTEXT,
	
	CONSTRAINT PK_Comments PRIMARY KEY (IssueId, CommentId),
	CONSTRAINT FK_Comments_Issues FOREIGN KEY (IssueId) REFERENCES issues.Issues(IssueId)
	)
GO


CREATE TABLE issues.IssueKeywords
	(
	Container ENTITYID NOT NULL,
	Type INT NOT NULL,	-- area or milestone (or whatever)
	Keyword VARCHAR(255) NOT NULL,

	CONSTRAINT PK_IssueKeywords PRIMARY KEY (Container, Type, Keyword)
	)
GO