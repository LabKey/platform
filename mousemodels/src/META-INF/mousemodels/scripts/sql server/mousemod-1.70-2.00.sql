/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
-- list of objects that might have notes
create table #_temp_mousemap (mm_entityid varchar(36), mm_discussionsrcidentifier varchar(200), mm_discussionsrcurl varchar(200));
go

insert into #_temp_mousemap (mm_entityid, mm_discussionsrcidentifier, mm_discussionsrcurl)
	select
	  entityid,
	  entityid,
 	  '~/MouseModel/' + cast(container as varchar(36)) + '/details.view?modelId=' + cast(modelid as varchar(10)) + '&entityId=' + cast(entityId as varchar(36))
	from mousemod.mousemodel;

insert into #_temp_mousemap (mm_entityid, mm_discussionsrcidentifier, mm_discussionsrcurl)
	select
	  entityid,
	  entityid,
 	  '~/MouseModel-Mouse/' + cast(container as varchar(36)) + '/details.view?modelId=' + cast(modelid as varchar(10)) + '&entityId=' + cast(entityId as varchar(36))
	from mousemod.mouse;

insert into #_temp_mousemap (mm_entityid, mm_discussionsrcidentifier, mm_discussionsrcurl)
	select
	  mousemod.sample.entityid,
	  lsid,
 	  '~/MouseModel-Sample/' + cast(mousemod.sample.container as varchar(36)) + '/details.view?modelId=' + cast(modelid as varchar(10)) + '&LSID=' + lsid
	from mousemod.sample JOIN mousemod.mouse ON organismid=mousemod.mouse.entityid;
go

-- find first note for each set of notes
create table #_temp_discussionmap (firstentityid varchar(36), mouseparent varchar(36));
go

insert into #_temp_discussionmap (firstentityid, mouseparent)
	select entityid, parent
	from comm.announcements
	where rowid in (
		select min(rowid)
		from comm.announcements join #_temp_mousemap on comm.announcements.parent = #_temp_mousemap.mm_entityid
		group by parent);
go

-- make the first note the disucssion, and make the other notes the replies

update comm.announcements set
  title = CASE WHEN title IS NULL THEN 'Note' ELSE title END,
  discussionsrcidentifier = (select mm_discussionsrcidentifier from #_temp_mousemap where parent=mm_entityid),
  discussionsrcurl = (select mm_discussionsrcurl from #_temp_mousemap M where parent=mm_entityid),
  parent = (select NULLIF(firstentityid,entityid) from #_temp_discussionmap where parent=mouseparent)
where parent in (select mm_entityid from #_temp_mousemap);
go

drop table #_temp_mousemap;
drop table #_temp_discussionmap;
go

DROP VIEW mousemod.MouseView
go

CREATE VIEW mousemod.MouseView AS
    Select mouseId, mousemod.Mouse.modelId modelId, mousemod.Mouse.Container Container, EntityId, MouseNo, mousemod.Cage.CageName As CageName, mousemod.Mouse.Sex Sex,
    mousemod.Mouse.Control Control, BirthDate, StartDate, DeathDate,MouseComments,NecropsyAppearance,NecropsyGrossFindings,toeNo,
    int1, int2, int3, date1, date2, string1, string2, string3,
    CASE WHEN DeathDate IS NULL THEN
        CASE WHEN StartDate IS NULL THEN
            DATEDIFF(week, BirthDate, GETDATE())
         ELSE
            DATEDIFF(week, StartDate, GETDATE())
         END
    ELSE
        CASE WHEN StartDate IS NULL THEN
            DATEDIFF(week, BirthDate, DeathDate)
         ELSE
            DATEDIFF(week, StartDate, DeathDate)
         END
    END
         Weeks From mousemod.Mouse JOIN mousemod.Cage on mousemod.Mouse.CageId = mousemod.Cage.CageId
GO

