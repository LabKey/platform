/*
 * Copyright (c) 2011 LabKey Corporation
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

/* query-11.20-11.21.sql */

ALTER TABLE query.QuerySnapshotDef
  ADD QueryTableContainer ENTITYID;

GO

UPDATE query.QuerySnapshotDef SET QueryTableContainer = Container WHERE QueryTableName IS NOT NULL;

/* query-11.21-11.22.sql */

ALTER TABLE query.QuerySnapshotDef ADD ParticipantGroups TEXT;

/* query-11.22-11.23.sql */

-- Issue 13594: Remove 'inherit' bit from custom view flags for list and dataset views known to only exist in a single container
UPDATE query.customview SET flags = flags & ~1
WHERE customviewid IN (
	SELECT cv.customviewid
	FROM query.customview cv
	WHERE
	((cv.flags & 1) != 0) AND
	(
	  cv.[schema] = 'lists' OR
	  (cv.[schema] = 'study' AND LOWER(cv.queryname) IN (SELECT LOWER(ds.name) FROM study.dataset ds))
	)
);