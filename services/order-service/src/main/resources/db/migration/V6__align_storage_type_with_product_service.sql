UPDATE cart_items SET storage_type = 'ROOM_TEMPERATURE' WHERE storage_type = 'ROOM';
UPDATE cart_items SET storage_type = 'REFRIGERATED' WHERE storage_type = 'CHILLED';
UPDATE order_items SET storage_type = 'ROOM_TEMPERATURE' WHERE storage_type = 'ROOM';
UPDATE order_items SET storage_type = 'REFRIGERATED' WHERE storage_type = 'CHILLED';
