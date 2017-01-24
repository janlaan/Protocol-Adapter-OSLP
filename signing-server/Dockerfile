FROM tomcat:7.0.73-jre8

MAINTAINER smart society services

ADD target/*.war /usr/local/tomcat/webapps
ADD https://github.com/OSGP/Config/blob/development/certificates/oslp/oslp_test_ecdsa_private.der /etc/ssl/certs

CMD ["catalina.sh", "run"]
