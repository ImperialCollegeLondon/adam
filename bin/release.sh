#!/bin/bash -ex

prog="adam"

rm -fr /tmp/$prog
REV=$(svn export https://cisbic.bioinformatics.ic.ac.uk/svn/cisbic/$prog /tmp/$prog | grep "^Exported revision" | egrep -o "[0-9]+")

tar cfj /tmp/$prog-source-r$REV.tar.bz2 -C /tmp $prog

echo r$REV >/tmp/$prog/VERSION
play dependencies /tmp/$prog --forceCopy --forProd
cp /tmp/$prog/conf/application-site.conf.template /tmp/$prog/conf/application-site.conf
play precompile /tmp/$prog
rm /tmp/$prog/conf/application-site.conf
find /tmp/$prog/app -mindepth 1 -maxdepth 1 ! -iname views -exec rm -r {} \;
tar cfj /tmp/$prog-r$REV.tar.bz2 -C /tmp $prog

DEST=/data/www/80/cisbic.bioinformatics.ic.ac.uk/htdocs/files/$prog
scp /tmp/$prog-r$REV.tar.bz2 /tmp/$prog-source-r$REV.tar.bz2 bss-srv4:$DEST
ssh bss-srv4 "sed -i 's/r[0-9]\+/r$REV/' $DEST/.htaccess"
