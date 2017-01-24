#!/bin/bash
COMPONENT=osgp-adapter-protocol-oslp
docker build -t $COMPONENT ./
docker run -it --rm -e "DBHOST=10.0.2.15" -e "DBPORT=5432" -e "JMSHOST=10.0.2.15" -e "JMSPORT=61616" $COMPONENT
