/*
 * Copyright (c) 2009-2019 LabKey Corporation
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

-- For each thread, select RowId, EntityId, Container, Body, RendererType, CreatedBy, and Created from the original post and add Title, Status,
--   Expires, CreatedBy, and Created from either the most recent response or the original post, if no responses.
CREATE VIEW comm.Threads AS
    SELECT threads.*, props.Title, props.AssignedTo, props.Status, props.Expires, props.CreatedBy AS ResponseCreatedBy, props.Created AS ResponseCreated FROM
    (
        SELECT parents.RowId, parents.EntityId, parents.Container, parents.Body, parents.RendererType, parents.DiscussionSrcIdentifier, parents.DiscussionSrcURL,
            parents.CreatedBy, parents.Created, parents.Modified, parents.LastIndexed, COALESCE(LastResponseId, RowId) AS LatestId, COALESCE(ResponseCount, 0) AS ResponseCount
        FROM comm.Announcements parents LEFT OUTER JOIN
        (
            SELECT Container, Parent, MAX(RowId) AS LastResponseId, COUNT(*) AS ResponseCount FROM comm.Announcements
            WHERE Parent IS NOT NULL AND Approved > '1970-01-01'
            GROUP BY Container, Parent
        ) responses ON responses.Container = parents.Container AND responses.Parent = parents.EntityId
        WHERE parents.parent IS NULL AND Approved > '1970-01-01'
    ) threads
    LEFT OUTER JOIN comm.Announcements props ON props.RowId = LatestId;


-- View that adds calculated Path and Depth columns
CREATE VIEW comm.PagePaths AS
  WITH RECURSIVE pages_cte AS (
    -- anchor
    SELECT
      p.RowId, p.EntityId,
      p.CreatedBy, p.Created,
      p.ModifiedBy, p.Modified,
      p.Owner, p.Container,
      p.Name, p.Parent,
      p.DisplayOrder, p.PageVersionId,
      p.ShowAttachments, p.LastIndexed,
      p.ShouldIndex,
      CAST(p.Name AS VARCHAR(2000)) AS Path,
      CAST(p.Name AS VARCHAR(2000)) AS PathParts,
      0 AS Depth
    FROM comm.Pages p
      WHERE p.Parent = -1

    UNION ALL

    -- recursive part
    SELECT
      q.RowId, q.EntityId,
      q.CreatedBy, q.Created,
      q.ModifiedBy, q.Modified,
      q.Owner, q.Container,
      q.Name, q.Parent,
      q.DisplayOrder, q.PageVersionId,
      q.ShowAttachments, q.LastIndexed,
      q.ShouldIndex,
      CAST(previous.Path || '/' || q.Name AS VARCHAR(2000)) AS Path,
      CAST(previous.PathParts || '{@~^' || q.Name AS VARCHAR(2000)) AS PathParts,
      previous.Depth + 1 AS Depth
    FROM comm.Pages q
    JOIN pages_cte AS previous ON q.Parent = previous.RowId
  )
  SELECT * from pages_cte;

-- View that joins each wiki with its current version (one row per wiki)
CREATE VIEW comm.CurrentWikiVersions AS
    SELECT pv.RowId, p.Container, p.Name, p.Path, p.PathParts, p.Depth, pv.Title, pv.Version, pv.Body, pv.RendererType, p.CreatedBy, p.Created, p.ModifiedBy, p.Modified
        FROM comm.PagePaths p INNER JOIN comm.PageVersions pv ON p.PageVersionId = pv.RowId;

-- View that joins every wiki version with its parent (one row per wiki version). Report the wiki's Created & CreatedBy,
-- but map the version's Created & CreatedBy to Modified & ModifiedBy, because that seems like the most useful mapping.
CREATE VIEW comm.AllWikiVersions AS
    SELECT pv.RowId, p.Container, p.Name, p.Path, p.PathParts, p.Depth, pv.Title, pv.Version, pv.Body, pv.RendererType, p.CreatedBy, p.Created, pv.CreatedBy AS ModifiedBy, pv.Created AS Modified
        FROM comm.PageVersions pv INNER JOIN comm.PagePaths p ON pv.PageEntityId = p.EntityId;
