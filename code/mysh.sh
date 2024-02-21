#!/bin/sh

javac -encoding UTF-8 -cp . ticketingsystem/Test.java
java -cp . ticketingsystem/Test 50 20 100 30 64
