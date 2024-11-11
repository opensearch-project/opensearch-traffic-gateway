FROM openjdk:17-jdk-buster

WORKDIR /home/openjdk/app
ADD proxy-server/build/distributions/proxy-server-0.0.1.tar .
ENV PATH="${PATH}:/home/openjdk/app/proxy-server-0.0.1/bin"
ENV PROXY_SERVER_OPTS="-Dlog4j2.configurationFile=/home/openjdk/app/proxy-server-0.0.1/conf/log4j2.xml"

RUN cp /usr/local/openjdk-17/lib/security/cacerts /home/openjdk/app/kafka.client.truststore.jks

ENTRYPOINT [ "proxy-server" ]
