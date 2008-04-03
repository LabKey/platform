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

