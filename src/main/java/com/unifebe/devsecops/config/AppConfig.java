package com.unifebe.devsecops.config;

/**
 * CORRECAO (Gestao de Segredos / Secret Sprawl):
 *
 * As credenciais que antes estavam gravadas como constantes no codigo-fonte
 * foram removidas. Agora sao lidas de variaveis de ambiente em tempo de
 * execucao, de forma que nenhum valor sensivel viva no repositorio Git.
 *
 * Em producao, estas variaveis NAO sao definidas a mao: elas devem ser
 * injetadas por um cofre de segredos (HashiCorp Vault, AWS Secrets Manager,
 * Azure Key Vault etc.) no momento do deploy — tipicamente via o orquestrador
 * (Kubernetes Secret montado a partir do cofre, task definition do ECS, etc.).
 *
 * Importante: o GITHUB_TOKEN do GitHub Actions NAO serve para isto. Ele e um
 * segredo de CI/build (existe apenas durante a execucao do workflow, para
 * autenticar no GHCR); os valores abaixo sao segredos de runtime, consumidos
 * pela aplicacao ja implantada.
 *
 * Nota tecnica: @Value do Spring nao injeta valores em constantes
 * "static final". Se quisermos usar a infraestrutura de configuracao do
 * Spring, esta classe precisa virar um bean (@Component/@ConfigurationProperties)
 * com campos de instancia. Mantemos System.getenv aqui para deixar explicito
 * que a origem do valor e o ambiente, e nao o codigo.
 */
public final class AppConfig {

    private AppConfig() {
        // classe utilitaria: nao deve ser instanciada
    }

    public static String dbPassword() {
        return requireEnv("DB_PASSWORD");
    }

    public static String awsAccessKeyId() {
        return requireEnv("AWS_ACCESS_KEY_ID");
    }

    public static String awsSecretAccessKey() {
        return requireEnv("AWS_SECRET_ACCESS_KEY");
    }

    public static String paymentGatewayApiKey() {
        return requireEnv("PAYMENT_GATEWAY_API_KEY");
    }

    /**
     * Falha rapido (fail fast) se a variavel nao estiver definida, em vez de
     * deixar a aplicacao subir com uma credencial nula e quebrar so no momento
     * da primeira chamada ao provedor externo.
     */
    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Variavel de ambiente obrigatoria nao definida: " + name
                            + ". Ela deve ser injetada pelo cofre de segredos no deploy.");
        }
        return value;
    }
}
