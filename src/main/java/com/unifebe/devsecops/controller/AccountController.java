package com.unifebe.devsecops.controller;

import com.unifebe.devsecops.service.PaymentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@RestController
public class AccountController {

    private final PaymentService paymentService;

    public AccountController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/discount")
    public double getDiscount(@RequestParam double price, @RequestParam int percent) {
        return paymentService.applyDiscount(price, percent);
    }

    /**
     * CORRECAO (SAST - SQL Injection):
     *
     * A versao anterior concatenava o parametro "id" vindo da requisicao HTTP
     * diretamente na string SQL executada por um Statement. Isso permitia a um
     * atacante alterar a estrutura da consulta (ex.: "1' OR '1'='1").
     *
     * Agora a consulta e fixa e o valor do usuario viaja como PARAMETRO de um
     * PreparedStatement: o driver envia comando e dados separadamente, de modo
     * que o conteudo de "id" nunca e interpretado como SQL.
     *
     * O try-with-resources tambem garante o fechamento de Connection,
     * PreparedStatement e ResultSet (vazamento de recursos e uma falha de
     * disponibilidade, que tambem e seguranca).
     */
    @GetMapping("/conta")
    public String buscarConta(@RequestParam String id) throws SQLException {
        String sql = "SELECT nome FROM contas WHERE id = ?";

        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:test");
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                StringBuilder resultado = new StringBuilder();
                while (rs.next()) {
                    resultado.append(rs.getString("nome")).append(" ");
                }
                return resultado.toString().trim();
            }
        }
    }

    @GetMapping("/health")
    public String health() {
        return "OK";
    }
}
