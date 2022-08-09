ARG BASE_BUILDER_IMAGE=registry.lab.boozallencsn.com/ironbank-mirror/ironbank/redhat/ubi/ubi8:8.6
ARG IB_NEO4J_IMAGE=registry.lab.boozallencsn.com/ironbank-mirror/ironbank/opensource/neo4j/neo4j:4.4.7
ARG NEO4J_PLUGIN_IMAGE=registry.lab.boozallencsn.com/docker-mirror/library/neo4j:4.4.7
#####
## ## BUILDER IMAGE
#####

FROM --platform=x86_64 "${BASE_BUILDER_IMAGE}" AS builder
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

#####
## ## MAIN IMAGE
#####

FROM --platform=x86_64 "${NEO4J_PLUGIN_IMAGE}" AS plugins

USER neo4j

ENV NEO4J_dbms_security_procedures_unrestricted="gds.*,apoc.*,policy.*"
ENV NEO4J_dbms_security_procedures_whitelist="gds.*,apoc.*,policy.*"
ENV NEO4J_ACCEPT_LICENSE_AGREEMENT=yes

# install custom config
COPY --chown=neo4j:neo4j ./docker/neo4j.conf /conf/neo4j.conf

# download & configure neo4j labs plugins + check permissions
ARG NEO4JLABS_PLUGINS='["graph-data-science", "apoc"]'
RUN env NEO4JLABS_PLUGINS="${NEO4JLABS_PLUGINS}" \
  bash /docker-entrypoint.sh dump-config

# install custom plugins
COPY --from=builder --chown=neo4j:neo4j /build-out/ /var/lib/neo4j/plugins/

FROM --platform=x86_64 "${IB_NEO4J_IMAGE}" 

USER neo4j

ENV NEO4J_dbms_security_procedures_unrestricted="gds.*,apoc.*,policy.*"
ENV NEO4J_dbms_security_procedures_whitelist="gds.*,apoc.*,policy.*"
ENV NEO4J_ACCEPT_LICENSE_AGREEMENT=yes

# install custom config
COPY --chown=neo4j:neo4j ./docker/neo4j.conf /conf/neo4j.conf

# copy all plugins from previous 2 stages to ironbank base image
COPY --from=plugins --chown=neo4j:neo4j /var/lib/neo4j/plugins/ /var/lib/neo4j/plugins/

# check
ARG NEO4JLABS_PLUGINS='["graph-data-science", "apoc"]'
RUN env NEO4JLABS_PLUGINS="${NEO4JLABS_PLUGINS}" \
  bash /docker-entrypoint.sh dump-config