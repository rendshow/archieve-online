INSERT INTO archive_hall (id, code, name, sort_order, created_at, updated_at) VALUES
(1, 'WEST', '西区', 1, NOW(), NOW()),
(2, 'NANHU', '南湖', 2, NOW(), NOW()),
(3, 'NANLING', '南岭', 3, NOW(), NOW()),
(4, 'CHAOYANG', '朝阳', 4, NOW(), NOW()),
(5, 'OTHER', '其他', 5, NOW(), NOW())
ON DUPLICATE KEY UPDATE name = VALUES(name), sort_order = VALUES(sort_order), updated_at = NOW();
