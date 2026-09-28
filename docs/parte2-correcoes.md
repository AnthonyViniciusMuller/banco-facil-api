# Parte 2 — Hands-on: pipeline, falhas observadas e correções

Repositório: https://github.com/AnthonyViniciusMuller/banco-facil-api
Imagem publicada: `ghcr.io/anthonyviniciusmuller/banco-facil-api`

A pipeline tem cinco gates em sequência, cada um rodando só se o anterior passar. Corrigi um problema por push, de propósito, para observar cada gate falhar por sua vez.

| Execução | Gate que falhou | O que foi reportado |
|---|---|---|
| 1 | `secret-scan` | Gitleaks: `aws-access-token` (linha 14) e `stripe-access-token` (linha 18) em `AppConfig.java`. `leaks found: 2` |
| 2 | `unit-tests` | `AssertionFailedError: expected: <180.0> but was: <198.0>` em `PaymentServiceTest` |
| 3 | `sast` | Semgrep: 2 findings em `AccountController`, regras `formatted-sql-string` e `tainted-sql-string` |
| 4 | `sca` | Trivy: 3 vulnerabilidades em `log4j-core 2.14.1` — CVE-2021-44228 e CVE-2021-45046 (CRITICAL) e CVE-2021-45105 (HIGH) |
| 5 | `dockerfile-lint` | Hadolint: `DL3007` (tag `latest`, linha 1) e `DL3002` (último `USER` é root, linha 9) |
| 6 | nenhum | Pipeline verde, imagem publicada no GHCR |

Vale um comentário sobre a execução 3. As duas regras do Semgrep enxergam a mesma linha por ângulos diferentes: `formatted-sql-string` é um padrão sintático ("há concatenação numa string SQL"), enquanto `tainted-sql-string` é taint analysis, que rastreou o dado do `@RequestParam` até o `executeQuery` e só reporta porque não há sanitização no caminho. E na execução 4 chamam atenção as três CVEs encadeadas: as primeiras correções do Log4Shell (2.15.0, depois 2.16.0) foram elas próprias furadas, o que explica por que a recomendação final foi 2.17.1 e não simplesmente "a próxima versão".

## Correções aplicadas

**1. Segredos.** As constantes de `AppConfig.java` foram trocadas por leitura de variáveis de ambiente com `System.getenv`, com falha rápida se a variável não existir. Em produção elas viriam de um cofre (Vault, AWS Secrets Manager), injetadas no deploy — e não do `GITHUB_TOKEN`, que é segredo de CI e nem existe mais quando a aplicação está rodando.

Só isso não fez o gate passar. Como o Gitleaks varre todo o histórico, as duas chaves continuam detectáveis no commit em que foram introduzidas, e continuariam mesmo se o arquivo fosse apagado. Como são valores de exemplo públicos, documentados pelos próprios fornecedores, que nunca foram credenciais válidas, reconheci os dois achados explicitamente por fingerprint (`commit:arquivo:regra:linha`) em um `.gitleaksignore`, em vez de reescrever o histórico. Reescrever só se justifica quando o segredo é real — e mesmo aí a primeira medida é rotacionar, não reescrever. Vale registrar que isso é diferente de desligar o gate: é uma aceitação de risco nomeada, versionada e revisável em code review.

**2. Teste unitário.** A fórmula de `applyDiscount` passou a dividir por 100.

**3. SAST.** O `Statement` com concatenação virou `PreparedStatement` com parâmetro `?`, dentro de `try-with-resources`. Com isso o driver envia comando e dados separadamente e o conteúdo do parâmetro nunca é interpretado como SQL. O achado da chave de API sumiu junto com a correção 1.

**4. SCA.** Removi o `log4j-core` em vez de atualizá-lo: o `PaymentService` usa apenas a API do log4j, que já vem com o `spring-boot-starter-logging` pela ponte `log4j-to-slf4j`. Dependência que não existe não precisa de patch futuro. Também subi o `spring-boot-starter-parent` de 3.2.5 para 3.5.16 e sobrescrevi `tomcat.version` para 10.1.60.

**5. Dependência não utilizada.** O `gson` estava declarado duas vezes no `pom.xml` e não era importado em nenhum arquivo de `src/` (`grep -r "import com.google.gson" src/` não retorna nada). Removi as duas declarações. Cada dependência inútil é uma aposta desnecessária em CVEs futuras.

**6. Dockerfile.** Virou multi-stage, com compilação em `maven:3.9-eclipse-temurin-17` e runtime em `eclipse-temurin:17-jre-alpine`, mais um usuário de sistema `app` sem privilégios criado com `addgroup -S`/`adduser -S` e o `.jar` copiado com `--chown`. O Hadolint ainda exibe `DL3066` (usuário não numérico), mas é nível *info* e não bloqueia. Como a compilação passou para dentro do primeiro estágio, o passo de empacotamento do job `build-and-push` ficou redundante e foi removido do workflow.

## Evidências

*(inserir aqui os prints da aba Actions: a primeira execução com o `secret-scan` falhando, a falha de cada gate seguinte, a execução final totalmente verde com o `build-and-push`, o Summary com o veredito LIBERADO e a aba Packages com a imagem publicada)*
