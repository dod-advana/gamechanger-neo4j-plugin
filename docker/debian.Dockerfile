ARG BASE_BUILDER_IMAGE=registry.lab.boozallencsn.com/ironbank-mirror/ironbank/redhat/ubi/ubi8:8.6
ARG IB_NEO4J_IMAGE=registry.lab.boozallencsn.com/ironbank-mirror/ironbank/opensource/neo4j/neo4j:4.4.7
ARG NEO4J_PLUGIN_IMAGE=registry.lab.boozallencsn.com/docker-mirror/library/neo4j:4.4.7
ARG GDS_VERSION=2.1.9

#####
## ## BUILDER IMAGE
#####

FROM --platform=x86_64 "${BASE_BUILDER_IMAGE}" AS builder
USER root

# ensure RHEL host repos are enabled (undo what's done here https://repo1.dso.mil/dsop/redhat/ubi/ubi8/-/blob/development/Dockerfile#L22)
RUN sed -i "s/enabled=0/enabled=1/" /etc/dnf/plugins/subscription-manager.conf\
  && yum update -y \
  && yum update systemd \
  && rpm --import https://yum.corretto.aws/corretto.key \
  && yum install -y \
  git \
  swig \
  && yum install -y \
  glib2 \
  file \
  wget \
  maven \
  python38 \
  cairo \
  unzip \
  && yum install -y https://corretto.aws/downloads/latest/amazon-corretto-11-x64-linux-jdk.rpm \
  && yum clean all \
  && rm -rf /var/cache/yum

ENV JAVA_HOME=/etc/alternatives/java_sdk

COPY ./ /build-src/
WORKDIR /build-src/

RUN \
  mvn clean package \
  && mkdir /build-out \
  && find /build-src/target/ -type f -name "gamechanger-plugin-*.jar" -exec cp {} /build-out/ \;

# manually grab plugins with specified versions to fix anchore finding VULNDB-93555+graph-data-science.jar:commons-codec

RUN chmod +x /build-src/docker/plugin-downloader.sh \
  && /build-src/docker/plugin-downloader.sh

FROM gradle:7.4.2-jdk11 as gds-builder
ARG GDS_VERSION=2.1.9

WORKDIR /app

RUN git clone https://github.com/neo4j/graph-data-science.git \
  && cd graph-data-science \
  && git checkout tags/${GDS_VERSION} \
  && chmod +x gradlew \
  && ./gradlew :open-packaging:shadowCopy

# /app/build/distribution/open-gds-${GDS_VERSION}.jar
#####
## ## MAIN IMAGE
#####

# install custom plugins

FROM --platform=x86_64 "${IB_NEO4J_IMAGE}" 

USER root

RUN sed -i "s/enabled=0/enabled=1/" /etc/dnf/plugins/subscription-manager.conf\
  && yum update -y \
  && yum update systemd

USER neo4j

ARG GDS_VERSION=2.1.9

ENV NEO4J_dbms_security_procedures_unrestricted="gds.*,apoc.*,policy.*"
ENV NEO4J_dbms_security_procedures_whitelist="gds.*,apoc.*,policy.*"
ENV NEO4J_ACCEPT_LICENSE_AGREEMENT=yes

ARG APP_UID=1001
ARG APP_GID=1001


# install custom config
COPY --chown=neo4j:neo4j ./docker/neo4j.conf /conf/neo4j.conf

# copy all plugins from previous stages to ironbank base image
COPY --from=builder --chown=neo4j:neo4j /build-out/ /var/lib/neo4j/plugins/
COPY --from=gds-builder --chown=neo4j:neo4j /app/graph-data-science/build/distributions/open-gds-${GDS_VERSION}.jar /var/lib/neo4j/plugins/graph-data-science.jar

# ARG NEO4JLABS_PLUGINS='["apoc"]'
# RUN env NEO4JLABS_PLUGINS="${NEO4JLABS_PLUGINS}" \
#   bash /docker-entrypoint.sh dump-config
RUN bash /docker-entrypoint.sh dump-config

