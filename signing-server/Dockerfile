FROM tomcat:7.0.73-jre8-alpine

MAINTAINER smart society services

ENV JAVA_OPTS="-Xms256m -Xmx500m"

ADD target/*.war /usr/local/tomcat/webapps
ADD https://github.com/OSGP/Config/raw/development/certificates/oslp/oslp_test_ecdsa_private.der /etc/ssl/certs

CMD ["catalina.sh", "run"]
