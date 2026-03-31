INSERT INTO users (username, password_hash, role)
VALUES
('victor', '$2a$10$4/4F1hOfJmcC1hk6FF6ZQO19Ioalt3jZYBzmTWuLD9/OU1w7XaJYq', 'EMPLOYEE'),
('simeon', '$2a$10$4YYVPLcB4CGufpUwPqL9FOheY7ysB797qT7a/ICnGaGOjBzh.Id5y', 'EMPLOYEE'),
('caesar', '$2a$10$i1DhpvyMiaPecFKCgZo.8OiU5XGAh9lWkyu9O6huJ3w4E55wp3q5W', 'EMPLOYEE')
ON CONFLICT (username) DO NOTHING;

