/*
 * Copyright (c) 2008-2014 LabKey Corporation
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
    SELECT y.RowId, y.EntityId, y.Container, y.Body, y.RendererType, PropsId AS LatestId, props.Title, props.AssignedTo,
        props.Status, props.Expires, props.CreatedBy AS ResponseCreatedBy, props.Created AS ResponseCreated,
        y.DiscussionSrcIdentifier, y.DiscussionSrcURL, y.CreatedBy, y.Created,
        y.modified, y.lastIndexed,
        (SELECT COUNT(*) FROM comm.Announcements WHERE Parent = y.EntityId) AS ResponseCount FROM
    (
        SELECT *, CASE WHEN LastResponseId IS NULL THEN x.RowId ELSE LastResponseId END AS PropsId FROM
        (
            SELECT *, (SELECT MAX(RowId) FROM comm.Announcements response WHERE response.Parent = message.EntityId) AS LastResponseId
            FROM comm.Announcements message
            WHERE Parent IS NULL
        ) x
    ) y LEFT OUTER JOIN comm.Announcements props ON props.RowId = PropsId;

GO

-- View that joins each wiki with its current version (one row per wiki)
CREATE VIEW comm.CurrentWikiVersions AS
    SELECT pv.RowId, p.Container, p.Name, pv.Title, pv.Version, pv.Body, p.CreatedBy, p.Created, p.ModifiedBy, p.Modified
        FROM comm.Pages p INNER JOIN comm.PageVersions pv ON p.PageVersionId = pv.RowId;

GO

-- View that joins every wiki version with its parent (one row per wiki version). Report the wiki's Created & CreatedBy,
-- but map the version's Created & CreatedBy to Modified & ModifiedBy, because that seems like the most useful mapping.
CREATE VIEW comm.AllWikiVersions AS
    SELECT pv.RowId, p.Container, p.Name, pv.Title, pv.Version, pv.Body, p.CreatedBy, p.Created, pv.CreatedBy AS ModifiedBy, pv.Created AS Modified
        FROM comm.PageVersions pv INNER JOIN comm.Pages p ON pv.PageEntityId = p.EntityId;

GO
