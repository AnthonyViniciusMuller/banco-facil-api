package com.unifebe.devsecops.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    private static final Logger logger = LogManager.getLogger(PaymentService.class);

    /**
     * Aplica um desconto percentual sobre um preco.
     *
     * CORRECAO: a formula dividia por 1000 em vez de 100, fazendo o desconto
     * aplicado ser 10x menor que o esperado (200,00 com 10% resultava em
     * 198,00 em vez de 180,00). O teste unitario PaymentServiceTest e quem
     * flagrou o erro antes do deploy.
     */
    public double applyDiscount(double price, int discountPercent) {
        logger.info("Calculando desconto de {}% sobre {}", discountPercent, price);
        return price - (price * discountPercent / 100);
    }
}
