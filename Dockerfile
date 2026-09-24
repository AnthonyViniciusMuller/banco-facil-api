# =====================================================================
# CORRECAO (Hardening de container)
#
# Problemas da versao anterior:
#   - DL3007: imagem base "openjdk:latest" (tag movel, build nao
#     reproduzivel e sem garantia de qual JDK/SO esta sendo usado);
#   - DL3002: ultimo USER definido como "root", violando o Principio
#     do Menor Privilegio;
#   - imagem base com JDK completo + ferramentas de build em producao,
#     ampliando desnecessariamente a superficie de ataque.
#
# Correcoes aplicadas: tag fixa, multi-stage build (JDK so na etapa de
# compilacao, apenas o JRE na imagem final) e execucao como usuario
# nao-privilegiado.
# =====================================================================

# ---------------------------------------------------------------------
# Estagio 1 - BUILD: compila e empacota a aplicacao.
# Nada deste estagio (Maven, JDK, codigo-fonte, cache ~/.m2) vai para a
# imagem final.
# ---------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# Copiar apenas o pom.xml primeiro faz o download das dependencias virar
# uma camada propria, reaproveitada enquanto o pom nao mudar.
COPY pom.xml ./
RUN mvn --batch-mode dependency:go-offline

COPY src ./src
RUN mvn --batch-mode -DskipTests package

# ---------------------------------------------------------------------
# Estagio 2 - RUNTIME: apenas o JRE e o .jar.
# ---------------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine

# Usuario de sistema sem shell e sem privilegios (PoLP). Se a aplicacao
# for comprometida, o atacante nao ganha root dentro do container — o que
# reduz a chance de escapar para o host via kernel/montagens.
RUN addgroup -S app && adduser -S app -G app

WORKDIR /app

COPY --from=builder --chown=app:app /build/target/banco-facil-api-0.0.1-SNAPSHOT.jar app.jar

USER app

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]
