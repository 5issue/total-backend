UPDATE oms_order_items SET storage_type = 'ROOM_TEMPERATURE' WHERE storage_type = 'ROOM';
UPDATE oms_order_items SET storage_type = 'REFRIGERATED' WHERE storage_type = 'CHILLED';
UPDATE shipments SET storage_type = 'ROOM_TEMPERATURE' WHERE storage_type = 'ROOM';
UPDATE shipments SET storage_type = 'REFRIGERATED' WHERE storage_type = 'CHILLED';
