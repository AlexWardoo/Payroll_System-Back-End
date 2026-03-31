UPDATE users
SET password_hash = '123',
    role = 'ADMIN'
WHERE username = 'adam';

UPDATE users
SET password_hash = '123',
    role = 'EMPLOYEE'
WHERE username IN ('victor', 'simeon', 'caesar');

INSERT INTO users (username, password_hash, role)
SELECT 'adam', '123', 'ADMIN'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'adam');

INSERT INTO users (username, password_hash, role)
SELECT 'victor', '123', 'EMPLOYEE'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'victor');

INSERT INTO users (username, password_hash, role)
SELECT 'simeon', '123', 'EMPLOYEE'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'simeon');

INSERT INTO users (username, password_hash, role)
SELECT 'caesar', '123', 'EMPLOYEE'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'caesar');
