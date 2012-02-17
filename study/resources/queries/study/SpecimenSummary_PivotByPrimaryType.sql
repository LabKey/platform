SELECT
Container, -- BUG!
ParticipantId,
Visit,
PrimaryType,
SUM(VialCount) AS VialCount,
SUM(LockedInRequestCount) AS LockedInRequestCount,
SUM(AtRepositoryCount) AS AtRepositoryCount,
SUM(AvailableCount) AS AvailableCount,
SUM(ExpectedAvailableCount) AS ExpectedAvailableCount

FROM SpecimenSummary

GROUP BY Container, ParticipantId, Visit, PrimaryType

PIVOT VialCount, AvailableCount, AtRepositoryCount, LockedInRequestCount, ExpectedAvailableCount
BY PrimaryType
IN (SELECT RowId, Description FROM SpecimenPrimaryType)
