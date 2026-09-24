# Parte 2 — Discussão Final: do Shift Left ao Shift Right

**Atividade:** Da Vulnerabilidade ao Deploy Seguro — BancoFácil Digital (item 3.6)

---

## Classificação dos controles implementados

A pipeline implantada tem cinco gates, e eles não se distribuem igualmente na linha do tempo.

**Exclusivamente Shift Left.** Três controles não têm contraparte útil depois do deploy, porque seu insumo é o artefato estático e ele não muda:

* **SAST (Semgrep).** Analisa o código-fonte. Reexecutá-lo em produção examinaria exatamente o mesmo código já avaliado no commit. A correção da SQL Injection em `AccountController` foi feita de uma vez, no lugar certo.
* **Testes unitários (Maven/JUnit).** O bug de `applyDiscount` — desconto dez vezes menor que o devido, uma falha de integridade financeira — foi barrado antes de existir artefato.
* **Lint de Dockerfile (Hadolint).** Avalia instruções de build. Depois que a imagem existe, o Dockerfile já não é o objeto relevante: o objeto é a imagem.

**Shift Left com extensão natural para Shift Right.** Os outros dois só cobrem metade do problema se ficarem no CI:

* **Detecção de segredos (Gitleaks).** No CI, impede a entrada de novos segredos. À direita, o que importa é o ciclo de vida da credencial já em uso: rotação automática pelo cofre, alerta de uso a partir de origem ou horário anômalo, e revogação imediata mediante suspeita. Vale a lição central do incidente: o que encerra uma exposição é a rotação, não a remoção do código. Como as chaves desta atividade eram valores públicos de exemplo, a aceitação documentada por fingerprint no `.gitleaksignore` substituiu a rotação — mas com uma credencial real a ordem seria rotacionar primeiro, sempre.
* **SCA (Trivy).** É o caso mais claro. A atualização do `log4j-core` e do Spring Boot, e a remoção do `gson`, resolveram o inventário **de hoje**. Mas o gate avalia o mundo no dia do commit, e o Log4Shell provou que um artefato aprovado em 8 de dezembro de 2021 estava crítico no dia 9 sem nenhuma linha alterada. O mesmo Trivy, rodando periodicamente contra as imagens já publicadas no GHCR, é um controle de Shift Right.

O **build-and-push** para o GHCR é a fronteira: tudo à esquerda dele é prevenção, tudo à direita é detecção e contenção.

## O que falta para a BancoFácil fechar o ciclo

Com 40 microsserviços e quatro squads, cinco lacunas são prioritárias:

1. **Branch Protection na `main` exigindo os cinco checks.** Enquanto houver caminho de push direto, a pipeline é uma recomendação, não um gate — e este é o controle mais barato da lista.
2. **SBOM por artefato implantado** (CycloneDX via Trivy ou Syft), armazenado junto com a imagem, mais reavaliação contínua do inventário contra novas CVEs (Dependabot/Renovate no repositório, varredura agendada do registry). É o que transforma "onde nós usamos isso?" de semanas de arqueologia em uma consulta. Dependabot resolve o repositório; o registry precisa de varredura própria.
3. **DAST (OWASP ZAP) em staging, e depois em produção sob janela controlada.** Nenhum dos gates atuais enxerga configuração de ambiente: Actuator exposto, cookie sem `HttpOnly`, cabeçalho de segurança ausente, TLS mal configurado. Também nenhum enxerga autorização quebrada — o endpoint `/conta?id=` agora está a salvo de injeção, mas continua sem verificar se o usuário autenticado pode ler aquela conta, e esse IDOR é invisível para SAST.
4. **Observabilidade com detecção de exploração.** WAF com regras para padrões conhecidos (o `${jndi:` do Log4Shell é o exemplo), logs centralizados em SIEM, alertas de comportamento anômalo. É o que dá capacidade de resposta na janela entre a divulgação de uma CVE e o deploy da correção — a mitigação virtual enquanto a definitiva é construída.
5. **Liberação progressiva** (feature flags, canary, blue/green) com rollback automatizado. Limita o raio de impacto de uma correção emergencial e reduz o custo de errar — o que, na prática, é o que permite corrigir rápido.

Some-se a isso o que precede tudo: **threat modeling** no design e **pre-commit hooks** com secret scanning local, deslocando o feedback do CI para o editor do desenvolvedor.

## Conclusão

A pipeline garante que a BancoFácil não reintroduza os problemas que já conhece. Ela não garante que a empresa saiba o que está rodando em produção nem que perceba quando o que está rodando se torna vulnerável. O incidente da chave exposta foi resolvido à esquerda; o próximo — uma CVE publicada depois do deploy, ou uma falha de autorização que nenhum scanner estático enxerga — só será detectado à direita. **Shift Left reduz a probabilidade; Shift Right reduz o tempo de detecção e o impacto.** Programa de DevSecOps completo precisa dos dois.
