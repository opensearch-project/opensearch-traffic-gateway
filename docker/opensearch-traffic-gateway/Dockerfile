FROM amazoncorretto:17

WORKDIR /home/corretto/app
ADD proxy-server/build/distributions/proxy-server-0.0.1.tar .
ENV PATH="${PATH}:/home/corretto/app/proxy-server-0.0.1/bin"
ENV PROXY_SERVER_OPTS="-Dlog4j2.configurationFile=/home/corretto/app/proxy-server-0.0.1/conf/log4j2.xml"

RUN cp $JAVA_HOME/lib/security/cacerts /home/corretto/app/kafka.client.truststore.jks

ENTRYPOINT [ "proxy-server" ]
