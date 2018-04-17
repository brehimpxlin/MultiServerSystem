# Project 1 Plan

## The aim of the project

- Developing a distributed application.
- To have a understanding of the principles and paradigms unerlying distruted software sytems.

## A high-level task breakdown to achieve the aim

- Group meeting - Weekly meeting is required and all the group members are expected to attend.

- Authentication

  - `AUTHENTICATE`/`AUTHENTICATION_FAIL`

- Genneral fail

  - `INVALID_MESSAGE`

- Register - Allow users to register for a new account

  - `REGISTER`/`REGISTER_SUCCESS`/`REGISTER_FAILED`/`LOCK_REQUEST`/`LOCK_DENIED`/`LOCK_ALLOWED`

- Login system - Allow users connect to the server, and server redirect when load is high

  - `LOGIN`/`LOGIN_SUCCESS`/`LOGIN_FAILED`/`LOGOUT`/`REDIRECT`/`LOGIN_FAIL`/`LOGOUT`

- Activity - Client can send activity to server / Server receive activity and broadcast to other servers and clients / Client can receive anctivity from server and display

  - `Activity_Message` - Client -> Server 
  - `Activity_Broadcast` - Server -> Server & Client

- Server_Announce

  - `SERVER_ANNOUNCE`

## Assignment of tasks to individuals in the group 

Register/GUI/ - ZICAN YAO

Login / redirect / - PEIXIN LIN

Activity - ~~WEIREN CHEN~~ MINGCHI ZHANG

server_anncounce / Authentication / ~~Interaction / Integration~~ - ~~MINGCHI ZHANG~~ WEIREN CHEN



## Expected completion of tasks

A usable multi-server system with a simple front-end GUI.

