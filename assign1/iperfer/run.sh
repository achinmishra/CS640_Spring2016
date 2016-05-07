#!/bin/bash

if test $1 == "make"
then
javac iperfer/*.java
cp *.class ../
fi


if test $1 == "clean"
then
rm *.class
fi


