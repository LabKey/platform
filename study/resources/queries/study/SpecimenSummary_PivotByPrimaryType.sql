SELECT
Container, -- BUG!
ParticipantId,
Visit,
PivotType,
SUM(VialCount) AS VialCount,
SUM(LockedInRequestCount) AS LockedInRequestCount,
SUM(AtRepositoryCount) AS AtRepositoryCount,
SUM(AvailableCount) AS AvailableCount,
SUM(ExpectedAvailableCount) AS ExpectedAvailableCount
FROM 

(SELECT 
  Container, ParticipantId, Visit, 
  PrimaryType.Description AS PivotType, 
  VialCount, LockedInRequestCount, AtRepositoryCount, AvailableCount,
  ExpectedAvailableCount
  FROM SpecimenSummary) AS X

GROUP BY Container, ParticipantId, Visit, PivotType
PIVOT VialCount, AvailableCount, AtRepositoryCount, LockedInRequestCount, ExpectedAvailableCount BY PivotType
