#!/bin/bash
COMPONENT=signing-server
docker build -t $COMPONENT ./
docker run -it --rm -e "JMSHOST=10.0.2.15" -e "JMSPORT=61616" $COMPONENT
