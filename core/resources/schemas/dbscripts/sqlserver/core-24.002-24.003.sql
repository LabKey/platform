ALTER TABLE core.datastates ADD Color NVARCHAR(7) NULL;
GO

UPDATE core.datastates SET Color='#F0F8ED' WHERE StateType='Available';
UPDATE core.datastates SET Color='#FDE6E6' WHERE StateType='Locked';
UPDATE core.datastates SET Color='#FCF8E3' WHERE StateType='Consumed';
