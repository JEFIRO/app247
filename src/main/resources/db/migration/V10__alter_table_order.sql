ALTER TABLE orders 
ADD COLUMN user_id INTEGER;

ALTER TABLE orders
ADD CONSTRAINT fk_orders_user
FOREIGN KEY (user_id)
REFERENCES users(user_id);