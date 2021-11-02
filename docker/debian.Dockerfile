ARG BASE_BUILDER_IMAGE=centos:8.4.2105 AS builder
FROM --platform=x86_64 "${BASE_BUILDER_IMAGE}"

USER root

RUN yum install -y https://corretto.aws/downloads/latest/amazon-corretto-11-x64-linux-jdk.rpm
ENV JAVA_HOME=/etc/alternatives/java_sdk

RUN yum install -y maven

COPY ./ /build-src/
WORKDIR /build-src/

RUN \
  mvn clean package \
  && mkdir /build-out \
  && find /build-src/target/ -type f -name "gamechanger-plugin-*.jar" -exec cp {} /build-out/ \;


ARG BASE_NEO4J_IMAGE=neo4j:4.2.3
FROM --platform=x86_64 "${BASE_IMAGE}"

ENV NEO4J_dbms_security_procedures_unrestricted="gds.*,apoc.*"
ENV NEO4J_dbms_security_procedures_whitelist="gds.*,apoc.*"
ENV NEO4J_ACCEPT_LICENSE_AGREEMENT=yes

# install custom config
COPY --chown=neo4j:neo4j ./docker/neo4j.conf /conf/neo4j.conf

# download & configure neo4j labs plugins + check permissions
ARG NEO4JLABS_PLUGINS='["graph-data-science", "apoc"]'
RUN env NEO4JLABS_PLUGINS="${NEO4JLABS_PLUGINS}" \
    bash /docker-entrypoint.sh dump-config

# install custom plugins
COPY --from=builder --chown=neo4j:neo4j /build-out/ /var/lib/neo4j/plugins/
