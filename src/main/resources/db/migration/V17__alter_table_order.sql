ALTER TABLE orders

    ADD CONSTRAINT fk_orders_pagamento
        FOREIGN KEY (id_pagamento)
        REFERENCES pagamento(id_pagamento);