#!/bin/bash

# Start 7 network nodes, then allocate the resources 4 times
java NetworkNode -ident 1 -tcpport 9000 A:4 &
sleep 1
java NetworkNode -ident 2 -tcpport 9001 -gateway localhost:9000 B:2 &
sleep 1
java NetworkNode -ident 3 -tcpport 9002 -gateway localhost:9000 C:2 &
sleep 1
java NetworkNode -ident 4 -tcpport 9003 -gateway localhost:9001 D:1 &
sleep 1
java NetworkNode -ident 5 -tcpport 9004 -gateway localhost:9001 E:1 &
sleep 1
java NetworkNode -ident 6 -tcpport 9005 -gateway localhost:9002 F:1 &
sleep 1
java NetworkNode -ident 7 -tcpport 9006 -gateway localhost:9002 G:1 &
sleep 1
java NetworkClient -ident 1 -gateway localhost:9000 A:1 B:1 D:1
java NetworkClient -ident 2 -gateway localhost:9000 A:1 C:1 E:1
java NetworkClient -ident 1 -gateway localhost:9000 A:1 B:1 F:1
java NetworkClient -ident 2 -gateway localhost:9000 A:1 C:1 G:1
java NetworkClient -gateway localhost:9000 terminate
