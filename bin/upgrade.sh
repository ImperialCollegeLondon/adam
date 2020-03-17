#!/bin/bash -ex

prog="adam"

service $prog stop

cd /tmp
curl -LO http://cisbic.bioinformatics.ic.ac.uk/files/$prog/$prog-latest.tar.bz2
tar xf /tmp/$prog-latest.tar.bz2 -C /opt
rm -f /tmp/$prog-latest.tar.bz2

service $prog start
