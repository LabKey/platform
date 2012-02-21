SELECT
  Container,
  ParticipantId,
  SequenceNum,
  ParticipantVisit,
  PrimaryType,
  SUM(VialCount) AS VialCount,
  SUM(LockedInRequestCount) AS LockedInRequestCount,
  SUM(AtRepositoryCount) AS AtRepositoryCount,
  SUM(AvailableCount) AS AvailableCount,
  SUM(ExpectedAvailableCount) AS ExpectedAvailableCount

FROM SpecimenSummary

GROUP BY Container, ParticipantId, SequenceNum, ParticipantVisit, PrimaryType

PIVOT VialCount, AvailableCount, AtRepositoryCount, LockedInRequestCount, ExpectedAvailableCount
  BY PrimaryType
  IN (SELECT RowId, Description FROM SpecimenPrimaryType)
