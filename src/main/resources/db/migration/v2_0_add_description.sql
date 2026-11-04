ALTER TABLE salles ADD COLUMN IF NOT EXISTS description VARCHAR(1000);
-- L'exécuteur vérifie aussi les index uniques équivalents avant cette instruction.
CREATE UNIQUE INDEX IF NOT EXISTS ux_utilisateurs_email ON utilisateurs(email);
