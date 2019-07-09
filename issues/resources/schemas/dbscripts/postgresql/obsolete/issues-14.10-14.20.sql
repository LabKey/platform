/*
 * Copyright (c) 2016-2019 LabKey Corporation
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
/* issues-14.10-14.11.sql */

DELETE FROM prop.properties WHERE name = 'experimentalFeature.issuesactivity';

/* issues-14.11-14.12.sql */

CREATE TABLE issues.RelatedIssues
(
    IssueId INT,
    RelatedIssueId INT,

    CONSTRAINT PK_RelatedIssues PRIMARY KEY (IssueId, RelatedIssueId),
    CONSTRAINT FK_RelatedIssues_Issues_IssueId FOREIGN KEY (IssueId) REFERENCES issues.Issues(IssueId),
    CONSTRAINT FK_RelatedIssues_Issues_RelatedIssueId FOREIGN KEY (RelatedIssueId) REFERENCES issues.Issues(IssueId)
);
CREATE INDEX IX_RelatedIssues_IssueId ON issues.RelatedIssues (IssueId);
CREATE INDEX IX_RelatedIssues_RelatedIssueId ON issues.RelatedIssues (RelatedIssueId);