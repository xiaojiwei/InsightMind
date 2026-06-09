// file Hello.g4
// Define a grammar called Hello
grammar Hello; // 1. grammer name
@header { package com.graphinsight.indicator.lax.helloworld.gen; } // 2. java package
r  : 'hello' ID ;         // 3. match keyword hello followed by an identifier
ID : [a-z]+ ;             // match lower-case identifiers
WS : [ \t\r\n]+ -> skip ;

