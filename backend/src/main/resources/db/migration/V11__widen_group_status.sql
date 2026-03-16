-- Ampliar el tamaño de la columna status para soportar estados más largos
ALTER TABLE consent_groups
    ALTER COLUMN status TYPE VARCHAR(50);
