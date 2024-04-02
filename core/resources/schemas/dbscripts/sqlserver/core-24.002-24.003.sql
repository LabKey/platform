ALTER TABLE core.datastates ADD Color NVARCHAR(7) NULL;
GO

UPDATE core.datastates SET Color='#D6E9C6' WHERE StateType='Available';
UPDATE core.datastates SET Color='#F9B3B3' WHERE StateType='Locked';
UPDATE core.datastates SET Color='#FAEBCC' WHERE StateType='Consumed';
