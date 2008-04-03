-- list of objects that might have notes
create temp table _temp_mousemap (mm_entityid varchar(36), mm_discussionsrcidentifier varchar(200), mm_discussionsrcurl varchar(200));

insert into _temp_mousemap (mm_entityid, mm_discussionsrcidentifier, mm_discussionsrcurl)
	select
	  entityid,
	  entityid,
 	  '~/MouseModel/' || container || '/details.view?modelId=' || modelid || '&entityId=' || entityId
	from mousemod.mousemodel;

insert into _temp_mousemap (mm_entityid, mm_discussionsrcidentifier, mm_discussionsrcurl)
	select
	  entityid,
	  entityid,
 	  '~/MouseModel-Mouse/' || container || '/details.view?modelId=' || modelid || '&entityId=' || entityId
	from mousemod.mouse;

insert into _temp_mousemap (mm_entityid, mm_discussionsrcidentifier, mm_discussionsrcurl)
	select
	  mousemod.sample.entityid,
	  lsid,
 	  '~/MouseModel-Sample/' || mousemod.sample.container || '/details.view?modelId=' || modelid || '&LSID=' || lsid
	from mousemod.sample JOIN mousemod.mouse ON organismid=mousemod.mouse.entityid;

-- find first note for each set of notes
create temp table _temp_discussionmap (firstentityid varchar(36), mouseparent varchar(36));

insert into _temp_discussionmap (firstentityid, mouseparent)
	select entityid, parent
	from comm.announcements
	where rowid in (
		select min(rowid)
		from comm.announcements join _temp_mousemap on comm.announcements.parent = _temp_mousemap.mm_entityid
		group by parent);

-- make the first note the disucssion, and make the other notes the replies

update comm.announcements set
  title = CASE WHEN title IS NULL THEN 'Note' ELSE title END,
  discussionsrcidentifier = (select mm_discussionsrcidentifier from _temp_mousemap where parent=mm_entityid),
  discussionsrcurl = (select mm_discussionsrcurl from _temp_mousemap M where parent=mm_entityid),
  parent = (select NULLIF(firstentityid,entityid) from _temp_discussionmap where parent=mouseparent)
where parent in (select mm_entityid from _temp_mousemap);

drop table _temp_mousemap;
drop table _temp_discussionmap;

DROP VIEW mousemod.MouseView;

CREATE VIEW mousemod.MouseView AS
    Select mouseId, mousemod.Mouse.modelId AS modelId, mousemod.Mouse.Container AS Container, EntityId, MouseNo, mousemod.Cage.CageName AS CageName, mousemod.Mouse.Sex AS Sex,
    mousemod.Mouse.Control AS Control, BirthDate, StartDate, DeathDate,MouseComments,NecropsyAppearance,NecropsyGrossFindings,toeNo,
    int1, int2, int3, date1, date2, string1, string2, string3,
    CASE WHEN DeathDate IS NULL THEN
        CASE WHEN StartDate IS NULL THEN
            (CURRENT_DATE - Date(BirthDate)) / 7
         ELSE
            (CURRENT_DATE - Date(StartDate)) / 7
         END
    ELSE
        CASE WHEN StartDate IS NULL THEN
            (Date(DeathDate) - Date(BirthDate)) / 7
         ELSE
            (Date(DeathDate) - Date(StartDate)) / 7
         END
    END
         AS Weeks FROM mousemod.Mouse JOIN mousemod.Cage on mousemod.Mouse.CageId = mousemod.Cage.CageId
;
