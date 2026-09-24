# Parte 2 — Registro das correções e roteiro de evidências

## 1. O que foi corrigido

| # | Gate | Problema encontrado | Correção aplicada | Arquivo |
|---|---|---|---|---|
| 1 | `secret-scan` | Gitleaks detectou `aws-access-token` (linha 14) e `stripe-access-token` (linha 18) em `AppConfig.java`, presentes no commit inicial | Constantes removidas; valores passam a ser lidos com `System.getenv(...)`, com falha rápida se a variável não existir. Os dois achados históricos foram aceitos explicitamente por fingerprint | `src/main/java/com/unifebe/devsecops/config/AppConfig.java`, `.gitleaksignore` |
| 2 | `unit-tests` | `PaymentServiceTest` falhava: `expected: <180.0> but was: <198.0>` | Fórmula de `applyDiscount` corrigida: divisão por `100` em vez de `1000` | `src/main/java/com/unifebe/devsecops/service/PaymentService.java` |
| 3 | `sast` | Semgrep reportava SQL Injection (parâmetro `id` concatenado na query) e a chave de API em código | `Statement` + concatenação substituídos por `PreparedStatement` com parâmetro `?`, dentro de `try-with-resources`. O achado da chave saiu com a correção nº 1 | `src/main/java/com/unifebe/devsecops/controller/AccountController.java` |
| 4 | `sca` | Trivy reportava `log4j-core:2.14.1` (CVE-2021-44228, Log4Shell) e CVEs HIGH/CRITICAL trazidas pelo Spring Boot 3.2.5 | Dependência `log4j-core` **removida** (o código usa apenas a API do log4j, que vem com o `spring-boot-starter-logging`); `spring-boot-starter-parent` atualizado de 3.2.5 para 3.5.16; `tomcat.version` sobrescrita para 10.1.60 | `pom.xml` |
| 5 | revisão manual | `com.google.code.gson:gson` declarada **duas vezes** e não importada em nenhum arquivo de `src/` | Ambas as declarações removidas | `pom.xml` |
| 6 | `dockerfile-lint` | Hadolint reportava `DL3007` (`FROM openjdk:latest`) e `DL3002` (`USER root`) | Multi-stage build: estágio de compilação em `maven:3.9-eclipse-temurin-17`, runtime em `eclipse-temurin:17-jre-alpine`; usuário de sistema `app` não-privilegiado; `COPY --chown` | `Dockerfile` |
| — | workflow | O passo "Empacotar aplicacao" (`mvn package`) tornou-se redundante com o multi-stage build | Passo e configuração de JDK removidos do job `build-and-push` | `.github/workflows/security.yml` |

## 2. Sobre o `.gitleaksignore`

O Gitleaks varre **todo o histórico de commits**, não apenas o estado atual dos arquivos. Remover as constantes de `AppConfig.java` não faz o gate passar: as chaves continuam existindo no commit em que foram introduzidas, e continuariam existindo mesmo se o arquivo fosse deletado.

Como as duas ocorrências são valores de exemplo públicos, documentados pelos próprios fornecedores, e nunca foram credenciais válidas, o tratamento adotado foi o reconhecimento explícito de cada achado pelo seu fingerprint (`commit:arquivo:regra:linha`), obtido da saída do próprio Gitleaks. Reescrever o histórico (`git filter-repo` / BFG + force-push) só se justifica quando o segredo é real — e, mesmo aí, a primeira medida é **rotacionar** a credencial, não reescrever a história.

Dois cuidados que valem para qualquer repositório:

* nenhum valor de chave foi copiado para os documentos, para o `README.md` ou para o próprio `.gitleaksignore` — um valor colado em qualquer arquivo gera uma ocorrência nova, com fingerprint próprio;
* se os commits forem reescritos (`amend`, `rebase`, `squash` + force-push), os hashes mudam e as entradas perdem validade: é preciso regenerá-las a partir do log do job `secret-scan`.

## 3. Validação local

Executada antes do push, com Docker (o repositório não tem Maven instalado localmente):

| Verificação | Comando equivalente | Resultado |
|---|---|---|
| Gitleaks sobre o histórico completo | `gitleaks detect --source . -v` | `no leaks found` (2 commits varridos) |
| Testes unitários | `mvn --batch-mode test` | `Tests run: 1, Failures: 0, Errors: 0` |
| Hadolint com o mesmo limiar do CI | `hadolint --failure-threshold warning Dockerfile` | Apenas `DL3066` (nível *info*, não bloqueia) |
| Build da imagem | `docker build -t banco-facil-api:test .` | Sucesso — multi-stage, imagem final apenas com JRE |
| Conteúdo do `.jar` empacotado | `jar tf target/*.jar` | `log4j-api-2.24.3`, `log4j-to-slf4j-2.24.3`, `tomcat-embed-core-10.1.60`, `spring-core-6.2.19`; **sem `log4j-core`, sem `gson`** |

O scan do Trivy não pôde ser concluído localmente: o Maven Central respondeu `429 Too Many Requests` (o mesmo limite temporário mencionado nas dicas da atividade), e o Trivy precisa resolver o POM pai remotamente para montar a árvore de dependências. A verificação fica por conta do job `sca` da pipeline, que roda a partir de outro IP. Caso o Trivy ainda aponte alguma CVE HIGH/CRITICAL, o ajuste é subir a versão do `spring-boot-starter-parent` ou sobrescrever no `pom.xml` a propriedade de versão do componente apontado — o mesmo procedimento já usado para o `tomcat.version`.

## 4. Roteiro de evidências (prints a capturar)

A cada push na `main`, a pipeline avança um gate. As capturas pedidas na avaliação:

1. **Primeira execução** — `secret-scan` 🔴, demais gates ⚪ *skipped*. *(Já ocorrida: execução registrada no `README.md` gerado pela pipeline.)*
2. **Falha de cada gate seguinte**, à medida que forem alcançados — `unit-tests`, `sast`, `sca`, `dockerfile-lint`. Como todas as correções foram aplicadas de uma vez, estas falhas intermediárias não vão ocorrer neste repositório; para produzi-las como evidência, aplique as correções **uma por vez**, com um push por correção (ver seção 5).
3. **Execução final totalmente verde**, incluindo `build-and-push` 🟢.
4. **Aba Summary** da execução final, com a tabela do job `security-summary` e o veredito `🟢 LIBERADO`.
5. **Packages** do repositório, mostrando a imagem publicada em `ghcr.io/<usuario>/<repositorio>`.
6. *(Opcional)* Tela da Branch Protection Rule configurada.

## 5. Como produzir as capturas de falha de cada gate

Se a evidência de cada gate falhando for exigida, a ordem abaixo reproduz a progressão, com um push por etapa (lembrando de rodar `git pull --rebase origin main` antes de cada push, porque o job `generate-readme` commita o `README.md` ao final de cada execução):

| Push | Aplicar apenas | Gate que passa a falhar |
|---|---|---|
| 1 | nada (estado original) | `secret-scan` |
| 2 | correção 1 (`AppConfig.java` + `.gitleaksignore`) | `unit-tests` |
| 3 | correção 2 (`PaymentService.java`) | `sast` |
| 4 | correção 3 (`AccountController.java`) | `sca` |
| 5 | correções 4 e 5 (`pom.xml`) | `dockerfile-lint` |
| 6 | correção 6 (`Dockerfile`) | nenhum — pipeline verde e imagem publicada |

## 6. Branch Protection (item opcional)

Configuração sugerida em **Settings → Branches → Add branch protection rule**, para o padrão `main`:

* *Require a pull request before merging* — com pelo menos 1 aprovação;
* *Require status checks to pass before merging* — marcando os cinco checks pelo **nome do job**: `Deteccao de Segredos (Gitleaks)`, `Testes Unitarios (Maven)`, `Analise Estatica de Codigo (Semgrep)`, `Analise de Dependencias (Trivy)` e `Lint do Dockerfile (Hadolint)`. O GitHub só lista um check depois que ele rodou ao menos uma vez;
* *Require branches to be up to date before merging*;
* *Do not allow bypassing the above settings* — sem isso, administradores contornam a regra.

Duas observações práticas: em repositório privado, o recurso depende do plano da conta; e o job `generate-readme` faz push direto na `main`, então passará a falhar quando a regra exigir pull request. Isso é esperado e não afeta os cinco gates.
