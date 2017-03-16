FROM tomcat:7.0.73-jre8-alpine

ENV JAVA_OPTS="-Xms256m -Xmx500m"

MAINTAINER smart society services

ADD target/*.war /usr/local/tomcat/webapps

ADD https://jdbc.postgresql.org/download/postgresql-9.4.1212.jar /usr/local/tomcat/lib

CMD ["catalina.sh", "run"]
